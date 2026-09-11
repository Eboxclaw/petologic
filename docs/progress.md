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

## 2026-09-09 — visual iteration 0.1.2

Implemented the researched [app/Sprite visual plan](plans/05-app-sprite-visual-improvements.md): shared navy/gold palette, more compact Chat, readable messages, new-session header action, clearer menus and correct overlay-to-Chat navigation. 36 JVM + 9 instrumented tests passed, including 10 paired real-model interactions. Signed 0.1.1→0.1.2 update preserved the test session. [Release and evidence](releases/0.1.2-preview.md). Full localization, rich Markdown, physical accessibility checks and sprite alpha cleanup remain open.

## 2026-09-10 — Sprint 1: idle pair and fully transparent Sprite

UI/UX refresh pre-plans set ([plans/09](plans/09-ui-ux-refresh-preplans.md)); Sprint 1 executed ([plans/10](plans/10-sprite-idle-pair-and-transparent-overlay.md)). The chat pet is bigger (168 dp empty state, 64 dp header, gradient hero canvas removed), a pure `IdleCycle` + shared `SpriteAnimator` now chain two idle clips (one + two cycles), and the floating Sprite lost every opaque fill — badge, bubble card, composer and actions are shadowed floating text over a hairline underline. The old idle GIF's baked opaque-black background was replaced by lossless transparent animated WebP placeholders (139 KB each), closing part of the long-open sprite alpha cleanup. 42 JVM tests + lint + builds + 2 instrumented tests passed; emulator screenshots in [evidence](evidence/2026-09-10-sprite-overlay/validation.md). Owner's authored idle WebPs, physical-device and TalkBack gates remain open.

## 2026-09-11 — User copy, reset from zero, and the 0.1.6 signing reset

Plan 11 ([plans/11](plans/11-user-copy-and-reset.md)) shipped a Settings "User copy" card: one-file export (WAL-checkpointed Room DB + non-secret prefs via SAF), import-and-restart with schema-version validation, and a full "delete everything and start over" wipe equivalent to a clean install. The original release keystore was lost, so 0.1.6 ships with a new signing identity — 0.1.5-or-earlier users must uninstall once (data loss; nothing can export from 0.1.5), and from 0.1.6 on the copy feature covers future transitions. Release notes, install guide and site copy updated accordingly. Tool-runtime generalization roadmap from the user's external audit recorded as [plans/12](plans/12-tool-runtime-generalization.md). 44 JVM + 4 instrumented tests passed; evidence in [evidence/2026-09-11-user-copy](evidence/2026-09-11-user-copy/validation.md).

## 2026-09-11 — Sprint A: SkillRegistry and the generalized tool runtime

Executed [plan 13](plans/13-skill-registry-and-security-skill-pack.md) Sprint A on the [plan 12](plans/12-tool-runtime-generalization.md) refactor: `NoteToolProtocol` gave way to `LfmToolCallParser` (registry-derived allowlist, native LFM call syntax primary) and `LfmToolDescriptorSchemer`; skills arrived with OFF/AUTO/PINNED states, per-tool toggles, permission gating and a per-turn temporary Koog registry — Memory is the first resident skill and a new Settings card exposes states, tool toggles and a token estimate. The Security Guard pack (Sprint B) now only needs to register as a second skill. 50 JVM tests green; emulator UI evidence in [evidence/2026-09-11-skills-registry](evidence/2026-09-11-skills-registry/validation.md).
