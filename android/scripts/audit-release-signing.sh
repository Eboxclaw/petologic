#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C
: "${PALADINO_KEYSTORE:?Set PALADINO_KEYSTORE to the existing release keystore. This script never creates one.}"
: "${PALADINO_KEY_ALIAS:?Set PALADINO_KEY_ALIAS to the existing release alias.}"
: "${PALADINO_STOREPASS_FILE:?Set PALADINO_STOREPASS_FILE to a protected file containing the keystore password.}"
: "${PALADINO_EXPECTED_CERT_SHA256:?Set PALADINO_EXPECTED_CERT_SHA256 to the approved colon-separated SHA-256 certificate fingerprint.}"
test -f "$PALADINO_KEYSTORE"
test -f "$PALADINO_STOREPASS_FILE"
details="$(keytool -list -v -keystore "$PALADINO_KEYSTORE" -alias "$PALADINO_KEY_ALIAS" -storepass:file "$PALADINO_STOREPASS_FILE" 2>/dev/null)"
actual="$(printf '%s\n' "$details" | awk -F'SHA256: ' '/SHA256:/{print $2;exit}')"
test -n "$actual"
expected="$(printf '%s' "$PALADINO_EXPECTED_CERT_SHA256" | tr '[:lower:]' '[:upper:]')"
if [[ "$actual" != "$expected" ]]; then
  echo "Release signing certificate does not match the approved fingerprint." >&2
  exit 1
fi
echo "Release signing identity verified: $actual"
