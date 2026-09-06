# 0xPaladino v1 — Android implementation plan

> Updated scope: see [the reviewed App/Chat/Sprite control-plane plan](../reviews/2026-09-06-control-plane-review.md). That review supersedes this original plan where later session, model-experiment and UX decisions differ.


Status: reviewed implementation proposal; no application has been built or benchmarked by this planning task.
Date: 2026-09-06.
Decision authority: the current user request, followed by the user’s decisions in “Research Codex Pets Competitors”. Prior assistant proposals are reference material, not approved requirements. Synced `sources/` files remain read-only.

## 1. Goal and launch contract

Build a native Android companion with one identity, **0xPaladino**, that can converse briefly offline, save and retrieve personal notes, prepare simple actions, and extend its reasoning through an authenticated cloud provider. The launch experience must be useful without a cloud account after its mandatory model installation.

There are exactly two execution paths:

- **Tiny:** default on-device execution. Deterministic code handles exact operations, a small embedder retrieves candidates, and the mandatory instruction-tuned **LFM2.5-350M** handles language tasks. One inference instance; serialized generation.
- **Maxx:** the same Paladino, task, permissions and memory namespace, using a cloud capability for stronger reasoning, larger tasks or context summarization. OpenRouter is the first provider. Maxx requires a connected provider through supported OAuth/PKCE or a user-supplied API key, plus permission to transmit the selected context.

“Cloud-first” means using cloud as the first escalation destination for work outside Tiny’s tested capability, and implementing that path early. It does not mean uploading every message. A user-selected Maxx request can go directly to cloud after local policy and context preparation; no compulsory local model inference before each cloud call.

### Concrete v1 user journeys

1. Install/verify local assets, enter airplane mode, and ask Paladino a short question.
2. “Remember that my bike lock code is in my red notebook” → preview/save a note → retrieve it by paraphrase. Treat personal notes as private by default.
3. “Find my packing notes and summarize them” → retrieve scoped evidence → concise answer with source links.
4. “Remind me to pack tomorrow at 9” → resolve timezone/date → clarify ambiguity → confirm an app-owned reminder. Explain approximate delivery where applicable.
5. Prepare a calendar event through the system calendar insert UI, or share drafted text through Android’s share sheet. Report handoff, not successful external creation or sending.
6. Ask a complex question → offer Maxx → connect OpenRouter if needed → review outbound context → stream the answer in the same conversation.
7. Cancel, deny a permission, lose connectivity, or restart the app during a task → show truthful recoverable state without repeating writes.

Proposed initial language acceptance: English and Portuguese, including Europe/Lisbon date/time cases. Expand only after evaluation. The earlier hardware target is at least 8 GB RAM / 128 GB device storage; available storage and measured performance still need checking.

## 2. Fixed decisions, implementation defaults and non-goals

### Fixed decisions

| Area | Decision | Reason |
|---|---|---|
| Platform | Kotlin, Jetpack Compose, native Android lifecycle | Direct platform integration and a testable mobile UI |
| Local model | LFM2.5-350M instruction model, mandatory | Stable offline capability independent of provider or phone brand |
| Agent runtime | Koog behind application interfaces | Explicit state transitions and one tool execution boundary |
| Cloud | OpenRouter first; provider abstraction for later adapters | Early capable escalation without implementing every provider |
| Routing | Deterministic rules → semantic candidates → local interpretation when needed | Avoid generation for exact commands; bound model tool selection |
| Retrieval | AppSearch plus a small on-device embedder | Lexical and semantic recall without another 350M router |
| Persistence | Room/SQLite source of truth and bounded knowledge graph | Transactions, migrations, inspectable data, low operational cost |
| Context | ContextBroker for both Tiny and Maxx | Enforce scope, evidence, privacy and token budgets centrally |
| Identity | Only `paladino` | Validate one coherent product before introducing a roster |
| Google/Pixel | Optional public capability adapters | Improve the experience without substituting for LFM |

### Defaults to validate in phase 0

