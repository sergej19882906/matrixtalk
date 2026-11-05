#!/bin/sh
set -eu
for file in /data/config.yml /data/registration.yml /data/passkey.pem; do
    if [ ! -s "$file" ]; then
        printf '%s\n' "[matrix-talk] Missing $file. Prepare Hookshot configuration, registration and key; see docs/bridges.md." >&2
        exit 64
    fi
done
# Matches the upstream Dockerfile, not a guessed source checkout path.
exec /usr/local/bin/node /bin/matrix-hookshot/App/BridgeApp.js /data/config.yml /data/registration.yml "$@"
