# Paladino security research and threat-memory program

Paladino improves through reviewed, signed data packs. Phones do not train the detector and do not upload findings. Exact indicators, bounded rules and Android evidence create findings; Liquid vector similarity only retrieves related reviewed context.

## Release loop

1. Ingest registered metadata into an isolated research workspace. Store provenance, first-seen time and licensing with every record. Never acquire malware binaries or executable payloads for this program.
2. Deduplicate by exact indicator and source revision. Split evaluation data chronologically and by campaign or malware family; never use a random split that leaks variants across train and test.
3. Cluster candidate behavior profiles and lures. Low similarity means unfamiliar, not malicious.
4. Require an analyst to promote a candidate into an exact indicator, rule, behavior profile or knowledge card.
5. Run benign false-positive, future-time, adversarial mutation, size, timeout and license gates.
6. Build a monotonic manifest with `android/scripts/build-threat-manifest.py`, sign it with an offline Ed25519 key, and publish immutable artifacts over HTTPS.

The release builder refuses any source that is not marked both production-eligible and redistribution-reviewed in `android/security-data/source-registry.json`. Research-only feeds may inform evaluation but cannot enter a release artifact.

The builder runs with Python 3 and the same JDK 17 required by the Android build. It accepts a PKCS#8 Ed25519 PEM key from outside the repository, signs the canonical JSON payload through Java's Ed25519 provider, and emits a base64 envelope understood by the app. Artifact descriptors must name the artifact kind, local path, immutable HTTPS URL, record count, source-set version, license-manifest path and registered source IDs. Keep the key offline; copy only the signed envelope and immutable artifacts to the publishing system.

## Cadence

- Ingest permitted feeds hourly and build candidates daily.
- Review drift and uncertain clusters weekly.
- Recalibrate thresholds and refresh the frozen time-sliced benchmark monthly.
- Review the Liquid model artifact quarterly. A new or fine-tuned model is a new pinned artifact and must pass the complete retrieval, privacy, memory and latency gates.
- Phones check the signed manifest daily. An urgent wake-up only starts a manifest check; it carries no verdict or threat content.

## First benchmark

- Android: permission, component, installer, signing-certificate and high-risk service profiles, split by observation year and family.
- Links: source-derived structural patterns converted in the isolated workspace into reserved-domain and documentation-network simulations. Raw active malicious URLs never enter the repository, development computer or phone.
- Messages: Portuguese and English lure families with paraphrases, benign lookalikes and withheld future campaigns.
- Retrieval: reviewed campaign and behavior queries with relevance labels. Release target is Recall@5 at least 0.90 in both supported languages and warm p95 at most 200 ms for 10,000 records on the reference ARM64 device.

Malware binaries, APKs, executable archives, active malicious URLs, private device content and signing keys never enter the application repository, development computer or benchmark phone. The versioned schemas, safety validator, inert smoke corpus, deterministic report generator, encrypted release workflow and offline Pixel 7a retrieval lane are documented in [safe-security-benchmark.md](safe-security-benchmark.md).
