# 0.1.3 preview validation — 2026-09-10

- 36 JVM tests: zero failures/errors. App and core Gradle test results.
- Debug/release ARM64 builds, Android lint and Bun website build: passed.
- Final English instrumentation: 8/8 passed in 91.534 s. Includes five UI regressions, paired real-model Chat/Sprite test, overlay lifecycle/drag, Markdown resource/render test.
- Portuguese flow: passed with font scale 1.0; repeated at 2.0 after stacking overlay actions vertically, passed (10.134 s). Test covers fresh conversation, Settings, FAB menu, floating overlay above Home and Full chat navigation.
- 228 resource keys in each language. A fixture verifies positional/dynamic translation, emphasis spans, image alt text without remote drawables. Pure URL tests reject non-web/credentialed links.
- Initial English run failed because its assertion expected Android StyleSpan instead of Markwon StrongEmphasisSpan. Corrected assertion; final run passed. Initial PT test selector did not traverse merged icon semantics; corrected selector.
- Public signed APK updated over 0.1.2 on separate API35 ARM64 release emulator without uninstall. Conversation 2 remained; switching locale displayed Conversa 2 and Definições. VersionCode 4/versionName 0.1.3-preview confirmed.
- Final APK: 97,641,994 bytes. SHA-256 fa1f60bd00b86a418bccd1b255439a820da4da4ccab72528c9ebc2e764550024. Certificate matches previous release (see signature.txt).

Real-model test uses one fresh session per surface, same five prompts with history preserved within each group. Transcript records actual tool events and approvals, not only generated tool-like text. Successful bounded repairs do not establish general model reliability; held-out evaluation remains necessary.

No physical phone, TalkBack, thermal/battery or live cloud-account validation was performed. Large-font navigation captions truncate visually but retain accessibility names. Native Markdown is tested; long tables/code may need further horizontal navigation polish. OAuth remains unimplemented. Supplied animation assets were retained unchanged.
