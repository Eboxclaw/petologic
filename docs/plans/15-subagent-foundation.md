# Plan 15 · Sub-agent foundation — the 230M slot and the worker path

User direction 2026-09-12: a small local model becomes a lightweight alternative brain and/or active sub-agent; when it isn't downloaded, sub-agent duties run as a session of the main 350M instead. The user's research note settles the pick: Liquid's **QAD-Q4_0 checkpoints match Q5_K_M quality with 4–33 % faster decode**, and **LFM2.5-230M** is the small sibling in the same official GGUF family.

**Small model (user-selected): `LFM2.5-230M-QAD-Q4_0`** — 149,081,056 B (~149 MB), sha256 `e75f83268de11b2a1bcfab5f3b5c5c0c97569ddbbc0990aad88437e45b8ba292`, pinned revision `cdf97bd8205908758f44aec508d68ac1aef98f5c` of `LiquidAI/LFM2.5-230M-GGUF`. Catalog id `lfm230-qad` (matches `lfm26-qad` naming).

## Reality check (verified in code)

- Catalog/install/verify already support N models (one `ModelCatalog` list entry; `selectModel` accepts any `lfm*`; a second `PaladinoAgent` is free — stateless per run).
- **True parallel loaded models are blocked in the native layer**: `paladino.cpp` holds one global `model`/`ctx`/`cancelled`, `load()` no-ops when a model is resident, and Kotlin's `loadedSpec` swaps (unload+load) on model change. That refactor is Phase S2 with memory gates.
- Until then, "parallel" means **queued**: worker turns run on the shared loaded model between user turns (`generationMutex` orders them). At 149 MB with a 2 048-token context, the swap cost is small.

## This sprint

1. **Catalog slot `lfm230-qad`** (pinned above).
2. **`runtime/SubAgent.kt`:** pure `workerModelId(installed)` (→ `lfm230-qad` when installed, else `lfm350`), `workerOptions()` (context 2048, output 128, toolReserve 128, no tools, validate-passing), `titleFrom(firstUser, firstAssistant)` (collapse whitespace, ≤42 chars, ellipsis, blank-safe), `run(local, modelId, system, task)` — one bounded worker turn, no tools.
3. **First consumer — auto-titling:** after a completed exchange in a session still titled "Conversation N", a worker proposes a short title; SessionController writes it to Room; failure/busy/model-missing → silent skip.

## Next phases (not this sprint)

- **S2 — native two-slot loading:** instance-handle refactor of `paladino.cpp` + per-slot Kotlin registry + low-RAM gates; then the worker gets its own resident 230M and notification triage becomes its background consumer.
- Koog `SubgraphWithTask` in-turn sub-agents; device_action + compose handoffs (plan-12 step ②); Sprint D Call Guard.

## Verification

JVM: fallback matrix (`lfm230-qad` installed → chosen; not installed → `lfm350`; minilm never), `titleFrom` shaping, `workerOptions().validate()`. Emulator: catalog lists LFM2.5-230M QAD as downloadable, app starts clean, send path unaffected, auto-title guard skips with no model (title unchanged).
