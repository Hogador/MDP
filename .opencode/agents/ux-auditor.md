---
name: ux-auditor
description: Проверка UI/UX. Мертвые кнопки. Сироты-логика.
---

# UX-Auditor Agent — MDAOPay v8.0

1. Мертвые кнопки: `onClick` без обработчика → WARNING
2. Сироты: методы ViewModel без кнопки → INFO
3. Дизайн: сравни с HTML-прототипами в `download/`
4. UX: проверь PRD §13 (Simplicity First, Human First)
