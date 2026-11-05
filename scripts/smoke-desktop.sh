#!/usr/bin/env bash
set -euo pipefail

app_root="${SMOKE_APP_ROOT:-desktop-app/build/compose/binaries/main/app}"
seconds="${SMOKE_SECONDS:-30}"
case "${RUNNER_OS:-}" in
  Windows) bin="$app_root/MatrixTalk/MatrixTalk.exe" ;;
  Linux) bin="$app_root/MatrixTalk/bin/MatrixTalk" ;;
  macOS) bin="$app_root/MatrixTalk.app/Contents/MacOS/MatrixTalk" ;;
  *) printf 'Unsupported smoke-test platform\n' >&2; exit 1 ;;
esac
if [ ! -f "$bin" ]; then
  printf 'Packaged launcher not found: %s. Run :desktop-app:createDistributable first.\n' "$bin" >&2
  exit 1
fi
out=$(mktemp)
pid=
cleanup() {
  if [ -n "$pid" ]; then kill "$pid" 2>/dev/null || true; fi
  rm -f "$out"
}
trap cleanup EXIT
if [ "$RUNNER_OS" = Linux ]; then
  xvfb-run -a "$bin" >"$out" 2>&1 &
else
  "$bin" >"$out" 2>&1 &
fi
pid=$!
sleep "$seconds"
if ! kill -0 "$pid" 2>/dev/null; then
  printf 'Packaged app exited early:\n' >&2
  cat "$out" >&2
  exit 1
fi
if grep -qE 'Exception in thread|InstanceCreationException|NoDefinitionFoundException|NoClassDefFoundError|UnsatisfiedLinkError|Could not create instance' "$out"; then
  cat "$out" >&2
  exit 1
fi
printf 'Packaged app smoke test OK\n'
