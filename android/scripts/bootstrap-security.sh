#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
revision=84b0e3cc0e42f8f8e6b84d19c97ec3ac6ff8aee8
vendor="$root/app/src/main/cpp/vendor/yara"
if [ ! -d "$vendor" ]; then
 git clone --depth 1 --branch v4.5.8 https://github.com/VirusTotal/yara.git "$vendor"
fi
test "$(git -C "$vendor" rev-parse HEAD)" = "$revision" || { echo "YARA revision mismatch" >&2; exit 1; }
echo "Pinned YARA source verified: $revision"
