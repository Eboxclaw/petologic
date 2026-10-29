# Minified release validation

Date: 22 September 2026

Environment: Android API 35 ARM64 emulator, JDK 17, Gradle 8.14.3, AGP 8.13.2, Kotlin 2.3.10, R8 code optimization and resource shrinking enabled.

## Artifact

- Before shrinking: 85,785,259-byte unsigned ARM64 release APK.
- Final unsigned ARM64 release APK: 20,077,633 bytes.
- Reduction: 65,707,626 bytes, or 76.6%.
- Current SHA-256 after URL-hardening changes: `9c8e5607a8e4b4dd089a61434e1560612d9aecd3d2bb2960698f4448474dfb29`.
- Output: `android/app/build/outputs/apk/release/app-release-unsigned.apk`.

The release configuration uses the optimized default ProGuard rules, resource shrinking and explicit JNI retention for `NativeLfm`, `NativeVectorModel`, `NativeRules`, native method names and `TokenCallback.onToken`.

## Runtime smoke test

The minified artifact at the runtime-smoke checkpoint (SHA-256 `5bcaf4dee4e7acf2761a351d3ee023e7eda4bc5242df24393f6b3be76b8c481e`) was copied, signed with the local Android debug key solely for emulator installation, and installed over the existing preview. It cold-launched in 921 ms. The following real paths completed without a fatal error:

- LFM2.5-VL-450M and its projector loaded through `libpaladino.so`.
- A Photo Picker image completed visual generation; quoted hallucinated labels were shown only as `[unverified image text]`.
- `libpaladino_security.so` loaded and the selected 28-byte `PETOLOGIC_SECURITY_SELF_TEST` file matched the bundled harmless YARA 4.5.8 self-test rule.

## Evidence limit

The current artifact was rebuilt successfully after the pure-Kotlin URL-hardening change and passed the complete JVM and lint gates; that exact hash was not rerun on the emulator. The checkpoint validates minification, JNI retention and the tested ARM64 emulator paths. The APK remains unsigned for production. This does not establish Play delivery size, x86_64 release runtime behavior, physical-device behavior, production key custody, malware detection efficacy or release certification.