- Minimum Android API 31 as a proposed support floor. Select compile/target SDK against current stable tooling and distribution requirements at implementation time. Raise the minimum only with recorded compatibility evidence and an explicit product decision.
- ARM64 release build; host-compatible emulator ABI for tests. Pin Gradle, JDK, Kotlin, Compose, Koog, AndroidX, NDK and native runtime versions after a successful compatibility build.
- Prefer pinned llama.cpp + a narrow JNI bridge and official LFM GGUF weights. Benchmark Q4_K_M against Q8_0 before choosing. Quantization must preserve the exact required model identity.
- Small embedder candidate selection is a phase-0 deliverable: compare at least two mobile-compatible, licensed candidates with English/Portuguese coverage. Target ≤100 MB packaged weights and ≤256 MB peak incremental memory. Do not claim Android universally supplies a suitable embedding model.
- No always-running inference service: load on demand, retain while useful in foreground, unload under memory pressure. Persist task state independently of model memory.

### Explicit non-goals

No additional pets, user-selectable roles, role marketplace, downloaded YAML execution, social features, premium tiers, payments, DeFi transactions, wallets, antivirus, email account integration, autonomous message sending, P2P synchronization, VPN, Tor, Tailscale, server agent daemon, or remote device control. Ordinary HTTPS for model delivery and authorized cloud inference is required and is not the excluded networking product.

No iOS/desktop app, Tauri, full KMP migration, custom attention kernels, SensorFM integration, fine-tuning, separate 350M router, general web browsing agent, arbitrary code execution, background surveillance, accessibility-based app automation, or access to private Pixel features.

Launch UI is an in-app character and chat with ordinary notifications for reminders. System-wide floating overlays, bubbles and elaborate sprite-cycle production are deferred. Use the existing Paladino artwork when available, with a simple state renderer and static fallback. Asset availability must not block runtime testing.

## 3. Architecture and execution contracts

```text
Compose: Paladino / chat / actions / memory / provider settings
                         |
                  TaskCoordinator
                         |
              Koog bounded state machine
                         |
                 RoutePolicy ------ CapabilityRegistry
                         |
                  ContextBroker
              /          |           \
      AppSearch     Room + graph    tool descriptions
                         |
             +-----------+-----------+
             |                       |
       Tiny executor           Maxx executor
   rules + LFM2.5-350M      OpenRouter adapter
             |                       |
             +------ ActionProposal -+
                         |
                ToolBroker + policy
                         |
           confirmation / Android / Room
```

Google adapters expose optional operations such as supported on-device summarization or input processing; they never replace either execution policy or the mandatory model. No model, provider adapter or retrieved document directly executes Android tools.

### Repository shape

```text
app/                      Compose, navigation, dependency wiring
core/domain/              IDs, task states, policies, contracts; pure Kotlin
core/agent/               Koog integration and bounded orchestration
core/context/             ContextBroker, ranking and budget enforcement
core/data/                Room, AppSearch, graph, outbox and migrations
runtime/local/            model lifecycle, tokenizer, JNI, generation queue
runtime/cloud/            provider adapter, credentials, streaming
platform/android/         tools, reminders, optional Google capabilities
assets/paladino/           bundled persona, validated manifest, character art
benchmark/                benchmark runner and versioned datasets
```

These may begin as packages and become Gradle modules where dependency enforcement or build isolation helps. Do not create separate services merely to match this diagram.

### Minimum application contracts

Define application-owned types before adapting Koog; these are conceptual interfaces, not claims about Koog API names.

- `TaskEnvelope(taskId, conversationId, roleId, mode, permissionScope, deadline, cancellationToken)`.
- `RouteDecision(path, reasonCode, candidateToolIds, needsClarification, needsCloudConsent)`.
- `ContextBroker.build(task, executorCapabilities) -> ContextEnvelope` with provenance, source versions, sensitivity labels, token count and outbound digest.
- `LocalExecutor.generate(context) -> Flow<AgentEvent>` and `CloudExecutor.generate(context, credentialRef) -> Flow<AgentEvent>`.
- `ActionProposal(toolId, typedArguments, evidenceIds)` → `ToolBroker.authorizeAndExecute(...)`.
- `ProviderCapabilities(contextLimit, structuredOutput, tools, streaming, usageReporting)`.
- `CapabilityRegistry` reports availability at runtime; missing capability has an explicit result.

