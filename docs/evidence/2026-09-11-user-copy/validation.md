# User copy and reset — verification evidence (2026-09-11)

Build: debug `0.1.6-preview` (versionCode 7) on `Paladino_API35` emulator.

## Instrumented tests (`UserCopyIntegrationTest`)

- `export_then_import_restores_data` — session, message and note inserted into a file-backed Room DB; export produced a zip with manifest (`format=1`, counts); after deleting the note, import restored all three rows through a fresh Room open. PASS.
- `wipe_leaves_no_trace` — after wipe: database file gone, `filesDir/models` gone, a fresh database on the same name is empty. PASS.

First run caught a real bug (staged `prefs/` directory not created on import → ENOENT); fixed and re-run green. A second iteration fixed the test fixture (message written to the default session instead of the test session).

## UI smoke (screenshots in this folder)

- `settings-user-copy-card.png` — Settings shows the User copy card between App updates and Language, with Save / Load buttons and the destructive "Delete everything and start over" action.
- `save-picker.png` — "Save user copy" opens the Android file picker pre-filled with `paladino-user-copy-2026-09-11.zip`.
- Saving through the picker wrote `/sdcard/Download/paladino-user-copy-2026-09-11.zip` (2,199 bytes; near-empty DB on the wiped emulator is expected).

## JVM

- `UserCopyManifestTest` — loadable() accepts same/older schema; rejects unknown/future formats, newer schema, missing schema version. Included in `:app:testDebugUnitTest` green run.

## Limits

- Export is a point-in-time snapshot taken with a WAL checkpoint; the app keeps running (concurrent writes after the checkpoint are not in the file).
- Provider API keys are never exported (AndroidKeyStore-bound; they would not decrypt on another install anyway).
- Import/wipe restart the process; overlay Sprite and widget state reinitialize.
