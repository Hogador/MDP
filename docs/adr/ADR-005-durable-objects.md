# ADR-005: KV TOCTOU в relay — миграция на Durable Objects

**Дата:** 2026-08-07
**Статус:** proposed (fixme: выставить accepted после реализации)
**Supersedes:** —
**Superseded by:** —

## Контекст

C-04 из аудита: relay (Cloudflare Worker) использует KV для invite/recovery
состояния. KV — eventually consistent, запись last-write-wins, НЕТ транзакций
и compare-and-swap. Отсюда check-then-act (TOCTOU) гонки:

1. **`POST /guardian/invite/:id/accept`** (`relay/src/index.ts:187-202`):
   `getInvite` → проверка `status === 'PENDING'` → `verifyP256Signature` →
   `updateInviteStatus('ACCEPTED')`. Между чтением и записью второй accept
   может прочитать тот же PENDING и тоже пройти. `updateInviteStatus`
   (`relay/src/storage.ts:26-34`) — read-modify-write не-атомарно.
   Последствия: double-accept → двойной `storeGuardian` для одного
   walletAddress (последний выигрывает), потенциальная потеря апдейта полей.

2. **`addApproval`** (`relay/src/storage.ts:78-102`): `getPendingRecovery` →
   check `vetoed/executed` → RMW `approve:${nonce}` массива →
   `setPendingRecovery`. Гонка: два guardian'а добавляют approval
   одновременно — один RMW теряется (last-write-wins). C-10 уже добавил
   идемпотентный guard (`approve:${walletAddress}:${nonce}`), который
   смягчает двойное добавление ОДНОГО guardian, но НЕ решает потерю
   апдейта при параллельных РАЗНЫХ guardian'ах — KV put всё ещё
   last-write-wins без атомарного read-modify-write.

3. **`registerPushToken`** (`relay/src/storage.ts:36-47`): RMW массива
   FCM-токенов — потеря апдейта при параллельных регистрациях.

## Решение

Мигрировать stateful-пути (accept, addApproval, vetoRecovery,
registerPushToken) на **Cloudflare Durable Objects**: один DO на
walletAddress (и на inviteId для accept), single-threaded исполнение,
атомарные методы — гонки исчезают на уровне архитектуры, а не guards.

- Новый класс `RecoveryDO` (walletAddress = id): методы `addApproval`,
  `veto`, `execute`, `registerPushToken` — state в память, персист в KV
  как снапшот (DO имеет собственный transactional storage, KV не нужен).
- Новый класс `InviteDO` (inviteId = id): атомарный `accept(signature…)`
  — единственная точка записи статуса, second-accept возвращает
  конфликт/уже обработан.
- HTTP-маршруты: `env.RECOVERY_DO.idFromName(walletAddress).get()` →
  `stub.fetch(...)` (или RPC `stub.addApproval(...)`).
- НЕ мигрируем: `getInvite`/`getPendingRecovery` (read-only, KV fine).

## Альтернативы

### Альтернатива A: Оставить KV + больше guards
- Плюсы: ноль изменений
- Минусы: last-write-wins неустраним guards'ами; каждая новая гонка —
  новый guard; KV eventual consistency даёт окно в секунды
- Почему не выбрано: C-10 показал, что guards лечат симптом, не причину

### Альтерватива B: KV + CAS (compare-and-swap через etag)
- Плюсы: меньше инфраструктуры, чем DO
- Минусы: Cloudflare KV **не поддерживает** CAS/conditional put на уровне
  API (на момент написания ADR); пришлось бы писать optimistic-lock
  поверх etag вручную, всё равно окно между get и put
- Почему не выбрано: KV не предоставляет примитив — CAS эмулируется
  криво, DO даёт это из коробки

### Альтернатива C: Внешняя БД (Postgres via Hyperdrive/Neon)
- Плюсы: полные транзакции
- Минусы: внешняя зависимость, latency на BSC-периферии, стоимость,
  ops-нагрузка; для одного инварианта (одна запись на invite/recovery)
  DO достаточно
- Почему не выбрано: over-engineering для объёма данных relay

## Последствия

**Позитивные:**
- TOCTOU исчезает структурно: DO гарантирует single-writer на ключ
- `addApproval` без потери апдейтов — корректный порог 2/3
- `accept` атомарен — двойной accept невозможен
- C-10 guard остаётся как дефенс-ин-депс, но больше не критичен
- Пони-хвост-комментарий в `storage.ts` закрывается

**Негативные:**
- DO — платная фича Cloudflare (входит в Workers Paid plan)
- Нужен billing + `migrations` в `wrangler.toml`
- Тесты требуют `vitest-environment-miniflare` (уже используется)

**Нейтральные:**
- Колд-старт DO (~5-10ms) на recovery-путях (редкие операции) не критичен
- Read-пути остаются на KV — latency не меняется

## Верификация

- Unit: relay-тесты переведены на DO-мок (miniflare), включая тест на
  двойной accept → второй запрос отклоняется
- Конкурентность: тест N=5 параллельных `addApproval` → финальный счётчик
  = N (до миграции терялись апдейты)
- `npx tsc --noEmit` + `npx vitest run` — зелёные
- Прод: ручная проверка accept→recovery flow на staging

## Ссылки

- Задача: C-04, C-10, H-03 (rate limiting — соседний, но отдельный)
- Файлы: `relay/src/index.ts`, `relay/src/storage.ts`, `relay/wrangler.toml`
- KB: KB-REL-005 (будет добавлен lessons-learned)
