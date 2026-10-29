# Safe efficacy benchmark smoke evidence

Date: 23 September 2026

The JVM smoke lane executed the same pure Kotlin exact-hash, URL, message and app-profile analyzers used by the Android application. Corpus `safe-smoke-1` matched its pinned SHA-256 `74395c11125a4b4d767ac5ab3029e392b7e45900abb572bba09827b1b5168761`.

All smoke gates passed:

- URL, app-profile, English-message and Portuguese-message suspicious recall: 1.0000.
- Critical `HIGH_ATTENTION` rate: 1.0000 in every applicable suite.
- Benign `HIGH_ATTENTION` and broader alert rates: 0.0000.
- Known inert hash: 1/1 exact match; mutated hash: 0 matches.
- Case expectation failures: 0.

The corpus contains only inert SHA-256 strings, synthetic messages and app metadata, reserved domains, and documentation-network addresses. The run acquired and executed no malware, APK, archive, executable, exploit, macro, or active malicious link. Reports contain no raw input.

This small smoke corpus validates the harness and regression wiring. It is not the external held-out release corpus, a Pixel 7a retrieval result, or live-malware certification.
