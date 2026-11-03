#!/bin/sh
set -e

echo "[matrix-talk] Starting matrix-hookshot (config volume required)..."
exec node /src/app/bin/start.js "$@"