Koog owns the bounded agent transitions through these adapters. It must use the same ToolBroker for local and cloud proposals. Do not build an unrelated second agent loop around it.

### Paladino manifest and future readiness

Ship exactly one bundled manifest with `schemaVersion`, `roleId: paladino`, `personaRef`, tool allowlist, memory namespace, trigger allowlist and policy references. Parse a restricted YAML schema into typed immutable data; reject unknown security-sensitive fields and external references. Bundle persona Markdown; never interpolate private notes as system instructions.

Model cloud as a reusable **execution capability**, conceptually the requested subrole: `CloudCapability(parentRoleId, purpose, contextBudget, permittedTools)`. Purposes include `reason`, `summarize_context` and `plan`. It inherits the parent identity and can only narrow permissions. No second persona, agent roster, model download or independent memory is created. Future roles can reuse this contract; v1 does not expose role registration.

Global security rules remain non-overridable. A future role manifest can tighten confirmation rules, never weaken the application’s minimum protections.

### Task lifecycle and limits

`Created → Routing → Clarifying | AwaitingCloudConsent | PreparingContext → RunningTiny | RunningMaxx → AwaitingActionApproval → Executing → Completed | Failed | Cancelled`.

Persist transitions and pending proposals. Start with maximum four model turns and three tool executions per request, one outstanding side-effecting tool, and a 60-second overall foreground task deadline. Permit one bounded structured-output repair. Deadline expiry or exhausted budget produces a clear result; no recursive cloud subroles or hidden infinite retries.

Cancellation closes streams, stops generation and invalidates unused approval tokens. A tool already committed is reported accurately. After process death, incomplete reads may restart; writes reconcile their ledger before any retry.

## 4. Tiny/Maxx routing, authentication and context

### Routing policy

1. Apply explicit mode choice, permissions and privacy restrictions first.
2. Parse exact known commands in deterministic code. Resolve dates, durations, IDs and arguments using typed validators, not generated arithmetic.
3. Use semantic ranking to select at most five eligible tool candidates. Calibrate score and margin thresholds on a held-out dataset; cosine similarity is not a probability.
4. Use LFM for ambiguous intent, short responses or argument extraction. Clarify missing or conflicting arguments. Model self-reported confidence cannot authorize an action.
5. Requests exceeding tested local task scope, complexity or context budget receive a Maxx offer. Explicit Maxx bypasses speculative local generation.
6. No credentials, connectivity or cloud consent: remain Tiny, offer connection or explain the local limitation. Never silently transmit or fabricate a cloud result.

Tiny mode guarantees no inference/context egress. Model downloads and provider setup are separate, user-visible operations. Test this distinction with a rejecting network transport and device network inspection.

### Provider connection

Implement BYOK first as the minimum v1 authentication route. Store ciphertext in app-private storage with an Android Keystore-backed encryption key; exclude credentials from backup, logs, crash reports and model context. Show connected/disconnected/error states, test connection, key replacement and disconnect. Disconnect clears local credentials; explain how to revoke at the provider if remote revocation is unavailable.

Implement OpenRouter OAuth/PKCE connection after verifying Android callback support end to end. Use the external browser, S256, a cryptographically random verifier, short-lived pending transaction and validated callback/state binding. OpenRouter’s documented flow exchanges its code for a user-controlled API key; do not invent refresh-token semantics. Prefer a verified HTTPS App Link on a controlled domain. If that infrastructure is unavailable, ship BYOK and mark account connection deferred, rather than claiming unsupported custom-scheme compatibility.

