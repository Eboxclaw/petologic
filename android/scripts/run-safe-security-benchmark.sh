#!/usr/bin/env bash
set -euo pipefail

: "${SAFE_BENCHMARK_CORPUS_URL:?Set the immutable HTTPS URL for the encrypted inert corpus.}"
: "${SAFE_BENCHMARK_CORPUS_SHA256:?Set the encrypted artifact SHA-256.}"
: "${SAFE_BENCHMARK_GPG_PASSPHRASE:?Set the GPG passphrase in the protected benchmark environment.}"

case "$SAFE_BENCHMARK_CORPUS_URL" in
  https://*) ;;
  *) echo "Corpus URL must use HTTPS." >&2; exit 2 ;;
esac

encrypted_file="$(mktemp "${RUNNER_TEMP:-/tmp}/paladino-safe-benchmark.XXXXXX.gpg")"
passphrase_file="$(mktemp "${RUNNER_TEMP:-/tmp}/paladino-safe-benchmark.XXXXXX.key")"
cleanup() {
  rm -f "$encrypted_file" "$passphrase_file"
}
trap cleanup EXIT
chmod 600 "$encrypted_file" "$passphrase_file"
printf '%s' "$SAFE_BENCHMARK_GPG_PASSPHRASE" > "$passphrase_file"
curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' --tlsv1.2 "$SAFE_BENCHMARK_CORPUS_URL" --output "$encrypted_file"
printf '%s  %s\n' "$SAFE_BENCHMARK_CORPUS_SHA256" "$encrypted_file" | sha256sum --check --strict

# Resolve and cache build dependencies before the protected runner loses network access.
./gradlew --no-daemon :security-benchmark:classes
if [[ "${CI:-false}" == "true" ]]; then
  command -v unshare >/dev/null || { echo "unshare is required to isolate the release benchmark." >&2; exit 2; }
  sudo -n true || { echo "Passwordless sudo is required to isolate the release benchmark." >&2; exit 2; }
fi

if [[ "${CI:-false}" == "true" ]]; then
  gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$passphrase_file" --decrypt "$encrypted_file" |
    sudo -n unshare --net -- sudo -n -u "$(id -un)" env HOME="$HOME" PATH="$PATH" \
      GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}" \
      ./gradlew --offline --no-daemon :security-benchmark:securityBenchmark -PbenchmarkCorpus=-
else
  gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$passphrase_file" --decrypt "$encrypted_file" |
    ./gradlew --offline --no-daemon :security-benchmark:securityBenchmark -PbenchmarkCorpus=-
fi
