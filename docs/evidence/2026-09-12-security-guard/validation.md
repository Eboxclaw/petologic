# Security Guard V0 (plan 13 Sprint B) — evidence (2026-09-12)

## What shipped

Second skill in the registry, first AUTO-default skill: `security_query`, `security_scan`, `security_action` over deterministic local logic — `UrlAnalyzer` (HTTPS, raw-IP host, @-userinfo, shorteners, brand look-alikes, subdomain count, hidden executable), `MessageAnalyzer` (pressure/credential/prize/payment patterns + link folding), `AppRiskEngine` (accessibility, device admin, VPN service, overlay, notification listener, installer origin, granted sensitive permissions) fed by `AppInspector` PackageManager queries. Results travel in the plan-12 envelope grammar (`OK|security.scan|…` / `ERROR|…`). Actions are handoffs only (open app settings, uninstall screen) and go through the existing `ActionProposal` approval flow; `dismiss_notification` and call actions are explicitly refused until Sprints C/D.

## Tests

- 61 JVM tests green (was 50): new `SecurityAnalysisTest` (6), `SecurityCommandTest` (3), activation suite now exercises the real `SecuritySkill` (AUTO wakes on "Is this link suspicious?", stays silent on "what is a black hole"), and a scripted Koog loop test drives `security_scan` end-to-end through `PaladinoAgent` and asserts the envelope reaches the model transcript.
- 3 instrumented tests on `Paladino_API35` (`SecurityIntegrationTest`): own-package inspection with real PackageManager signals, unknown package → UNKNOWN (never LOW), facade envelope shapes.

One real bug caught during wiring: `PaladinoManifest.parse` correctly refused the unregistered `security.*` tool ids at app startup ("Unsupported tool or privilege expansion") — the ids were added to the bundled role manifest and the parser's supported set, preserving the code-level privilege boundary.

## UI

Orchestration → Skills & MCPs lists Security Guard with default state Auto and three toggles; estimate "Idle ~0 tokens · active ~146 tokens · 3 tools exposed". Screenshots in this folder.

## Limits

- Local heuristics only: no network reputation, no embedding similarity yet (both planned; the LFM explains findings but never upgrades verdicts).
- "apps | suspicious" sees only the apps Android package-visibility exposes to Paladino.
- Call screening and notification actions intentionally unavailable (Sprints C/D).
- No real-model chat run yet on the emulator (its LFM model was wiped by release testing); the scripted loop test covers the agent path.