Credential possession is not context-transmission consent. Default to consent per escalation with an outbound preview, selected provider/model and disclosed fields. An optional remembered grant must be narrow, visible and revocable. New source categories require new consent. Changing the outbound payload invalidates its prior consent digest.

One approved cloud model at a time; no automatic provider substitution. Later adapters implement the same contract. Provider/model selection must consider current context, tool and privacy capabilities rather than hardcoded brand assumptions.

Cap request tokens and session usage locally; use provider spending limits where supported. If usage is unavailable, label cost unknown and enforce a conservative token ceiling. Do not promise an exact billing cap. Retry transient read-only requests at most once with backoff inside the deadline; do not retry ambiguous tool execution.

### ContextBroker algorithm

1. Load immutable persona/policy, active request and task checkpoint.
2. Filter by `roleId`, source visibility, deletion status and current permission scope **before** retrieval.
3. Merge AppSearch lexical hits and embedder semantic hits; deduplicate by source/chunk ID and rank with a reproducible fusion rule.
4. Expand graph evidence at most two hops, at most 50 nodes / 100 edges. Require source provenance for every asserted relationship.
5. Add only candidate tool schemas and necessary conversation turns.
6. Enforce the selected executor’s real tokenizer/context limit. For Tiny, start with 4,096 total tokens, reserve 512 output tokens and 256 safety tokens; budget remaining input among policy, request, tools, history and evidence. Reduce evidence first, never discard policy. Reject/clarify an oversized irreducible request.
7. For Maxx, start with an application cap of 16,384 total tokens or provider limit, whichever is smaller; reserve output explicitly. Apply local sensitivity filtering and user-approved source selection before transmission.
8. If cloud summarization is requested, send only already-approved context. Store summary as a derived artifact with source IDs, source versions and provider provenance. A cloud summary cannot authorize tools or become an unquestioned persistent fact.

Local-only memory never enters Maxx, even for context compression. Deletion or source edits invalidate derived summaries and caches. Treat all retrieved content, model output and cloud summaries as untrusted data; prompt delimiters help readability but ToolBroker policy provides enforcement.

## 5. Persistence, retrieval and tools

### Room/SQLite schema

- `Conversation`, `Message`, `Task`, `TaskEvent` and `ActionLedger`.
- `Note(id, text, createdAt, updatedAt, sensitivity, roleId, deletedAt)`.
- `Entity(id, type, canonicalLabel, roleId)` and `Edge(id, sourceId, targetId, relation, evidenceNoteId, evidenceVersion, status)`.
- `Chunk(id, noteId, sourceVersion, text)` and `Embedding(chunkId, modelId, modelVersion, dimension, vector)`.
- `DerivedSummary`, `Reminder`, `ConsentGrant`, `IndexOutbox`.

Index graph source/target/relation and namespace columns. Use foreign keys, uniqueness constraints and transactions. Initial entity/edge creation comes from deterministic structure or user-confirmed proposals; no automatic unrestricted graph mining. Search must work when the graph is empty.

Room is canonical. Write source data and indexing-outbox entry in one transaction. Update AppSearch asynchronously and idempotently; reconcile missing entries after crashes. Recheck every returned hit against canonical source version/deletion status before showing it. The same rule applies to embeddings and graph evidence. Remove references and derived artifacts on deletion; complete physical index cleanup within 60 seconds while the app is active, with durable retry otherwise.

Prefer AppSearch local storage for predictable app-owned indexing. Detect embedding-search support for the pinned backend. If absent, retain AppSearch lexical search and use an app-owned bounded vector scorer over at most 10,000 chunks; benchmark full-scan recall/latency before acceptance. Do not silently reduce semantic search to lexical candidates only. Above the validated corpus limit, show the supported limit or implement a separately reviewed index upgrade.

### Initial tool catalog

| Tool | Execution and confirmation |
|---|---|
| Search/list notes | Local read; namespace filtering |
| Create/update/delete note | Show exact write preview; confirm; allow undo where possible |
| Create/cancel reminder | Confirm normalized time/timezone and content; app-owned scheduling |
| Prepare calendar event | Validate draft; launch system insert UI; report handoff only |
| Share drafted text | User previews text and completes Android share UI |

