# Research notes — memory, embeddings, sub-agents, tool weight, battery (2026-09-12)

Answers to the user's questions, verified against the code (file:line refs in the audit notes) and public sources where marked.

## Graph RAG — scaffold only

`graph_edges` exists (Room v3, PaladinoDatabase.kt:17) but the only writer is a single `owns_note` **self-edge** per created note (MemoryRepository.kt:77). `expandGraph()` (Domain.kt:88, bounded 2-hop/50-node) has **zero production callers** — only `PolicyTest`. Wiring it for retrieval is ticket **NEXT-04**, sequenced after the Koog memory/embeddings evaluation.

## Vector database — none, by design (for now)

Room `embeddings` table (per-note `vector` BLOB, cache keyed noteId+sourceVersion+modelId) + brute-force dot product over the **latest 200 notes**, fused with lexical AppSearch via reciprocal rank fusion (MemoryRepository.kt:40–67). No ANN index, no chunking, 256-token WordPiece cap. The 200-note cap and "chunking/10k-corpus" are the explicit release gates (NEXT-03). At current scale this is honest and fast; a vector DB would be premature.

## MiniLM vs a ~350M embedder

The 229 MB `lfm350` artifact is an **LLM** (LFM2.5-350M GGUF) — not an embedding model; using it as one would be slower and architecturally wrong. MiniLM-L6-v2 qint8 (23 MB, 384-dim, mean-pooled, L2-normalized, ONNX with 2 intra-op threads) is the right weight today. Public benchmarks ([Kaggle thread](https://www.kaggle.com/getting-started/436563), [embedding benchmark roundup](https://adityarajsingh.com/best-local-embedding-model/), [HN discussion](https://news.ycombinator.com/item?id=46081800)) show **bge-small-en-v1.5 / gte-small outscore MiniLM at the same 384 dimensions**, so a swap is a plausible future drop-in. But the real gap is not model size — it's **NEXT-03**: Portuguese/multilingual coverage, chunking, a background embedding outbox, and 10k-corpus scoring. Do NEXT-03 before touching the encoder.

## Can Paladino spawn sub-agents?

Koog 1.2.0 ships the machinery — `AIAgentSubgraph`, `SubgraphWithTaskBuilder`, parallel nodes with merge/fold (verified in the cached agents-core AAR). What blocks useful sub-agents here is the runtime: `LocalModel` holds **one** llama.cpp session and serializes all generation behind a mutex (LocalModel.kt:34–50), so two agents would just take turns on a single 350M context. Sub-agents become interesting with the 2.6B model (specialist + orchestrator) or per-agent native sessions. Parked: possible, not useful yet — and the per-turn skill registry already gives most of the benefit (small focused tool sets per task).

## Are the tools/skills light and well designed?

Measured, not assumed:

- **7 tools total**, each registered only when its skill woke this turn (prefs + regex activation, skipped entirely for greetings). 2–4 visible per turn is the norm — matches the 350M design rule of few choices with one flat string argument.
- **Prompt constants are tiny:** persona.md is 796 bytes, skill stubs are one-liners, tool schemas are rebuilt per hop as single-string-argument objects. Hard prompt ceiling 9,216 bytes with compression at 70 % and a "compression must shrink" invariant; LoopBudget caps 4 hops / 3 tools / 1 repair with repeat-digest rejection; every tool result is size-capped (1 000–4 000 chars).
- **Writes/actions stay behind approval** (digest-bound, expiring); the manifest whitelist rejects unknown tool ids at startup (proven live in Sprint B).

Verdict: yes — light and on-purpose. The known design debts are already ticketed (NEXT-02 semantic routing calibration, NEXT-03 multilingual retrieval, NEXT-04 graph wiring).

## Foreground services and battery

Current posture (verified): the only FGS is the overlay Sprite — `specialUse` type with documented subtype, `START_NOT_STICKY`, `IMPORTANCE_LOW` notification; **no wake locks, no periodic work, no WorkManager/JobScheduler loops**; screen-off pauses the animation and hides the window; the LLM and embedder unload on `TRIM_MEMORY_UI_HIDDEN`; the model download resumes by byte-ranges with a single OkHttp call; the widget never auto-updates (`updatePeriodMillis=0`).

External guidance applied (sources: [Doze & App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby), [FGS types required](https://developer.android.com/about/versions/14/changes/fgs-types-required), [FGS timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout), [long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running), [FGS→WorkManager case study](https://www.suridevs.com/blog/posts/foreground-service-to-workmanager-migration/), [Beyond Doze](https://proandroiddev.com/beyond-doze-building-reliable-background-execution-on-modern-android-including-oem-realities-5fa0a6e05672)):

1. **Keep the overlay FGS** — it is a genuinely user-visible ongoing surface; `specialUse` (not `dataSync`) means no Android 15 6-hour timeout. This is the correct use of the type.
2. **Never request battery-optimization exemptions** (Play policy friction; unnecessary for our workload).
3. **Future deferrable work goes to WorkManager**, not service loops: NEXT-03's background embedding outbox, NEXT-06 reminders, model re-verification. A documented migration case reports ~70 % less battery drain after moving FGS work to WorkManager.
4. Adopted now with the SFX layer: sounds skip when the ringer is silent/vibrate or **battery saver** is on.
5. OEM reality check (Xiaomi/Samsung aggressive killers): the overlay is user-started and not sticky by choice — re-launch remains manual, which is the honest tradeoff for a privacy-first preview.

No thermal/Doze-specific code is needed today: nothing runs when the screen is off and nothing is deferrable-but-polling.
