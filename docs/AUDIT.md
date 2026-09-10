# Petologic codebase audit — 2026-09-10

Scope: full review of this repository at commit `c6e8817` ("Add read-only phone capabilities and guide restricted Sprite permissions"). Method: direct reading of every runtime/data/core source file, build files, bundled assets, plans, ADRs, reviews and progress records. The untracked `UI_HANDOFF.md` in the parent workspace is a spec for a different product and was deliberately excluded from this analysis. This audit adds an outside-in reading of the code; it changes no code and supersedes nothing.

## 1. What Petologic is (as built)

Two deliverables share this repository:

1. **Landing page** (repo root, `src/`): Vite/React single page deployed to petologic.vercel.app. Marketing language still describes a four-pet "party" and premium/secret pets; `docs/MISSING.md` explicitly flags that this older copy is a separate content decision, not a shipped-product claim.
2. **Native Android app** (`android/`): the real product — a Kotlin 2.3.10 / Jetpack Compose / AGP 8.12.3 app, `applicationId ai.petologic.paladino`, minSdk 31, target/compile SDK 36, version 0.1.5-preview (versionCode 6). It is a working development preview of one pet, **0xPaladino**, with real on-device inference, real tool calls and real approvals.

The app's contract with the user, as implemented: converse offline via a mandatory local **LFM2.5-350M** (Q4_K_M GGUF, 229 MB, SHA-256-pinned), keep private notes in a local Room database with lexical (AppSearch) + semantic (MiniLM ONNX) retrieval, propose every write for explicit user approval, and offer an explicitly consented cloud escalation (**Maxx**) to OpenRouter, OpenAI or Z.ai. Cloud text is never parsed into executable tools.

## 2. Architecture map

```text
android/
  core/            pure Kotlin: domain, policy, manifest validation, model catalog, WordPiece
  app/
    runtime/       LocalModel (JNI llama.cpp), PaladinAgent (Koog), NoteToolProtocol,
                   CloudProvider, OpenRouterTransport, CredentialStore, ModelLibrary, SmallEmbedder
    data/          Room v3 (PaladinoDatabase), MemoryRepository (Room + AppSearch + embeddings + graph)
    ui/            MainActivity (Compose), SessionController/SessionHub/ControlCenter,
                   SpriteOverlayService (floating pet), PaladinoWidget, onboarding, PT/EN strings
    cpp/           paladino.cpp — pinned llama.cpp JNI bridge
    assets/paladino/  manifest.yaml, persona.md, greetings (PT/EN), MiniLM vocab.txt
```

Supporting surfaces: floating **Sprite** overlay service (specialUse foreground service, drag/edge-dock/position restore, per-session drafts, compact chat bubble), launcher widget (static entry point), onboarding cards, EN/PT localization with `locales_config`, Markdown replies via Markwon.

## 3. Local model integration

- **Runtime**: llama.cpp pinned at `73a43d1f…`, fetched by `scripts/bootstrap-native.sh`, excluded from Git, compiled via CMake/NDK 28.2 into `libpaladino.so`. `paladino.cpp` serializes all model ownership behind a mutex, uses the model's **official chat template** (`llama_chat_apply_template`), validates roles, tokenizes with the native vocab (final authority on context size), streams tokens through JNI callbacks, supports cancellation via an atomic flag independent of the mutex, and returns prefill/decode timings.
- **Model identity**: `ModelCatalog` pins four artifacts — LFM2.5-350M Q4_K_M (baseline), LFM2.5-2.6B Q4_K_M and QAD-Q4_0 (experimental), and MiniLM-L6-v2 qint8 ONNX (23 MB embedder). Every install/import path (download with resume, SAF file import, folder scan) verifies exact size + SHA-256 before atomic rename; nothing unverified ever reaches the runtime.
- **Sampling/inference options** are session-scoped and validated (`SessionOptions`): context 1024–8192 tokens (default 4096), output ≤1024, temperature/topK/topP/repeatPenalty/seed, threads/batch/microBatch/mmap. Generation is mutex-serialized; a spec change reloads the context.
- **No background inference** anywhere; load on demand, unload explicitly.

## 4. Koog orchestration and the tool protocol

`PaladinoAgent` wraps a Koog `AIAgent` with `singleRunStrategyWithHistoryCompression` and a custom `PromptExecutor`:

