# Paladino production antivirus readiness

Paladino now has a production-shaped Android antivirus foundation. It is still a preview until the external services, signing identity, physical-device evidence, and independent laboratory results below exist. No malware, executable, APK payload, active malicious URL, or exploit is needed on a developer computer or personal phone.

## Implemented in the app

- Android-wide installed-app visibility through `QUERY_ALL_PACKAGES`, subject to Google Play approval and the user-facing declaration.
- Explicit opt-in for installed-app inventory access, followed by app install, update, and removal event handling plus daily reconciliation. Before opt-in, background inventory access stays off. Unchanged versions are skipped by a local version ledger.
- Static checks over Android-visible permissions, privileged services, installer source, target SDK, debug state, signing metadata, and bounded installed-APK archive structure.
- Streaming SHA-256 for installed base and split APKs. Files are read without loading, opening, copying, or executing their code.
- Exact matching against the active signed local threat pack.
- Optional signed k-anonymous reputation. The client sends one 8-hex SHA-256 prefix. It never sends the full hash, package name, filename, signer, device ID, or model output. Responses are size bounded, expiring, pinned to the bundled Ed25519 trust root, and matched locally by suffix.
- High-attention notification for exact hash or deterministic rule evidence. Notification permission remains user controlled.
- Separate direct and Play release behavior. The direct release may use the verified self-update flow. `playRelease` removes `REQUEST_INSTALL_PACKAGES`, hides self-update UI, and relies on store-managed updates. Both retain the same application ID.
- Deterministic detectors own findings. Liquid Embedding-350M retrieves reviewed context; VL-450M explains it. Neither model can create, raise, clear, or enforce a verdict.

## Required production inputs

1. **Signing identity.** The 25 September audit found only the older 0.1.1–0.1.5 certificate locally; it does not match the post-0.1.6 certificate recorded in the release notes. See [the signing audit](evidence/security-guard/signing-audit-2026-09-25.md). Recover and verify the post-0.1.6 key before claiming an in-place update. If it cannot be recovered, establish the intended new identity, communicate the reinstall boundary, enroll it in Play App Signing, back it up under two-person custody, and pin that fingerprint before building.
2. **Threat update service.** Host the signed manifest and immutable artifacts on HTTPS. Configure `-PthreatManifestUrl=https://…`. Exercise expiry, replay, rollback, revocation, partial-download, CDN-cache, and recovery drills.
3. **Private reputation service.** Build static signed prefix shards with `android/scripts/build-reputation-shards.py`, host the output as immutable HTTPS content, and configure `-PreputationUrl=https://…`. Use only reviewed metadata sources whose redistribution and production rights are recorded in `security-data/source-registry.json`. Do not ingest samples into this repository.
4. **Play policy.** Review and submit the [package-visibility declaration draft](play-query-all-packages-declaration.md) for the antivirus core. The Play upload artifact is `bundlePlayRelease`; the direct artifact is `assembleRelease`. Confirm the merged Play manifest contains `QUERY_ALL_PACKAGES` and excludes `REQUEST_INSTALL_PACKAGES`.
5. **Safe efficacy evidence.** Run the encrypted inert release corpus and the network-disabled Pixel 7a retrieval lane. The report must pass the existing recall, false-positive, provenance, crash, network, and latency gates.
6. **Physical Android validation.** On clean test phones, test fresh install, upgrade, reboot, package install/update/remove broadcasts using benign apps, permission denial, notification routing, battery, thermal behavior, storage pressure, offline operation, and 16 KiB page-size compatibility. Use ordinary benign Play or locally built test apps only.
7. **Independent antivirus evaluation.** Contract an AMTSO-aligned lab such as AV-TEST or AV-Comparatives. The lab owns any live-malware environment. Accept results only for protection, false positives, performance, usability, and current Android versions. No live sample is transferred to a Paladino developer machine or personal phone.

## Release decision

The product may describe the implemented build as an antivirus preview after its local and inert gates pass. Public claims equivalent to established antivirus products require a controlled production feed, verified signing continuity, Play policy approval, three physical-device runs, and independent lab evidence. Call screening and VPN/web protection remain later products with separate consent, policy, battery, and efficacy gates.

## Fail-closed release evidence

`android/scripts/verify-production-readiness.py` produces a machine-readable JSON report and a Markdown report. It has two explicit levels:

- `release-candidate` requires a full inert deterministic benchmark tied to the exact Git commit, real HTTPS threat and reputation endpoints, and signed direct and Play artifacts whose certificates match approved fingerprints.
- `public-antivirus` adds the network-disabled Pixel 7a retrieval report and an independent lab attestation bound to the exact direct APK digest. The lab attestation format is illustrated by `docs/lab-attestation.example.json`; a completed attestation and its report remain protected release evidence, not repository fixtures.

Missing inputs, placeholder or private endpoints, unsigned artifacts, a certificate mismatch, unknown Git revisions, a benchmark from another commit, incomplete English or Portuguese metrics, fewer than three measured retrieval runs, network-enabled device evidence, or absent external lab evidence fail the verifier. The verifier reads artifacts for hashing and signature metadata only; it does not install or execute them.

The manual `Signed production release candidate` workflow consumes a successful full-benchmark run for the same commit from the protected `security-benchmark` environment. It retrieves the signing key and production endpoint configuration only from the protected `production-release` environment, checks the approved certificate before building, then uploads artifacts only after the `release-candidate` gate passes. Its artifact name deliberately states that independent lab certification is still absent.

Release owners can run the stronger local evidence gate after the external lab returns its protected report:

```text
android/scripts/verify-production-readiness.py \
  --level public-antivirus \
  --git-revision <commit> \
  --deterministic-report <report.json> \
  --retrieval-report <retrieval-report.json> \
  --lab-attestation <completed-attestation.json> \
  --embedding-model-sha256 <pinned-embedding-model-digest> \
  --direct-apk <signed-direct.apk> \
  --play-aab <signed-play.aab> \
  --threat-manifest-url <https-url> \
  --reputation-url <https-url> \
  --expected-direct-cert-sha256 <fingerprint> \
  --output-dir <empty-report-directory>
```
