#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/decrypted-safe-release-corpus.json" >&2
  exit 2
fi

corpus="$1"
[[ -f "$corpus" ]] || { echo "Safe corpus file not found." >&2; exit 2; }

adb_bin="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
[[ -x "$adb_bin" ]] || { echo "adb was not found." >&2; exit 2; }
device_count="$($adb_bin devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
[[ "$device_count" == "1" ]] || { echo "Connect exactly one authorized Android device." >&2; exit 2; }
model="$($adb_bin shell getprop ro.product.model | tr -d '\r')"
[[ "$model" == "Pixel 7a" ]] || { echo "Release retrieval benchmark requires Pixel 7a; found '$model'." >&2; exit 2; }

git_revision="$(git -C .. rev-parse HEAD 2>/dev/null || printf unknown)"

# This validates and runs the deterministic suites locally before any safe text is staged.
./gradlew :security-benchmark:securityBenchmark -PbenchmarkCorpus="$corpus" \
  -PbenchmarkGitRevision="$git_revision"

remote_dir="/sdcard/Android/data/ai.petologic.paladino/files/safe-benchmark"
remote_corpus="$remote_dir/release-corpus.json"
cleanup_remote() {
  $adb_bin shell rm -f "$remote_corpus" >/dev/null 2>&1 || true
}
trap cleanup_remote EXIT
$adb_bin shell mkdir -p "$remote_dir"
$adb_bin push "$corpus" "$remote_corpus" >/dev/null

./gradlew :app:connectedDebugAndroidTest -PpaladinoAbi=arm64-v8a \
  -Pandroid.testInstrumentationRunnerArguments.class=ai.petologic.paladino.SafeRetrievalBenchmarkTest \
  -Pandroid.testInstrumentationRunnerArguments.safeRetrievalBenchmark=true \
  -Pandroid.testInstrumentationRunnerArguments.safeBenchmarkCorpus="$remote_corpus" \
  -Pandroid.testInstrumentationRunnerArguments.benchmarkGitRevision="$git_revision"

mkdir -p app/build/reports/safe-retrieval
$adb_bin pull "/sdcard/Android/data/ai.petologic.paladino/files/safe-benchmark-results/retrieval-report.json" \
  app/build/reports/safe-retrieval/retrieval-report.json >/dev/null
echo "Aggregate report: app/build/reports/safe-retrieval/retrieval-report.json"
