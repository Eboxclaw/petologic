# Sprint C — notification awareness (2026-09-12)

## What shipped

Plan 13 Sprint C, read/dismiss first: a `NotificationListenerService` (`ai.petologic.skills.security.android.NotificationListener`) that Android binds only after the user grants notification access; `security_query("notifications | all | <package>")` lists recent on-device notifications (key · app · title · text) with keys as stable handles; `security_action("dismiss_notification | id")` cancels one notification — still approval-gated like every action. The new `notification_query` tool is the registry's first capability-gated tool: `cap.notification_listener` resolves through the same three-gate mechanism as Android permissions (granted → registers; not granted → the tool does not exist for the turn), its toggle defaults to OFF, and the Orchestration detail row shows Granted/Not granted through the capability-aware check. RemoteInput replies are deferred (plan 13 allows read/summarize/dismiss first).

## Tests

- 62 JVM tests green (new: notifications grammar incl. pipe-bearing ids; activation test proving the capability gate and the off-by-default toggle).
- Instrumented `notifications_fail_closed_without_listener_access` passes in both states on `Paladino_API35`: without access the facade returns `ERROR|permission_required`; after `adb shell cmd notification allow_listener …` and posting a real notification, `notifications | all` returns `OK|security.query|` through the live listener.

## UI

`security-guard-notification-monitoring.png`: Security Guard detail with four toggles, Notification monitoring ON and Granted, estimate "Idle ~0 tokens · active ~161 tokens · 4 tools exposed".

## Limits

- Snapshot updates while Android has the listener bound; after a fresh grant the app may need a restart for the first snapshot.
- Reading shows title/text truncated to 140 chars; no history — only notifications active at query time.
- Replies via RemoteInput remain deferred.
