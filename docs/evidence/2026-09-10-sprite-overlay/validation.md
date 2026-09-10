# Sprint 1 validation — Sprite idle pair & fully transparent overlay (2026-09-10)

Sprint plan: [plans/10-sprite-idle-pair-and-transparent-overlay.md](../../plans/10-sprite-idle-pair-and-transparent-overlay.md).
Emulator: `Paladino_API35` (sdk_gphone64_arm64, API 35), debug build `0.1.5-preview`.

## Automated

- `:core:test` + `:app:testDebugUnitTest`: **42 tests, 0 failures** — includes the
  new `IdleCycleTest` (loop order `0,1,1` with wrap-around, per-clip cycle
  counting, configurable repeat count) and the updated `PetReactionTest`
  (idle pair slots, thinking mapping).
- `:app:lintDebug`: passes. `:app:assembleDebug`: passes (arm64+x86_64).
- Instrumented `TinyPetIntegrationTest` (`am instrument`, focused run): **OK
  (2 tests)** — `paladino_idle1`, `paladino_idle2` and `paladino_thinking` all
  decode as `AnimatedImageDrawable` with positive intrinsic size.

## Visual (screenshots in this folder)

- `chat-empty-state.png` — pet at **168 dp** in the chat empty state with no
  backdrop behind it (the old 112 dp radial-gradient hero canvas is gone);
  header sprite measured at exactly 64 dp (168 px at 2.625 density) via UI
  hierarchy bounds.
- `overlay-collapsed.png` — the floating Sprite over the launcher wallpaper
  with **no box around it**; pixels sampled around the sprite equal the
  wallpaper (≈ rgb 15,15,25) with no rectangle edge.
- `overlay-expanded.png` — expanded bubble with **no opaque card**: floating
  title/status/reply text with dark shadows, backgroundless composer over the
  25%-alpha gold hairline, gold circular send, tint-only collapse and plain
  action labels.
- Animation playback proven by pixel-diffing two screenshots 0.45 s apart over
  the sprite bounds: 1,062 changed pixels (static art would be 0).

## Asset transparency fix (found during verification)

The previous `paladino_idle.gif` declared a transparent color index per frame,
but its decoded frames are **opaque black** at the background (verified by
decoding frame 1: corners `rgb(0,0,0) a=255`) — the long-pending "sprite alpha
cleanup". The static PNG was already fully transparent. Both placeholder slots
were rebuilt as **lossless animated WebP with real alpha** (8 frames, 750 ms
per frame, loop): frames extracted with ffmpeg, border-connected background
removed by flood-fill (≈36k px per frame; interior dark outlines preserved),
encoded with libwebp via Pillow. Each file is 139 KB (was 175 KB GIF). These
are still placeholders: the owner's authored `paladino_idle1.webp` /
`paladino_idle2.webp` replace them file-for-file.

## Limits

- Idle clips are identical placeholders, so the 1+2 cycle chain is proven by
  `IdleCycleTest` logic, not by visible clip differences yet.
- Verification ran on an emulator with animator duration scale restored to 1;
  at scale 0 the app correctly shows the static art (reduced-motion path).
- The expanded bubble over bright launcher icons can reduce text contrast —
  inherent to the owner-selected fully transparent style; shadows mitigate.
- Physical-device checks and TalkBack pass remain open release gates.
