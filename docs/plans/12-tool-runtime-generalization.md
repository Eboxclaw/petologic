# Plan 12 · Tool-runtime generalization and the Android capability layer

Source: external architecture audit provided by the user (2026-09-11, ChatGPT) auditing `main` at `4b963d0`, cross-checked against the code the same day. This doc normalizes it into repo conventions and records the build order. It amends the capability drafts in `docs/capabilities/` where noted. Implementation happens in later sprints after user sign-off — nothing in this plan changes app behavior on its own.

## 1. Verified current state (code, 2026-09-11)

- `runtime/NoteToolProtocol.kt` parses model output with a hard-coded allowlist: the Pythonic-call regex accepts only `notes_save|notes_search`, and the JSON branch `require(name in setOf("notes_save","notes_search"))`. One string argument, ≤12000 chars, hand-decoded literal (never evaluated).
- `runtime/PaladinoAgent.kt` hand-builds the model-facing schema (`{"argument":{"type":"string"}}`) from Koog descriptors inside the custom `PromptExecutor`, and the system prompt instructs: "Output function calls as JSON." Tool history is re-serialized as `<|tool_call_start|>…<|tool_call_end|>` assistant turns + `tool`-role results.
- Deterministic routing already precedes the agent: `SessionController.send()` runs `phoneReadRequest()` first (clock/alarm/calendar/weather/capabilities; write-verbs and long/summarize inputs rejected), then `RoutePolicy` (clarify / note proposal / direct note search / generate). Only `Route.Generate` reaches Koog.
- `LoopBudget` (core `SessionPolicy`): 4 hops, 3 tool calls, repeat-digest rejection, 1 repair, 60 s/hop, 180 s total. Koog `singleRunStrategyWithHistoryCompression` with a byte-shrinking compression strategy.
- `docs/capabilities/RISK_TIERS.md` T0–T5 (silent read → transactional write → handoff → outbound → security posture → never-autonomous), digest-bound consent, model never authorizes itself.

The audit's verdict: the Koog/LFM/Android stack is sound; the weak point is the note-only parser and the JSON-first instruction, which will not scale to 20+ tools at 350M.

## 2. Adopted architecture changes (when implemented)

1. **`LfmToolCallParser`** replaces `NoteToolProtocol`. The allowed tool-name set is derived from the **active Koog `ToolRegistry`** per request — never a literal in a regex. Native LFM Pythonic call syntax (`tool(argument="…")`, incl. `<|tool_call_start|>` wrappers) becomes primary; JSON stays as a compatibility fallback. Keep: single flat string argument, literal-only decoding, length caps, one repair, no Python execution.
2. **`LfmToolDescriptorSchemer`** — move descriptor→schema out of `PaladinoAgent` into a Koog `ToolDescriptorSchemer` implementation (Koog ships OpenAI/Ollama schemers; custom is supported). Model adapters become LFM-specific; Android tools stay model-independent (2.6B / cloud later).
3. **`CanonicalAction` + `ToolResultEnvelope`** — one stable result grammar, e.g. `OK|calendar.events|count=2` / `ERROR|permission_required|calendar`, instead of ad-hoc strings.
4. **Two model-facing tools per domain** — `<domain>_query` and `<domain>_action` for notes, calendar, contacts, messaging, notifications, files, media, device, apps, web. This **amends `docs/capabilities/TOOL_SPEC.md`** (one-tool-per-category): same single-string-argument schema and verb grammar, but a hard read/write boundary per domain at the tool-name level. Query tools are T0/T2, action tools follow RISK_TIERS.
5. **Contextual exposure** — 2–4 tools normally, ~6 max per model hop; deterministic routing absorbs everything that does not need intelligence (extend the `PhoneReads` pattern: battery, connectivity, VPN state, open-URL, dialer, compose handoffs).
6. **Success vs handoff** — handoff tools report "opened X with content prepared", never "sent" (already RISK_TIERS T2; keep as invariant).

## 3. Android capability matrix (audit's free/native-first list)

