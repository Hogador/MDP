# Security Audit Process Rules

## Правило: CLAIMED_FIXED требует независимой верификации

**Статус:** ACTIVE (2026-07-16)

### Правило
Никто не может закрыть finding как CLAIMED_FIXED без:

1. **Тест-кейна** — конкретный assertion, который проходит (unit test, integration test, или Foundry test)
2. **Независимой верификации** — не тот же разработчик который чинил
3. **Записи в FINDINGS-INDEX.md** — кто верифицировал, когда, какой test-case

### Формат записи

```
| F-XXX | CLAIMED_FIXED | Verified by: @reviewer | Date: YYYY-MM-DD | Test: test_xxx() passes | Notes: ... |
```

### Процесс верификации

1. **Найти файл** из описания finding'а в FINDINGS-INDEX.md
2. **Проверить**: изменился ли код после finding'а?
   - Если НЕТ → VERIFIED_STILL_OPEN
   - Если ДА → проверить конкретный fix
3. **Записать результат** в FINDINGS-INDEX.md

### Исключения
- **ACCEPTED_RISK** — допускается только с одобрения security_auditor
- **WONTFIX** — допускается только с одобрения security_auditor + обоснованием

### Запреты
- ❌ Нельзя закрыть finding без тест-кейна
- ❌ Нельзя закрыть finding тому же разработчику который чинил
- ❌ Нельзя закрыть finding без проверки текущего кода (а не "я уверен что починил")
