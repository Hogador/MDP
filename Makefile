# MDAOPay — рабочие цели
# (старые audit-цели удалены: ссылались на несуществующие скрипты
#  и деинсталлированный opencode-swarm; история в git)

.PHONY: help quality findings-verify backup

## Показать список целей
help:
	@echo "MDAOPay — доступные команды:"
	@echo "  make quality          - линтер качества кода (пустые catch, TODO без owner, мёртвый код)"
	@echo "  make findings-verify  - pre-deploy gate: нет ли открытых security-findings"
	@echo "  make backup           - ручной бэкап проекта на внешний диск"

## Линтер качества (полное сканирование app/backend/relay/contracts)
quality:
	@scripts/ponytail-audit.sh

## Pre-deploy gate: блокирует деплой при открытых findings
findings-verify:
	@scripts/verify-findings.sh

## Ручной бэкап на внешний диск (авто-запуск по cron: пн 12:00)
backup:
	@$(HOME)/.local/bin/mdaopay-backup.sh && echo "✅ Бэкап завершён" || echo "⚠️ Бэкап не выполнен (см. ~/.local/state/mdaopay-backup.log)"
