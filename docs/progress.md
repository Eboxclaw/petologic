# Implementation progress — feature checkpoint

The current scope and acceptance gates are in [the reviewed plan](reviews/2026-09-06-control-plane-review.md). The original website remains in place. The app is a native Android development preview, not a release-certified v1.

Implemented in this branch:

- Chat with a conversation drawer; create/open/branch/archive/restore sessions and persist the selected session.
- Orchestration submenus for memory, tools, permissions and instructions; Console for per-session execution events.
- Model library with exact hash verification, file/folder reuse, baseline 350M and experimental 2.6B Q4/QAD selection.
- Single local inference runtime, supported advanced CPU/sampling settings, cancellation and sampled memory/timing metrics.
- Koog multi-turn note tools, observation return, bounded hops/tools/retries/timeouts and local in-loop compression hook.
- Room v3 session ownership, migration, AppSearch and small semantic encoder; expiring argument-bound write approvals.
- OpenRouter API-key storage in Keystore-backed encryption and explicit outbound context review; no live provider claim.
- Basic home-screen Paladino widget opening the app; no background inference or overlay permission.
- Android build CI and reproducible test lanes.

Not complete: physical-device model evaluation, durable context summaries and execution resume, full permission hierarchy, Sprite FAB/bubbles/overlay, foreground service, provider OAuth, operational knowledge graph, production signing and public APK download.

Main is unchanged. The first push is a user-authorized feature-branch checkpoint. Read [VALIDATION.md](VALIDATION.md) for tests actually run and failures; a successful build is not a merge recommendation.

## 2026-09-07 — Sprite and real inference update

Rebuilt APK passed 19 JVM and 15 device tests, including actual model replies through Chat and floating overlay. Optimized previously unoptimized debug CPU kernels; see [measured evidence](evidence/2026-09-07-model-and-sprite.md). Pet reaction presentation and the [format/authoring handoff](plans/03-pet-format-and-authoring.md) are added. These changes are local on the feature branch, not a production release.

## 2026-09-07 — Real model tool loop verified

24 JVM and 16 Android device tests passed after fixing native tool parsing, message roles, history order and retrieval payloads. Real Chat UI tests cover two greetings without tools, model-selected note creation with approval, and model-selected search followed by a grounded natural-language reply. See [full evidence and limitations](evidence/2026-09-07-real-tool-loop.md). This supersedes the earlier inference-only verification, not the remaining release gates.

## 2026-09-07 — Draggable Sprite UI checkpoint

26 JVM and 16 Android device tests passed. Floating Sprite drag, edge docking and position restoration across service restart are verified alongside actual local inference. Added compact navy/gold bubble, original vector action icons and matching widget surface. See [UI foundation](plans/04-sprite-overlay-foundation.md) and [Liquid baseline](reviews/2026-09-07-liquid-baseline.md). Physical-device and release gates remain open.


## 2026-09-08 — idle/thinking e integração com main

Thinking WebP ligado ao estado real, notificações opcionais sem bloquear overlay, pesquisa Android e estado documentados. 27 JVM + 16 Android passaram; lint, APK e build web passaram. Ver [ponto de situação](STATUS-2026-09-08.md). Transparência dos assets e validação física continuam pendentes.


## 2026-09-09 — paired Chat/Sprite validation and public APK

35 JVM tests, lint, debug/release builds and 4 focused Android instrumentation tests passed. The same five real Tiny prompts passed in Chat and Sprite after fixing English greetings and requiring a real note-search result instead of accepting a claim based on history. [Results and UI improvements](reviews/2026-09-09-chat-sprite-test.md).

OpenAI/Z.ai API-key integration added with isolated encrypted storage and provider-bound consent. OAuth and live cloud validation remain pending. Signed ARM64 0.1.1-preview APK installed and launched on a clean emulator; the website source now links to a separate public binary release, keeping source private. [Release record](releases/0.1.1-preview.md). Physical-device testing, signed upgrades, sprite alpha cleanup and the listed UI improvements remain open.

Vercel blocked the production deployment. The APK is publicly downloadable and checksum-verified, but the live website update awaits authenticated Vercel access.
