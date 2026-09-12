# Plan 16 — Local inference backends: LEAP challenger vs llama.cpp champion

Started 2026-09-12 after the user's external research handover. Principle: **don't migrate to
LEAP; make LEAP compete.** Koog stays at 1.2.0 (no koog-edge, no downgrade); `paladino.cpp`,
`NativeLfm` and the SkillRegistry/approval architecture are untouched. The sprint ends with a
measured recommendation: `DEFAULT = LEAP | LLAMA_CPP | MORE TESTING REQUIRED`.

## Why a backend interface

Today the Koog layer knows the native runtime directly: `PaladinoAgent` holds a `LocalModel?` and
calls it from inside its custom `PromptExecutor` (`PaladinoAgent.kt:111`), `SubAgent.worker`
generates titling turns on it, and the UI reads status/progress/ready/metrics from it. The JNI
runtime (`paladino.cpp`) is single-slot by design (one global model/context), which is what plan
15 S2 proposed to refactor natively. A `LocalInferenceBackend` seam lets LEAP 0.10.9 compete
without touching Koog, the tool gates or the parser, and may retire plan 15 S2 if LEAP's model
runner supports safe multi-model residency.

## Stage 1 — Pure refactor (this commit)

- New package `ai.petologic.paladino.inference`: `LocalInferenceBackend` (ready/metrics/
  capabilities + verify/generate/unload), `LocalGenerationRequest`, `BackendCapabilities`,
  `InferenceMetrics` (moved verbatim from `LocalModel.kt`).
- `llama/LlamaCppBackend` wraps the existing `LocalModel` by composition — zero logic moved,
  zero behavior change. Contracts preserved: "text so far" streaming, serialized generation,
  cooperative cancellation.
- Consumers switched: `PaladinoAgent(inference, …)`, `SubAgent.worker(inference, …)`,
  `PaladinoApplication.inference` lazy singleton, trim-memory unload through the interface.
  UI lifecycle (status/progress/ready/install/verify) intentionally stays on `LocalModel`.
- Gate: 18/18 JVM suites green; release + debug builds OK; emulator launch smoke clean.

## Stage 2 — LEAP 0.10.9 format spike (decides the catalog branch)

Runtime spike against `ai.liquid.leap:leap-sdk:0.10.9` on the emulator:
1. GGUF sideload of `LFM2.5-350M-QAD-Q4_0.gguf` / `LFM2.5-230M-QAD-Q4_0.gguf` (docs are
   `.bundle`-centric; sideload support must be proven, not assumed).
2. Streaming/cancel/unload API shape and what `InferenceMetrics` can honestly report.
3. Two resident runners (350M + 230M): memory, serialization, concurrency.
4. Dependency audit (leap-sdk pulls Ktor 3.3.3 + kermit + kotlinx-io) and APK size delta.
Branch if GGUF fails: keep the GGUF catalog, add backend-tagged `LEAP_BUNDLE` artifacts.

## Stage 3 — `engine` flavors + experimental LeapBackend

Flavor dimension `engine`: `llama` (default, no LEAP dependency) and `leap` (LEAP dependency +
flavor source set + `applicationIdSuffix ".leap"` for side-by-side phone installs). Per-flavor
`BackendFactory`. Dual-engine wiring behind a BuildConfig flag for the JNI-collision cycle test
(startup → 350M load/generate/unload → LEAP load/generate → alternate ×20).

## Stage 4 — Benchmarks (Liquid's methodology, emulator-first)

Instrumented, runner-arg-guarded (`bench=true`, `thermal=true`): cold/warm load, TTFT, prefill/
decode tok/s, P50/P95, peak/end PSS, 256/512/1024-token prompts → 100 out, contexts
2048/4096/8192, cancellation + switch latency, 10 consecutive requests — for both engines.
Tool-call harness with EN / PT-PT / mixed fixtures × {no-tool decision, correct tool + exact
args, malformed, repair, wrong tool, grounded answer} × protocols {A llama+JSON, B llama+native
LFM syntax, C LEAP native FC, D LEAP+JSON if sensible}. 30-min thermal implemented but deferred
to a USB phone run. Evidence in `docs/evidence/`.

## Stage 5 — Verdict docs

`docs/reviews/2026-09-12-leap-sdk-evaluation.md` with the licensing table (Koog Apache-2.0 /
llama.cpp MIT / LFM weights model license / LEAP proprietary-but-free — a LEAP build is **not**
"fully open source"), benchmark tables, acceptance-gate checklist, plan 15 S2 keep/retire verdict,
and the final recommendation.

## Acceptance gates for any future DEFAULT = LEAP flip

350M + 230M reliable · tool calling ≥ current (PT included) · streaming/cancel/history semantics ·
memory ≤ current + acceptable overhead · no native conflicts · download/load lifecycle intact ·
APK impact documented · no SkillRegistry/Koog/approval regression. Performance does not need to
win every metric; halving custom native code with ≤~5% slowdown is a valid win.

## Outcome (executed 2026-09-12, same day)

All stages executed on the emulator. **DEFAULT = LLAMA_CPP for production today; LEAP becomes the
supported experimental engine; LEAP native function calling is the recommended engine for tool
turns (PT-first win); plan 15 S2 retired in favor of LEAP for workers.** Full tables, gates and
licensing: [docs/reviews/2026-09-12-leap-sdk-evaluation.md](../reviews/2026-09-12-leap-sdk-evaluation.md).
Raw data: `docs/evidence/2026-09-12-leap-benchmarks/`. Remaining open item before any default
flip: `ThermalSoakTest` + absolute numbers on real hardware over USB.
