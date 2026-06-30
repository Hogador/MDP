# MDAOPay Swarm v2.0 — Инструкция внедрения

## Шаг 1. Backup текущей конфигурации

```bash
cd ~/project/MDAOPay
mkdir -p .backup/$(date +%Y%m%d)
cp -r .opencode AGENTS.md docs/orchestration .backup/$(date +%Y%m%d)/
mv ~/.config/opencode/opencode-swarm.json ~/.config/opencode/opencode-swarm.json.bak
echo "✅ Backup created"
```

## Шаг 2. Настроить OpenRouter API key

```bash
# Получить ключ на https://openrouter.ai/keys
export OPENROUTER_API_KEY="sk-or-v1-ВАШ_КЛЮЧ"

# Добавить в ~/.bashrc или ~/.zshrc
echo 'export OPENROUTER_API_KEY="sk-or-v1-ВАШ_КЛЮЧ"' >> ~/.bashrc
source ~/.bashrc

# Проверить
echo $OPENROUTER_API_KEY | head -c 20
```

## Шаг 3. Создать структуру security/

```bash
cd ~/project/MDAOPay
mkdir -p security scripts/prompts .opencode/agents

# Скопировать файлы из этого архива:
# security/FINDINGS.md
# security/ERRORS-MEMORY.md
# security/ANTI-PATTERNS.md
# security/FIX-PATTERNS.md
# security/TEST-COVERAGE-MAP.md
```

## Шаг 4. Заменить .opencode/opencode-swarm.json

```bash
# Скопировать .opencode/opencode-swarm.json из этого архива
# (заменяет существующий на 14 ролей с OpenRouter free моделями)
```

## Шаг 5. Создать 8 agent definitions

```bash
# Скопировать файлы из .opencode/agents/ в этом архиве:
# - verifier.md
# - librarian.md
# - tester.md
# - researcher-contracts.md
# - researcher-backend.md
# - researcher-relay.md
# - researcher-infra.md
# - researcher-mobile.md
```

## Шаг 6. Создать 21 prompt файл

```bash
# Скопировать все файлы из scripts/prompts/ в этом архиве
# (01-secrets.md через 21-consolidate.md)
```

## Шаг 7. Обновить AGENTS.md

```bash
# Заменить содержимое AGENTS.md на AGENTS.md из этого архива
```

## Шаг 8. Создать скрипты

```bash
# Скопировать:
# - scripts/spawn-full-audit.sh
# - scripts/audit-simple.sh
# - scripts/verify-findings.py

chmod +x scripts/spawn-full-audit.sh scripts/audit-simple.sh scripts/verify-findings.py
```

## Шаг 9. Обновить Makefile

```bash
# Добавить targets из Makefile.audit из этого архива в существующий Makefile
# (или создать новый если нет)
```

## Шаг 10. Обновить CI

```bash
# Добавить job findings-verify в .github/workflows/ci.yml (см. ci.yml.additions)
```

## Шаг 11. Активировать git hooks

```bash
git config core.hooksPath .githooks
```

## Шаг 12. Тестовый запуск

```bash
make swarm-config       # показать роли
make findings-stats     # dashboard
./scripts/audit-simple.sh  # быстрый тест
```

## Шаг 13. Полный аудит

```bash
./scripts/spawn-full-audit.sh
```

## Шаг 14. Commit

```bash
git add -A
git commit -m "feat: swarm v2.0 — 14 roles, OpenRouter free models, FINDINGS.md registry

- Verifier: nemotron-3-ultra-550b (best reasoning, no limit)
- Researchers (4 stacks): qwen3-coder (code specialist)
- Librarian: lfm-2.5-1.2b (fast metadata)
- 21 audit prompts in 6 phases
- security/FINDINGS.md (backfill from 7 waves, ~36 findings)
- security/ERRORS-MEMORY.md (63 patterns)
- security/ANTI-PATTERNS.md (7 forbidden approaches)
- security/FIX-PATTERNS.md (approved solutions)
- scripts/spawn-full-audit.sh (parallel 18 phases)
- scripts/verify-findings.py (CI enforcement)
"
```
