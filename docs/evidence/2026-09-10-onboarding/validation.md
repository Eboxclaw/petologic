# Validation — 2026-09-10

36 JVM tests passed, zero failures/errors. Android debug/release ARM64 builds, lint and Bun build passed.

- regression.txt: 9 passed, includes normal chat, actual model-selected note tools, same five requests in isolated Chat/Sprite sessions and overlay lifecycle.
- onboarding.txt: 3 passed on final source. Missing catalog entry (files preserved) blocks inference; request survives Settings; availability alone does not send; explicit Continue saves one user message and produces a real answer. Floating Sprite opens the same setup card with its pending request. Update action resolves to exactly the public releases URL.
- pt.txt and pt-large.txt: Portuguese flow passed with fontScale 1.0 and 2.0. Original locale/font restored afterward.
- Signed release installed over 0.1.3 on separate API35 emulator; Conversa 2 still present. New clean conversation with actually absent model produced the Portuguese card and retained Ola Paladino. Settings showed Atualizar app and installed 0.1.4-preview. Screenshots included.
- Signature matches prior public APK. Final APK 97,678,858 bytes; hash in release notes. No device data/model files were removed.

No physical-device, TalkBack, OEM energy or live cloud account claim. The update button is a browser handoff, not a background updater. Waiting model-setup drafts are in-memory, not process-death durable. Palette/vector changes reuse original animation files without background cleanup.
