#!/usr/bin/env bash
# Public copy must not contain em dashes (U+2014) or en dashes (U+2013).
# Regular hyphens are fine. Extend the file list when new public copy is added.
set -euo pipefail
cd "$(dirname "$0")/.."
files=(
  src/routes/index.tsx
  src/components/ApkDownload.tsx
  src/lib/apkDownload.ts
  README.md
  android/README.md
  docs/INSTALL-ANDROID.md
  android/app/src/main/AndroidManifest.xml
  android/app/src/main/res/values/strings.xml
  android/app/src/main/res/values-pt/strings.xml
  android/app/src/main/res/values/security.xml
  android/app/src/main/res/values-pt/security.xml
  android/app/src/main/java/ai/petologic/paladino/UiText.kt
  android/app/src/main/java/ai/petologic/paladino/ControlCenter.kt
  android/app/src/main/java/ai/petologic/paladino/OnboardingCards.kt
  android/app/src/main/java/ai/petologic/skills/security/guard/GuardRepository.kt
  android/app/src/main/java/ai/petologic/skills/security/guard/SecurityViewModel.kt
)
failed=0
for f in "${files[@]}"; do
  [[ -f "$f" ]] || continue
  hits=$(grep -n $'\u2014\|\u2013' "$f" 2>/dev/null || true)
  if [[ -n "$hits" ]]; then
    echo "FAIL $f" >&2
    echo "$hits" >&2
    failed=1
  fi
done
if [[ $failed -ne 0 ]]; then
  echo "Public copy contains em/en dashes. Replace them with ':' or plain wording." >&2
  exit 1
fi
echo "Public copy typography OK (no em/en dashes)."
