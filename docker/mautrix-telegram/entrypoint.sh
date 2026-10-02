#!/bin/sh
set -e

BRIDGE_BIN=/usr/bin/mautrix-telegram
CONFIG=/data/config.yaml
REGISTRATION=/data/registration.yaml

HOMESERVER_ADDRESS="${HOMESERVER_ADDRESS:-http://synapse:8008}"
HOMESERVER_DOMAIN="${HOMESERVER_DOMAIN:-example.org}"
POSTGRES_USER="${POSTGRES_USER:-synapse}"
BRIDGE_ADMIN="${BRIDGE_ADMIN:-@admin:$HOMESERVER_DOMAIN}"

# Step 1: Generate default config if it doesn't exist
if [ ! -f "$CONFIG" ]; then
    echo "[matrix-talk] Generating default config..."
    $BRIDGE_BIN || true
    [ -f /data/example-config.yaml ] && [ ! -f "$CONFIG" ] && mv /data/example-config.yaml "$CONFIG"
fi

# Step 2: Patch config from environment variables
echo "[matrix-talk] Patching config from env vars..."

sed -i "/^homeserver:/,/^[^ ]/ s|address: .*|address: ${HOMESERVER_ADDRESS}|" "$CONFIG"
sed -i "/^homeserver:/,/^[^ ]/ s|domain: .*|domain: ${HOMESERVER_DOMAIN}|" "$CONFIG"
sed -i "/^appservice:/,/^[^ ]/ s|hostname: .*|hostname: 0.0.0.0|" "$CONFIG"

DB_URI="postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres/mautrix_telegram?sslmode=disable"
sed -i "/^database:/,/^[^ ]/ s|uri: .*|uri: ${DB_URI}|" "$CONFIG"

sed -i "s|\"example\.org\": user|\"${HOMESERVER_DOMAIN}\": user|" "$CONFIG"
sed -i "s|@admin:example\.org|${BRIDGE_ADMIN}|g" "$CONFIG"

# Telegram API credentials
if [ -n "${TELEGRAM_API_ID:-}" ]; then
    sed -i "s|api_id: .*|api_id: ${TELEGRAM_API_ID}|" "$CONFIG"
fi
if [ -n "${TELEGRAM_API_HASH:-}" ]; then
    sed -i "s|api_hash: .*|api_hash: ${TELEGRAM_API_HASH}|" "$CONFIG"
fi

# Step 3: Generate registration if it doesn't exist
if [ ! -f "$REGISTRATION" ]; then
    echo "[matrix-talk] Generating registration..."
    $BRIDGE_BIN -g || true
fi

# Step 4: Start bridge
echo "[matrix-talk] Starting mautrix-telegram..."
exec $BRIDGE_BIN
