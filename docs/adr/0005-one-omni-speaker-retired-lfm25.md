# ADR-0005: One LFM3-Omni speaker and retired LFM2.5 models

**Status:** Accepted

**Date:** 2026-11-10

## Context

ADR-0004 introduced the LFM3 pack alongside the LFM2.5 duo (VL-450M speaker + mmproj,
Embedding-350M vectors), keeping the VL pair as the mandatory default and the visual engine for
every visual turn. That meant two speakers, two projector pairings, per-speaker visual plumbing
and up to ~3.5 GB of downloads. Product decision: the LFM2.5 generation is no longer needed with
the LFM3 models available.

The pinned llama.cpp b11960 mtmd natively supports the LFM3-Omni projector (`lfm3vl` projector
type, n_embd pairing validated at load), so making LFM3-Omni-2B the only speaker is an
app-layer change only. LFM3 ships no embedding model.

## Decision

1. **LFM3-Omni-2B Q4_0 (1.0 GB) is the one local speaker** — chat and, with its own 516 MB Q8_0
   projector, visual inspection. `ModelCatalog.SPEAKER_ID` now points at it; the VL-450M speaker,
   its 103 MB projector and their artifacts are removed from the catalog and every download,
   selection and hardcheck path.
2. **Visual turns pair the session speaker with its own projector** via
   `ModelCatalog.projectorFor`/`visualSpeakerOrDefault`. With a single speaker this collapses to
   the Omni pair; the pairing contract stays in place for future speakers.
3. **LFM2.5-Embedding-350M is retired.** The vector subsystem (Room + AppSearch, outbox,
   knowledge/finding namespaces) is kept intact but dormant: `LocalVectorMemory.available` is
   false without a catalog artifact, semantic search degrades to the existing lexical path, the
   semantic install UI is hidden, and the retrieval benchmark lane self-skips. Security knowledge
   packs can still ship records over the signed channel; only their vector indexing waits for a
   future Liquid embedding model.
4. **d1-2B stays the decision router** (it outscores d1-Omni-2B on text decision tasks). Audio
   input through d1-Omni-2B (mtmd `d1omni_a` is supported in the pinned runtime) remains a later
   phase requiring `RECORD_AUDIO`, PCM capture and a new JNI audio-bitmap path.
5. The signed channel can now also deliver **YARA rule packs** (`YaraRulesVerifier`, ≤256 sources,
   ≤8 MiB compiled-source budget, version- and expiry-bound), flowing to the isolated scanner as
   a read-only descriptor beside the hash pack; a manifest that drops rules retires the installed
   set.

## Consequences

- Downloads shrink from up to ~3.5 GB to ~2.6 GB when everything is installed; one model resident
  serves chat and vision, so the previous VL↔Omni slot-swap churn disappears.
- Existing installs that only had VL-450M must download the 1.0 GB Omni model after update; the
  app shows setup status and keeps deterministic checks working without any model.
- Semantic memory ("find by paraphrase", security-knowledge similarity) is unavailable until an
  embedding artifact is pinned again; the code path and schema stay ready.
- Chat quality/explanation fidelity must be re-validated on Omni (the VL-450M explanation lane was
  the previous baseline) — physical-device gates from ADR-0004 still apply.
