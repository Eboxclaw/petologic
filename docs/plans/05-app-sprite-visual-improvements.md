# App and Sprite visual improvement plan — 9 September 2026

## Reviewed starting point

Chat competes with a 225 dp illustration, a brand banner and a large heading. Green app surfaces, purple Material defaults and navy/gold overlay controls have different visual identities. The Sprite shows literal Markdown and an ambiguous “Open app” action. Writing approval requires leaving the bubble. The full session and permission review already work and remain authoritative.

## Design direction

Use the supplied blue/gold Paladino as the visual anchor: background #0B1422, panel #142238, raised/control surface #20324A, primary gold #E4BB65, text #F2F5FA and secondary text #ADBDD1. Gold identifies primary/selected actions; status always has text, never color alone. Use one palette for Compose and native overlay views. Set Material surface/outline colors explicitly so menus and dialogs no longer inherit purple defaults.

Chat remains the first destination. Keep sessions in the drawer and add a 48 dp new-conversation action in the header. Give the composer and messages priority: smaller empty-state sprite, shorter title/copy, 16 sp message text with 24 sp line height and clear distinction between user and assistant. Put Tiny/Maxx beneath the session heading so modes do not squeeze the title. Keep both mode names; explain local/cloud in nearby text.

Settings starts with companion controls, model setup and providers; move advanced runtime controls to the bottom. Use “Controls” in primary navigation instead of the long “Orchestration” label. Keep Console and all existing controls accessible in this iteration.

Sprite remains a draggable character when collapsed. Expanded: identity/state, latest reply only, composer, then New chat / Full chat. Change Full chat to Open to approve when approval is required. Open the correct session and Chat tab. Disable new-session action during pending/running requests. Reuse the existing original vector icons. In-app FAB menu uses “Message Paladino”, “Save a note”, “Full conversation”; gold primary and navy surface match the bubble. Format paired bold/code emphasis in compact previews without modifying stored messages, executing HTML or loading links.

## Implementation phases

1. **This iteration:** shared palette/theme, Chat layout/empty state, shorter labels, settings order, FAB/menu styling, readable compact preview and explicit approval/full-chat action. Preserve authentication, inference and approval policies.
2. **Next:** full PT/EN UI resource localization (current interface remains consistently English), safe rich-text rendering in full Chat, searchable/session management refinements, optional return-to-bubble after approval, large-font/landscape layout adaptation.
3. **Device validation:** physical-phone TalkBack, 200% font scale, keyboard/rotation, contrast in all states, motion/battery and permission revocation. Repair supplied sprite alpha artifacts separately; this iteration does not edit the original art.

## Exit gates

- Calculate contrast for primary text/control pairs: normal text at least 4.5:1. This is a palette check, not a complete accessibility certification.
- All changed primary touch targets at least 48 dp; original sprite drag/new-chat/full-chat interactions still work.
- Build, JVM tests, lint and focused UI/overlay tests pass; repeat the same five real-model prompts in Chat and Sprite because composer/navigation changed.
- Capture and inspect Chat, Settings/FAB and expanded Sprite on API 35. State exactly which physical/accessibility tests remain pending.
- Store evidence and final status next to this plan. Publish a preview APK only after release validation; do not imply an older website APK contains these changes.

## Research and rationale

- [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/apps): minimum 48 dp touch targets and readable text/contrast. Larger visual controls must not crowd the viewport.
- [Android layouts and navigation](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns): primary destinations, secondary controls and contextual actions. Keep session management separate from the compact overlay.
- [Material floating action button](https://m2.material.io/components/buttons-floating-action-button): promote a primary action and use related contextual actions sparingly. Retain one main FAB with a short labeled menu.

No extra roles, social/premium features, model tuning, OAuth implementation, autonomous permissions or sprite asset regeneration are included in this visual iteration.

## Implementation and verification

Implemented the shared palette/Material surface scheme, smaller Chat empty state, separate mode row, header new-conversation action, selectable 16/24 sp messages, Controls label, settings ordering, matching FAB menu and compact reply emphasis cleanup. Overlay Full chat opens the active session in Chat, even when Settings was the last app screen. Open to approve is contextual and does not bypass approval; new chat is disabled during pending/running work.

Palette contrast measured: text/panel 14.61:1, secondary text/panel 8.34:1, gold/raised 7.17:1, dark text/gold 10.19:1. 36 JVM tests and 9 instrumented tests passed (235.521 seconds), including five real-model prompts per interface, drag/position restoration and new-header/full-chat navigation. Debug build/lint and signed release build passed.

This first visual pass keeps the existing original vector icons and supplied animation files. Full Chat still preserves literal model text; only compact previews simplify paired bold/code emphasis. Full localization, physical-phone testing, 200% text/landscape validation, supplied art alpha repair and richer text rendering remain follow-up work.
