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
