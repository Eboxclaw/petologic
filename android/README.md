# 0xPaladino Android

Native Kotlin/Compose application, alongside the original Lovable landing page. This is a working development preview of the implementation plan, not a release-certified v1.

## Run locally

Requirements: JDK 17, Android SDK platform 36, build-tools 36.0.0, NDK 28.2.13676358, CMake 3.22.1. Minimum supported app API is 31. Use an API 35+ ARM64 emulator on Apple Silicon or x86_64 emulator on an Intel/Linux host.

```sh
cd android
./scripts/bootstrap-native.sh
./scripts/bootstrap-security.sh
# Set ANDROID_HOME to your SDK, or create local.properties with sdk.dir=/absolute/sdk/path
./gradlew :core:test :security-core:test :security-scanner:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:installDebug
```

Open 0xPaladino. In Settings, download the mandatory LFM2.5-VL-450M speaking model (219 MB), optionally add its 103 MB vision projector, then optionally install LFM2.5-Embedding-350M vector memory (229 MB). Every artifact uses a pinned revision and SHA-256 verification. The app shows setup status rather than substituting a fake model. Installed models work with networking disabled. No provider key is required for Tiny.

Outputs: `app/build/outputs/apk/debug/app-debug.apk`. The debug APK contains ARM64 and x86_64 native libraries. Debug signing is for development only.

Chat is the everyday app tab. Open its conversation drawer to create, switch, branch, archive or restore sessions. Orchestration groups memory and permissions; Console shows scoped execution metadata. Settings contains the model library, existing file/folder import, OpenRouter and supported advanced inference controls. The launcher widget is a simple entry point; background inference and overlay Sprite are not enabled.

## Try it

- Type `Remember that my bicycle is in the blue garage`. Review and save the note.
- Type `Find bicycle`. Inspect Orchestration → Memory, then delete the note through confirmation.
- With vector memory installed, search for a paraphrase in English or Portuguese. The preview considers the latest 200 notes.
- Ask a short question in Tiny. This executes actual LFM2.5-VL-450M via Koog and streams text into chat.
- In Security, choose a screenshot or image and run the local visual check. The image is resized in memory, excluded from chat history and cannot trigger tools or security actions. Treat the response as fallible visual cues, not OCR: exact text-like content is masked and must be verified independently.
- In Settings, supply your own OpenRouter key and exact provider/model ID. Select Maxx, send a message, inspect the outbound context, and approve. The key is Keystore-encrypted. Connection is verified by the first request; saving a key is not a successful authentication claim.
- Stop a running response. Mode changes and new requests stay blocked until the task finishes cancellation.

## Tests

```sh
./gradlew :core:test :security-core:test :security-scanner:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The default device suite skips tests that need real downloaded weights. For the full lane, install both models in the app first, then:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.realModel=true -Pandroid.testInstrumentationRunnerArguments.realGuardModel=true -Pandroid.testInstrumentationRunnerArguments.realVision=true
```

Or build/install the app and test APK and run `adb shell am instrument -w -e realModel true -e realGuardModel true -e realVision true ai.petologic.paladino.test/androidx.test.runner.AndroidJUnitRunner`. Real-model tests never silently pass when weights are missing. The opt-ins are deliberately separate from ordinary UI coverage. For offline evidence disable emulator Wi-Fi and mobile data before running this lane.

An emulator proves functionality, not phone battery or thermal performance. See [validation](../docs/VALIDATION.md) for the actual evidence and [missing work](../docs/MISSING.md) for release gaps.

## Layout

- `core`: pure Kotlin routing, context/consent, approval policy, manifest validation, graph bounds and tokenizer tests.
- `app/.../runtime`: native Liquid generation and vector-model lifecycles, OpenRouter transport and Koog adapter.
- `app/.../data`: Room source of truth, AppSearch outbox/index, embedding cache and transactional note actions.
- `app/.../MainActivity.kt`: native Compose screens.
- `app/src/main/cpp`: pinned llama.cpp integration via JNI. Vendor source is fetched by the bootstrap script and excluded from Git.
- `app/schemas`: exported Room schemas; migrations 1→2 add the embedding cache and 2→3 attach existing data to the default session.

