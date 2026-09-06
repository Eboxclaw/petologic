# Petologic Landing Page — Pixel Knight UI

Build the chosen "Pixel Knight UI" direction as the home page of the app.

## What you'll see

A dark navy, retro pixel-game landing page for Petologic — "Tiny intelligence inside your phone" — starring the pixel paladin mascot:

- **Sticky nav**: PETOLOGIC logo chip (royal blue + gold pixel border), links (The familiar / Security / Tools), gold ENLIST button.
- **Hero**: left side — "Protocol initialized" chip, big Silkscreen pixel headline with gold "intelligence", short pitch, INSTALL ON ANDROID button; right side — the paladin sprite (your uploaded 0xPaladino art) floating inside a chunky black-bordered frame with dithered background, "LVL 99" badge, gold corner square, soft blue glow.
- **Features**: three module cards on a black-bordered band — LOCAL BRAIN (royal), TOOL USE (gold), CLOUD RELAY (cyan) — each with a `[ MODULE_XX ]` label and a segmented progress-bar accent.
- **Bottom CTA**: "Waiting for command..." pulse, "Ready to deploy your familiar?", email input + JOIN WAITLIST button in a pixel-framed bar, and muted partner names (OpenRouter, Anthropic, OpenAI, Gemini).
- **Footer**: copyright line + Twitter/Discord/GitHub links.
- **Atmosphere**: subtle animated scanline overlay, stepped retro motion, custom pixel-border box-shadows, dithered dot patterns.

## Technical details

- `src/styles.css`: add theme tokens (navy #0a0c14, royal #2a4ed6, gold #f5d547, cyan #00f7ff, card #151a26) as oklch, fonts (Silkscreen display, Inter Tight body, JetBrains Mono), `@utility` for pixel-border / pixel-border-gold / dither-pattern, and float/scanline keyframes.
- `src/routes/__root.tsx`: load the three Google Fonts via `<link>` tags; set proper default meta (replace "Lovable App" placeholders).
- `src/routes/index.tsx`: replace the placeholder with the full landing page; add route `head()` with unique title "Petologic — Tiny Intelligence Inside Your Phone", description, og/twitter tags.
- Mascot: the uploaded `0xpaladino.png` becomes a CDN asset (`lovable-assets`) and is used in the hero frame — no image generation needed since you provided the real sprite.
- Email input is visual only (front-end demo state with a "joined" confirmation); no backend storage since Lovable Cloud is disabled.
- Mobile-responsive: hero stacks, nav links collapse behind the ENLIST button.
