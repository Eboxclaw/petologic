#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
revision=73a43d1f69345aee8bb186ef4b3172cef892f2e5
vendor="$root/app/src/main/cpp/vendor/llama.cpp"
if [ -d "$vendor/.git" ]; then
 actual="$(git -C "$vendor" rev-parse HEAD)"
 if [ "$actual" != "$revision" ]; then
  echo "Native revision mismatch: expected $revision, found $actual" >&2
  exit 1
 fi
 echo "Pinned native source verified: $revision"
 exit 0
fi
if [ -f "$vendor/.paladino-revision" ]; then
 actual="$(cat "$vendor/.paladino-revision")"
 if [ "$actual" = "$revision" ]; then
  echo "Local source snapshot recorded at $revision"
  exit 0
 fi
fi
if [ -e "$vendor" ]; then
 echo "Unrecognized native directory. Move it aside and rerun; it will not be overwritten." >&2
 exit 1
fi
mkdir -p "$(dirname "$vendor")"
git clone --filter=blob:none --no-checkout https://github.com/ggml-org/llama.cpp.git "$vendor"
git -C "$vendor" checkout "$revision"
