# Plan 14 · Light SFX (water + 8-bit) and haptics

User request 2026-09-12: subtle water-based sounds and 8-bit blips with light haptics — "not too much but give some sense to the user". Companion research answers (graph RAG, vector store, MiniLM, sub-agents, tool weight, battery) are recorded in `docs/reviews/2026-09-12-research-notes.md`.

## Design

- **Assets:** 4 synthesized mono WAVs in `res/raw` (~0.1–0.25 s, 22.05 kHz, ≤16 KB total set): `fx_drop` (water drop, sine sweep down + decay), `fx_plop` (deeper drop), `fx_blip` (8-bit square two-step), `fx_confirm` (8-bit two-note chirp). Generated with Python stdlib; no third-party audio, no new dependency.
- **Pure mapping:** `FeedbackEvent` (event → raw resource + `HapticFeedbackConstants` effect) and gating (`shouldPlay(soundOn, ringerNormal, powerSave, event)`, `shouldBuzz(hapticsOn, event)`) in one JVM-testable file.
- **Player:** app-scoped `FeedbackPlayer` — lazy `SoundPool` (maxStreams 2, `USAGE_ASSISTANCE_SONIFICATION`, volume 0.25), `buzz(view, event)` via `View.performHapticFeedback` (no VIBRATE permission; respects the system haptic setting). Sounds suppressed when ringer is silent/vibrate or battery saver is on.
- **Hooks (few, quiet):** message sent (in-app + overlay) → drop; reply finished → plop; approval requested → blip; approved/declined → confirm/REJECT haptic; error → plop + REJECT; sprite tap → drop; widget-open → blip. Skill wake deliberately silent (too chatty).
- **Settings:** `sound`/`haptics` booleans (default ON) in the existing `tinypet_paladino` prefs, two `PetSwitch` rows inside `TinyPetSettings`.

## Verification

`FeedbackMappingTest` (pure): mapping completeness + gating matrix. Emulator smoke: toggle sounds/vibration in Settings, exercise send/approve/tap paths, no crashes. Evidence screenshots + progress/VALIDATION entries.
