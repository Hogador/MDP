# RISK REGISTRY — MDAOPay

> Реестр рисков с Mitigation-планом. Риски заносятся verifier'ом после
> подтверждения findings из режима --mode=audit.
>
> Риск без Mitigation = незакрытый риск. Critical без дедлайна = блокер.

---

## Шкала критичности

| Severity | Описание | SLA |
|---|---|---|
| Critical | Может привести к потере средств | Блокер mainnet, фикс <= 7 дней |
| High | Существенная уязвимость | Фикс <= 30 дней |
| Medium | Ограниченная уязвимость | Фикс <= 90 дней |
| Low | Качество кода / минор | По возможности |

---

## Формат записи

~~~markdown
### RISK-NNN: <название>

**Severity:** Critical | High | Medium | Low
**Likelihood:** Low | Medium | High
**Domain:** SOL | MOB | BACK | INF | SEC | UX
**Источник:** audit/<timestamp>/findings-<mode>.md
**Дата обнаружения:** YYYY-MM-DD
**Статус:** open | mitigated | accepted | closed

**Описание:**
<что не так>

**Влияние:**
<что произойдёт, если эксплуатировать>

**Mitigation:**
- [ ] <шаг 1>
- [ ] <шаг 2>

**Верификация Mitigation:**
verifier --logic --risk=RISK-NNN
~~~

---

## Активные риски

*(риски добавляются verifier'ом после аудита)*

---

## Закрытые риски (архив)

*(переносится сюда после verifier --logic --risk=RISK-NNN = mitigated)*

---

## Журнал изменений

| Дата | Действие | RISK ID | Агент |
|---|---|---|---|
| — | — | — | — |