Use persisted idempotency keys for app-owned writes. Approval binds task ID, tool ID, canonical argument hash and expiry; changed arguments require a new approval. No model-generated tool names outside the allowlist. Protect exported components and validate external intent inputs. Never report “sent” after merely opening another app.

Use Android-supported scheduling for reminders; do not run a model while idle. Prefer inexact scheduling for v1 and disclose timing expectations. Notification denial and Doze must have visible, tested outcomes; precise alarms require a separately justified permission and policy review.

## 6. UI, lifecycle and security

Paladino states: idle, thinking locally, thinking in cloud, awaiting confirmation, acting, done, error. Drive them from task events; never fake progress. Include text status, cancel, Tiny/Maxx indicator and source links. Respect reduced motion, 48 dp touch targets, TalkBack, dark mode and large font settings. Hide sensitive reminder text on the lock screen by default.

Model setup has progress, resumable download, disk-space check, pinned manifest/checksum validation, atomic installation and a retry path. Keep the previous working asset until replacement validates. Asset corruption or missing weights disables local generation with an honest repair screen, not a substitution model. Core Tiny acceptance requires a completed installation; first-install offline availability is not promised unless model packaging is later chosen.

Use app-private storage; no embedded service keys. Disable cleartext transport. Default to excluding personal memory and credentials from Android backup. Room is not inherently encrypted: document reliance on Android sandbox/device encryption; if the threat model requires protection beyond that, choose and benchmark explicit database encryption before sensitive launch claims. Debug traces contain timing, IDs and reason codes, not note contents or tokens. Export diagnostics only through a user action.

LFM and embedder load off the UI thread with bounded queues. Avoid concurrent LFM/Nano generation by default; unload/release resources around optional accelerators if required. No promise of access to Pixel’s private Gmail summaries, screen context or assistant state. Every Google feature is checked independently and must degrade to ordinary Tiny behavior when absent.

## 7. Phases, deliverables and exit gates

All numerical thresholds below are **proposed release targets**, not measured results. Freeze benchmark data and hardware configurations before judging them. Any gate change requires an ADR explaining evidence and product impact; never lower it silently to obtain a pass.

| Phase | Goal and dependencies | Deliverables | Measurable exit gate |
|---|---|---|---|
| P0 — feasibility | Establish real Android support before feature work | Toolchain lock; model/license inventory; JNI LFM probe; Koog Android probe; embedder comparison; AppSearch feature matrix; emulator instructions | Clean build and launch; exact LFM emits a response offline on emulator; Koog drives one local request; two embedder candidates evaluated; asset sizes/hashes recorded; blockers explicit |
| P1 — vertical slice | Depends on P0; prove one useful local task | Compose chat + Paladino states; model setup; Tiny pipeline; typed save/search note tool; Room; cancellation | 20 scripted offline UI journeys pass using real LFM; 100/100 invalid or unapproved write proposals blocked; process restart preserves notes and does not duplicate writes |
| P2 — Maxx early | Depends on P1 contracts; prove same identity across cloud | OpenRouter BYOK, streaming, consent preview, usage limits, error handling; PKCE spike/integration if supported | All 30 transport/auth/consent fixtures pass; zero outbound context in Tiny/local-only cases; one opt-in live provider smoke succeeds; cloud proposal obeys identical ToolBroker checks |
| P3 — memory and routing | Depends on P1; integrate with P2 | Small embedder, AppSearch, graph, ContextBroker, summaries, index recovery | Retrieval Recall@5 ≥0.90 on 200 labelled queries; context limits hold in every fixture; zero stale/deleted hits surfaced; routing macro-F1 ≥0.90 and unsafe-action execution 0 |
| P4 — Android product | Depends on P2/P3 | Reminders, calendar/share handoffs, accessibility, optional Google adapter, lifecycle recovery | 40 end-to-end UI journeys pass; all core journeys pass with Google capabilities absent; 20 process-death/cancel/permission scenarios pass without duplicate actions |
| P5 — release validation | Depends on all above | Release artifact, SBOM, license review, performance report, privacy text, known limitations | Emulator suite green; real-device performance gates met; no unresolved critical/high exploitable findings; install/update/recovery and asset integrity checks pass |

