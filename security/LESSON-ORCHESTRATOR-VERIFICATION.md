# LESSON: Orchestrator Parallel Task Verification

**Date:** 2026-07-20
**Severity:** HIGH —直接影响 аудит качество

## Инцидент

Запустил 4 параллельных аудита (2× security_auditor, 1× reviewer, 1× planner).
Вернулись результаты: 3 из 4 — пустые `<task_result></task_result>`.
Только mobile review отработал. Coverage ~20% вместо 100%.

Координатор НЕ проверил каждый результат на пустоту и продолжил работу,
как будто всё идёт нормально.

## Корневые причины

1. **Нет пост-верификации после parallel spawn.**
   Отправил 4 задачи, получил ответы, не проверил каждый на non-empty.
   Воспринял один успешный ответ как "процесс работает".

2. **Не проверяю state задач.**
   Task tool возвращает `state=completed` даже при пустом task_result.
   `completed` ≠ `success`. Нужно проверять CONTENT результата.

3. **Security_auditor модели ненадёжны.**
   OpenRouter free tier (nemotron-3-super-120b) периодически падает с
   "/chat/completions cannot be parsed as a URL". Это системная проблема.

4. **Planner compaction-hang.**
   При большом контексте (PRD 455 строк + TDD 1166+ строк) planner
   зависает на компакции контекста.

## Правила для будущего

### Обязательные шаги после parallel spawn:

```
1. Отправить N задач параллельно
2. Дождаться ВСЕХ ответов (не первый попавшийся)
3. Для КАЖДОГО ответа: проверить task_result != ""
4. Если пустой → залогировать: какой агент, какая модель, какая ошибка
5. Решить: re-spawn с другой моделью / сделать самому / отложить
6. Только ПОСЛЕ проверки всех N → продолжать работу
```

### When to use parallel vs sequential:

| Ситуация | Подход |
|----------|--------|
| Независимые подзадачи, мало контекста | Parallel OK |
| Критический аудит, полное покрытие | Sequential — лови ошибки сразу |
| Больше 2 агентов | Sequential — не теряй visibility |
| Нестабильная модель | Sequential + fallback check |

### Fallback chain для security_auditor:

1. Primary: `openrouter/nvidia/nemotron-3-super-120b-a12b:free`
2. Fallback: `openrouter/google/gemma-4-31b-it:free`  
3. Last resort: Coordinator читает файлы сам и анализирует

### Метрика для self-check:

"Сколько из отправленных задач вернули non-empty результат?"
- 4/4 → OK
- 3/4 → WARNING, retry failed
- 2/4 → CRITICAL, switch to sequential
- 1/4 → ABORT parallel, do everything sequentially
- 0/4 → model issue, check config