- **TINY** → local LFM with tools; **MAXX** → cloud, tools stripped (text only, no side effects).
- Tool registry is assembled per session from capabilities: `notes_search` requires `memoryRead`, `notes_save` requires `memoryWrite`, both only in TINY with `toolCalls` enabled. Tools use a deliberately tiny schema — one `argument: string` — sized for a 350M model.
- **Strict tool-call parsing** (`NoteToolProtocol`): accepts LFM's documented Pythonic-call format, `<|tool_call_start|>` tokens, JSON object/array and fenced JSON; validates names against the registry, argument length ≤12000, never evaluates expressions. A bounded repair loop (≤`maxRetries`) regenerates malformed calls.
- **Anti-hallucination guard**: when a request explicitly asks for a note search (EN/PT regex), the agent checks a real `notes_search` result occurred this turn and forces a repair or fails honestly — history is never accepted as a search result.
- **LoopBudget**: bounded hops (≤4 default), tool calls (≤3), digest-keyed repeat detection ("repeated tool call stopped to prevent a loop"), hop/total timeouts, byte-level hard context check, bounded history compression (≤2 attempts, must shrink the prompt).
- Session capabilities are a three-way intersection (`effectiveCapabilities`: global ∩ agent ∩ session); `Capability` already enumerates FILES, WEB_SEARCH, MCP, SKILLS, VISION, BACKGROUND, NOTIFICATIONS, APP_ACCESS — reserved vocabulary for the roadmap.

## 5. Actions, approvals and the manifest

- **Writes are proposals**: every mutating action becomes an `ActionProposal` persisted in Room with a `tool\u0000argument` SHA-256 hash and a 5-minute expiry. `ApprovalPolicy` validates tool allowlist, expiry, hash equality ("action changed after approval") and content bounds at execution time, inside a Room transaction. The in-chat `notes_save` tool blocks on a `CompletableDeferred` until the user approves or declines; decline cancels and is reported truthfully to the model.
- **Role manifest** (`assets/paladino/manifest.yaml`, parser `PaladinoManifest`): restricted YAML subset, exact field set, fixed role/namespace/trigger, `cloudPurposes: [reason, summarize_context, plan]`, `confirmationPolicy: app_minimum`, and an `allowedTools` list validated against a hardcoded supported set — `notes.search`, `notes.create`, `notes.delete`, `clock.read`, `alarm.next`, `calendar.today`, `weather.current`. Privilege expansion is rejected at parse time.
- **Deterministic routes** (`RoutePolicy`): exact EN/PT "remember that…"/"find…" commands short-circuit generation for save/search. Greetings bypass tools entirely.
- **Phone reads** (`PhoneReads`, newest layer): deterministic, length/line-bounded EN/PT intent router (rejecting write verbs and summarize/translate phrasings) over `CLOCK` (no permission), `ALARM` (`AlarmManager.nextAlarmClock`), `CALENDAR` (`READ_CALENDAR` → `CalendarContract.Instances`, today, ≤20 events), `WEATHER` (opt-in city → Open-Meteo geocoding + current conditions, no GPS, response size-capped), `EMAIL` (honest refusal: no mailbox integration), and `CAPABILITIES` (truthful self-report). Every read is logged as `tool_call`/`tool_result` and validated against the manifest allowlist.

## 6. Memory and persistence

Room v3 is authoritative (`notes`, `messages`, `actions`, `index_outbox`, `graph_edges`, `tasks`, `reminders`, `embeddings`, `sessions`, `execution_events`), with exported schemas and tested migrations. `MemoryRepository`:

- AppSearch is a rebuildable lexical index fed by a transactional outbox (write + outbox in one transaction; reconcile on launch); queries are sanitized to plain tokens (never model-provided grammar); hits are revalidated against canonical rows so stale/deleted notes can't escape the index.
- Optional semantic layer: MiniLM ONNX embeddings (cached per note version), cosine similarity over the latest 200 notes, ≥0.25 threshold, reciprocal-rank fusion with lexical results — real paraphrase retrieval with explicit, documented limits.
- Knowledge graph is a tested scaffold (ownership edges + bounded 2-hop/50-node expansion) not yet used for retrieval (NEXT-04).
- Conversation history is per-session; sessions support branch/archive/restore; `execution_events` is the in-app audit log ("Console") — every hop, tool call, repair, compression and approval decision is recorded per session.

## 7. Cloud relay (Maxx)

