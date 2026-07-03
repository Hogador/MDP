#!/usr/bin/env bash
# swarm-stats.sh v2 — статистика сворма за N дней
set -uo pipefail

DAYS=7
for arg in "$@"; do
  case "$arg" in
    --days=*) DAYS="${arg#*=}" ;;
    --help|-h) echo "Usage: $0 [--days N]"; exit 0 ;;
  esac
done

STATS_FILE=".hive/stats/daily.jsonl"
FB_FILE=".hive/stats/fallback.jsonl"

if [ ! -f "$STATS_FILE" ]; then
  echo "Файл статистики не найден: $STATS_FILE"
  exit 1
fi

CUTOFF=$(date -d "-$DAYS days" +%Y-%m-%d 2>/dev/null || date -v-${DAYS}d +%Y-%m-%d)

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  Swarm v10 — статистика за $DAYS дней (с $CUTOFF)"
echo "═══════════════════════════════════════════════════════════════"

python3 <<PYEOF
import json
from pathlib import Path
from collections import Counter, defaultdict

stats_file = Path("$STATS_FILE")
fb_file = Path("$FB_FILE")
cutoff = "$CUTOFF"

records = []
for line in stats_file.read_text(encoding="utf-8").splitlines():
    if not line.strip(): continue
    try:
        r = json.loads(line)
        if r.get("date", "") >= cutoff:
            records.append(r)
    except Exception:
        continue

total = len(records)
print(f"\n  Всего задач: {total}")
if total == 0:
    print("\n  Записей за период нет. Запустите задачу через сворм.")
    print("═══════════════════════════════════════════════════════════════")
    exit(0)

# 1. NSM-1
print("\n─── 1. NSM-1 (Success Rate) ───")
status_counts = Counter(r.get("status", "unknown") for r in records)
completed = status_counts.get("completed", 0)
nsm1 = (completed / total * 100) if total else 0
print(f"  Цель: >=80% | Текущее: {nsm1:.0f}% ({completed}/{total})")
for s in ["completed", "partial", "blocked", "escalated", "unknown"]:
    if status_counts.get(s):
        marker = "OK" if s == "completed" else ("!" if s == "partial" else "X")
        print(f"    [{marker}] {s}: {status_counts[s]}")
gap = max(0, 80 - nsm1)
if gap > 0: print(f"  До цели: -{gap:.0f}%")
else: print("  Цель достигнута!")

# 2. Токены по провайдерам
print("\n─── 2. Расход токенов по провайдерам ───")
prov_tokens = defaultdict(int)
for r in records:
    agents = r.get("models_used", {})
    if not agents: continue
    per_agent = r.get("tokens", {}).get("total", 0) / max(1, len(agents))
    for agent, model in agents.items():
        prov = model.split("@")[-1].split("/")[0] if "@" in model else model.split("/")[0]
        prov_tokens[prov] += int(per_agent)
total_tokens = sum(prov_tokens.values()) or 1
for prov, tok in sorted(prov_tokens.items(), key=lambda x: -x[1]):
    pct = tok / total_tokens * 100
    print(f"  {prov:25s} {tok:>10,} tok ({pct:.0f}%)")
print(f"  {'TOTAL':25s} {total_tokens:>10,} tok")

# 3. Стабильные модели
print("\n─── 3. Стабильные модели (без fallback) ───")
model_total = Counter()
model_ok = Counter()
for r in records:
    fallbacks = set()
    for fb in r.get("fallbacks_triggered", []):
        if ":" in fb: fallbacks.add(fb.split(":")[0])
    for agent, model in r.get("models_used", {}).items():
        model_total[model] += 1
        if agent not in fallbacks: model_ok[model] += 1
stable = [(m, model_ok[m], model_total[m]) for m in model_total]
stable.sort(key=lambda x: -x[1]/x[2])
for model, ok, tot in stable[:5]:
    pct = ok/tot*100
    print(f"  {pct:5.0f}%  {ok}/{tot}  {model}")

# 4. Проблемные модели
print("\n─── 4. Проблемные модели (частые fallback) ───")
problematic = [(m, model_total[m]-model_ok[m], model_total[m]) for m in model_total if model_total[m] >= 1]
problematic.sort(key=lambda x: -(x[1]/x[2]) if x[2] else 0)
for model, fb, tot in problematic[:5]:
    if fb > 0:
        pct = fb/tot*100
        print(f"  {pct:5.0f}%  {fb}/{tot}  {model}")
if not any(p[1] > 0 for p in problematic): print("  Нет fallback за период")

# 5. Агенты с fallback
print("\n─── 5. Агенты с наибольшим числом fallback ───")
agent_fallbacks = Counter()
for r in records:
    for fb in r.get("fallbacks_triggered", []):
        if ":" in fb: agent_fallbacks[fb.split(":")[0]] += 1
for agent, count in agent_fallbacks.most_common(10):
    print(f"  {count} переключений  {agent}")
if not agent_fallbacks: print("  Нет fallback за период")

# 6. Тренд по дням
print("\n─── 6. Тренд по дням ───")
by_date = defaultdict(lambda: {"total": 0, "completed": 0, "tokens": 0, "fallbacks": 0})
for r in records:
    d = r.get("date", "unknown")
    by_date[d]["total"] += 1
    if r.get("status") == "completed": by_date[d]["completed"] += 1
    by_date[d]["tokens"] += r.get("tokens", {}).get("total", 0)
    by_date[d]["fallbacks"] += len(r.get("fallbacks_triggered", []))
for d in sorted(by_date.keys()):
    s = by_date[d]
    rate = s["completed"]/s["total"]*100 if s["total"] else 0
    print(f"  {d}  tasks={s['total']:2d}  ok={rate:5.0f}%  tok={s['tokens']:>8,}  fb={s['fallbacks']}")

# 7. Fallbacks (если есть отдельный лог)
print("\n─── 7. FALLBACKS ЗА ПЕРИОД ───")
if fb_file.exists():
    fb_records = []
    for line in fb_file.read_text(encoding="utf-8").splitlines():
        if not line.strip(): continue
        try:
            r = json.loads(line)
            if r.get("ts", "")[:10] >= cutoff: fb_records.append(r)
        except: continue
    
    print(f"  Всего переключений: {len(fb_records)}")
    if fb_records:
        print("\n  Топ моделей которые падали (primary):")
        prim = Counter(r.get("primary","?") for r in fb_records)
        for m, c in prim.most_common(5): print(f"    {c}x  {m}")
        print("\n  Топ backup моделей которые выручили:")
        act = Counter(r.get("actual","?") for r in fb_records)
        for m, c in act.most_common(5): print(f"    {c}x  {m}")
        print("\n  Топ агентов с fallback:")
        ag = Counter(r.get("agent","?") for r in fb_records)
        for a, c in ag.most_common(5): print(f"    {c}x  {a}")
else:
    print("  Файл fallback.jsonl не найден")

print("\n═══════════════════════════════════════════════════════════════")
PYEOF
