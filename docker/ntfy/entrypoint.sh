#!/bin/sh
set -eu
# ntfy health can be green without a Matrix gateway base URL: reject that setup.
case "${NTFY_BASE_URL:-}" in
    https://*) ;;
    *) printf '%s\n' '[matrix-talk] Set NTFY_BASE_URL to the public HTTPS origin before enabling push.' >&2; exit 64 ;;
esac
host="${NTFY_BASE_URL#https://}"
case "$host" in
    ""|*[!A-Za-z0-9.:-]*) printf '%s\n' '[matrix-talk] NTFY_BASE_URL must be an HTTPS origin without credentials, path, query or fragment.' >&2; exit 64 ;;
esac
exec ntfy "$@"
