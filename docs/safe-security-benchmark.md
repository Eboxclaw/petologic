# Safe Paladino efficacy benchmark

This benchmark measures Paladino's production deterministic analyzers and Liquid Embedding-350M retrieval without acquiring or executing malware. It is an inert behavioral benchmark, not live-malware certification.

## Absolute safety boundary

The corpus may contain only SHA-256 text, reserved-domain or documentation-network links, synthetic English and Portuguese messages, synthetic Android capability profiles, and reviewed retrieval text. It must never contain a binary, APK, archive, macro, exploit, executable script, active malicious URL, credential, private key, or personal data.

The validator runs before any detector or model. It rejects executable/archive magic, embedded binary payloads, non-reserved URL hosts, traversal paths, credentials, personal email or phone patterns, duplicate identifiers, family leakage across splits, missing relevance labels, unregistered sources, license mismatches, and oversized records. Reports contain aggregate metrics and hashed case identifiers, never raw inputs.

## Lanes

Run the committed smoke corpus locally:

```sh
cd android
./gradlew :security-benchmark:test :security-benchmark:benchmarkSmoke
```

Run a decrypted safe release corpus without copying it into the repository:

```sh
./gradlew :security-benchmark:securityBenchmark -PbenchmarkCorpus=/absolute/path/safe-release-corpus.json
```

The protected GitHub workflow accepts only a GPG-encrypted artifact from an immutable HTTPS URL, verifies the encrypted SHA-256, and streams decrypted JSON directly into the runner. It writes only aggregate reports. Configure `SAFE_BENCHMARK_CORPUS_URL`, `SAFE_BENCHMARK_CORPUS_SHA256`, and `SAFE_BENCHMARK_GPG_PASSPHRASE` in the protected `security-benchmark` environment.

The physical retrieval lane is opt-in and accepts only a release corpus that has already passed the desktop validator. It requires one offline Pixel 7a, stages the safe text under the app's external test directory, uses an in-memory Room database plus a dedicated AppSearch database, runs one warm-up and three measured passes, clears both indexes, and deletes the staged corpus:

```sh
cd android
scripts/run-pixel7a-safe-retrieval.sh /absolute/path/safe-release-corpus.json
```

The Pixel report uses the median Recall@5 and worst p95 latency across the three measured passes. English and Portuguese each require Recall@5 of at least 0.90; warm p95 must be at most 200 ms over at least 10,000 records.

## Deterministic release gates

- Exact known hashes: 100% matches; mutated hashes: zero matches.
- Suspicious URLs, messages, and app profiles: at least 97% reach `REVIEW` or `HIGH_ATTENTION`.
- Critical multi-signal cases: at least 90% reach `HIGH_ATTENTION`.
- Benign cases: fewer than 1% reach `HIGH_ATTENTION`, and at most 15% reach `REVIEW` or higher.
- English and Portuguese message gates are calculated independently.

The release corpus remains an encrypted external artifact. Only schemas, the inert smoke corpus and its digest, source/license metadata, and aggregate reports belong in Git.
