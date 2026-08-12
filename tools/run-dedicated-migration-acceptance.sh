#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

gradle_command="${GRADLE_COMMAND:-gradle}"
lock_context="${AFTERLIGHT_LOCK_CONTEXT:-macos}"
token="${AFTERLIGHT_DEDICATED_ACCEPTANCE_TOKEN:-}"
if [[ -z "$token" ]]; then
  if ! command -v openssl >/dev/null 2>&1; then
    printf '%s\n' 'Missing openssl for the dedicated acceptance challenge.' >&2
    exit 1
  fi
  token="$(openssl rand -hex 32)"
fi
if [[ ! "$token" =~ ^[0-9a-f]{64}$ ]]; then
  printf '%s\n' 'Dedicated acceptance challenge must be 64 lowercase hexadecimal characters.' >&2
  exit 1
fi
if [[ "$lock_context" != 'macos' && "$lock_context" != 'linux' ]]; then
  printf 'Unsupported dedicated acceptance lock context: %s\n' "$lock_context" >&2
  exit 1
fi

run_phase() {
  local phase="$1"
  "$gradle_command" \
    runGameTestServer \
    -PafterlightDedicatedMigrationAcceptance=true \
    -PafterlightDedicatedMigrationPhase="$phase" \
    -PafterlightDedicatedMigrationToken="$token" \
    -PafterlightLockContext="$lock_context" \
    --no-daemon
}

run_phase prepare
run_phase verify

printf '%s\n' 'DEDICATED MIGRATION ACCEPTANCE: OK'
