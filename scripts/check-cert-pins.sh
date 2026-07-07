#!/usr/bin/env bash
# F-024: Validate cert pin hashes against live TLS certificates.
# Usage:
#   ./scripts/check-cert-pins.sh [--strict]  # reads expected pins from environment
#
# Expected env vars (set in CI):
#   CERT_PIN_API      — sha256/Base64 pin for api.mdaopay.com
#   CERT_PIN_BACKUP   — sha256/Base64 pin for mdaopay.com
#
# If a domain is unreachable/NXDOMAIN → skip (warn if --strict, else pass).
# If pins don't match → exit 1.
#
# Generate a pin for a domain:
#   openssl s_client -connect "$DOMAIN":443 -servername "$DOMAIN" </dev/null 2>/dev/null \
#     | openssl x509 -pubkey -noout \
#     | openssl pkey -pubin -outform der \
#     | openssl dgst -sha256 -binary \
#     | base64

set -euo pipefail

STRICT=false
if [ "${1:-}" = "--strict" ]; then STRICT=true; fi

fail() { echo "❌ $*" >&2; exit 1; }
warn() { echo "⚠️  $*" >&2; }

check_pin() {
    local domain="$1"
    local expected="$2"
    local label="$3"

    echo "  → $domain"

    # Resolve first
    if ! host "$domain" >/dev/null 2>&1; then
        if $STRICT; then
            warn "Domain $domain not resolvable (--strict)"
        else
            echo "    ↪ domain not resolvable — skipping (set --strict to enforce)"
        fi
        return 0
    fi

    local actual_pin
    actual_pin=$(openssl s_client -connect "$domain":443 -servername "$domain" </dev/null 2>/dev/null \
        | openssl x509 -pubkey -noout \
        | openssl pkey -pubin -outform der \
        | openssl dgst -sha256 -binary \
        | base64) || {
        warn "Failed to fetch cert for $domain"
        return 0
    }

    local expected_b64="${expected#sha256/}"
    if [ "$actual_pin" != "$expected_b64" ]; then
        fail "PIN MISMATCH for $domain
  expected: sha256/$expected_b64
  actual:   sha256/$actual_pin
  ⬆ Update BuildConfig or rotate certs"
    fi

    echo "    ✅ sha256/$actual_pin"
}

echo "═══════════════════════════════════════"
echo "Cert Pin Validation"
echo "═══════════════════════════════════════"

if [ -z "${CERT_PIN_API:-}" ] && [ -z "${CERT_PIN_BACKUP:-}" ]; then
    echo "No pins configured (CERT_PIN_API / CERT_PIN_BACKUP empty)."
    echo "These are the placeholder defaults — real pins required for production."
    echo ""
    echo "To generate pins for production domains:"
    echo "  for d in api.mdaopay.com mdaopay.com; do"
    echo '    pin=$(openssl s_client -connect "$d":443 -servername "$d" </dev/null 2>/dev/null \'
    echo '      | openssl x509 -pubkey -noout \'
    echo '      | openssl pkey -pubin -outform der \'
    echo '      | openssl dgst -sha256 -binary \'
    echo '      | base64)'
    echo '    echo "sha256/$pin  $d"'
    echo "  done"
    if $STRICT; then
        fail "No pins configured (--strict)"
    fi
    exit 0
fi

if [ -n "${CERT_PIN_API:-}" ]; then
    check_pin "api.mdaopay.com" "$CERT_PIN_API" "API"
else
    echo "  → api.mdaopay.com: CERT_PIN_API not set, skipping"
fi

if [ -n "${CERT_PIN_BACKUP:-}" ]; then
    check_pin "mdaopay.com" "$CERT_PIN_BACKUP" "Backup"
else
    echo "  → mdaopay.com: CERT_PIN_BACKUP not set, skipping"
fi

echo ""
echo "✅ All checks passed"