P0 failure on the required LFM/Koog combination blocks the architecture gate. Investigate compatible versions/runtime patches, quantify the blocker, and request a product decision if no viable implementation exists. Do not substitute a different local model or orchestration framework.

P2 and P3 can be developed in parallel after their shared contracts stabilize. Calendar/share integrations and cosmetic polish must not delay proving the local-to-cloud path.

## 8. Test strategy and measurable budgets

### Datasets and evaluation

Version a 300-request routing dataset: 100 deterministic/local requests, 80 paraphrases, 40 ambiguous requests, 40 cloud-appropriate requests and 40 injection/unauthorized-action attempts. Balance English and Portuguese where meaningful. Separate development/calibration/test splits (60/20/20), freeze the test split and report confusion matrices, not only averages. Add a separate 100-case security suite for tool/schema/approval abuse.

Retrieval dataset: 1,000 representative synthetic notes, expanded to a 10,000-chunk scale fixture, with 200 labelled paraphrase queries and expected source IDs. Include conflicting dates, duplicates, deletion, multilingual paraphrases and injected instructions. Keep real personal notes out of CI.

Tool evaluation measures end-to-end correct argument values and outcomes, not just valid JSON. Target ≥95% correct arguments on unambiguous in-scope requests; 100% of ambiguous write requests in the fixed safety suite must clarify or refuse rather than execute. Report raw counts and uncertainty for small samples.

### Layers

- JVM tests: date/time parser, route policy, context truncation, permission inheritance, graph bounds, consent digest and idempotency.
- Native/runtime tests: exact chat template/tokenizer, malformed output, cancellation, repeated load/unload, corrupt assets, bounded memory. Verify real quantized weights separately from fake executor tests.
- Android instrumentation: Room migration/outbox recovery, AppSearch capability fallback, secure credential persistence, intent validation, reminder permission handling.
- Compose UI tests: setup, Tiny/Maxx transitions, action confirmation, source navigation, errors, large fonts and accessibility semantics.
- Mock HTTP server: streaming fragments, invalid JSON, 401, 429, timeout, partial response, connection loss, unknown usage and credential revocation. Assert no credentials leak into requests to unintended origins or logs.
- Security adversarial tests: instructions hidden in notes, cloud-produced unauthorized tools, changed approval arguments, callback replay/state mismatch, cancelled tasks, backup leakage and unauthorized context export.

### Sandbox and evidence without the user’s phone

Use an Android Emulator with a visible UI locally and automated instrumentation in CI. Select ARM64 emulator on supported Apple Silicon hosts; use an appropriate supported ABI for the CI host and build the native library for it. Keep fast fake-executor UI tests separate from a real-LFM emulator lane. If nested virtualization is unavailable, move that lane to a runner with supported acceleration; a static screenshot or JVM-only test does not satisfy Android execution.

Persist APK checksum, commit, toolchain versions, emulator/API/ABI, logs, JUnit results, screenshots or short screen recording, model hash and evaluation report for each phase. Suggested commands after scaffolding are `./gradlew assembleDebug lint testDebugUnitTest connectedDebugAndroidTest`; document actual module/task names once created.

Emulator evidence establishes functional/UI behavior, not phone latency, thermal or battery claims. P5 uses a hosted physical-device lab or dedicated test devices, including a Pixel and a non-Pixel at the minimum supported RAM class; it does not require the user’s phone. If physical devices are unavailable, label the build an emulator-validated preview and leave production performance approval open.

### Physical-device targets

Measure release builds with fixed corpus, tokenizer/context sizes, airplane mode for local tests, documented ambient/device temperature and three repeated runs after warm-up.

