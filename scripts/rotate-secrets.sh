#!/usr/bin/env bash
# scripts/rotate-secrets.sh
# Этап 0.2 ROADMAP_SECURITY_FIXES: ротация секретов перед деплоем.
#
# Генерирует НОВЫЕ секреты и обновляет:
#   - .env (backend, gitignored)  — реальные значения
#   - .env.example (backend)      — только имена переменных (без значений)
#   - Cloudflare Worker secrets   — через `wrangler secret put` (если --apply)
#
# Безопасность: скрипт НЕ выводит секреты в stdout, НЕ коммитит их.
# Использование:
#   ./scripts/rotate-secrets.sh            # dry-run: показать что будет ротироваться
#   ./scripts/rotate-secrets.sh --apply    # сгенерировать и записать в .env
#   ./scripts/rotate-secrets.sh --cloudflare  # + обновить секреты Cloudflare Worker
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

APPLY=0; CLOUDFLARE=0
for a in "$@"; do
    case "$a" in
        --apply)      APPLY=1 ;;
        --cloudflare) CLOUDFLARE=1 ;;
        *) log_error "Неизвестный аргумент: $a"; exit 1 ;;
    esac
done

gen_hex() { openssl rand -hex 32; }  # 64 hex chars = 256-bit

# Секреты для ротации (C-3: разделение RELAY_SECRET на HMAC + JWT; C-2: MoonPay)
SECRETS="RELAY_HMAC_SECRET RELAY_JWT_SECRET"
# MoonPay нельзя сгенерировать локально — ключ выдаётся в панели MoonPay.
# Скрипт только проверяет наличие в .env и напоминает о ручной ротации.

ENV_FILE="backend/.env"
ENV_EXAMPLE="backend/.env.example"

check_tool() { command -v "$1" >/dev/null 2>&1; }

if [ "$APPLY" = "1" ]; then
    # ── 1. Проверка инструментов ──
    check_tool openssl || { log_error "openssl не найден"; exit 1; }

    # ── 2. Генерация и запись в .env (gitignored) ──
    [ -f "$ENV_FILE" ] || { log_error "$ENV_FILE не найден — создайте из .env.example"; exit 1; }

    # Бэкап текущего .env
    cp "$ENV_FILE" "$ENV_FILE.bak.$(date +%Y%m%d%H%M%S)"
    log_info "Бэкап: $ENV_FILE.bak.*"

    for name in $SECRETS; do
        value="$(gen_hex)"
        # Удалить существующую строку, добавить новую (ротация)
        grep -v "^${name}=" "$ENV_FILE" > "$ENV_FILE.tmp" || true
        printf '%s=%s\n' "$name" "$value" >> "$ENV_FILE.tmp"
        mv "$ENV_FILE.tmp" "$ENV_FILE"
        log_info "Ротирован: $name (новое значение записано в $ENV_FILE)"
    done

    # ── 3. Обновление .env.example (только имена, БЕЗ значений) ──
    for name in RELAY_HMAC_SECRET RELAY_JWT_SECRET MOONPAY_API_KEY MOONPAY_WEBHOOK_SECRET; do
        grep -q "^# ${name}=" "$ENV_EXAMPLE" || {
            printf '# %s=\n' "$name" >> "$ENV_EXAMPLE"
            log_info ".env.example: добавлено # $name="
        }
    done

    # ── 4. MoonPay — ручная ротация (нельзя сгенерировать локально) ──
    if grep -q "^MOONPAY_API_KEY=" "$ENV_FILE" 2>/dev/null; then
        log_warn "MOONPAY_API_KEY найден в .env. Ротация вручную: панель MoonPay → API Keys → Roll. C-2: ключ был в 302 redirect."
    else
        log_warn "MOONPAY_API_KEY отсутствует в .env — добавьте после получения нового ключа в панели MoonPay."
    fi
else
    log_info "DRY-RUN. Будут ротированы: $SECRETS"
    log_info "Добавлены в .env.example: RELAY_HMAC_SECRET RELAY_JWT_SECRET MOONPAY_API_KEY MOONPAY_WEBHOOK_SECRET"
    log_info "MoonPay: ручная ротация в панели (см. C-2)."
    log_info "Для применения: $0 --apply"
    exit 0
fi

# ── 5. Cloudflare Worker (relay) ──
if [ "$CLOUDFLARE" = "1" ]; then
    check_tool wrangler || { log_warn "wrangler не найден — пропускаю Cloudflare, значения уже в .env"; exit 0; }
    log_warn "Обновление Cloudflare Worker секретов. Убедитесь, что вы в relay/ директории."
    for name in $SECRETS; do
        value="$(grep "^${name}=" "$ENV_FILE" | cut -d= -f2-)"
        # --secret передаёт значение из stdin; НЕ показываем его в логе
        printf '%s' "$value" | wrangler secret put "$name" 2>/dev/null || {
            log_error "Не удалось обновить $name в Cloudflare — проверьте auth: wrangler login"
            exit 1
        }
        log_info "Cloudflare: $name обновлён"
    done
    log_warn "ВАЖНО: RELAY_SECRET больше не используется. Обновите relay/src/index.ts:102,109 на RELAY_HMAC_SECRET."
else
    log_info "Cloudflare secrets НЕ обновлены (без --cloudflare). Значения в $ENV_FILE."
    log_info "Далее: wrangler secret put RELAY_HMAC_SECRET / RELAY_JWT_SECRET"
fi

log_info "✅ Ротация завершена. Перезапустите сервисы: docker compose restart backend"
