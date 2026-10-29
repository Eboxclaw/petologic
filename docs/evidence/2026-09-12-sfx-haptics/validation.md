# Light SFX + haptics (plan 14) — evidence (2026-09-12)

## What shipped

Four synthesized WAVs in res/raw (water drop ×2, 8-bit blip, two-note confirm chirp; 28 KB total, license-free, generated with Python stdlib). A pure `FeedbackEvent`/`FeedbackSound` vocabulary with gating functions (`shouldPlay` = user toggle + ringer normal + not battery saver + non-silent event; `shouldBuzz` = user toggle + event has effect), an app-scoped `FeedbackPlayer` (SoundPool maxStreams 2, USAGE_ASSISTANCE_SONIFICATION, volume 0.25; haptics via `View.performHapticFeedback` — no VIBRATE permission, respects the system setting), and quiet hooks: message sent (in-app, bubble, overlay), reply finished, approval asked/approved/declined (action + Maxx), error dialog, sprite taps, widget-open. Skill wake is deliberately silent. Settings: Sounds/Vibration switches inside Sprite & Widget controls (default ON), stored in `tinypet_paladino`.

## Tests

- `FeedbackMappingTest` (4 tests): mapping completeness (only APPROVAL_NO is silent), gating matrix (silent ringer, battery saver, user toggle), haptics events (REPLY_DONE deliberately still), platform-backed haptic constants. Total 73 JVM tests green.
- Emulator: sounds/vibration switches render and persist in Sprite & Widget; send path exercises the player without errors (logcat clean, process alive). Screenshot in this folder.

## Battery etiquette (research-backed, see docs/reviews/2026-09-12-research-notes.md)

Sounds pause in silent/vibrate ringer and in battery saver; no sound plays from background services unprompted; no new permissions; no wake locks introduced.
