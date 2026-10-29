# ADR-0004: LFM3 stack — one Omni speaker plus a d1 decision router

**Status:** Proposed (blocked on the native LFM3 runtime gate)

**Date:** 2026-10-28

## Context

Liquid AI announced LFM3 on 2026-10-27: six open-weight 2B-class models built on the LFM3-1.7B-Base tri-hybrid backbone (24 layers, gated short convs + SWA + global attention), with day-0 GGUF releases and llama.cpp support (the Audio-2B GGUF card states b11960 / v0.8.0 and newer). License: LFM Open License v1.0 — commercial use is permitted for entities under the $10M annual-revenue threshold, which covers Petologic.

The current product has no classifier/router: routing is exact-command rules plus the user-selected Tiny/Maxx split, and the planned Encoder-230M security classifier was never built (NEXT-02, `docs/MISSING.md`). Today's optional-model downloads total about 551 MB: LFM2.5-VL-450M Q4_0 (219 MB), its mmproj Q8_0 (103 MB) and LFM2.5-Embedding-350M Q4_K_M (229 MB).

Verified LFM3 artifacts (sizes and SHA-256 from the Hugging Face LFS metadata, pinned to repository revisions):

| Artifact | Quant | Size | Role |
|---|---|---|---|
| LFM3-Omni-2B | Q4_0 | 1,049,617,920 B | Text+image+video+audio input, text+speech output |
| mmproj-LFM3-Omni-2B | Q8_0 | 540,892,768 B | Omni vision/audio projector |
| d1-2B | Q4_K_M | 1,099,629,888 B | Decision model: calibrated yes/no/choices/scores in one pass |
| mmproj-d1-2B | Q8_0 | 203,031,584 B | Decision model vision projector |
| LFM3-1.7B (alternative) | QAD-Q4_0 | 1,049,615,680 B | Text agent, tool calling, 256K context |
| LFM3-VL-2B (alternative) | QAD-Q4_0 | 1,049,615,648 B | Vision-language agent |

No LFM3 embedding model exists, so LFM2.5-Embedding-350M stays the vector model.

## Decision

1. **LFM3-Omni-2B Q4_0** becomes the target local speaking model, replacing LFM2.5-VL-450M in the "one generative model" role. One model covers chat, explanation, screenshot inspection (via its projector) and later audio input and speech output. Chosen over the two-agent alternative (LFM3-1.7B + LFM3-VL-2B, about 3.5 GB with projector and router) because it is about 0.7 GB smaller end-to-end, needs one resident model instead of two, and adds an audio surface. If Omni text or security-explanation quality fails the promotion gates, fall back to the two-agent stack.
2. **d1-2B Q4_K_M** becomes the decision/router model: Tiny-vs-Maxx escalation, security triage and urgency/complexity calls, replacing exact-command-only routing (NEXT-02). d1 is explicitly not a chat model and its output never enters the verdict or authorization path; the deterministic fast path stays first, d1 is the second stage.
3. **LFM2.5-VL-450M remains the default low-end speaker** for this preview. LFM3 artifacts are pinned in `Lfm3Pack` but stay out of `ModelCatalog.artifacts`, downloads and selection UI until the runtime gate passes. The 450M path is not compiled out.
4. **LFM2.5-Embedding-350M Q4_K_M is unchanged.**

## Runtime gate (blocking)

The pinned llama.cpp revision `73a43d1f69345aee8bb186ef4b3172cef892f2e5` predates LFM3 support. Before any LFM3 artifact is offered:

1. Bump `android/scripts/bootstrap-native.sh` to a revision containing LFM3 (≥ b11960 / v0.8.0; verify VL/Omni and d1 architectures in that range, pin the exact commit).
2. Rebuild the JNI bridges and re-run the 16 KB alignment check.
3. Validate on ARM64 emulator and a physical 6–8 GB phone: load, RAM (PSS), cold/warm latency, cancellation for Omni Q4_0 and d1 Q4_K_M.
4. Spike d1 through llama.cpp: the calibrated "one forward pass" wrapper is transformers custom code (`system_one`), so the app must reproduce typed answers plus first-token logit probabilities over a strict template; if calibration is not reproducible, fall back to `d1-2B-ONNX` on the existing ONNX Runtime path.

## Consequences

- Optional-model downloads grow from about 551 MB to about 2.8 GB when the Omni pack, its projector, d1 and the embedding model are all installed. The Settings model library must present them as separate staged packs with size labels, and the website `RELEASE`/`TECHNOLOGY` blocks must match.
- Only one large model may be resident; the existing scheduler invariant is unchanged.
- d1 routing needs a held-out PT/EN evaluation (300-request split, macro-F1 ≥ 0.90 target from NEXT-02) before it may influence escalation, and it can never create or change detector findings.
- Speech output from Omni is a later phase: llama.cpp realtime voice is server-oriented today; the JNI app ships text and vision first.

## Sources

- [LFM3 announcement (Hugging Face blog)](https://huggingface.co/blog/LiquidAI/lfm3)
- [LiquidAI/LFM3-Omni-2B-GGUF](https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF)
- [LiquidAI/d1-2B](https://huggingface.co/LiquidAI/d1-2B) · [LiquidAI/d1-2B-GGUF](https://huggingface.co/LiquidAI/d1-2B-GGUF)
- [LFM Open License v1.0](https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF/blob/main/LICENSE)
