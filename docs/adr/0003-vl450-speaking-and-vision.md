# ADR-0003: VL-450M as Paladino's eyes and voice

**Status:** Accepted

**Date:** 2026-09-21

## Context

Paladino needs short local security explanations and can gain unique value from user-selected screenshots of scams, deceptive interfaces and Android security screens. LFM2.5-VL-450M uses the LFM2.5-350M language backbone with an 86M vision encoder. Its official instruction-following scores are lower than the text-only 350M, while its BFCLv4 tool score is close. Product quality therefore depends on Paladino-specific testing rather than general chat quality.

The product keeps two semantic model roles: one generative model and one embedding model. The vision projector is an optional verified component of the generative model, not another speaking persona.

## Decision

1. **LFM2.5-VL-450M Q4_0** is the only local generative model. It handles bounded chat, explanations and text-only tool selection.
2. **mmproj-LFM2.5-VL-450M Q8_0** is an optional download. It is loaded only for a user-selected image and released after visual prompt evaluation.
3. **LFM2.5-Embedding-350M Q4_K_M** remains the vector model for private retrieval and similarity.
4. A visual turn has no tools. Text rendered inside an image is untrusted input. Visual output contains observations and uncertainty, never a malware verdict or action claim.
5. Images enter through Android's picker or share sheet, are dimension-bounded and resized in memory, and are not written to conversation history or security findings.
6. Deterministic scanners, exact extraction and Android signals remain the security authority. OCR, QR payloads, URLs, package IDs, codes and amounts must be verified outside the VLM.

```text
user-selected image
        |
bounded in-memory decode
        |
VL observations, no tools
        |
canonical evidence boundary
        |
deterministic checks and user-visible limitations
```

## Consequences

- The required speaker stays about 219 MB. Enabling vision adds about 103 MB. The embedding model adds about 229 MB.
- Existing sessions that reference removed speakers migrate to the VL model.
- The Android runtime links llama.cpp `libmtmd`, loads the projector on demand and serializes visual work with text generation.
- Visual inference adds native code, memory pressure and hostile-image input. Source dimensions, decoded edge length, image tokens, output tokens and execution time are bounded.
- General chat may be weaker than the prior 350M QAD artifact. Promotion is conditional on security explanation, tool, privacy and physical-device gates.

## Validation gates

- Exact artifact hashes and decoder/projector revision pairing are enforced.
- English and Portuguese security explanations preserve detector facts and limitations.
- Text-only tools meet the expected callback matrix with zero unauthorized callbacks.
- Visual prompt injection produces no tool or action call.
- Visual observations beat deterministic extraction alone on selected screenshot journeys without overriding exact extracted values.
- Physical 4 GB, 6 GB and 8 GB devices meet peak PSS, latency, cancellation, thermal and battery budgets.
- No raw image or extracted secret appears in Room, logs, notifications or exports.

## Sources

- [LiquidAI LFM2.5-VL-450M](https://huggingface.co/LiquidAI/LFM2.5-VL-450M)
- [LiquidAI LFM2.5-VL-450M GGUF](https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF)
- [llama.cpp multimodal runtime](https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md)
