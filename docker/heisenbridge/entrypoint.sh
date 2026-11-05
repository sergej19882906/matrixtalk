#!/bin/sh
set -eu
CONFIG=/data/registration.yaml
if [ ! -s "$CONFIG" ]; then
    printf '%s\n' '[matrix-talk] Missing /data/registration.yaml. Generate heisenbridge registration first; see docs/bridges.md.' >&2
    exit 64
fi
: "${HOMESERVER_ADDRESS:?Set HOMESERVER_ADDRESS}"
: "${BRIDGE_ADMIN:?Set BRIDGE_ADMIN to the owner MXID}"
# Upstream CLI takes owner with -o, preserves the full URL as a positional argument.
exec heisenbridge -l 0.0.0.0 -c "$CONFIG" -o "$BRIDGE_ADMIN" "$HOMESERVER_ADDRESS" "$@"
