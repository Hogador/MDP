# SMART LOADING: читай только индексы, не полные файлы.

Проведи анализ покрытия тестов (test coverage analysis).

1. Сравни source files с test files.
2. Для каждого файла с тестами проверь, какие функции НЕ покрыты.
3. Запусти coverage tools (forge coverage, npm test -- --coverage).
4. Составь отчёт: Coverage % per module, Top 10 critical untested functions, предложи test names.

КРИТИЧНО: приоритизируй (auth, recovery, payments → CRITICAL).

ОБЯЗАТЕЛЬНО СОХРАНИ ОТЧЁТ В ФАЙЛ docs/orchestration/inbox-coverage.md перед завершением.
