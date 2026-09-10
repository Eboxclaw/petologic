# Petologic capability matrix — 0.1 draft

Status: proposal draft (2026-09-10), not an implementation record. Companion docs: [TOOL_SPEC.md](TOOL_SPEC.md), [RISK_TIERS.md](RISK_TIERS.md), [PET_ASSIGNMENT.md](PET_ASSIGNMENT.md). Ground rules inherited from the codebase: every capability goes through the role manifest allowlist, session capabilities (`SessionOptions`/`Capability`), hash-bound approvals for writes, and honest refusal when something is not connected. The v1 plan (§2 non-goals) deliberately deferred everything in waves 2–4 behind "a later product plan"; this document is that plan's first draft.

Operating target: everything must remain excellent on one local **LFM2.5-350M**. That dictates the shape: **one tool per category** with a single `argument` string, deterministic routers for known intents, and the local model only selecting among a handful of registered tools per session.

## Wave 1 — personal data core (start here)

| Category | Read | Write | Edit | Android mechanism | Permission | Offline | Risk tier | Owner pet | Status today |
|---|---|---|---|---|---|---|---|---|---|
| Private notes | ✅ `notes_search` | ✅ `notes_save` (approved) | ➖ no API yet (delete+recreate) | Room + AppSearch + MiniLM embeddings | none (app-private) | ✅ full | T1 writes | 0xPaladino | **Implemented** (0.1.x); edit/undo = NEXT-10 |
| Calendar | ✅ `calendar.today` (today, ≤20 events) | `calendar.prepare` → system insert UI handoff | `calendar.edit` (by event ID, approved) | `CalendarContract.Instances` / `Events`; `ACTION_INSERT` for handoff | `READ_CALENDAR`; `WRITE_CALENDAR` only for direct writes | ✅ read/write offline | T0 read · T2 handoff · T2 direct write | Focus pet (read via Paladino until pet 3 ships) | Read **implemented** (0.1.5); `calendar.prepare` already in `ApprovalPolicy.allowed`, not yet wired; edit not started (NEXT-06) |
| Email | paste-into-chat only today | via account connection only | via account connection only | No on-device inbox API for Gmail/Outlook. Two viable paths: (a) OAuth + Gmail API / Microsoft Graph over HTTPS with consent-bound transport; (b) `ACTION_SENDTO`/share-sheet handoffs for composing | OAuth account consent (scope-limited); no Android runtime permission can read Gmail | read via (a) needs network; paste path offline | T0 local paste · T3 any account send | Duelist pet (triage) — Paladino refuses until connected | **Refused honestly today** ("cannot read another app's private inbox"); decision needed on provider path before any build |

Wave 1 acceptance inherits the existing gates: 100% of unapproved writes blocked, no fabricated results, PT/EN parity.

## Wave 2 — messaging & ambient reads

| Category | Read | Write | Edit | Android mechanism | Permission | Offline | Risk tier | Owner pet | Status |
|---|---|---|---|---|---|---|---|---|---|
| SMS / phone messages | inbox + recent threads | send (approved) / draft | delete thread (approved) | `Telephony.Sms` provider; `SmsManager` for sending; drafts via `Telephony.Sms.Draft` | `READ_SMS`; `SEND_SMS`; `WRITE_SMS` needs default-SMS-app (avoid) | ✅ read offline | T0 read · T3 send | Duelist pet | Not started. Sideload distribution makes READ_SMS viable (Play default-app policy does not apply) |
| Call history | recent calls | dial handoff | ➖ | `CallLog.Calls`; `ACTION_DIAL` handoff | `READ_CALL_LOG`; none for dial handoff | ✅ read offline | T0 read · T2 handoff | Duelist pet | Not started |
| Weather | ✅ current conditions (opt-in city) | ➖ | change city in Settings | Open-Meteo geocoding + forecast; no GPS | none (network opt-in) | ➖ needs network | T0 (session `network` gate) | Focus pet | **Implemented** (0.1.5) |
| Telegram | incoming via notifications; bot chats via Bot API | share-sheet handoff / bot send | ➖ | `NotificationListenerService` (opt-in); Bot API over HTTPS for bot-owned chats; TDLib only as a later, explicit product decision | notification-listener consent; bot token | read offline (notifications) | T0 notif triage · T3 any direct send | Duelist pet | Not started; TDLib deliberately deferred (heavy, account-level) |
| WhatsApp | incoming via notifications | share handoff (`ACTION_SEND` to package, user presses send) | ➖ | `NotificationListenerService`; share intent; **no official personal-account API** | notification-listener consent | read offline | T0 notif triage · T3 handoff-draft | Duelist pet | Not started; autonomous sending is a non-goal by design |

