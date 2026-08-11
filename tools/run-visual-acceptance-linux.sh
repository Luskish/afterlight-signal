#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

gradle_command="${GRADLE_COMMAND:-gradle}"
artifact_root="$root/build/visual-artifacts"
server_marker="$root/build/visual-server-ready.txt"
server_log="$root/build/visual-server.log"
client_log="$root/build/visual-client.log"
server_pid=""

cleanup() {
  status=$?
  if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  if [[ "$status" -ne 0 ]]; then
    printf '%s\n' 'VISUAL ACCEPTANCE: FAILED'
    printf '%s\n' '--- visual server log ---'
    tail -200 "$server_log" 2>/dev/null || true
    printf '%s\n' '--- visual client log ---'
    tail -200 "$client_log" 2>/dev/null || true
  fi
}
trap cleanup EXIT

"$gradle_command" \
  test \
  prepareVisualServerWorkspace \
  prepareVisualClientWorkspace \
  createVisualServerLaunchScript \
  createVisualClientLaunchScript \
  -PafterlightLockContext=linux \
  --no-daemon

timeout 900 build/moddev/runVisualServer.sh >"$server_log" 2>&1 &
server_pid=$!

for ((attempt = 1; attempt <= 600; attempt++)); do
  if grep -Fq 'Done (' "$server_log" 2>/dev/null; then
    break
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    printf '%s\n' 'Visual server exited before writing its ready marker.' >&2
    exit 1
  fi
  sleep 1
done

if ! grep -Fq 'Done (' "$server_log" 2>/dev/null; then
  printf '%s\n' 'Visual server readiness timed out.' >&2
  exit 1
fi

timeout 900 env \
  LIBGL_ALWAYS_SOFTWARE=1 \
  MESA_LOADER_DRIVER_OVERRIDE=llvmpipe \
  __GLX_VENDOR_LIBRARY_NAME=mesa \
  xvfb-run -a -s '-screen 0 3840x2160x24 +extension GLX +render -noreset' \
  build/moddev/runVisualClient.sh >"$client_log" 2>&1

test -s "$server_marker"
test -s "$artifact_root/visual-acceptance-success.txt"
test -s "$artifact_root/manifest.json"

expected_artifacts=(
  title-1920x1080.png
  title-3440x1440.png
  title-854x480.png
  echo-wide.png
  echo-standard.png
  echo-compact.png
  echo-minimal.png
  echo-item-gui.png
  echo-item-first-person.png
  echo-item-third-person.png
  echo-item-dropped.png
  echo-item-frame.png
  gate-idle.png
  gate-open.png
  gate-fault.png
  far-relay-arrival.png
  far-relay-central.png
  far-relay-east.png
  far-relay-west.png
  far-relay-north.png
  far-relay-south.png
  far-relay-return.png
)
expected_count=22
actual_count="$(find "$artifact_root/screenshots" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d ' ')"
if [[ "$actual_count" -ne "$expected_count" ]]; then
  printf 'Expected %s PNG artifacts, found %s.\n' "$expected_count" "$actual_count" >&2
  exit 1
fi
for artifact in "${expected_artifacts[@]}"; do
  test -s "$artifact_root/screenshots/$artifact"
done

printf '%s\n' 'VISUAL ACCEPTANCE: OK'
