#!/usr/bin/env bash
# swarm-stats.sh — статистика сворма за N дней
# Запуск: ./scripts/swarm-stats.sh [--days N]
# По умолчанию: 7 дней

set -uo pipefail
DAYS=7
for arg in "$@"; do
  case "$arg" in
    --days=*) DAYS="${arg#*=}" ;;
    --help|-h)
      echo "Usage: $0 [--days N]"
      echo "  --days N  За сколько дней смотреть (по умолчанию 7)"
      exit 0 ;;
  esac
done

STATS_FILE=".hive/stats/daily.jsonl"
if [ ! -f "$STATS_FILE" ]; then
  echo "Файл статистики не найден: $STATS_FILE"
  echo "Запустите первую задачу через сворм — файл наполнится."
  exit 1
fi

# Срез по дате
CUTOFF=$(date -d "-$DAYS days" +%Y-%m-%d 2>/dev/null || date -v-${DAYS}d +%Y-%m-%d)

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  Swarm v10 — статистика за $DAYS дней (с $CUTOFF)"
echo "═══════════════════════════════════════════════════════════════"

# Подсчёт через Python (надёжнее чем bash awk для JSON)
python3 <<PYEOF
import json
from pathlib import Path
from collections import Counter, defaultdict
from datetime import datetime

stats_file = Path("$STATS_FILE")
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

if not records:
    print()
# 7. Fallbacks
echo ""
echo "─── 7. FALLBACKS ЗА ПЕРИОД ───"
FB_FILE=".hive/stats/fallback.jsonl"
if [ -f "$FB_FILE" ]; then
    FB_COUNT=$(grep -c "agent" "$FB_FILE" 2>/dev/null || echo 0)
    echo "  Всего переключений: $FB_COUNT"
    echo ""
    echo "  Топ моделей (упали):"
    grep -o '"primary":"[^"]*"' "$FB_FILE" 2>/dev/null | sort | uniq -c | sort -rn | head -5 | while read line; do
        echo "    $line"
    done
    echo ""
    echo "  Топ backup моделей (выручили):"
    grep -o '"actual":"[^"]*"' "$FB_FILE" 2>/dev/null | sort | uniq -c | sort -rn | head -5 | while read line; do
        echo "    $line"
    done
    echo ""
    echo "  Топ агентов с fallback:"
    grep -o '"agent":"[^"]*"' "$FB_FILE" 2>/dev/null | sort | uniq -c | sort -rn | head -5 | while read line; do
        echo "    $line"
    done
else
    echo "  Файл fallback.jsonl не найден"
fi


    print("Записей за период нет. Запустите задачу через сворм.")
    print()
    exit(0)

total = len(records)
print(f"  Всего задач: {total}")
print()

# 1. NSM-1
print("─── 1. NSM-1 (Success Rate) ───")
status_counts = Counter(r.get("status", "unknown") for r in records)
completed = status_counts.get("completed", 0)
nsm1 = (completed / total * 100) if total else 0
print(f"  Цель: ≥80% | Текущее: {nsm1:.0f}% ({completed}/{total})")
for s in ["completed", "partial", "blocked", "escalated", "unknown"]:
    if status_counts.get(s):
        marker = "✅" if s == "completed" else ("⚠️" if s == "partial" else "❌")
        print(f"    {marker} {s}: {status_counts[s]}")
gap = max(0, 80 - nsm1)
if gap > 0:
    print(f"  До цели: -{gap:.0f}%")
else:
    print(f"  Цель достигнута!")
print()

# 2. Расход токенов
print("─── 2. Расход токенов по провайдерам ───")
prov_tokens = defaultdict(int)
for r in records:
    for agent, model in r.get("models_used", {}).items()).items():
        # Извлекаем провайдера (часть до /)
        provider = model.split("@")[-1].split("/")[0] if "@" in model else model.split("/")[0]
        tokens = r.get("tokens", {}).get("total", 0)
        # Делим поровну между всеми агентами в задаче (грубая оценка)
        agents_count = max(1, len(r.get("models_used", {})))
        prov_tokens[provider] += tokens // agents_count
total_tokens = sum(prov_tokens.values()) or 1
for prov, tok in sorted(prov_tokens.items(), key=lambda x: -x[1]):
    pct = tok / total_tokens * 100
    print(f"  {prov:25s} {tok:>10,} токенов ({pct:.0f}%)")
print(f"  {'ИТОГО':25s} {total_tokens:>10,} токенов")
print()

# 3. Стабильные модели
print("─── 3. Стабильные модели (без fallback) ───")
model_total = Counter()
model_ok = Counter()
for r in records:
    fallbacks = set()
    for fb in r.get("fallbacks_triggered", []):
        if ":" in fb:
            fallbacks.add(fb.split(":")[0])
    for agent, model in r.get("models_used", {}).items():
        model_total[model] += 1
        if agent not in fallbacks:
            model_ok[model] += 1
stable = [(m, model_ok[m], model_total[m]) for m in model_total if model_total[m] >= 1]
stable.sort(key=lambda x: -x[1]/x[2])
for model, ok, tot in stable[:5]:
    pct = ok/tot*100
    print(f"  {pct:5.0f}%  {ok}/{tot}  {model}")
print()

# 4. Проблемные модели
print("─── 4. Проблемные модели (частые fallback) ───")
problematic = [(m, model_total[m]-model_ok[m], model_total[m]) for m in model_total if model_total[m] >= 2]
problematic.sort(key=lambda x: -(x[1]/x[2]))
for model, fb, tot in problematic[:5]:
    if fb > 0:
        pct = fb/tot*100
        print(f"  {pct:5.0f}%  {fb}/{tot}  {model}")
if not any(p[1] > 0 for p in problematic):
    print("  Нет fallback за период")
print()

# 5. Проблемные агенты
print("─── 5. Агенты с наибольшим числом fallback ───")
agent_fallbacks = Counter()
for r in records:
    for fb in r.get("fallbacks_triggered", []):
        if ":" in fb:
            agent_fallbacks[fb.split(":")[0]] += 1
for agent, count in agent_fallbacks.most_common(10):
    print(f"  {count} переключений  {agent}")
if not agent_fallbacks:
    print("  Нет fallback за период")
print()

# 6. Тренд
print("─── 6. Тренд по дням ───")
by_date = defaultdict(lambda: {"total": 0, "completed": 0, "tokens": 0, "fallbacks": 0})
for r in records:
    d = r.get("date", "unknown")
    by_date[d]["total"] += 1
    if r.get("status") == "completed":
        by_date[d]["completed"] += 1
    by_date[d]["tokens"] += r.get("tokens", {}).get("total", 0)
    by_date[d]["fallbacks"] += len(r.get("fallbacks_triggered", []))
for d in sorted(by_date.keys()):
    s = by_date[d]
    rate = s["completed"]/s["total"]*100 if s["total"] else 0
    print(f"  {d}  tasks={s['total']:2d}  ok={rate:5.0f}%  tokens={s['tokens']:>8,}  fb={s['fallbacks']}")
print()
print("═══════════════════════════════════════════════════════════════")
PYEOF
