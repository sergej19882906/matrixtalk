#!/bin/sh
set -e

HOMESERVER_ADDRESS="${HOMESERVER_ADDRESS:-http://synapse:8008}"
HOMESERVER_DOMAIN="${HOMESERVER_DOMAIN:-example.org}"
OWNER="${BRIDGE_ADMIN:-@admin:${HOMESERVER_DOMAIN}}"
CONFIG=/data/heisenbridge.yaml

mkdir -p /data

echo "[matrix-talk] Starting heisenbridge..."
exec python3 -m heisenbridge     -c "$CONFIG"     -u "$OWNER"     -H "${HOMESERVER_ADDRESS#http://}"     "$@"
