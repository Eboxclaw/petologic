# ADR-0002: Two-model local security Paladino

**Status:** Superseded by ADR-0003

**Date:** 2026-09-21

## Context

Petologic is becoming an Android security product whose character and interface are 0xPaladino. The product needs local conversation, multilingual semantic retrieval and deterministic security detection without making a language model the source of truth. Earlier plans exposed several local generative models, a 230M worker and a small MiniLM vector model. That creates product confusion, duplicate memory pressure and more runtime paths to validate.

## Decision

The on-device product has exactly two Liquid AI models:

1. **LFM2.5-350M** is the only model that speaks. It handles chat, explanation, summarization and bounded tool selection. It cannot create detector verdicts, approve actions or silently dismiss threats.
2. **LFM2.5-Embedding-350M** is the only vector model. It creates normalized 1024-dimensional vectors for private retrieval, related-finding recall, clustering and later similarity-based candidate generation. It never speaks or calls tools.

Deterministic code remains the security authority. Exact hashes, signed feeds, native rules, Android system signals and bounded heuristics create findings. The speaking model receives redacted findings and limitations. Vector similarity may retrieve or rank candidates, but cannot independently label content malicious or trigger enforcement.

```text
Android signal / user-selected input
                |
        bounded security engines
       hashes + rules + heuristics
                |
         canonical redacted finding
            /                 \
  dense vector + search       LFM2.5-350M chat
  related local evidence      explanation and guidance
            \                 /
             user-visible result
                     |
        explicit approval for any action
```

## Options considered

| Option | Runtime cost | Product clarity | Retrieval | Decision |
|---|---:|---:|---:|---|
| 230M router + 350M chat + small embedder | Three model paths | Low | Basic | Rejected |
| 350M chat + 350M dense vectors | Two model paths | High | Compact single-vector index | Accepted |
| 350M chat + 350M ColBERT | Two model paths | High | Higher retrieval quality, much larger per-token index | Deferred |
| One generative model for chat and pseudo-embeddings | One model path | Medium | Poorly defined and hard to calibrate | Rejected |

The dense embedding model is preferred over ColBERT for the first mobile index because it stores one vector per item and uses ordinary cosine similarity. ColBERT remains a benchmark candidate only if dense retrieval misses security recall targets within the phone memory budget.

## Consequences

- Model setup, copy and testing can describe two roles without the word “encoder” in product UI.
- Existing 230M, 2.6B, vision and MiniLM choices leave the local catalog. Stored sessions automatically return to the 350M speaking model.
- Existing MiniLM vectors are ignored because the model ID changes; records are rebuilt lazily with the Liquid vector model.
- Two loaded 350M models may exceed the comfortable memory budget on low-end phones. The runtime must serialize heavy work, unload on memory pressure and benchmark keeping both models resident versus load-on-demand.
- The embedding model’s `query: ` and `document: ` prefixes are part of the retrieval contract and must be tested.

## Release gates

- Real ARM64 device test proves 1024-dimensional normalized vectors and Portuguese/English paraphrase ranking.
- p95 query-vector plus search latency and peak PSS meet device-tier budgets defined in the product plan.
- Security similarity is evaluated on held-out malicious and benign examples. Similarity alone never emits a final malware verdict.
- The model files, hashes, revisions and LFM Open License obligations are reviewed before public distribution.

## Sources

- [LiquidAI LFM2.5-Embedding-350M](https://huggingface.co/LiquidAI/LFM2.5-Embedding-350M)
- [LiquidAI LFM2.5-Embedding-350M GGUF](https://huggingface.co/LiquidAI/LFM2.5-Embedding-350M-GGUF)
