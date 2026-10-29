# Petologic: Own your Paladino

An Android-first, AI-powered mobile security guardian. Paladino combines deterministic open security engines with local Liquid AI chat and private vector memory.

## Native Android app

The 0xPaladino Android preview lives in [`android/`](android/README.md). It is a Kotlin/Jetpack Compose app with local file, APK, message and link checks; LFM2.5-VL-450M for security chat and explicit image inspection; LFM2.5-Embedding-350M for private vectors; and optional authenticated cloud escalation.

- [Build and run](android/README.md)
- [Mobile security product plan](docs/plans/17-paladino-mobile-security-product.md)
- [Vision and speaking-model architecture decision](docs/adr/0003-vl450-speaking-and-vision.md)
- [Security implementation status](docs/security-guard-implementation.md)
- [Missing work and next tickets](docs/MISSING.md)
- [Validation evidence](docs/VALIDATION.md)

The landing page at the repository root now presents the Paladino security direction while clearly labeling the downloadable companion preview and unreleased Security Guard build.
