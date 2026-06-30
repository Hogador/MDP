# SMART LOADING (ОБЯЗАТЕЛЬНО)
# Перед началом прочитай ТОЛЬКО индексы (не полные файлы):
# 1. security/FINDINGS-INDEX.md (~80 строк) — НЕ FINDINGS.md
# 2. security/ERRORS-INDEX.md (~50 строк) — НЕ ERRORS-MEMORY.md  
# 3. docs/orchestration/CODE-INDEX.md (~150 строк) — карту кода
# Для релевантных findings — security/findings/F-XXX.md (один файл)
# Для релевантного кода — ТОЛЬКО указанные в индексе файлы
# ЗАПРЕЩЕНО читать FINDINGS.md целиком (трата токенов)
# ЗАПРЕЩЕНО сканировать все файлы (используй CODE-INDEX)
# Исключение: verifier при full verification

Ты — architect. Проанализируй все OPEN и REGRESSED findings из security/FINDINGS.md и предложи обоснованные решения.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
1. Прочитай security/FINDINGS.md (все OPEN и REGRESSED findings)
2. Прочитай security/FIX-PATTERNS.md (approved solutions)
3. Прочитай security/ANTI-PATTERNS.md (запрещённые подходы)
4. Прочитай PRD/PRD_COMPLETE.md (контекст продукта)
5. Прочитай TDD/TDD.md (технический контекст)

Для каждого finding создай раздел с:

### 1. Root Cause (почему возникла проблема)
Технический анализ.

### 2. Варианты решения (2-3 варианта)
Для каждого:
- Название
- Реализация (код или псевдокод)
- Плюсы (3-5 пунктов)
- Минусы (2-4 пункта)
- Gas/Performance impact
- Breaking changes (да/нет + описание)

### 3. Рекомендация
Какой вариант выбираешь и ПОЧЕМУ (3-5 причин).

### 4. Пошаговый план реализации
5-10 шагов.

### 5. Тесты (из test-scenarios.md)
- Существующий scenario: SEC-XX-XX
- Новый тест: testSECXXXDescription — что проверяет

### 6. TDD изменения
- Какую секцию обновить (§X.X)
- Что добавить в status table

### 7. Risk assessment
- Если не сделать: последствия
- Если сделать неправильно: последствия
- Mitigation: как снизить риск

ВАЖНО:
- ВСЁ НА РУССКОМ
- Если используешь GLM 5.2 — отметь «[GLM 5.2]» в начале
- Если переключаешься на nemotron-3-ultra (после 5 calls) — сообщи «⚠️ Переключаюсь на nemotron-3-ultra (GLM 5.2 budget исчерпан)»
- Если переключаешься на llama.cpp/qwythos-local — сообщи «⚠️ Переключаюсь на локальную модель»

Приоритезация:
1. CRITICAL findings — первыми (детально)
2. HIGH — вторыми (детально)
3. MEDIUM — третьими (кратко)
4. LOW/INFO — в конце (one-liner)

Вывод в файл docs/orchestration/architect-report.md:
# Architect Report — Решения для findings

Дата: YYYY-MM-DD
Модель: [GLM 5.2 / nemotron-3-ultra / qwythos-local]
Findings обработано: X
- CRITICAL: Y
- HIGH: Z
- MEDIUM: W
- LOW/INFO: V

---

## F-XXX [CRITICAL] Title

### Root Cause
...

### Варианты решения
...

### Рекомендация
...

### Пошаговый план
...

### Тесты
...

### TDD изменения
...

### Risk assessment
...

