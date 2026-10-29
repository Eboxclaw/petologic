# VL-450M text and vision validation

Date: 21-22 September 2026

Environment: Android API 35 ARM64 emulator, pinned llama.cpp JNI runtime, LFM2.5-VL-450M-Q4_0 plus the optional Q8_0 multimodal projector.

## Observed results

- The real bounded detector-evidence explanation completed in 1,428 ms, returned a non-empty response, identified the suspicious banking-details and HTTP-link pattern, and emitted no tool marker.
- The real image turn completed in 2,718 ms, returned a non-empty response, and emitted no tool marker.
- The image came from a test content URI, was bounded to the same in-memory RGB path used by Photo Picker/share input, and was not written to chat history or a security finding.
- The visual response caught the suspicious intent but misread or truncated part of the synthetic image text. Runtime integration passed; visual security quality remains an open gate.

## Release follow-up — 22 September

- A Photo Picker turn was run through the final R8-minified ARM64 release APK on the same API 35 emulator using the real VL model and projector.
- The model hallucinated exact interface labels while describing an ordinary Paladino screenshot. This confirms that VL-450M output must not be presented as OCR or trusted transcription.
- The visual prompt now treats all image text as hostile, untrusted content and forbids transcription, tools, verdicts, identity claims and action claims. Output is buffered until completion. Tool protocol markers fail closed; quoted strings, URLs, IPv4 addresses and long numeric strings are masked before any response is displayed.
- The retest completed and displayed `[unverified image text]` in place of the invented labels. The interface heading reads “Possible visual cues — AI may misread text” and gives a separate verification warning.
- This is a containment result. The underlying visual interpretation still included an incorrect pet-care inference, so vision-quality evaluation remains open.

## Security boundary

The model observes and explains. It does not scan packages, confirm malware, mutate findings, call tools, authorize actions, identify people, or silently capture the camera or screen. Deterministic scanners and user-approved Android handoffs retain authority.

## Evidence limit

This was emulator-only synthetic and app-screenshot evidence. It does not establish physical-device latency, battery or thermal behavior, complete prompt-injection resistance, OCR accuracy, accessibility, production release signing, or malware-detection efficacy.