| Metric | Initial exit target |
|---|---|
| Deterministic route latency | p95 ≤50 ms excluding external UI |
| Semantic retrieval at 10k chunks | p95 ≤200 ms warm, including query embedding |
| ContextBroker assembly | p95 ≤300 ms with retrieval |
| Tiny time to first token | p95 ≤2 s warm at 1k input tokens; ≤5 s cold |
| Tiny decode | Median ≥15 tokens/s for a 128-token answer |
| Memory | Total app PSS ≤1.5 GB during 4k-context local generation, including loaded embedder |
| Stability | No OOM/ANR in 100 sequential tasks and 30-minute mixed-use soak |
| UI | <5% janky frames during streaming/character animation in benchmark flow |
| Idle | Zero background inference; no app-owned permanent wake lock |
| Thermal endurance | Last-five-minute decode throughput ≥70% of first-five-minute throughput in controlled 20-minute run |
| Installation | Model + embedder + native assets ≤1 GB; check actual free space for download plus atomic replacement |

Record energy consumption for a fixed 20-task scenario using available device power metrics, against an idle baseline. Establish a numerical energy regression budget after P0 hardware measurements; do not invent cross-device battery percentages. Maxx network latency is reported separately from local processing and is not guaranteed by the application.

## 9. First sprint — ten working days, dependency ordered

Goal: emulator-visible Paladino using the actual local model to save and retrieve a confirmed note, with an early mocked Maxx path proving consent and identity continuity. This is a planning estimate for one experienced Android engineer supported by Codex, not a delivery promise.

| Ticket | Days / dependency | Work and acceptance |
|---|---|---|
| S1-01 | Day 1 | Inspect workspace; create implementation directory outside read-only sources; scaffold Compose app, CI and version catalog. Clean build + visible emulator launch; record environment. |
| S1-02 | Days 1–3; S1-01 | Pin LFM weights/runtime, check license and hashes, implement minimal JNI load/generate/cancel. Actual offline response on emulator; record memory/latency without treating emulator numbers as phone gates. |
| S1-03 | Days 2–3; S1-01 | Validate Koog Android dependency and adapt fake/local executor. One bounded tool proposal flows through Koog and ToolBroker; forbidden tool rejected. |
| S1-04 | Days 3–4; S1-03 | Implement TaskEnvelope, route enums, state persistence and bundled Paladino manifest. Reject a manifest that attempts privilege expansion. |
| S1-05 | Days 4–6; S1-02/03/04 | Room note tools, confirmation-bound arguments, lexical search and Compose chat states. Save, find, deny, cancel and restart flows pass. |
| S1-06 | Days 6–7; S1-05 | ContextBroker token budget and provenance; compare two embedders and record selection ADR. Oversized input cannot exceed runtime budget. |
| S1-07 | Days 7–8; S1-04/06 | Mock OpenRouter executor, outbound preview, disconnected state and credential-store interface. Tiny sends zero context; Maxx keeps conversation/task identity. |
| S1-08 | Days 9–10; all above | Run 20 real-LFM offline UI journeys and safety tests; fix blockers; package APK, screenshots/video, results and next sprint backlog. |

Sprint definition of done: reproducible debug APK; visible emulator demo; verified exact LFM model; Koog in the real execution path; one confirmed persisted note workflow; no unapproved writes; cancellation/restart recovery; documented embedder decision; honest list of unmet P0/P1 gates. If JNI/Koog feasibility consumes the sprint, deliver its evidence and blocker report before adding features.

## 10. Risk review and resolved design tensions

