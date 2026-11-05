#!/bin/sh
set -eu
# Upstream creates templates with an intentional exit; production never pretends that is ready.
for file in /data/config.yaml /data/registration.yaml; do
    if [ ! -s "$file" ]; then
        printf '%s\n' "[matrix-talk] Missing $file. Prepare the pinned upstream bridge config and registration; see docs/bridges.md." >&2
        exit 64
    fi
done
exec /docker-run.sh "$@"
