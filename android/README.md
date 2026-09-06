# 0xPaladino Android

Native Kotlin/Compose application, alongside the original Lovable landing page. This is a working development preview of the implementation plan, not a release-certified v1.

## Run locally

Requirements: JDK 17, Android SDK platform 36, build-tools 36.0.0, NDK 28.2.13676358, CMake 3.22.1. Minimum supported app API is 31. Use an API 35+ ARM64 emulator on Apple Silicon or x86_64 emulator on an Intel/Linux host.

```sh
cd android
./scripts/bootstrap-native.sh
# Set ANDROID_HOME to your SDK, or create local.properties with sdk.dir=/absolute/sdk/path
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:installDebug
```

Open 0xPaladino. In Settings, download the mandatory LFM2.5-350M model (229 MB), then optionally install semantic memory (23 MB). Both downloads use pinned revisions and SHA-256 verification. The app shows setup status rather than substituting a fake model. Installed LFM works with networking disabled. No provider key is required for Tiny.

Outputs: `app/build/outputs/apk/debug/app-debug.apk`. The debug APK contains ARM64 and x86_64 native libraries. Debug signing is for development only.

Chat is the everyday app tab. Open its conversation drawer to create, switch, branch, archive or restore sessions. Orchestration groups memory and permissions; Console shows scoped execution metadata. Settings contains the model library, existing file/folder import, OpenRouter and supported advanced inference controls. The launcher widget is a simple entry point; background inference and overlay Sprite are not enabled.

## Try it

- Type `Remember that my bicycle is in the blue garage`. Review and save the note.
- Type `Find bicycle`. Inspect Orchestration → Memory, then delete the note through confirmation.
- With semantic memory installed, search for a paraphrase. The encoder preview is English-oriented and considers the latest 200 notes.
- Ask a short question in Tiny. This executes actual LFM2.5-350M via Koog and streams text into chat.
- In Settings, supply your own OpenRouter key and exact provider/model ID. Select Maxx, send a message, inspect the outbound context, and approve. The key is Keystore-encrypted. Connection is verified by the first request; saving a key is not a successful authentication claim.
- Stop a running response. Mode changes and new requests stay blocked until the task finishes cancellation.

## Tests

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The default device suite skips tests that need real downloaded weights. For the full lane, install both models in the app first, then:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.realModel=true
```

Or build/install the app and test APK and run `adb shell am instrument -w -e realModel true ai.petologic.paladino.test/androidx.test.runner.AndroidJUnitRunner`. Real-model tests never silently pass when weights are missing. The `realModel` opt-in is deliberately separate from ordinary UI coverage. For offline evidence disable emulator Wi-Fi and mobile data before running this lane.

An emulator proves functionality, not phone battery or thermal performance. See [validation](../docs/VALIDATION.md) for the actual evidence and [missing work](../docs/MISSING.md) for release gaps.

## Layout

- `core`: pure Kotlin routing, context/consent, approval policy, manifest validation, graph bounds and tokenizer tests.
- `app/.../runtime`: native LFM lifecycle, ONNX embedder, OpenRouter transport and Koog adapter.
- `app/.../data`: Room source of truth, AppSearch outbox/index, embedding cache and transactional note actions.
- `app/.../MainActivity.kt`: native Compose screens.
- `app/src/main/cpp`: pinned llama.cpp integration via JNI. Vendor source is fetched by the bootstrap script and excluded from Git.
- `app/schemas`: exported Room schemas; migrations 1→2 add the embedding cache and 2→3 attach existing data to the default session.

The bundled manifest exposes only implemented note tools. Cloud text is never parsed into executable tools. Future capabilities must go through the same approval boundary; see the architecture decision record.