| Risk / tension | Resolution and trigger for reconsideration |
|---|---|
| Tiny-default versus cloud-first | Local is default; cloud is the preferred escalation destination. Route reason is visible and tested. |
| Small model tool reliability | Limit tools, validate all arguments, clarify ambiguity, one repair maximum. Failed held-out gate blocks wider tool scope. |
| Shared identity versus cloud subrole | Execution capability inherits identity and narrows permissions. No nested agent hierarchy in v1. |
| Native runtime/Koog Android mismatch | P0 real-device-ABI build and emulator inference before product expansion; pin known-working versions. |
| AppSearch semantic availability | Runtime capability check plus bounded vector fallback; identical retrieval tests on both paths. |
| Memory duplication across LFM/embedder/Nano | Single LFM instance, bounded queues and sequential accelerators; enforce total app PSS budget. |
| Cloud context leaks or prompt injection | Local scope filtering, preview-bound consent and independent tool policy. Cloud never receives unrestricted storage access. |
| Summaries become false memory | Derived source-versioned artifacts; user confirmation before durable factual graph updates. |
| Room/index inconsistency | Transactional outbox, canonical revalidation and invalidation of deleted sources. |
| Android background restrictions | User-triggered agent sessions, persisted state and platform scheduling; no always-on model promise. |
| OAuth redirect infrastructure missing | BYOK satisfies v1 account prerequisite; ship PKCE only after verified Android callback tests. |
| License and model distribution constraints | Record exact license and redistribution requirements before bundling/release; no assumption that all weights are permissively licensed. |
| Google availability changes | Optional adapters, runtime checks and absent-capability acceptance lane. |
| Scope expansion into security/DeFi/pets | Keep only interface seams; require a later product plan for additional functionality. |

This review removes unsupported dependencies on universal Pixel AI access, Android-provided embeddings, automatic cloud credential availability, and emulator-derived battery claims. It preserves every fixed technology choice while making feasibility failures visible.

## 11. Codex execution protocol

1. Read this plan and repository instructions. Inspect existing code before scaffolding. Do not alter synced reference files.
2. Start at the earliest incomplete phase. Maintain `plans/progress.md` with ticket status, evidence paths, unresolved risks and exact gate results.
3. For each ticket, implement a reviewable increment and its relevant tests. Use fakes for deterministic coverage and retain explicit real-runtime lanes.
4. Save decisions in `docs/adr/`; record pinned model/dependency versions and source licenses. Do not silently change mandatory stack choices or broaden scope.
5. Keep builds/tests local or in authorized CI. Do not embed credentials, send messages, publish to stores or claim successful cloud smoke tests without the required configured access and actual evidence.
6. A phase is complete only when its deliverables and gates have evidence. Distinguish passed, failed, blocked and not run. Continue independent work when a credential or physical-device dependency is missing.
7. End each implementation increment with changed behavior, tests/results, artifact location and next unresolved gate. This document itself is a plan, not evidence of those implementation outcomes.

## 12. Source verification and evidence limits

Reviewed primary sources on 2026-09-06. Recheck versions and deployment requirements during P0. The links support platform feasibility; architecture choices and performance targets above are this plan’s recommendations.

- [LiquidAI LFM2.5-350M model card](https://huggingface.co/LiquidAI/LFM2.5-350M): instruction-model identity, tool/extraction positioning, formats and advertised context. Vendor benchmark numbers are not application acceptance evidence.
- [Koog overview](https://docs.koog.ai/): Android support in its target platforms. [Koog quickstart](https://docs.koog.ai/quickstart/): OpenRouter client integration. Exact Android dependencies and local adapter must still be compiled and tested.
- [OpenRouter OAuth PKCE](https://openrouter.ai/docs/guides/overview/auth/oauth): code-to-key connection flow and S256. Android redirect integration remains a required spike.
- [AppSearch release notes](https://developer.android.com/jetpack/androidx/releases/appsearch): embedding-query API evolution; use capability detection for the selected backend.
- [Android AI overview](https://developer.android.com/ai/overview) and [ML Kit Prompt API setup](https://developers.google.com/ml-kit/genai/prompt/android/get-started): public companion capability integration and availability constraints.
- Prior conversation: `6a9d26c9-a418-83eb-ab46-c593f01f572d`, including the attached historical research Markdown, reviewed for user decisions. Older assistant statements about SDK deprecation, Android bubbles or specific Pixel generations were not promoted to verified requirements.
