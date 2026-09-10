# Validation — phone reads

37 JVM tests passed with no failures/errors. Includes read-intent false-positive/write exclusion cases and manifest tests. App debug/release builds, lint and website build passed.

services.txt: actual clock/next-alarm, calendar denied then granted (emulator empty provider), live opt-in Lisbon weather, and real Chat clock request passed (2 tests). bubble.txt: in-app dialog action layout and overlay-denial help passed. Initial mixed lane: paired five real-model prompts and overlay integration passed; live weather initially failed with DNS while Wi-Fi disabled (retained in initial-run.txt). Retested successfully after enabling Wi-Fi. A layout assertion initially matched background drawer text as well as dialog text; selector scoped to dialog and passed.

Signed upgrade from 0.1.4 to 0.1.5 opened successfully and retained Conversa 3. Same signing certificate verified. Final subsequent rebuild changes only two explanatory resource strings. APK hash and limits in release notes. Temporary emulator Wi-Fi/calendar grant restored afterward.

No physical OEM restricted-setting override was performed. Only user/system settings can grant overlay access. No populated-calendar/account sync, email OAuth or full natural-language intent accuracy claim. Phone read routing is deterministic, not LFM-selected Koog tool calls. No email implementation was claimed: provider/authentication input remains outstanding.
