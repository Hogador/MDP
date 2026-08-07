# LESSON: Audit Verification Gate — 2026-07-20

## Правило
**Forge test ОБЯЗАН быть запущен до пометки любого finding как VERIFIED_FIXED.**

## Контекст
Предыдущий аудит пометил "98 VERIFIED_FIXED", но forge test никто не запускал — 36 тестов падали. VERIFIED без запуска тестов = CLAIMED на словах.

## Pipeline
```
finding → CLAIMED_FIXED → forge test (полный прогон) → 0 failed? → VERIFIED_FIXED
                                                                    ↓
                                                         >0 failed → оставить CLAIMED_FIXED
```

## Формат в аудиторском отчёте
```markdown
### F-XXX: [название]
- Status: VERIFIED_FIXED
- Verification: `forge test`: 403 passed, 0 failed (commit 282d549)
```

## Почему это важно
- "Код выглядит правильно" ≠ "код работает"
- Test может пройти по ошибке (мягкие assertion, mock и т.д.)
- Но test НЕ МОЖЕТ пройти если баг реален — это обратимая проверка
