# Verification ledger — 2026-09-06 checkpoint

**Development checkpoint, not a release or main-merge recommendation.** The first feature-branch push was explicitly requested by the user. Commit identity: Eboxclaw, `eboxclaw@proton.me`.

## Current passing evidence

- `android/gradlew -p android :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :core:test :app:lintDebug`: build successful.
- Pure Kotlin core: **9 tests, 0 failures**.
- Android JVM transport/Koog tests: **7 tests, 0 failures**. Includes mocked OpenRouter streaming/consent handling and Koog search → save → answer observations, repeated-tool guard and disabled tools.
- Android instrumentation: **9 tests, 0 failures**, 14.954 seconds in the final run. Includes Chat conversation drawer, approval/save/delete UI, model settings UI, Room v2→v3 migration, session isolation/branch/archive/restore, idempotent note writes, real LFM2.5-350M through Koog, and actual semantic encoder retrieval.
- Emulator Wi-Fi and mobile data disabled for the real-model lane. Models were verified/imported before the run; this is offline inference evidence, not an in-app download test.
- Lint: **0 errors, 24 warnings**. Warnings remain and require triage before release.
- Raw final instrumentation output: [device test log](evidence/2026-09-06-device-tests.txt).

## Environment and reproduction

Host: Apple Silicon macOS. Android SDK API 36 build toolchain; API 35 Google APIs ARM64 emulator, launched with 8192 MB RAM. The original smaller emulator configuration produced a 60-second real-model timeout on one run. The final rerun on the current emulator passed. This does not prove a physical 8 GB phone's latency, thermal behavior or available RAM.

Pinned runtime: llama.cpp `73a43d1f69345aee8bb186ef4b3172cef892f2e5`, Koog 1.2.0, ONNX Runtime 1.22.0. Exact model hashes are in `android/core/src/main/kotlin/ai/petologic/core/ModelCatalog.kt`.

```sh
android/scripts/bootstrap-native.sh
android/gradlew -p android :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Install/import the pinned baseline and semantic encoder first.
adb shell svc wifi disable
adb shell svc data disable
adb shell am instrument -w -e realModel true ai.petologic.paladino.test/androidx.test.runner.AndroidJUnitRunner
```

Models and SDK are not committed. `local.properties`, model weights, keys, caches and build outputs are excluded. The bootstrap script downloads the pinned native source; Gradle fetches dependencies. CI executes build/JVM tests/lint, not the real-weight emulator lane.

## Local debug artifact

APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 184,530,801 bytes. SHA-256: `3643bd8d6db17fe9554d44ee7ea7265aaed99f56b58c7199bad559ede7529130`. This local artifact is excluded from Git; CI builds downloadable artifacts for authenticated repository users.

## Issues found and limits

- ONNX 1.29.0 produced SIGILL during encoder initialization in an earlier ARM emulator run. 1.22.0 passed the current real encoder test. Native 16KB-page compatibility remains an explicit release gate.
- Earlier device runs found test selector ambiguity and an optional field in the migration fixture. Those fixtures were corrected and the full lane rerun. No failed run is counted as passing.
- The isolated 60-second local-model timeout remains a performance investigation item; the final passing run does not establish its root cause.
- No live OpenRouter account call, OAuth connection, provider billing verification or other direct-provider adapter was tested.
- No real 2.6B/VL inference benchmark or physical phone thermal/battery measurement is claimed. Both 2.6B files were downloaded to the host for subsequent experiments; only baseline350M/encoder have passed this lane.
- Koog's in-loop compression hook compiles; dedicated compression-quality, over-limit and recovery fixtures remain required. Durable session summaries are not implemented.
- Launcher widget builds, but launcher placement/click, overlay, FAB/bubble accessibility and background-service behavior are not covered by this suite.
- Session data/migration tests do not certify full process-death execution resumption, race-free permission revocation or global/agent policy inheritance.
- Model picker/download interruption, disk-pressure recovery and folder grant revocation need device tests.
- No public APK site deployment or signed production release. Debug APK signing is development-only and not a stable public update channel.

Current result: suitable as a documented feature checkpoint; **hold main merge and public release** pending the reviewed plan's remaining gates.

## 2026-09-07 — Sprite and real inference update

Rebuilt APK passed 19 JVM and 15 device tests, including actual model replies through Chat and floating overlay. Optimized previously unoptimized debug CPU kernels; see [measured evidence](evidence/2026-09-07-model-and-sprite.md). Pet reaction presentation and the [format/authoring handoff](plans/03-pet-format-and-authoring.md) are added. These changes are local on the feature branch, not a production release.

## 2026-09-07 — Real model tool loop verified

24 JVM and 16 Android device tests passed after fixing native tool parsing, message roles, history order and retrieval payloads. Real Chat UI tests cover two greetings without tools, model-selected note creation with approval, and model-selected search followed by a grounded natural-language reply. See [full evidence and limitations](evidence/2026-09-07-real-tool-loop.md). This supersedes the earlier inference-only verification, not the remaining release gates.

## 2026-09-07 — Draggable Sprite UI checkpoint

26 JVM and 16 Android device tests passed. Floating Sprite drag, edge docking and position restoration across service restart are verified alongside actual local inference. Added compact navy/gold bubble, original vector action icons and matching widget surface. See [UI foundation](plans/04-sprite-overlay-foundation.md) and [Liquid baseline](reviews/2026-09-07-liquid-baseline.md). Physical-device and release gates remain open.

## 2026-09-10 — Signed PT/EN and Markdown preview

0.1.3 supersedes earlier distribution/UI gaps: 36 JVM tests, lint/builds, 8 final Android tests and PT flows at normal/200% text passed. Signed update preserved Conversation 2. [Evidence and limits](evidence/2026-09-10-localization/validation.md), [release](releases/0.1.3-preview.md), [installation](INSTALL-ANDROID.md). Physical-device, TalkBack, provider-account and production-readiness gates remain open.
