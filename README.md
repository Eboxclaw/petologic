# Petologic: Own your Paladino

An Android proof of concept: one app combining an on-device AI agent (0xPaladino) with antivirus, antimalware and anti spam checks, deterministic open security engines and local Liquid AI chat.

## Native Android app

The 0xPaladino Android preview lives in [`android/`](android/README.md). It is a Kotlin/Jetpack Compose app with local file, APK, message and link checks; LFM3-Omni-2B for security chat and explicit image inspection; optional d1-2B for decision offers; and optional authenticated cloud escalation.

- [Build and run](android/README.md)
- [Mobile security product plan](docs/plans/17-paladino-mobile-security-product.md)
- [Model stack decisions](docs/adr/0004-lfm3-stack.md) and [one Omni speaker](docs/adr/0005-one-omni-speaker-retired-lfm25.md)
- [Security implementation status](docs/security-guard-implementation.md)
- [Missing work and next tickets](docs/MISSING.md)
- [Validation evidence](docs/VALIDATION.md)

The landing page at the repository root presents the proof of concept direction while clearly labeling the downloadable preview, its limits and its unreleased production build.
