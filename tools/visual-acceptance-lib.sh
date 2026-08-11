#!/usr/bin/env bash

visual_glxinfo_field() {
  local file=$1
  local label=$2
  awk -F': ' -v label="$label" '$1 == label {sub(/^[^:]+: /, ""); print; exit}' "$file"
}

visual_assert_approved_glxinfo() {
  local file=$1
  local vendor renderer version normalized_vendor normalized_renderer normalized_version
  vendor="$(visual_glxinfo_field "$file" 'OpenGL vendor string')"
  renderer="$(visual_glxinfo_field "$file" 'OpenGL renderer string')"
  version="$(visual_glxinfo_field "$file" 'OpenGL core profile version string')"
  if [[ -z "$version" ]]; then
    version="$(visual_glxinfo_field "$file" 'OpenGL version string')"
  fi
  normalized_vendor="$(printf '%s' "$vendor" | tr '[:upper:]' '[:lower:]')"
  normalized_renderer="$(printf '%s' "$renderer" | tr '[:upper:]' '[:lower:]')"
  normalized_version="$(printf '%s' "$version" | tr '[:upper:]' '[:lower:]')"
  if [[ "$normalized_vendor" != *mesa* && "$normalized_vendor" != *x.org* ]]; then
    printf 'Unapproved OpenGL vendor: %s\n' "$vendor" >&2
    return 1
  fi
  if [[ "$normalized_renderer" != *llvmpipe* ]]; then
    printf 'Unapproved OpenGL renderer: %s\n' "$renderer" >&2
    return 1
  fi
  if [[ "$normalized_version" != *mesa* ]]; then
    printf 'Unapproved OpenGL version: %s\n' "$version" >&2
    return 1
  fi
}

visual_wait_for_log() {
  local pid=$1
  local file=$2
  local needle=$3
  local attempts=$4
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if grep -Fq "$needle" "$file" 2>/dev/null; then
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      printf 'Process %s exited before log marker: %s\n' "$pid" "$needle" >&2
      return 1
    fi
    sleep 1
  done
  printf 'Timed out waiting for log marker: %s\n' "$needle" >&2
  return 1
}

visual_assert_png_inventory() {
  local artifact_root=$1
  shift
  local expected=("$@")
  local actual_count artifact
  actual_count="$(find "$artifact_root/screenshots" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d ' ')"
  if [[ "$actual_count" -ne "${#expected[@]}" ]]; then
    printf 'Expected %s PNG artifacts, found %s.\n' "${#expected[@]}" "$actual_count" >&2
    return 1
  fi
  for artifact in "${expected[@]}"; do
    if [[ ! -s "$artifact_root/screenshots/$artifact" ]]; then
      printf 'Missing PNG artifact: %s\n' "$artifact" >&2
      return 1
    fi
  done
}
