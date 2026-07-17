# Postmortem: Subagent Model Fix — 2026-07-17

## Что было сломано

**Все 16 subagent'ов (planner, coder, reviewer и др.) возвращали пустой результат.** Task tool: `<task_result></task_result>`. Координатор работал, subagent'ы — нет.

## Корневые причины (3 проблемы)

### 1. Неправильный location файлов агентов
- **Где ломалось**: opencode читает агентов из `~/.config/opencode/agent/*.md` (singular!)
- **Что делалось**: правки шли в `.swarm/agents/*.md` — opencode их **не читает**
- **Почему**: opencode.json `prompt: {file:.swarm/agents/planner.md}` загружает system prompt, но model берётся из `~/.config/opencode/agent/*.md` frontmatter
- **Как починили**: обновили оба места

### 2. API ключи не резолвились
- **Где ломалось**: `{env:GROQ_API_KEY}` в opencode.json не подхватывался из `.env`
- **Симптом**: `AI_APICallError: Unauthorized` (Groq), `Invalid API Key` (пустой ключ)
- **Как починили**: захардкожили ключи напрямую в `provider.options.apiKey`

### 3. Groq free tier 8K TPM
- **Где ломалось**: coordinator шлёт ~46K токенов контекста в subagent
- **Симптом**: `TPM: Limit 8000, Requested 46233`
- **Как починили**: переключились на OpenRouter free (нет TPM лимита)

## Что именно чинилось

| Шаг | Действие | Результат |
|-----|----------|-----------|
| 1 | Поменял модели в `.swarm/agents/*.md` | Не помогло (wrong location) |
| 2 | Поменял модели в `opencode.json` | Не помогло (frontmatter override) |
| 3 | Нашёл `~/.config/opencode/agent/*.md` | **Корневая причина №1** |
| 4 | Поменял модели в `~/.config/opencode/agent/*.md` | Groq key invalid |
| 5 | Захардкожил Groq key | TPM overflow (8K vs 46K) |
| 6 | Переключился на OpenRouter free | **Все агенты работают** |

## Как предотвратить

### При добавлении/изменении агента
**Три места**, которые нужно синхронизировать:

```
1. ~/.config/opencode/agent/<name>.md    ← opencode читает model отсюда
2. ~/.config/opencode/opencode.json      ← fallback если нет frontmatter
3. ~/.swarm/agents/<name>.md             ← system prompt (prompt field)
```

**Правило**: если agent.md имеет `model:` в frontmatter → он **переопределяет** JSON.

### При смене API провайдера
- `{env:VAR}` в opencode.json **не работает** для subagent'ов → ключи хардкодить
- Groq free: **8K TPM** — не тянет контекст coordinator'a (>40K tokens)
- OpenRouter free: **нет TPM**, но ~10-20 req/day rate limit

### При добавлении нового провайдера
1. Проверить `curl` ключ из `.env` → если работает через curl, ключ валиден
2. Захардкодить ключ в `provider.options.apiKey` в opencode.json
3. Протестировать task tool: `task(subagent_type="new_agent", prompt="Say OK")`

## Модели (текущая конфигурация)

| Уровень | Модель | TPM | Где используется |
|---------|--------|-----|-----------------|
| 120B reasoning | `openrouter/nvidia/nemotron-3-super-120b-a12b:free` | ∞ | planner, judge, reviewer, security, research |
| 20B code | `openrouter/openai/gpt-oss-20b:free` | ∞ | coder, debugger, tester, builder |
| Primary | `opencode/big-pickle` | ∞ | coordinator (only visible agent) |
