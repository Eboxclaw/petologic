# ADR 0001: Native Paladino scaffold and honest capability boundaries

Accepted for this development preview, 2026-09-06.

Build the app under `android/` and retain the existing Lovable website. Kotlin 2.3.10, AGP 8.12.3, Gradle 8.14.3 and Koog 1.2.0 compile together. LFM2.5-350M Q4_K_M runs through pinned llama.cpp JNI in process, with a single serialized generation owner. No local HTTP server or extra model persona is introduced.

The Koog adapter owns a bounded generation turn and shares the Paladino context across Tiny and Maxx. Deterministic note commands execute via persisted approval proposals and Room transactions; generative outputs do not yet invoke tools. This avoids claiming structured-tool reliability before implementing and evaluating that path.

AppSearch local storage provides lexical indexing. Room is canonical and an indexing outbox allows repair. A separate 23 MB quantized MiniLM ONNX encoder provides real semantic vectors. The first preview explicitly limits semantic search to the latest 200 notes and English-oriented behavior; it does not claim the plan’s 10k/multilingual gates. A second candidate and full retrieval benchmark remain NEXT-03.

The graph schema stores ownership edges. A pure Kotlin bounded traversal helper exists and is tested; semantic knowledge graph extraction and ContextBroker integration remain NEXT-04. Cloud summarization is an interface direction, not an exposed feature.

OpenRouter uses a user key, Keystore encryption and exact-payload consent. We use a narrow HTTP streaming adapter under Koog so payload control and transport failures are directly testable. No credential refresh protocol, OAuth callback assumptions, cost cap or live-provider success is invented. OAuth is deferred until real callback infrastructure is available, as allowed by the plan.

## Sources and pinned artifacts

- Koog [1.2.0 source](https://github.com/JetBrains/koog/tree/1.2.0), inspected for actual Android and executor APIs.
- llama.cpp revision `73a43d1f69345aee8bb186ef4b3172cef892f2e5`.
- [LFM2.5-350M GGUF](https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF), revision `9969000761ce34de907bf20017cbfc3d52d6eaf9`, Q4_K_M SHA-256 `7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4`.
- [MiniLM](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2), revision `1110a243fdf4706b3f48f1d95db1a4f5529b4d41`, ARM int8 ONNX SHA-256 `4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474`.
- [OpenRouter OAuth documentation](https://openrouter.ai/docs/guides/overview/auth/oauth) informs the deferred code-to-key PKCE connection design.

Published vendor speed figures are not treated as application performance measurements.
