# Plan 13 · Skill registry, per-turn activation, and the Security Guard skill pack

Source: user's follow-on design (2026-09-11) — a modular skill layer for Paladino, tested with a Security Guard V0 skill pack, structured so the same pack later moves into a dedicated security pet without rewriting. Extends [plan 12](12-tool-runtime-generalization.md): plan 12's runtime refactor (registry-derived `LfmToolCallParser`, schemer, result envelopes) is the prerequisite; this plan adds the layer **above** tools. No code changes yet — implementation starts with Sprint A after user sign-off.

## 1. Skill registry (never hard-wire a capability into PaladinoAgent)

```text
Pet
 └── SkillRegistry
      ├── NotesSkill
      ├── CalendarSkill
      ├── SecurityGuardSkill
      └── future skills…
```

```kotlin
SkillDefinition(
    id = "security_guard",
    name = "Security Guard",
    description = "Spam, phishing, app and device security checks",
    activation = AUTO,               // OFF | AUTO | PINNED
    tools = [...],
    promptStub = "...",
    permissions = [...],
    riskTier = ...
)
```

Three skill states:

- **OFF** — no prompt, no routing, no tools.
- **AUTO** — costs essentially nothing until the deterministic router activates it for the current turn.
- **PINNED** — always in context for the session.

This is what lets Paladino grow many installable skills without injecting every skill's instructions and tool descriptions into every LFM prompt.

## 2. Three registration gates per tool

```text
Skill enabled → Tool enabled → Android permission/capability available → registered with Koog
```

If any gate fails, the tool **does not exist in the Koog registry** — never merely "the model is told not to use it". This continues the existing code-level gating pattern (manifest-gated registration, `SessionOptions` read/write flags). Settings expose skills and tools as separate toggles under each skill.

## 3. Per-turn context activation (two levels)

- **Level A — router manifest (always cheap):** per skill, a handful of trigger terms, e.g. `security_guard: spam, scam, phishing, malware, suspicious app, suspicious link, virus, phone security, call blocking`. Matched deterministically (the `phoneReadRequest`/`RoutePolicy` layer), optionally by the lightweight semantic layer later.
- **Level B — active skill context:** only after activation, inject the small stub (~150 tokens): the tool roles plus hard rules — "never claim malware is confirmed from heuristics alone"; "never claim an external app action succeeded unless Android confirms it". That is the entire security manual; no pages of instructions.

Per turn: user input → deterministic router → candidate skills × session toggles × available Android capabilities → build a **temporary Koog `ToolRegistry`** → run the agent turn. Asking about quantum entanglement supplies zero security tools; asking "Is this APK suspicious?" activates the stub plus exactly `security_query`, `security_scan`, `security_action`.

## 4. Security Guard V0 (three model-facing tools, many Android functions behind them)

The LFM never distinguishes 15 different Android APIs.

- `security_query` — read-only information:
  - `security_query("device_status")`
  - `security_query("app | com.example.app")`
  - `security_query("apps | suspicious")`
  - `security_query("vpn_status")`
  - `security_query("notification_access")`
  - `security_query("call_screening_status")`
- `security_scan` — analysis without changing anything:
  - `security_scan("url | https://…")`
  - `security_scan("text | <message>")`
  - `security_scan("app | package.name")`
  - `security_scan("installed_apps")`
- `security_action` — anything that changes something, **always through the existing `ActionProposal`/`ApprovalPolicy` approval flow** (tool+argument digest-bound, expiring — no second confirmation system):
  - `security_action("open_app_settings | package")`
  - `security_action("uninstall_handoff | package")`
  - `security_action("dismiss_notification | id")`
  - `security_action("enable_call_screening")`

## 5. Deterministic scanners first, LFM explains

**Installed-app review** (`PackageManager`, subject to Android 11+ package visibility): installer/source, requested permissions, granted dangerous permissions, overlay capability, accessibility-service declaration, device-admin capability, VPN service declaration, notification-listener capability, exposed service behavior, signing-certificate fingerprint; APK/source hash only where safely accessible. Output is a deterministic finding — `LOW | REVIEW | HIGH_ATTENTION | UNKNOWN` with reasons ("Requests accessibility service", "Can draw over other apps", "Installed outside recognized store"). **LFM never decides malware**: the wording is "This app has several higher-risk capabilities", never "This is malware", unless there is a genuinely authoritative match.

