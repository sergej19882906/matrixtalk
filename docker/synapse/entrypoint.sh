#!/bin/bash
set -e

CONFIG_FILE="/data/homeserver.yaml"

# Generate homeserver.yaml if it doesn't exist
if [ ! -f "$CONFIG_FILE" ]; then
    echo "[matrix-talk] Generating homeserver.yaml for $SYNAPSE_SERVER_NAME ..."
    python -m synapse.app.homeserver \
        --generate-config \
        -H "$SYNAPSE_SERVER_NAME" \
        --report-stats="${SYNAPSE_REPORT_STATS:-no}" \
        --config-path "$CONFIG_FILE"
fi

# Apply configuration from environment variables on every start
echo "[matrix-talk] Applying configuration from env vars..."
python /usr/local/bin/configure.py

# Start Synapse
exec python -m synapse.app.homeserver --config-path "$CONFIG_FILE"
