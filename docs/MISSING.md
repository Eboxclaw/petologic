# Remaining implementation work

The repository now has an installable native app scaffold with real local inference, Koog orchestration, private note actions, AppSearch, a small semantic encoder, and an authenticated/consent-gated cloud transport. These items below are deliberately not presented as finished features. The current reviewed plan is [the control-plane review](reviews/2026-09-06-control-plane-review.md). This is a development checkpoint, not a completed release.

## Next implementation tickets

| ID | What is missing | Concrete next work | Acceptance evidence |
|---|---|---|---|
| NEXT-01 | Tool quality and loop hardening | Koog multi-hop note tools and one malformed-call repair now exist. Add held-out real-model evaluation, timeout/revocation races and bounded queue fairness. | Held-out argument correctness ≥95%; every invalid/unapproved action blocked. |
| NEXT-02 | Semantic routing and calibrated escalation | Add tool-description embeddings, held-out score/margin calibration, explicit complexity routing and Maxx offers. Current path is exact-command rules plus user-selected Tiny/Maxx. | 300-request split evaluation, confusion matrix, macro-F1 ≥0.90; no unsafe execution. |
| NEXT-03 | Scalable multilingual retrieval | Compare MiniLM with a multilingual mobile encoder; implement versioned chunks, background embedding outbox, 10k-chunk scorer/index and multilingual tokenizer fidelity. Current preview uses 256-token encoding and latest 200 notes with foreground lazy caching. | 200 labelled retrieval queries; Recall@5 ≥0.90; measured 10k latency; Portuguese results reported independently. |
| NEXT-04 | Operational knowledge graph | Replace the scaffold ownership edges with typed entities and source-versioned user-confirmed relations; wire bounded graph expansion into ContextBroker. The current graph helper is tested but not used for retrieval. | Two-hop/50-node bounds, stale evidence invalidation and grounded-answer fixtures. |
| NEXT-05 | Richer ContextBroker | Add candidate tool schemas, executor-specific token allocation, source navigation, durable checkpoint summaries and approved cloud summarization. A bounded Koog in-loop summary hook now exists; pre-first-hop compression remains open. Current broker uses a conservative byte budget plus final native token validation, lexical/semantic memory and short Tiny history. | Exact budget fixtures, stale-summary invalidation, local-only data excluded from all Maxx purposes. |
| NEXT-06 | Reminders/calendar/share integrations | Implement durable app-owned reminders, notification denial/Doze/reboot recovery, user-reviewed calendar insert and share-sheet handoffs. No inert receiver or simulated action is exposed. | UI and lifecycle tests report scheduling/handoff truthfully; no duplicate writes. |
| NEXT-07 | Provider connection polish | Add dedicated key test, supported model discovery, user-visible usage/cost accounting, per-session limits and credential replacement. Validate OpenRouter PKCE with controlled HTTPS callback before exposing it. | Auth/callback replay tests; interrupted SSE fixtures; opt-in live account smoke; no secret logs. |
| NEXT-08 | Optional Google/Pixel accelerators | Implement runtime capability discovery and a public supported companion API, behind an adapter. No private Pixel APIs or substitution of LFM. | Core flows identical when capability is unavailable; accelerator performance measured separately. |
| NEXT-09 | Model delivery robustness | Test download resume and corruption on device, background download persistence, cancellation during download, low-storage recovery and license presentation. General model installation is foreground today. | Interrupted download matrix, verified atomic replacement, precise setup errors and original model preserved. |
| NEXT-10 | Memory UX/lifecycle hardening | Add note edit, undo, privacy controls, stable source navigation, explicit conversation management, persisted approval recovery and deterministic startup recovery barrier. Current process recovery cancels pending proposals. | Full process-death suite, DB migration tests and conversation isolation. |
| NEXT-11 | Security and release validation | Threat review, SBOM/license inventory, backup/credential inspection, exported-component review, accessibility pass, physical-device performance and energy profiling. | Full plan P5 gates; signed release artifact only after evidence. |

## External inputs needed later

- A user-controlled OpenRouter account/key for a live cloud smoke test. No key is required for compiling, mocked cloud tests or Tiny. Do not put keys in this repository or prompts.
- A controlled callback domain if OAuth account connection is selected.
- Hosted physical-device lab or test devices for real RAM, energy and thermal release gates. The user’s own phone is not needed for emulator work.
- Final character animation cycles, if desired. The current app uses the existing canonical artwork; animation production remains deferred by the plan.

## Important limits

- Cloud is a reusable execution adapter retaining Paladino identity, not a second pet or selectable role. Only one launch manifest is available.
- Current cloud requests are text-only with 512 output tokens. No cloud tool execution, recursive subroles, automatic provider substitution or hidden cloud escalation.
- Notes are always local-only in the current UI. Cloud receives the current request and persona; prior Tiny messages and private notes are omitted.
- The published landing page has not been rewritten to claim the native preview is a shipped product. Its older multiple-pet marketing language needs a separate content decision before launch.
- A working scaffold does not satisfy every numerical release gate in the plan. See `progress.md` for phase status rather than assuming a green build is a release approval.

## Control-plane iteration gaps

- Persistent global → agent → session capability ceilings and OS access inventory; current controls are session-local and intentionally narrow.
- Source-versioned session summaries, exact tokenizer preflight, checkpoint execution resume and scoped token totals.
- Fair bounded local-runtime queue and controller eviction; separate conversations currently share a mutex-protected model.
- Physical 8 GB device benchmarks for both 2.6B quants; VL-450M/VL-3B multimodal runtime and projector tests.
- Physical-phone/TalkBack and cooldown coverage, plus live launcher-widget state. Chat/Sprite paired inference, drag, new/full conversation, PT/EN labels and enlarged text now have emulator evidence. The launcher widget remains a static entry point.
- Durable background recovery and OEM battery-policy validation. An explicit stoppable foreground overlay service now permits requests while other apps are foreground; process-death task resumption remains open.
- Production release gates remain open. User-authorized signed preview APKs are distributed through the public downloads repository and website; preview distribution does not imply completion of physical-device/security gates.