- `CloudProvider` is a fixed enum — OpenRouter, OpenAI, Z.ai — with pinned endpoints, no model-chosen URLs, `followRedirects(false)`, validated model-ID regexes.
- `CredentialStore`: per-provider Android Keystore AES/GCM keys, per-provider ciphertext slots, disconnect + "revoke at provider" guidance.
- `OpenRouterTransport`: consent is a `CloudConsent` bound to the exact outbound payload digest + provider + model + 5-minute expiry; `LOCAL_ONLY` sources are refused before transmission; SSE streaming with 2 MB cap, `[DONE]` enforcement, mapped 401/402/429 errors, cancellation watcher. Tool execution in cloud transport is intentionally not enabled; cloud output is plain text.
- The outbound review UI shows exactly what leaves the phone before approval; Tiny sends nothing.

## 8. Platform and security posture

- Manifest permissions: `INTERNET`, `READ_CALENDAR`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE(+SPECIAL_USE)` — deliberately minimal; the overlay service documents its subtype ("no screen capture or autonomous activity").
- `allowBackup=false`, `usesCleartextTraffic=false`, data extraction rules, RTL, per-app language (PT/EN).
- Model downloads verify pinned SHA-256 with atomic replacement and disk-space preflight; the app shows setup status rather than substituting a fake model.
- Overall security design is unusually disciplined: strict parsers, digest-bound consent and approvals, no redirects, no embedded keys, honest failure messages, and a project rule that cloud text is never tool-parsed.

## 9. Verification culture

Tests: 35+ JVM (policy, protocol, transport, routing, position, reactions) plus a large instrumentation suite (Room migrations, AppSearch, overlay, paired Chat/Sprite, PT UI, provider credentials, onboarding, phone reads) with an opt-in `realModel=true` lane that runs real LFM weights and refuses to silently pass when weights are missing. `docs/evidence/` stores dated transcripts, timings and screenshots; `docs/VALIDATION.md` and per-release records state what actually ran. Release APKs (0.1.1→0.1.5-preview, signed, ARM64) are distributed via the separate public `petologic-downloads` repo; debug builds are not upgrade-compatible.

## 10. Known gaps (from the repo's own tickets, confirmed in code)

- Physical-device battery/thermal/perf gates, 16 KB page-size native certification, Play Protect policy review — open.
- One serialized model instance shared across conversations (mutex, no fair queue); no durable context summaries or execution resume; foreground-only inference.
- Embedder is English-oriented, 200-note/256-token caps; multilingual retrieval (NEXT-03), knowledge graph integration (NEXT-04), richer ContextBroker (NEXT-05) open.
- Reminders table exists but no durable scheduling/notification path yet (NEXT-06); calendar writes and share handoffs not implemented; email integration intentionally absent pending an authorized account connection.
- No OAuth/PKCE (BYOK only), no usage/cost accounting (NEXT-07); optional Google/Pixel accelerator adapter not started (NEXT-08); no fine-tuning anywhere.

## 11. Extension points the capability roadmap must use

1. **New model artifacts** → `ModelCatalog` + `ModelLibrary` (hash-pinned) — no unverified weights.
2. **New tools** → extend the `PaladinoManifest` supported set + bundled `manifest.yaml`, register in the Koog registry behind a capability flag, and follow the single-`argument` schema + strict-parser pattern (extend `NoteToolProtocol` per category or generalize it).
3. **New writes** → `ActionProposal`/`ApprovalPolicy.allowed` set; hash-bound, expiring, session-scoped, transactional — the pattern is already proven and tested.
4. **New deterministic reads** → the `PhoneReads` pattern: router + permission check + capped result + honest refusal + manifest check.
5. **New Android surfaces** (notifications, SMS, files) → corresponding runtime permissions + manifest entries; each must be opt-in, logged, and revocable per session via `SessionOptions`/`Capability`.
6. **New pets** → the roster seam already exists conceptually: `TinyPetCatalog` (one entry today) + per-role manifests that can only narrow the app-minimum policy; the plan's identity contract (one role per manifest, cloud as subrole) is the intended multi-pet chassis.
7. **On-device Google/Pixel AI offload** → reserved as an adapter behind runtime capability discovery (NEXT-08); it must never substitute the mandatory LFM or widen permissions.

## 12. Verdict

This is a real, unusually honest engineering preview: local-first inference works end-to-end with real tool calls and approvals, the safety architecture (hash-bound approvals, manifest allowlists, digest-bound cloud consent, strict parsers, bounded loops) is coherent and tested, and the documentation separates evidence from marketing. The gap between the website's four-pet story and the one-pet preview is known and documented. The next phase — the capability matrix under `docs/capabilities/` — should be read as the "later product plan" that `docs/plans/0xPaladino-v1-Android-implementation-plan.md` §2 requires before security/network/messaging scope is added.
