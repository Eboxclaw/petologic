# Petologic UI handoff — sprite, FAB, option menu, and bubbles (v1)

## Product intent

The persistent sprite is Petologic's friendly AI guide. It should help a pet parent take the next useful action without taking over the screen. The primary interaction is one-handed: tap the floating action button (FAB), choose an action, and get a short contextual response from the sprite.

This v1 assumes a mobile-first app with a pet selected. The UI must work without a pet selected by routing pet-dependent actions to pet setup.

## Core layout

```text
+------------------------------------------------+
| Header / current pet                            |
|                                                |
| Main screen content                             |
|                                                |
|                          [short sprite bubble] |
|                               (sprite)         |
|                                      [+] FAB    |
+------------------------------------------------+
```

- Place the FAB at the bottom-right, 16 px from the safe-area edge; minimum tap target: 48 x 48 px.
- Keep the sprite 12 px above and 8–12 px to the left of the FAB. It never blocks bottom navigation or primary screen content.
- The sprite is a tappable visual affordance, but the FAB remains the predictable way to open actions.
- On scroll, the FAB and sprite remain fixed. A screen-level modal or keyboard hides the sprite and FAB so they cannot overlap form controls.

## Sprite behavior

### States

| State | Visual | Trigger | Interaction |
| --- | --- | --- | --- |
| Idle | Subtle breathing/blink loop | Default | Tap sprite opens the current message bubble. |
| Nudge | One short wave / bounce | A useful but non-urgent prompt exists | Show one bubble; do not repeat until the cooldown expires. |
| Listening | Gentle pulse | Voice/input capture is active | FAB is disabled; tap stop/cancel ends capture. |
| Thinking | Small looping dots | AI request pending | Bubble says `Thinking…`; no progress percentage. |
| Success | Brief happy bounce | Action completed | Show confirmation bubble, then return to idle. |
| Alert | Calm, non-alarming accent | Reminder or incomplete safety-relevant answer | Bubble uses clear copy and a direct CTA. Never present diagnosis as fact. |

### Motion and accessibility

- Animate only opacity, transform, and scale. Keep idle animation low-energy and under 3 seconds per cycle.
- Respect `prefers-reduced-motion`: render a static sprite; retain state changes through icon/color/text.
- The sprite needs an accessible label that reflects state, e.g. `Petologic assistant, new suggestion available`.
- Do not use color or motion as the sole way to communicate a status.

## FAB interactions

### Collapsed FAB

- Default: circular 56 px button with a plus icon and accessible name `Quick actions`.
- Tap opens the menu. The plus rotates to an X (180 degrees, 160–200 ms) and the FAB keeps its position.
- Tap outside, press Escape/back, navigate, or select an action closes it.
- The FAB has exactly one visible primary affordance at rest; no persistent label in v1.

### Expanded action menu

Show a vertical action stack above the FAB. Each row has a 48 px icon button plus a text label in a pill/button; rows are 56 px apart and appear bottom-to-top with a 40–60 ms stagger. The stack must flip to the left or upward if the safe area would be exceeded.

| Priority | Action | Label | Result |
| --- | --- | --- | --- |
| P0 | Ask Petologic | `Ask a question` | Opens the AI chat composer with selected pet context attached. |
| P0 | Add observation | `Log an observation` | Opens a compact sheet: photo optional, symptom/behavior, timestamp, notes. |
| P0 | Quick care | `Log care` | Opens a sheet with food, water, walk/activity, medication, and custom event. |
| P1 | Scan | `Check with camera` | Opens camera/media picker; explicit consent/copy must state that results are informational, not a veterinary diagnosis. |

Implementation rule: action configuration comes from a typed data array (`id`, `label`, `icon`, `enabled`, `requiresPet`, `handler`) rather than branching UI markup. This keeps future actions additive.

### Availability rules

- If no pet is selected, `Ask a question`, `Log an observation`, and `Log care` first open `Choose or add a pet`.
- If camera permission is denied, `Check with camera` opens the system-permission recovery explanation; it must not silently fail.
- While an action is submitting, retain the sheet, disable its submit control, and prevent a second FAB action from firing.

## Bubble system

### Anatomy

Each bubble has: optional status icon, 1–2 sentence message, optional single CTA, close button, and a tail pointing toward the sprite. Maximum width: 280 px on phone; keep at least 16 px screen margin. Avoid placing the bubble over a destructive or required control.

### Bubble types

