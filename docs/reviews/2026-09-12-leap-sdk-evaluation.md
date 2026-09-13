# LEAP SDK evaluation — plan 16 (2026-09-12)

> **DEPRECATED VERDICT (2026-09-13, supersedes everything below):** Liquid deprecated the LEAP SDK
> (banner on the [changelog](https://docs.liquid.ai/deployment/on-device/leap-sdk-changelog): "The
> LEAP SDK is deprecated and this page is no longer maintained"); their official migration target
> is llama.cpp — LEAP was, per Liquid, only a Kotlin Multiplatform wrapper around it. The `leap`
> flavor, `LeapBackend` and all LEAP-only code were **removed** in the rollback commit; the
> production stack is and remains llama.cpp (users were never exposed — the flavor never shipped).
> Alternatives scan kept the same verdict: LiteRT-LM (Gemma-centric, no LFM2.5/QAD-GGUF path),
> MLC (per-model TVM compiles), MNN (Qwen-centric) — none fits better. Two things worth
> recovering from LEAP's wins, on llama.cpp: **GBNF grammar-constrained tool calls**
> (`llama_sampler_init_grammar()`; the PT-tool-call path) and **plan 15 S2 two-slot native
> loading (un-retired)** for resident multi-model. This document stays as the measured record.

Question: should Petologic's local runtime migrate from the custom llama.cpp/JNI stack
(`paladino.cpp` + `NativeLfm`, wrapped as `LlamaCppBackend`) to Liquid's LEAP SDK 0.10.9
(`LeapBackend`)? Method: make LEAP **compete** against what already works, on identical fixtures,
same emulator (API 35, arm64, CPU-only), same QAD GGUF files. Raw data:
`docs/evidence/2026-09-12-leap-benchmarks/` (JSON), logcat tags `LEAPSPIKE`/`COLLISION`/`BENCH`/
`TOOLBENCH`. **Emulator numbers are relative-only**; absolute + 30-min thermal numbers require the
USB phone run (`ThermalSoakTest`, implemented, not yet run).

## Verdict (measured evidence)

**DEFAULT = LLAMA_CPP for the production app today; LEAP is promoted to a supported experimental
engine and is the recommended engine for the sub-agent path going forward.** Plan 15 S2 (native
two-slot llama.cpp refactor) is **RETIRED in favor of "leap backend for workers"** — if/when LEAP
ships as default, the two-slot work is unnecessary; llama.cpp stays single-slot by design.

## What was proven

| Gate | Result |
|---|---|
| QAD GGUF sideload (no .bundle) | ✅ 230M cold 2.0 s / warm 1.1 s; 350M loads alongside |
| Two resident runners + concurrency | ✅ 350M+230M resident, concurrent generate ok; at the user's 8192 standard: **1.00 GB both resident** (see Memory review below; the earlier "1.55 GB" was the benchmark's leftover context buckets) |
| Unload/cancel | ✅ unload 143 ms at 8192 (1.00 GB→613 MB); cancel 46 ms (llama 5 ms) |
| JNI collision (llama.cpp + LEAP same process) | ✅ 10 alternating cycles, all correct, stable PSS — the iOS symbol-collision class does **not** reproduce on Android |
| Real-model e2e through Koog agent | ✅ `RealModelTest` passes on BOTH flavors ("Hello Paladino") |
| Streaming + token stats | ✅ flow-based, `GenerationStats` feeds `InferenceMetrics` |
| Tool calling, EN (12 fixtures, minimal single-turn harness) | llama JSON 6/12 · llama native 6/12 · LEAP JSON 7/12 · LEAP native 7/12 · **LEAP native FC 9/12** |
| Tool calling, PT-PT tool requests | llama forced-text 0/5 · LEAP forced-text 0/5 · **LEAP native FC 5/5** (search+save, incl. MIXED) |
| Runtime speed (emulator, 350M@4096) | warm reply ~0.66 s/TTFT ~0.3 s (LEAP) vs ~1.0 s/~0.75 s (llama); 3600-char prompt TTFT 2.25 s vs 5.24 s |
| Model switching | LEAP: instant after both resident (0.78 s) but 350→230 first-touch 2.16 s; llama: reload every switch (0.94 s / 1.72 s) |
| Memory (emulator PSS) | 350M@4096 single: LEAP ~0.66 GB vs llama ~0.43 GB; at 8192 both resident LEAP = **1.00 GB** vs llama single-slot ~0.48 GB — residency is the trade for instant switching |
| APK size | leap flavor adds the leap-sdk native libs + deps (see flavors build); llama-only flavor unchanged |

## The PT result is the product headline

Forced-text tool protocols (JSON or pythonic) fail **all** PT-PT tool requests on both engines in
the minimal harness. LEAP's native function calling with `LFMFunctionCallParser` gets every PT and
mixed request right. Petologic is PT-first: if the next public build adopts one thing from this
evaluation, it is **LEAP native function calling for tool turns**. Production currently compensates
with a repair loop (one extra hop, slower and still weaker in PT).

## Integration notes (leap flavor, committed)

- `BackendFactory` (leak-free split): llama flavor has no LEAP dependency; leap flavor maps
  `LeapClient.loadModel(path, ModelLoadingOptions(cpuThreads, contextSize, useMmap))` →
  `ModelRunner` cached per (model, context, threads); `Conversation.generateResponse` streams
  `MessageResponse.Chunk/ReasoningChunk/FunctionCalls/Complete/Error`.
- LEAP extracts LFM tool-call tokens into `FunctionCalls` responses and strips them from text;
  `LeapBackend` materializes them back into `[name(argument="…")]` so the agent-layer
  `LfmToolCallParser` sees one protocol regardless of engine.
- LFM2.5 "thinking" arrives as separate reasoning chunks; when a reply is all reasoning,
  `LeapBackend` surfaces it rather than returning "".
- `ModelLoadingOptions.contextSize` is loader-fixed (unlike llama per-request context), so runners
  are keyed by context bucket; the spike used `GenerationOptions()` defaults for sampling.
- Duplicate `libc++_shared.so` warning when building the leap flavor (AGP picks the app's). No
  runtime effect observed; revisit if LEAP native libs are updated.

## Licensing (document, do not relabel)

| Component | License |
|---|---|
| Koog 1.2.0 | Apache-2.0 |
| llama.cpp (vendored) | MIT |
| LFM2.5 weights (.gguf) | LiquidAI model license (pinned HF revisions) |
| LEAP SDK 0.10.9 | proprietary "Leap Terms" — free of charge, object-code redistribution inside an app, NOT open source |
| This app with LEAP | **not** "fully open source" while LEAP is linked |

## Memory review (2026-09-12, user-challenged numbers)

The benchmark table's "1.55 GB" was an artifact of the benchmark itself: the runtime sweep left
**three 350M context-buckets (2048/4096/8192) plus 230M resident** in the backend's runner cache.
Re-measured with the production policy the user set — **every model loaded once at 8192**
(`LeapMemoryTest`, logcat `LEAPMEM`):

| State | PSS |
|---|---|
| App baseline, no model | 75 MB |
| 350M@8192 resident | 688 MB (model cost ~614 MB over baseline) |
| **+230M@8192 (both resident)** | **1 004 MB ≈ 1.0 GB** |
| Steady state after more generations | 1 000 MB (no growth — no leak) |
| Worker unload | 143 ms → 613 MB |

So "350M + 230M under 1 GB" is essentially exact (1.00 GB including the whole app). MiniLM is a
separate ONNX encoder (+~23–30 MB), unrelated to the engine numbers.

## Policy changes from the docs review (docs.liquid.ai model-loading + changelog)

1. **Fixed 8192 runners** — one runner per model, no context-bucket keying (was the artifact).
2. **Manifest sampling by default** — docs: models are trained against manifest sampling and
   overriding "can significantly degrade output quality"; `SessionOptions` sampling now passes
   through only when the user actually changed it.
3. **Persistent KV-prefix reuse enabled** (`EngineOptions.CacheOptions(enabled=true)` under
   `cacheDir/leap-kv/<model>`) — docs: prefill avoidance for multi-turn/agent loops.
4. **Thinking disabled** (`enableThinking=false`): LEAP's default-on LFM2.5 reasoning silently
   consumed the output budget (empty replies), stretched emulator generations past the UI's 90 s
   window, and has no llama.cpp counterpart — parity until a deliberate thinking UX exists.
5. Under manifest sampling, **forced-text tool protocols on LEAP dropped to 6/12** (thinking +
   different sampling); LEAP **native FC stays the recommended tool-turn path** (9/12, and it was
   already measured under manifest sampling). Realizing it in-app needs the interface to carry the
   tool list so `LeapBackend` can register `LeapFunction`s — next step, not yet wired.
6. In-app verification: manual chat on the leap flavor streams fine end-to-end; the bundle-format
   probe line (`BundleProcessor … zip END header not found`) in logcat is LEAP's normal
   format-detection fallback to GGUF, harmless. `RealToolConversationTest` on the leap flavor
   times out only on the emulator (first in-app turn under the full production prompt exceeds its
   90 s window) — rerun on the phone with the thermal pass.

## On-device proof (2026-09-13 emulator batch, leap flavor)

- **`RealToolConversationTest` PASSES on LEAP**: the production loop — PT greetings without tools,
  PT save request → `notes_save` → approval gate → note in Room, PT search recall with the saved
  fact in the final answer — runs end-to-end through Koog → LeapBackend → LEAP native function
  calling. This is the in-app realization of the 9/12 bench win. Needed two fixes found by the
  test (both general correctness):
  - `SpriteAnimator`: `registerAnimationCallback` requires a Looper thread; drawable wiring now
    hops to Main after IO decode (crashed under the compose-test dispatcher before).
  - `SessionController` logged metrics from `app.local.metrics` (llama's flow) instead of
    `app.inference.metrics` — the "inference" quest-log event was silently missing on the leap
    flavor; now reads the interface.
  - Instruction tuning: under native FC the 350M reached for `notes_search` on save requests;
    the native-FC instruction now binds save/register/remember intents to `notes_save` explicitly.
- Re-baseline `ToolCallLeapNativeTest`: 10/12 strict + 1 partial-arg + 1 wrong-tool (consistent
  with 9/12; run-to-run 350M variance). Llama forced-text partial re-run (11/24 rows before an
  emulator slowness timeout): 6/11 malformed — forced-text weakness confirmed again; full tables
  from the 2026-09-12 runs stand.
- `ThermalSoakTest` still pending — needs the user's phone over USB.

## Acceptance-gate checklist (for a future DEFAULT = LEAP flip)

- [x] 350M + 230M reliable generation (emulator)
- [x] no native conflicts (same-process collision test)
- [x] streaming / cancellation / unload lifecycle
- [x] tool calling ≥ current (PT strongly better via native FC)
- [ ] **thermal + absolute perf on real hardware** (`ThermalSoakTest` over USB)
- [ ] battery behavior on device (background/foreground mix)
- [ ] APK size sign-off for the release build
- [ ] offline upgrade path for existing installs (model files stay; loader changes)

Recommendation stands until the phone run contradicts it. If LEAP ever regresses on device:
`Koog → LocalInferenceBackend → llama.cpp` is unchanged and remains fully shippable.
