# Plan 11 · User copy (store/load) and reset from zero

## Why

The original release signing keystore was lost, so 0.1.6 ships with a new signing identity: every existing 0.1.5-or-earlier install must uninstall before installing 0.1.6, and uninstalling wipes all local data. From 0.1.6 onward, users need a way to carry their data across such breaks (and across devices), plus an explicit way to wipe and start fresh without uninstalling. Requested by the user 2026-09-11.

## Scope

Settings gains a "User copy" card with three actions:

1. **Save user copy** — SAF `CreateDocument` (`application/zip`, suggested name `paladino-user-copy-<date>.zip`). Contents:
   - `manifest.json` — `{format, schemaVersion, createdAt, appVersionName, counts}` (org.json; no new dependency).
   - `paladino.db` — Room DB (conversations, messages, notes, actions, graph, tasks, reminders, embeddings, sessions, execution events). WAL is checkpointed (`PRAGMA wal_checkpoint(FULL)`) before copy so the main file is a consistent snapshot.
   - `prefs/*.xml` — `preferences`, `session_hub`, `tinypet_paladino`, `phone_reads`, `sprite_position`.
   - Excluded on purpose: model files + `model_library` prefs (re-downloadable, machine-independent), `provider_credentials` (secret; AndroidKeyStore-bound, would be undecryptable after reinstall anyway), caches, AppSearch index (rebuildable).
2. **Load user copy** — SAF `OpenDocument` → confirm dialog → validate manifest (`format==1`, `schemaVersion<=running`) → stream zip to cache, close Room, swap DB file (delete `-wal`/`-shm`), copy prefs XMLs → process restart. Newer-format copies are refused with a clear message.
3. **Delete everything and start over** — confirm dialog with explicit consequences → close Room, delete `databases/`, `filesDir` (models included), `cacheDir`, external app files, `shared_prefs` (everything, including provider keys), AppSearch dir (`getDir("app_search")`; index is rebuildable), AndroidKeyStore `paladino.*` aliases → process restart. Equivalent to a fresh install without uninstalling.

Restart = relaunch `MainActivity` with `NEW_TASK|CLEAR_TASK`, then `Runtime.exit(0)` (ProcessPhoenix pattern).

## Files

- `data/UserCopy.kt` — manager object (export/import/wipe/restart); DB name parameterized for tests.
- `OnboardingCards.kt` — `UserCopyCard` composable next to `AppUpdateCard`; wired into `Settings` in `MainActivity.kt`.
- Strings EN + PT in `values*/strings.xml`, `UiLabels` map entries.
- Tests: JVM manifest round-trip/validation; instrumented export→wipe→import round trip on the emulator.

## Limits (stated in UI)

Export is a point-in-time snapshot; the app keeps running afterwards. Import/wipe restart the app. API keys are never exported. Models are never exported.

## Verification gates

JVM tests green; instrumented round-trip on `Paladino_API35`; UI smoke of the card on the emulator; then release build signed with the new keystore and end-to-end release publish.
