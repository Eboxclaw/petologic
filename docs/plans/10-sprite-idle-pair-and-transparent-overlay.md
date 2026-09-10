# Sprint 1 — Sprite idle pair & fully transparent overlay (2026-09-10)

Sprint 1 of [09-ui-ux-refresh-preplans.md](09-ui-ux-refresh-preplans.md). Goal:
the pet feels bigger and alive in chat, the floating Sprite loops two idle
clips continuously, and neither the bubble nor the badge looks like a window.

## What shipped

1. **Idle pair sequencer.** `IdleCycle` (`:core`, pure Kotlin) defines the loop:
   clip 1 plays once, clip 2 plays two cycles, then the pair repeats. Unit
   tested (order, per-clip cycles, configurable repeat count).
2. **One shared player.** `SpriteAnimator` (app) decodes GIF/animated-WebP via
   `ImageDecoder` and chains idle clips with `repeatCount=0` +
   `OnAnimationEventListener.onAnimationEnd`. Both `PaladinoSprite` (Compose,
   static PNG until the first frame decodes) and `SpriteOverlayService` use it,
   so chat, preview and the floating Sprite stay in sync. The owner gate
   (animate pref, lifecycle, reduced motion, lock screen) decides whether
   anything decodes or plays at all.
3. **Asset slots.** `res/raw/paladino_idle1.*` and `paladino_idle2.*` are the
   drop-in slots for the owner's animated WebPs (256×256, alpha). Until the
   real files arrive both slots temporarily hold a copy of the previous idle
   GIF, so behavior is correct and the swap is a file replace. The old
   `paladino_idle.gif` and the unreferenced 1.4 MB `drawable/paladino.png`
   are removed.
4. **Bigger chat pet.** Empty state 104→**168 dp** (and the radial-gradient
   hero canvas behind it is gone — the pet floats on the page, not in a
   circle); header sprite 48→**64 dp**. FAB sprite keeps the owner-set
   48/64/88 preference.
5. **Fully transparent overlay.** No opaque fills remain:
   - reaction badge: borderless gold text with a soft dark shadow (no fill);
   - expanded panel: the `#080A18` card background, border and elevation are
     gone; title/status/reply render as floating shadowed text;
   - composer: backgroundless input over a 25%-alpha gold hairline underline;
   - "New chat"/"Full chat" rows: plain shadowed text (icon + label, no pill);
   - collapse button: tint-only icon; send stays a small gold circle.
   The home-screen launcher widget keeps its surface for RemoteViews
   legibility (owner may fold it into a later pass).

## Out of scope (later sprints)

Voice (S3), FAB quick actions (S4), settings reorg (S5), onboarding cards
(S6), Koog streaming/events (S7). Widget transparency is deferred with the
owner's agreement. When the real WebP assets land, revisit the Settings copy
"Android launcher widgets do not play this GIF".

## Verification

- `:core:test` (incl. new `IdleCycleTest`), `:app:testDebugUnitTest` (incl.
  updated `PetReactionTest`), `:app:lintDebug`, `assembleDebug`.
- Instrumented `TinyPetIntegrationTest` updated to decode the three animation
  resources (idle1, idle2, thinking) as animated drawables.
- Emulator run with screenshots: chat empty state, chat with the bigger header
  sprite, floating Sprite collapsed + expanded over a wallpaper.

## Follow-ups recorded

- Swap placeholder GIFs for the owner's `paladino_idle1.webp` /
  `paladino_idle2.webp` when provided; re-check loop continuity and size.
- `NEEDS_INPUT`/`BLOCKED` still reuse the idle pair (no dedicated art);
  per [plan 03](03-pet-format-and-authoring.md), missing states are never
  faked — dedicated cycles remain a future authoring handoff.
