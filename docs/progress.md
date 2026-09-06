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
