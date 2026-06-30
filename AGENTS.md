# MDAOPay Agent Rules v3.0

## SMART LOADING (CRITICAL)
Before ANY work, read in this order:
1. `security/FINDINGS-INDEX.md` (~80 lines) — NOT FINDINGS.md
2. `security/ERRORS-INDEX.md` (~50 lines) — NOT ERRORS-MEMORY.md
3. `docs/orchestration/CODE-INDEX.md` (~150 lines) — code map
4. For relevant findings: `security/findings/F-XXX.md` (one file)
5. For relevant code: read ONLY files/lines from CODE-INDEX
6. `security/ANTI-PATTERNS.md`
7. `security/FIX-PATTERNS.md`
8. `AGENTS.md` (this file)
9. `ponytail/AGENTS.md`

**FORBIDDEN:** Reading FINDINGS.md, ERRORS-MEMORY.md, or all code at once.
**EXCEPTION:** verifier during full verification.

## 5 Commands (via coordinator)
- «запусти аудит» — security audit
- «рассмотри внешний аудит» — integrate external report
- «проведи сверку» — code vs docs
- «реализуй функцию» — new feature
- «проверь код» — code review + ponytail

## Ponytail Philosophy
- YAGNI, stdlib first, no abstractions, minimum code

## Rules
- Russian always
- Confirmations depend on mode (fast/careful/hybrid)
- Ponytail audit before commit
- TDD update in same commit
- `bash scripts/generate-indexes.sh` after findings changes

## v3.1 Additions
- New agent: researcher-coverage (finds untested code)
- New command: «проверь покрытие» (coverage analysis)
- Pre-commit: warns about code changes without tests
- CI: coverage gate