| Capability | Mechanism | Tool |
| --- | --- | --- |
| Time/alarm | `java.time`, `AlarmManager.nextAlarmClock` | deterministic (shipped 0.1.5) |
| Battery, connectivity, VPN state, sensors | `BatteryManager`, `ConnectivityManager`, `NetworkCapabilities.TRANSPORT_VPN`, `SensorManager` | `device_query` |
| Vibration | `VibratorManager` | `device_action` |
| Calendar read / insert | `CalendarContract` (read shipped) / `ACTION_INSERT` handoff | `calendar_query` / `calendar_action` |
| Contact pick / full read | `ACTION_PICK` (no broad grant) / `READ_CONTACTS` | `contacts_query` |
| Files open/save | SAF pickers (pattern already in model import) | `file_query` / `file_action` |
| Photo/video | Android Photo Picker (preferred over media permission) | `media_query` |
| Speech / TTS | `SpeechRecognizer` (API 31 on-device where available), `TextToSpeech` | direct UI service |
| Dial / SMS compose / email compose | `ACTION_DIAL`, `ACTION_SENDTO smsto:`, `ACTION_SENDTO mailto:` | `message_action` handoffs |
| Share / open URL / open app | Sharesheet `ACTION_SEND`, `ACTION_VIEW`, explicit intents | `app_action` |
| Installed packages / usage | `PackageManager`, `UsageStatsManager` (special access) | `app_query` |
| Notifications | `NotificationListenerService` (explicit grant) | `notification_query` / `notification_action` (dismiss; RemoteInput reply where the target app offers it — not universal) |
| Call screening / own VPN | `CallScreeningService` (system role), `VpnService` | later, specialist pet |
| Accessibility automation | `AccessibilityService` | **last resort only** |

Priority order: native API → Intent/handoff → official account API → Accessibility.

## 4. Policy and API corrections (docs to amend)

- **SMS/call log:** design V1 messaging around compose Intents + notifications, **not** `READ_SMS`/`SEND_SMS`/`READ_CALL_LOG`. Google Play restricts these to default SMS/Phone/Assistant handlers; an Assistant role is the strategic later phase. Sideloading bypasses Play review, **not** Android's permission model. → amendment to `docs/capabilities/CAPABILITY_MATRIX.md` Wave 2 and `TOOL_SPEC.md`.
- **Open-Meteo:** free endpoint is non-commercial only, ≤10k calls/day; commercial needs their paid tier or self-hosting. Note in the weather settings copy/docs.
- **Gmail API:** standard use currently free; Google plans quota overage billing during 2026 — do not encode "permanently free".
- **Telegram:** Bot API free (bot conversations); TDLib is a full client — much heavier security/product commitment. **Microsoft Graph:** OAuth with per-service throttling.
- Account APIs (Gmail/Graph/Telegram) must appear as implementations **behind** `message_query`/`message_action`, never as a second agent architecture.

## 5. Build order

1. **Tool-runtime refactor:** `LfmToolCallParser` (registry-derived allowlist), `LfmToolDescriptorSchemer`, `CanonicalAction`/`ToolResultEnvelope`, native-syntax primary; keep LoopBudget/repeat stop/Koog loop; goldens for the parser in both syntaxes.
2. **Cheap Android-native capabilities:** device_query cluster, calendar insert handoff, contact picker, file pickers, photo picker, dial/SMS/email compose, share, app launch/deep links, TTS + on-device speech.
3. **Notification access:** read/summarize/dismiss first; approved RemoteInput replies only where the notification provides it.
4. **Deeper app capabilities:** app safety via PackageManager, UsageStats, media controls, CallScreening, then Assistant role consideration.
5. **Account APIs** behind the same interfaces.

## 6. Non-goals / unchanged

- 350M stays the standard model; escalation paths unchanged (Tiny/Maxx consent flow).
- Approval primitives (`ActionProposal` hash binding, expiry, allowlist) unchanged — new tools slot into them.
- UI refresh sprints S2–S7 (plan 09) continue in parallel; plan 12 is the tool-layer track.