Wave 2 design note: message **content** never leaves the device in Tiny. Triage (spam detection, summaries) runs locally; anything cloud-bound goes through the existing outbound-consent review.

## Wave 3 — security & network

| Category | Detect/act | Android mechanism | Permission | Offline | Risk tier | Owner pet | Status |
|---|---|---|---|---|---|---|---|
| Spam SMS identification | classify incoming SMS locally; surface verdict; dismiss notification | SMS receiver / notification listener + local classifier (embedder first, LFM fallback for ambiguous text) | `READ_SMS` or notification-listener consent | ✅ | T0 verdict · T1 dismiss | Duelist pet | Not started (user Mark 1: "not sure how yet" — proposal: start with notification-listener + on-device heuristics, no default-app requirement) |
| Spam call screening | screen/decline known-bad incoming calls | `CallScreeningService` (user must designate the app in caller-ID & spam settings); `CallLog` for post-hoc review | screening role + `READ_CALL_LOG` | ✅ | T0 verdict · T4 screening role | Duelist + Shadow | Not started |
| Antivirus / malware review | inventory installed apps; risk signals (accessibility abuse, device admin, unknown sources, dangerous permission combos); optional indicator lookup; deep-link Play Protect for a vendor scan | `PackageManager`; `Sha256Summary`-style APK hashing vs local indicator list; `ACTION_DELETE` handoff for uninstall | `QUERY_ALL_PACKAGES` (sideload OK; declare justification) | ✅ core; indicator refresh optional | T0 report · T4 uninstall always user-handoff | Shadow paladin | Not started; Play Protect has no public scan API — deep-link + honest report only |
| Tor / VPN routing | status read (is a VPN active?), Orbot start/stop via its documented intent API, handoffs to Mullvad/other VPN apps and VPN settings | `ConnectivityManager` `TRANSPORT_VPN` status; Orbot intents (`org.torproject.android.intent.action.START/STOP` + package extra); `ACTION_VPN_SETTINGS` | none for status; Orbot must be installed | status ✅ offline | T0 status · T4 route change | Shadow paladin | Not started; Mullvad exposes no toggle API — handoff + status readback only, never claim "routed" |

Wave 3 rule: the pet reports network state truthfully and never silently flips routing. Every route change is an approved action with a status readback ("Orbot reports started" — or the truth).

## Wave 4 — OSINT protection (defensive)

| Capability | Android mechanism | Network | Risk tier | Owner pet | Status |
|---|---|---|---|---|---|
| Breach/exposure check for user's own email/phone | Have-I-Been-Pwned-style APIs (k-anonymity where possible), through the consent-bound transport | opt-in, consented | T3 (outbound identifier + cloud) | Shadow paladin | Not started |
| Self-audit of the user's own exposure | installed-app permission audit, lock-screen/backup settings review, share-target inventory | none | T0 | Shadow paladin | Not started |
| Link/phrasing safety pre-check | local classifier on pasted text before user taps; never auto-fetch suspicious URLs | fetch only on explicit consent | T0 local · T3 fetch | Shadow paladin | Not started |

## Cross-cutting: native Android leverage

- **Memory DB**: already native — Room (source of truth) + AppSearch (lexical index) + embedding cache. Category knowledge (calendar events, message threads) should reuse this exact outbox pattern rather than introduce a second store.
- **On-device Google AI (AICore/Gemini Nano, ML Kit GenAI summarization)**: fits NEXT-08's planned adapter — optional accelerators for summarization/classification on supported devices, behind runtime capability discovery, never substituting LFM and never widening permissions. Core flows must be identical when absent (plan §8 already mandates this acceptance lane).
- **Deterministic routers** (the `PhoneReads` pattern) stay the front door for known intents in every wave; the 350M model selects tools only when phrasing is generative.

## Sequencing recommendation

1. Wave-1 completion (calendar prepare/insert + reminder durability = NEXT-06; notes edit/undo = NEXT-10) — proves write-handoffs end-to-end before new categories.
2. SMS read + approved send (highest-value Wave-2 row, pure Android APIs, no third-party dependency).
3. Spam SMS via notification listener + local classifier (first Duelist skill).
4. CallScreeningService designation flow (first T4 role the user must explicitly grant).
5. Orbot status + intent control (first Shadow skill; small, well-documented API).
6. Email account connection (biggest consent/UX decision; do it last, informed by everything above).