```text
security_scan → Android scanner → { verdict: "REVIEW", reasons: [...] } → LFM explains
```

**Spam/phishing message scanner** (one of the first useful demonstrations; pasted text first: "Does this SMS look like a scam?"):

```text
message → deterministic URL extraction → phone/email/domain extraction
        → known phishing-pattern rules → local classifier/embedding similarity
        → LFM only if ambiguous → security result
```

`NotificationListenerService` arrives later (Sprint C) for user-authorized awareness of messaging/email notifications — consistent with the capability docs' position that notification access gives local app awareness without pretending to read other apps' private databases.

## 6. Later increments inside the same pack

- **Call Guard V1:** `CallScreeningService` with an explicit user-granted system role. Modes `OFF / WARN_ONLY / SILENCE_HIGH_CONFIDENCE`; warn-only first, no autonomous blocking to start. Incoming number → local known-number/reputation signals → heuristics → "Potential spam" verdict. Eventually stronger protection silences/rejects per a deterministic policy. **The LFM never sits in the real-time decision loop for incoming calls.**
- **Network guard:** read-only `security_query` first (VPN active, private DNS status, current transport, network security state). A `VpnService`-based local DNS/firewall layer for malicious-domain protection is its own project phase for the dedicated pet — it changes Petologic from inspecting the phone to participating in network traffic.

## 7. Settings UX

A skills section in Settings (placed to fit the existing Settings/Controls structure):

```text
PALADINO SKILLS

Memory                 AUTO
Calendar               AUTO
Security Guard         AUTO
Web                    OFF
…
```

Selecting Security Guard opens:

```text
SECURITY GUARD

Mode
○ Off   ● Auto   ○ Always available

Tools
✓ Security status
✓ Scan suspicious text/link
✓ Inspect applications
□ Notification monitoring
□ Call protection
□ Network guard

Permissions
Notification access        Not granted
Call screening             Not granted

Context estimate
Idle: ~0 tokens
Active: ~150 tokens
Tools exposed: 3
```

The context-estimate block makes the 350M prompt budget visible while developing.

## 8. Pet portability (zero Paladino-specific logic)

Target package layout:

```text
ai.petologic.skills.security/
    SecuritySkill.kt
    SecurityRouter.kt
    tools/        SecurityQueryTool.kt · SecurityScanTool.kt · SecurityActionTool.kt
    android/      AppInspector.kt · NotificationGuard.kt · CallGuard.kt · NetworkInspector.kt
    analysis/     UrlAnalyzer.kt · MessageAnalyzer.kt · AppRiskEngine.kt
    models/       SecurityFinding.kt · RiskSignal.kt
```

Paladino's profile simply owns `skills: [notes, calendar, security_guard]`. The future security pet ("Sentinel" working name) owns `[security_guard, notification_guard, call_guard, malware_analysis, network_guard]` — same implementation; only persona, sprite/animations, default skill states, tool permissions, specialization prompts and risk policy change.

## 9. Delivery sequence

- **Sprint A — architecture:** plan-12 runtime refactor + `SkillDefinition`/`SkillRegistry`, OFF/AUTO/PINNED, per-tool enable flags, dynamically constructed per-turn Koog registry.
- **Sprint B — Security Guard V0:** the three tools; pasted-message phishing analysis; URL inspection; installed-app inspection with the deterministic risk engine.
- **Sprint C — Android awareness:** notification-listener opt-in; notification scan/summarize; explicit dismiss/reply actions where Android exposes them.
- **Sprint D — Call Guard:** `CallScreeningService`, warn-only first, configurable protection later.
- **Sprint E — dedicated pet:** move the pack out of Paladino's default role into the new pet's profile.

**Acceptance gate for the first release:** with Security Guard on AUTO and the user discussing an unrelated topic, neither its prompt nor its Koog tools enter the model context; when the user asks a security question, activation happens deterministically and exposes no more than the minimum required tools.
