# Model and Sprite verification — 2026-09-07

Environment: Paladino_API35 ARM64 emulator, Android API 35, 4 GB guest RAM, host Apple Silicon. APK rebuilt and installed from the feature branch working tree. Emulator Wi-Fi and mobile data are disabled. These are emulator results, not physical phone benchmarks.

## Model presence and real app wiring

Recomputed inside the app sandbox: files/models/LFM2.5-350M-Q4_K_M.gguf matches pinned SHA-256 7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4. Previously recorded size: 229312224 bytes. No replacement model or cloud credential was used for these tests.

Actual Compose Chat UI, new independent session:

- User: Reply with exactly: Blue shield.
- Assistant: Blue shield
- User: What two words did I ask you to say?
- Assistant: Blue shield

Actual TYPE_APPLICATION_OVERLAY UI over the Android launcher, new session created with New chat:

- User: Reply with exactly: Golden star.
- Assistant: Golden star

Tests check persisted assistant rows, not echoed user text or just the presence of a model file. Screenshots: [floating Sprite](2026-09-07-overlay-home.png), [overlay reply](2026-09-07-overlay-reply.png). Both real transcripts remained in the same Room session store used by the app.

## Diagnosed performance problem

The debug native build had symbols but no optimization flags for llama/ggml CPU kernels. JNI diagnostic timestamps isolated delay to prompt evaluation, not model loading. Before optimization, the two app prompts (256 and 276 tokens) took 31.637 s and 41.201 s to prefill. Replies were correct in this diagnostic run; older runs timed out. Those older timeout causes are not individually proven.

CMake now applies -O2 to the vendored runtime targets in Debug only, retaining symbols and assertions and leaving the JNI bridge debuggable. Generated Ninja flags confirm -O2 is present. On the same app test after this change, prefill took 0.839 s and 1.088 s; two-token decode took 19 ms and 29 ms. Overlay prompt prefill took 0.816 s. These are single-run measurements, not latency percentiles or a phone performance guarantee.

No speculative model replacement, thread-count change or cancellation redesign was needed. Logs contain stage timings/counts, not user prompts.

## Validation

- 19 JVM tests passed (9 core, 10 app, including 3 reaction precedence/fallback tests).
- Full device lane: 15 tests passed in 18.211 s; realModel=true and overlay=true. Includes actual Chat follow-up, actual overlay response, GIF decoding, UI controls, session isolation and memory flows. See [runner output](2026-09-07-device-tests.txt).
- App and instrumentation APK builds passed; lint passed with no errors (warnings remain).
- Earlier combined lane had one memory UI timeout at confirmation-result visibility. Each general UI test now explicitly creates a fresh conversation through the drawer, preventing inherited conversation state; the entire lane then passed. This change establishes test isolation, not proof that every long-history scrolling case is resolved.

## Still open

Physical phone speed/battery/thermal and 16 KB page validation; repeated cancellation/low-memory stress; overlay rotation, drag position, permission revocation and lock-screen matrix; live changes to animation preferences while an overlay is already visible; launcher widget round trip; actual cloud provider test; complete animation cycles and validated OpenPets package export. No main push, public APK publication or production readiness claim.
