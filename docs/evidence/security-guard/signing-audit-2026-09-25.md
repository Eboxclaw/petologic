# Release signing audit — 25 September 2026

This read-only audit did not create, replace, export, or modify a signing key.

- The documented local keystore exists at `~/.local/share/petologic/signing/paladino.jks`; its alias is `paladino`.
- Its certificate SHA-256 is `BB:B5:93:B4:11:23:F3:23:9B:E4:6A:C4:26:0C:35:2F:60:F8:A4:17:9E:3F:1A:8B:59:6F:39:51:67:63:F4:C7`.
- That certificate matches the identity documented for 0.1.1–0.1.5.
- The 0.1.6 release record says 0.1.6 and later use certificate SHA-256 `2E:5E:8C:D1:B5:6C:A4:27:13:C1:92:F1:8A:BC:59:BB:EE:FD:5B:8E:D3:0B:74:C8:B4:AD:B0:47:76:2F:6F:EC`.
- The certificates do not match. The current local key therefore cannot be treated as the 0.2.2 update identity from repository evidence alone.

Release signing continuity remains blocked until the post-0.1.6 key is recovered from a secure backup or the actual published 0.2.2 APK certificate is independently verified and an approved matching key is located. Do not sign 0.3.0 with the currently found key and claim an in-place update from 0.2.2. If the newer key cannot be recovered, choose and document a new identity, Play App Signing setup, reinstall/data-migration communication, and custody procedure before publishing.
