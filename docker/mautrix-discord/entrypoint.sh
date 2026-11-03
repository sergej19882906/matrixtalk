#!/bin/sh
set -e

BRIDGE_BIN=/usr/bin/mautrix-discord
CONFIG=/data/config.yaml
REGISTRATION=/data/registration.yaml

HOMESERVER_ADDRESS="${HOMESERVER_ADDRESS:-http://synapse:8008}"
HOMESERVER_DOMAIN="${HOMESERVER_DOMAIN:-example.org}"
POSTGRES_USER="${POSTGRES_USER:-synapse}"

if [ ! -f "$CONFIG" ]; then
    echo "[matrix-talk] Generating default config..."
    $BRIDGE_BIN || true
    [ -f /data/example-config.yaml ] && [ ! -f "$CONFIG" ] && mv /data/example-config.yaml "$CONFIG"
fi

echo "[matrix-talk] Patching config from env vars..."

sed -i "/^homeserver:/,/^[^ ]/ s|address: .*|address: ${HOMESERVER_ADDRESS}|" "$CONFIG"
sed -i "/^homeserver:/,/^[^ ]/ s|domain: .*|domain: ${HOMESERVER_DOMAIN}|" "$CONFIG"
sed -i "/^appservice:/,/^[^ ]/ s|hostname: .*|hostname: 0.0.0.0|" "$CONFIG"

DB_NAME="${BRIDGE_DB_NAME:-mautrix}"
DB_URI="postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres/${DB_NAME}?sslmode=disable"
sed -i "/^database:/,/^[^ ]/ s|uri: .*|uri: ${DB_URI}|" "$CONFIG"

sed -i "s|\"example\.org\": user|\"${HOMESERVER_DOMAIN}\": user|" "$CONFIG"

if [ ! -f "$REGISTRATION" ]; then
    echo "[matrix-talk] Generating registration..."
    $BRIDGE_BIN -g || true
fi

echo "[matrix-talk] Starting bridge..."
exec $BRIDGE_BIN
