# TinyPet 01 — Paladino Sprite and Widget

This iteration adds the user's supplied `paladino_idle.gif` and `0xpaladino.png` unchanged. Paladino is the first TinyPet presentation and the existing `paladino` role; the catalogue currently contains exactly one entry. Visual preferences do not grant tools, spawn agents or download another model.

## Implemented

- Settings → Sprite & Widget: animated preview, visibility, animation toggle, compact Sprite size, widget caption and target conversation.
- Android native GIF decoding; static PNG fallback; animation stops when its screen is not resumed and obeys system animation disablement when activated.
- Launcher pin request with fallback instructions. All Paladino widget instances currently share settings. The home-screen widget is static and opens the selected non-archived session, or the active conversation if unavailable.
- In-app Sprite quick actions on control-center screens: ask, remember, open full conversation. Hide the floating controls under keyboard or modal; dropdown dismisses on outside/back.
- Compact Sprite chat: one current reply, composer, Stop, full conversation and **New conversation**. Long replies remain available in the app's full transcript.
- Bubble messages execute through the same active SessionController and persist in the same Room messages table. No separate Sprite transcript. Transient UI status such as “Thinking” is not saved as an invented assistant message.
- Creating a session in the bubble switches its context and draft to that session and adds it to the app drawer. Existing history remains in its original session.
- Existing write/cloud approval dialogs remain authoritative. Closing the bubble does not grant or revoke any capability.

## Tests / next gates

Device tests cover preference reconstruction, Android decoding of the supplied animated GIF, manager UI and a bubble → new conversation → deterministic agent response → full app transcript journey. These complement existing real offline LFM, encoder, session and tool-loop tests. The bubble test uses a deterministic note search; it is not evidence of a live cloud call from the bubble.

Next: verify actual launcher placement and click target; distinct widget-instance settings if needed; small-screen/TalkBack/RTL/focus restoration; model-generated replies through the bubble; lifecycle and reduced-motion changes while visible; persisted nudge eligibility/cooldowns before adding proactive bubbles; overlay lifecycle and permission-revocation coverage. An opt-in Android foreground service now provides a draw-over-other-apps Sprite with compact chat, New chat, Open app and Stop controls. The launcher widget does not perform background inference.

Do not display separate pets or role downloads yet. Keep role manifests, sessions and visuals separate so a later role can reuse the same local/cloud runtime through explicit permissions.

Android API references: [AnimatedImageDrawable](https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable), [widget pinning](https://developer.android.com/develop/ui/views/appwidgets/discoverability).

See [pet format and authoring](03-pet-format-and-authoring.md) for the researched asset boundary, reaction mapping and missing animation cycles.
