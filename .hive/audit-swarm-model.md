# Swarm Model Audit

## Контекст
MDAOPay swarm v9.0 — оркестрация через OmniRoute (порт 3000).
Первоначальная проблема: все агенты использовали одну модель (`oc/deepseek-v4-flash-free`), task tool был сломан (model=undefined).

## Алгоритм починки

### Шаг 1: Аудит провайдеров
**Что сделано:** Подключены 6 новых API-провайдеров через omniroute API
- `groq/` — 10 моделей, быстрые (LPU), бесплатно 30rpm
- `sambanova/` — 5 моделей, 200K токенов/день бесплатно
- `mistral/` — 7 моделей, ~1B токенов/мес бесплатно
- `nvidia/` — 27 моделей, Developer Program free
- `alibaba/` — 13 моделей, free quota для новых
- `huggingface/` — 14 моделей, rate-limited

### Шаг 2: Тестирование моделей
**Что сделано:** Проверены curl-запросом все провайдеры
- Sambanova DeepSeek-V3.2 ✅ 530ms TTFT
- Groq Llama 3.3 70B ✅ 394 TPS
- Mistral Codestral ✅ отличный код
- NVIDIA DeepSeek V4 Flash ✅ работает
- Alibaba Qwen-Plus ✅ чистый код
- HF Qwen2.5-7B ✅ отвечает

### Шаг 3: Настройка комб
**Что сделано:** Созданы 5 комб через omniroute API
- coordinator → samba/DeepSeek-V3.2 → mistral-large → OC
- coder → codestral → groq-llama-3.3 → nvidia-deepseek → OC
- thinker → samba/DeepSeek-V3.2 → mistral-large → nvidia → OC
- debugger → groq-llama-3.3-70b → samba-deepseek → OC
- auditor → qwythos-9b local → samba-deepseek → nvidia → OC

### Шаг 4: Подключение OAuth (от пользователя)
**Что сделано:** Пользователь настроил OAuth провайдеры в дашборде
- codex (cx/) — GPT-5.4-mini ✅
- cline (cl/) — Claude Opus 4.7 ✅
- kiro (kr/) — Claude Haiku 4.5 ✅
- github (gh/) — gpt-4o-mini ⚠️
- antigravity — 19 моделей ⚠️ таймаут
- kilocode (kc/) — rate limited
- kimi-coding (kmc/) — no credentials
- agy — unsupported

### Шаг 5: Перестройка комб с OAuth
**Что сделано:** Все 8 комб пересозданы с OAuth-primary моделями
- coordinator → claude-opus-4.7 → samba-deepseek → mistral-large
- coder → gpt-5.4-mini → codestral → groq-llama-3.3
- thinker → claude-opus-4.7 → samba-deepseek → mistral-large
- debugger → claude-haiku-4.5 → groq-llama-3.3 → samba-deepseek
- reviewer → gpt-5.4-mini → samba-llama-3.3 → groq
- tester → claude-haiku-4.5 → groq-llama-3.1 → qwen-plus
- auditor → claude-opus-4.7 → samba-deepseek → qwythos-9b
- researcher → gpt-5.4-mini → qwen-plus → samba-deepseek

### Шаг 6: Настройка opencode.json
**Что сделано:** Маппинг суб-агентов на комбы
- Все суб-агенты получили model=omniroute/{combo_name}
- Удалены мёртвые провайдеры (pollinations, zai_direct, siliconflow)
- Обновлены описания моделей

### Шаг 7: Убийство Docker контейнера
**Что сделано:** Остановлен и удалён Docker-контейнер omniroute (порт 20128)
- Освобождено: 1.56GB диск (образ) + 242MB RAM
- Причина: все нужные провайдеры работают на порту 3000
- OAuth токены НЕ перенесены (привязаны к client_id)

### Шаг 8: Восстановление пароля
**Что сделано:** Сброшен пароль дашборда через прямую запись в SQLite
- bcrypt-хэш записан в key_value таблицу
- Пароль: 12345678

## Итоговая архитектура
```
OmniRoute (порт 3000, v16.2.9)
  ├── API-key провайдеры (6): Sambanova, Groq, Mistral, NVIDIA, Alibaba, HF
  ├── OAuth провайдеры (9): Cline, Codex, Kiro, Github, Kilocode, Antigravity, Agy, Kimi-coding, Amazon-q
  └── Комбы (8): coordinator, coder, thinker, debugger, reviewer, tester, auditor, researcher

Opencode (opencode.json)
  └── coordinator → omniroute/coordinator (Claude Opus 4.7)
  └── coder → omniroute/coder (GPT-5.4-mini)
  └── thinker → omniroute/thinker (Claude Opus 4.7)
  └── debugger → omniroute/debugger (Claude Haiku 4.5)
  └── reviewer → omniroute/reviewer (GPT-5.4-mini)
  └── tester → omniroute/tester (Claude Haiku 4.5)
  └── auditor → omniroute/auditor (Claude Opus 4.7)
  └── researcher → omniroute/researcher (GPT-5.4-mini)
```

## Известные проблемы
1. **task tool** — суб-агенты получают model=undefined (opencode v1.17.13).
   Комбы настроены — при фиксе заработает автоматически.
2. **agy/antigravity** — модели есть, но не отвечают. Возможно проблема с прокси.
3. **github** — из 22 моделей работает только gpt-4o-mini.
4. **kilocode/kimi-coding** — rate limited / нет креденшиалов.

## Free tier лимиты в день
- Sambanova: 200K токенов
- Groq: 14.4K запросов
- Mistral: ~1B токенов/мес
- NVIDIA: ~40rpm
- Alibaba: free quota (новые пользователи)
- OAuth: безлимит (но rate limited) — через личные токены пользователя