The bundled manifest exposes only implemented note tools. Cloud text is never parsed into executable tools. Future capabilities must go through the same approval boundary; see the architecture decision record.

## Security preview

The Security tab and Android share target perform local text and file checks without a model. When the optional vision projector is installed, a user-selected image can also be observed by the local VL model. Visual observations stay separate from deterministic findings, cannot call tools, and are buffered through a policy that masks quoted text, URLs, IP addresses and long numbers before display.
The isolated scanner uses SHA-256, a signed historical ESET indicator subset, and pinned YARA 4.5.8 with a harmless self-test rule. This is not comprehensive antivirus coverage.

When LFM2.5-Embedding-350M is installed, reviewed knowledge cards and redacted findings are indexed into separate, versioned namespaces. Room is the durable source of truth and AppSearch provides exact 1024-dimensional cosine retrieval when the device supports embedding search; the app falls back to exact local dot products over the same vectors. Deleting or expiring a finding removes its derived vector. Similarity retrieves context for the Security chat and never creates or changes a finding.

Threat updates use an Ed25519-signed, expiring, monotonic manifest plus immutable HTTPS artifacts. The app validates every required artifact before activation, rejects rollback, records revocations, retains the verified manifest for audit, and checks daily under network and battery constraints. No production endpoint is bundled. Configure one at build time only after provisioning offline signing and reviewed feeds:

```sh
./gradlew :app:assembleDebug -PthreatManifestUrl=https://updates.example/security/stable.json
```

The validation build also checks visible installed apps after install/update events and during a daily reconciliation. Base and split APKs are hashed and structurally inspected without executing them. Optional private reputation uses signed prefix shards and can be enabled only with a deployed HTTPS endpoint:

```sh
./gradlew :app:assembleRelease \
  -PthreatManifestUrl=https://updates.example/security/stable.json \
  -PreputationUrl=https://reputation.example
./gradlew :app:bundlePlayRelease \
  -PthreatManifestUrl=https://updates.example/security/stable.json \
  -PreputationUrl=https://reputation.example
```

`assembleRelease` is the direct distribution and can hand verified updates to Android. `bundlePlayRelease` produces the Play upload and removes `REQUEST_INSTALL_PACKAGES` from that variant while using store-managed updates. Both use `ai.petologic.paladino`; release signing must use the same approved identity. See [production antivirus readiness](../docs/production-antivirus-readiness.md) before publishing either artifact.

The offline research/release tools are `scripts/build-threat-manifest.py`, `security-data/source-registry.json`, and [the research program](../docs/security-research-program.md). The builder refuses sources that lack both production eligibility and redistribution review.

The efficacy harness executes the same pure Kotlin URL, message, app-profile and exact-hash detectors used by the app. Its committed smoke corpus contains only inert hashes, reserved-domain links and synthetic metadata:

```sh
./gradlew :security-benchmark:test :security-benchmark:benchmarkSmoke
```

Full corpora remain encrypted outside Git and are streamed into the isolated release workflow without writing plaintext. The opt-in Pixel 7a lane evaluates only validated safe text with LFM2.5-Embedding-350M while the phone is offline, then clears the temporary index and deletes the staged corpus. No malware, APK, executable, archive or active malicious URL is allowed in any benchmark lane. See [safe benchmark operations](../docs/safe-security-benchmark.md).

Release builds use R8 code optimization and resource shrinking with explicit JNI retention. See [implementation status and release gates](../docs/security-guard-implementation.md) and [minified release evidence](../docs/evidence/security-guard/release-shrinking.md) for tested behavior, remaining model work, signing custody, artifact size and reproduction steps.
