# 0.1.4 — Model onboarding, updates and brand

## Verified starting point

The 0.1.3 APK has no update scheduler, installer or Play Store update integration. Public signed APKs update in place; users open the newer APK and approve Android installation. They do not need to uninstall. Older installed versions need one manual download to gain the new Update app button.

## Implemented flows

- Settings: current build version, Update app button and explicit manual-update instructions. Opens the allowlisted official public GitHub releases page, including previews. No hidden checks, downloads, new installer permission or silent installation claim.
- Tiny send: await the initial verified model inventory; check the session's selected model, not just a filename or presence of an embedder. Missing model keeps the message in the session controller and displays a setup card before any inference or user message is committed.
- Setup card: exact model/quantization/size, explicit install, progress, retry status, import/library entry, and explicit Continue conversation after verification. Installing/importing never automatically sends the waiting request. Discard is explicit. The waiting request is in-memory until sent; process termination does not preserve this unsent draft.
- In-app Sprite redirects to setup; floating Sprite shows Set up model and opens the same active Chat. Its composer is disabled while setup is pending.
- Maxx retains its existing credential/consent flow. No automatic switch to cloud.

## Design system extension

Website references are the current `src/styles.css` and `src/routes/__root.tsx`: deep navy, royal blue, gold, cyan, pixel emblem and display/monospace accents. Native palette now uses near-black navy, brighter gold and cyan secondary accents, shared with the overlay. The header uses a vector pixel gem and a monospace wordmark; cards have restrained gold borders and compact corners. Conversation text remains the legible native sans serif. No background image, pixel texture or web font download is applied to conversation text.

Cards have readable body text, explicit action hierarchy, scrollable placement and Android semantics. Update and model setup are separate actions; no inert automatic-update toggle is presented.

## Acceptance

- Missing selected model produces the setup card and no inference/message write.
- Request survives moving to Settings and back; verified availability alone does not send it. Continue sends it once.
- Installed-model chat/tool regressions still pass on both app and Sprite.
- Update action opens only the official release URL and displays installed version.
- Portuguese menus, large text, signed upgrade and website release links verified separately.

## Limits

Manual update browsing is implemented; background version checks and in-app APK downloads remain future work. Public and debug signatures differ. Physical OEM overlay/battery/TalkBack validation remains pending.

[Android PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) documents user-confirmation states. [Installation guide](../INSTALL-ANDROID.md).
