#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$root/tools/visual-acceptance-lib.sh"
cd "$root"

gradle_command="${GRADLE_COMMAND:-gradle}"
artifact_root="$root/build/visual-artifacts"
server_marker="$root/build/visual-server-ready.txt"
server_log="$root/build/visual-server.log"
client_log="$root/build/visual-client.log"
xvfb_log="$root/build/visual-xvfb.log"
glxinfo_log="$artifact_root/glxinfo-B.txt"
server_pid=""
xvfb_pid=""

cleanup() {
  status=$?
  if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  if [[ -n "$xvfb_pid" ]] && kill -0 "$xvfb_pid" 2>/dev/null; then
    kill "$xvfb_pid" 2>/dev/null || true
    wait "$xvfb_pid" 2>/dev/null || true
  fi
  if [[ "$status" -ne 0 ]]; then
    printf '%s\n' 'VISUAL ACCEPTANCE: FAILED'
    printf '%s\n' '--- Xvfb log ---'
    tail -200 "$xvfb_log" 2>/dev/null || true
    printf '%s\n' '--- visual server log ---'
    tail -200 "$server_log" 2>/dev/null || true
    printf '%s\n' '--- visual client log ---'
    tail -200 "$client_log" 2>/dev/null || true
  fi
}
trap cleanup EXIT

for command in timeout Xvfb glxinfo xauth; do
  if ! command -v "$command" >/dev/null 2>&1; then
    printf 'Missing visual acceptance command: %s\n' "$command" >&2
    exit 1
  fi
done

"$gradle_command" \
  test \
  prepareVisualServerWorkspace \
  prepareVisualClientWorkspace \
  createVisualServerLaunchScript \
  createVisualClientLaunchScript \
  -PafterlightLockContext=linux \
  --no-daemon

mkdir -p "$artifact_root"
dpkg-query -W -f='${binary:Package}\t${Version}\n' \
  xvfb xauth libgl1-mesa-dri libglx-mesa0 mesa-utils \
  | LC_ALL=C sort >"$artifact_root/ubuntu-package-versions.txt"

export DISPLAY="${AFTERLIGHT_VISUAL_DISPLAY:-:99}"
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
export __GLX_VENDOR_LIBRARY_NAME=mesa
Xvfb "$DISPLAY" \
  -screen 0 3840x2160x24 \
  +extension GLX \
  +render \
  -noreset \
  -nolisten tcp \
  -ac >"$xvfb_log" 2>&1 &
xvfb_pid=$!

for ((attempt = 1; attempt <= 100; attempt++)); do
  if glxinfo -B >"$glxinfo_log" 2>/dev/null; then
    break
  fi
  if ! kill -0 "$xvfb_pid" 2>/dev/null; then
    printf '%s\n' 'Xvfb exited before glxinfo could connect.' >&2
    exit 1
  fi
  sleep 1
done
if [[ ! -s "$glxinfo_log" ]]; then
  printf '%s\n' 'glxinfo did not produce renderer metadata.' >&2
  exit 1
fi
visual_assert_approved_glxinfo "$glxinfo_log"

if [[ -e "$server_marker" ]]; then
  printf '%s\n' 'Visual server marker existed before an authorized client joined.' >&2
  exit 1
fi
timeout 900 build/moddev/runVisualServer.sh >"$server_log" 2>&1 &
server_pid=$!
visual_wait_for_log "$server_pid" "$server_log" 'Done (' 600

timeout 900 build/moddev/runVisualClient.sh >"$client_log" 2>&1

test -s "$server_marker"
test -s "$artifact_root/visual-acceptance-success.txt"
test -s "$artifact_root/manifest.json"
test -s "$artifact_root/glxinfo-B.txt"
test -s "$artifact_root/ubuntu-package-versions.txt"

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
visual_assert_png_inventory "$artifact_root" "${expected_artifacts[@]}"

printf '%s\n' 'VISUAL ACCEPTANCE: OK'
