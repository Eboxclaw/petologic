# LEAP SDK evaluation — plan 16 (2026-09-12)

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
| Two resident runners + concurrency | ✅ 350M+230M resident, concurrent generate ok, peak ~1.09–1.55 GB |
| Unload/cancel | ✅ unload 91 ms (1.55 GB→124 MB); cancel 46 ms (llama 5 ms) |
| JNI collision (llama.cpp + LEAP same process) | ✅ 10 alternating cycles, all correct, stable PSS — the iOS symbol-collision class does **not** reproduce on Android |
| Real-model e2e through Koog agent | ✅ `RealModelTest` passes on BOTH flavors ("Hello Paladino") |
| Streaming + token stats | ✅ flow-based, `GenerationStats` feeds `InferenceMetrics` |
| Tool calling, EN (12 fixtures, minimal single-turn harness) | llama JSON 6/12 · llama native 6/12 · LEAP JSON 7/12 · LEAP native 7/12 · **LEAP native FC 9/12** |
| Tool calling, PT-PT tool requests | llama forced-text 0/5 · LEAP forced-text 0/5 · **LEAP native FC 5/5** (search+save, incl. MIXED) |
| Runtime speed (emulator, 350M@4096) | warm reply ~0.66 s/TTFT ~0.3 s (LEAP) vs ~1.0 s/~0.75 s (llama); 3600-char prompt TTFT 2.25 s vs 5.24 s |
| Model switching | LEAP: instant after both resident (0.78 s) but 350→230 first-touch 2.16 s; llama: reload every switch (0.94 s / 1.72 s) |
| Memory (emulator PSS) | 350M@4096: LEAP ~0.66 GB vs llama ~0.43 GB; LEAP keeps runners resident (350M+230M ≈ 1.5 GB); llama single-slot ≈ 0.43 GB constant |
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