| Type | Use | Example copy | Dismissal |
| --- | --- | --- | --- |
| Welcome | First meaningful visit | `Hi, I’m here to help with Luna’s care.` | Close; do not show again after dismissal. |
| Contextual nudge | Timely, low urgency | `Luna’s walk hasn’t been logged today.` | Close, CTA, or auto-dismiss after 8 s. |
| Action confirmation | Successful user action | `Observation saved to Luna’s timeline.` | Auto-dismiss after 4 s; screen-reader announcement persists. |
| AI status | Request lifecycle | `I’m checking Luna’s recent notes…` | Replace with response; user can cancel request. |
| Safety escalation | Possible urgent concern | `This may need a veterinarian’s advice today.` | Requires explicit close; CTA may link to emergency guidance/contact flow. |

### Frequency and priority

- Show at most one bubble at a time.
- Safety escalation > action error > submission status > explicit sprite tap > contextual nudge > welcome.
- Do not show a nudge while a sheet, conversation composer, system permission prompt, onboarding step, or keyboard is open.
- Contextual nudges: maximum one per session, 24-hour cooldown per message key, and never within 10 minutes of a user dismissal.
- Persist dismissal and cooldown state per user/pet in local storage/server preference; do not use only component state.

## Interaction flows

### 1. Quick action

1. User taps FAB.
2. Menu expands; background remains interactive but is visually de-emphasized with a light scrim only if needed for contrast.
3. User taps an option.
4. Menu closes immediately; selected bottom sheet or composer opens.
5. On successful save, dismiss sheet and show one confirmation bubble.
6. On failure, keep inputs intact and show inline error. Do not rely on a transient bubble for errors.

### 2. Sprite conversation

1. User taps sprite or active bubble.
2. If there is a contextual prompt, reveal its CTA; otherwise open the Ask composer.
3. The composer must visibly show which pet is in context and allow changing it before sending.
4. While waiting, sprite switches to Thinking.
5. Response appears in chat; any medical uncertainty uses the safety pattern, never false reassurance.

### 3. Nudge lifecycle

1. App evaluates eligible prompts after dashboard content is ready.
2. If an eligible prompt wins priority and cooldown rules, sprite enters Nudge and a bubble appears.
3. CTA opens the relevant destination; close records dismissal; timeout records impression only.
4. On next render, no duplicate bubble may appear for that key inside its cooldown window.

## Visual rules

- Keep the sprite visually secondary to task content: its idle footprint should be approximately 56–72 px square.
- Use the app's existing primary color for the FAB; use neutral surfaces for menu labels and bubbles.
- Sprite bubble copy is warm, concise, and specific to pet/action. No baby-talk, guilt, or unsupported medical claims.
- Use 8 px spacing increments, 12–16 px corner radius for bubbles/sheets, and 16 px body text spacing around interactive items.
- Maintain contrast at WCAG AA minimum; every icon-only control has a text alternative.

## Engineering acceptance checklist

- [ ] FAB, sprite, and every menu option have at least a 44 x 44 px interactive target.
- [ ] FAB menu is keyboard navigable; focus moves to the first action on open and returns to FAB on close.
- [ ] Expanded menu closes on outside tap, Escape/back, route change, and action selection.
- [ ] Only one bubble can be mounted/announced at a time, according to the priority rules.
- [ ] Bubble eligibility/dismissal cooldown survives app restart and is scoped to the correct pet where applicable.
- [ ] Loading, permission-denied, offline, and failed-save states have visible non-transient handling.
- [ ] Reduced-motion mode removes looping and entrance motion without removing status information.
- [ ] Mobile safe areas, keyboard overlap, small screens, and RTL layout are tested.
- [ ] No pet-care content presents the AI or scan as a diagnosis; urgent language routes toward professional care.

## Deliberate v1 exclusions

- No free-dragging/repositioning of the sprite.
- No multiple simultaneous assistants, chat heads, or stacked bubbles.
- No gamification levels, rewards, or emotional-pressure streaks.
- No autonomous health alerts without a defined, reviewable eligibility rule.
- No diagnostic claim from camera or AI output.

## Decisions needed before build

1. Final sprite asset set: idle, nudge, thinking, success, and alert (static fallback required for each).
2. Which Quick care event types are in the first release.
3. Whether `Check with camera` ships in v1 or remains a P1 feature behind an availability flag.
4. The clinical/safety review owner and exact escalation copy/route by launch market.