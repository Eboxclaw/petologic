# Google Play package-visibility declaration draft

## Core purpose

Paladino is an Android antivirus and anti-scam application. Its installed-app protection must identify user-installed packages so it can check newly installed and updated apps and periodically reconcile apps that changed while Paladino was inactive. Without broad package visibility, this core antivirus feature cannot inspect the complete user-app inventory and would silently omit apps.

## User-facing use

Installed-app protection is off by default. Protection setup presents this disclosure before opt-in:

> When enabled, Paladino reads the list of installed apps, app security metadata and installed APK bytes after installs or updates and once a day. The inventory stays on this phone. Optional reputation sends only an 8-character hash prefix.

The user can turn the feature off at any time. Turning it off cancels scheduled reconciliation and package-event scans. A one-time manual check is available as an explicit user action and does not enable background monitoring.

## Data handling

- Package inventory and app metadata are processed locally and are not sold, shared for advertising, or uploaded for analytics.
- Installed APKs are opened read-only and scanned in an isolated Android process. Their code is never executed by Paladino.
- Findings store redacted detector evidence and a version fingerprint. They do not store APK bytes or a package inventory.
- Optional reputation requests contain one 8-hex-character SHA-256 prefix. They exclude the full hash, package name, filename, signing certificate, device identifier, private content, and model output.
- Liquid AI models do not receive package inventory authority and cannot create or change a security verdict.

## Play review demonstration

1. Open Paladino → Protection → Protection setup.
2. Show that Installed-app protection is off and read the disclosure.
3. Enable it and show the scheduled initial check.
4. Install or update an ordinary benign test app and show the resulting Activity entry.
5. Disable installed-app protection and show that background checks are off.
6. Show the Play build manifest: `QUERY_ALL_PACKAGES` is present and `REQUEST_INSTALL_PACKAGES` is absent.

This draft must be reconciled with the final Play listing, privacy policy, production endpoint behavior, and Play Console form before submission.
