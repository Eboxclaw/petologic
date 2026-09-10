# Phone reads and overlay recovery — 0.1.5

## What the photos establish

The Android app info screen shows 0.1.1-preview, older than the released UI updates. The access-denied dialog is an Android restricted-setting block; the application cannot grant SYSTEM_ALERT_WINDOW itself. An OEM/supervised-device policy may make the setting unavailable. The screenshot alone does not identify the handset/OS; requested those details.

The new help appears when the user returns without overlay permission, and is also available under Permission blocked?. It links to Android app info and explains the restricted-settings option, only when available and the installation is trusted. It never disables Play Protect or silently changes permissions. The in-app bubble remains usable. Its two conversation actions now occupy separate full-width rows. Controls navigation uses labelled cards.

## Read-only capabilities

- clock.read: system local date/time/timezone, no special permission.
- alarm.next: AlarmManager.nextAlarmClock. Absence means Android reports none, not proof every clock app has no alarm. No universal alarm inventory or alarm writes.
- calendar.today: READ_CALENDAR runtime grant; synchronized CalendarContract.Instances for today's local interval, at most 20 event titles/start times. No calendar writes or automatic cloud transfer.
- weather.current: opt-in configured city; Open-Meteo geocoding/current conditions. No GPS permission, conversation or calendar payload. City-match name/country and source time returned. Session network switch is respected; public API use is a non-commercial preview.
- Email: account reader is NOT connected. Await provider choice and an authorized integration. Android has no common API for reading Gmail/Outlook private inboxes. Pasted email can already be read locally; this is not mailbox synchronization.

Phone reads are deterministic routes for supported explicit requests, not a claim that LFM independently selected new Koog tools. They pass session tool settings and a bundled role allowlist, and produce stored source responses/tool metadata in the same session used by Chat and Sprite. Existing model/Koog note tools remain unchanged. Unknown phrasing can still fall back to Tiny; broader intent evaluation and clarification are future work. Weather uses the configured city; calendar is limited to today.

## Validation limits

JVM routing/role validation, real clock and next-alarm reads, denied/granted calendar query, live Open-Meteo query, Chat entry, bubble layout and overlay guidance. The emulator has no populated real calendar account; event content and OEM restrictions need physical-device testing. No live email account or alarm creation test is claimed.

## Sources

- https://support.google.com/android/answer/12623953?hl=en
- https://developer.android.com/reference/android/app/AlarmManager
- https://developer.android.com/reference/android/provider/CalendarContract
- https://open-meteo.com/en/docs
- https://developers.google.com/workspace/gmail/api/auth/scopes
