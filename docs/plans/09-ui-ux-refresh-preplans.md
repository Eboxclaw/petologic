# UI/UX refresh — pre-plans (0.1 draft, 2026-09-10)

Round plan agreed with the product owner: this round **sets pre-plans**; each
pre-plan gets its own detailed sprint plan written immediately before that
sprint executes. Sprint 1 detail lives in
[10-sprite-idle-pair-and-transparent-overlay.md](10-sprite-idle-pair-and-transparent-overlay.md).

Facts this roadmap builds on (verified in code, 2026-09-10):

- The floating Sprite window is transparent, but the expanded bubble and the
  reaction badge use opaque `#080A18` fills — the "window playing an animation"
  look. There is one idle clip (`paladino_idle.gif`); the chat pet is 48 dp in
  the header and 104 dp in the empty state.
- There is no speech input at all (no `RECORD_AUDIO`), and the update card is a
  static browser link: no version check, no "on latest" state, no history view.
- The optional semantic encoder is MiniLM qint8 ONNX at **23 MB**
  (`ModelCatalog.minilm`); "semantic routing" remains NEXT-02 and graph
  retrieval remains NEXT-04 (scaffold `graph_edges` + `expandGraph()` are tested
  but unused).
- Koog 1.2.0 is used as the orchestration shell with a custom prompt executor.
  Unused capabilities relevant later: event handlers, real streaming frames,
  agent persistence/checkpointing, graph strategies, memory/embeddings beta
  modules.

## Decisions (owner, 2026-09-10)

1. Voice-to-text: **Android built-in `SpeechRecognizer`** (on-device when
   available), with a first-press info card before the mic permission; graceful
   fallback card when the device has no speech service.
2. Bubble transparency: **fully transparent** — no card, no tint; text carries a
   soft dark shadow for legibility on any wallpaper. The Sprite's own window is
   and stays transparent.
3. First sprint to execute: **Sprite & transparency**.
4. New idle assets arrive as **animated WebP (256×256, alpha)**, replacing the
   placeholder slots described in Sprint 1.

## Sprint pre-plans

### S1 · Sprite & transparency (executing now)

Bigger chat pet (168 dp empty state / 64 dp header), an idle **pair** sequencer
(idle1 once, idle2 twice, repeating — `IdleCycle` in `:core`), one shared
`SpriteAnimator` for Compose and the overlay, and the fully transparent overlay
pass (borderless badge, backgroundless panel, hairline composer underline,
shadow-only text). Detail: [sprint 10](10-sprite-idle-pair-and-transparent-overlay.md).

### S2 · In-app updates & release history

Check `Eboxclaw/petologic-downloads` releases via the GitHub API when the
update card opens (manual re-check button; no background polling, per
[plan 07](07-model-onboarding-updates-brand.md)). Compare against
`BuildConfig.VERSION_*`. Update available → release notes, size, in-app APK
download (reuse `ModelLibrary`'s Range-resume + progress patterns), SHA-256
verify against `SHA256SUMS.txt`, then the system installer intent (needs
`REQUEST_INSTALL_PACKAGES` + `FileProvider`). On latest → "You're on the
latest" plus a **release history** view (past releases rendered with the
existing Markwon renderer). Offline → keep today's manual link as fallback.
Unit tests: version comparison, release parsing (MockWebServer), hash verify.

### S3 · Voice input

`RECORD_AUDIO` with an explainer card *before* the system dialog ("Paladino
listens only while you tap the mic; recognition runs on the phone when
available"). `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`; tap-to-talk with
live partial results into the composer (editable before sending) in Chat and
the overlay composer. Devices without a speech service get a fallback card
instead of a dead button. Tests: permission/state machine unit tests;
instrumented smoke on the emulator (which ships a simulated speech service).

### S4 · FAB & deterministic quick actions

Two-tier FAB on the floating Sprite and in-app: first tap expands the quick
menu (Voice · Write note · Chat), second tap runs the action. **Write note is
deterministic**: it opens a note composer that produces the existing T1
`ActionProposal` approval directly — no LLM hop, works before any model is
downloaded (per [TOOL_SPEC](../capabilities/TOOL_SPEC.md) "deterministic routers
first"). Quick actions also prime the session (e.g. write-note pre-registers
`notes_save`) so the agent gets intent context without a router model. Tests:
deterministic note route creates an approval without inference; menu parity.

### S5 · Settings reorganization

Replace the ten-card scroll with grouped categories and drill-in sub-screens:
Sprite & widget / Model & AI brain / Semantic memory / Cloud (Maxx) / Privacy &
permissions / App (language, updates, about). Advanced sampling moves under
Model & AI brain. Keep the Controls tab as the agent control plane; dedupe
overlapping entries. Same tab-based navigation pattern, no new dependency.
Tests: navigation state unit tests + instrumented flow check.

### S6 · Onboarding cards & semantic nudge

Verify the missing-model card fires on **any** first message when Tiny has no
model (close any regex/greeting bypass). After the **second user message** in a
conversation, show a one-time popup introducing semantic memory with the real
size (23 MB), deep-linking to the existing MiniLM install; a persisted flag
means it never nags again. Includes the database health pass (schema v3, seed
sessions, embeddings cache) — verified healthy during planning.

### S7 · Koog leverage

Adopt Koog **event handlers** to feed the Console screen and
`execution_events`; make the executor emit **real streaming frames** (today
`executeStreaming` synthesizes frames after completion). Then a documented
adopt/reject spike on: structured output vs the hand-rolled
`NoteToolProtocol`, agent persistence/checkpointing for task resume, graph
strategies/subgraphs for deterministic routers, and Koog memory/embeddings beta
modules vs our Room+AppSearch+MiniLM stack. Output: a dated review doc under
`docs/reviews/`. Koog-first per the owner: no external framework is added
before this evaluation.

## Backlog (pre-planned, later rounds)

- **Graph RAG** — wire the tested-but-unused `expandGraph()` into retrieval
  beyond `owns_note` edges; typed entities; use graph context for tool calls
  (NEXT-04). Sequenced after S7's Koog memory/embeddings evaluation.
- Reminders receiver/alarms (NEXT-06), multilingual retrieval outbox (NEXT-03),
  capability waves 2–4 per [CAPABILITY_MATRIX](../capabilities/CAPABILITY_MATRIX.md).
- Home-screen widget surface: stays opaque for launcher legibility this round;
  owner may fold it into a later transparency pass.
