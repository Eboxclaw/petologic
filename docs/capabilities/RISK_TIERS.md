# Risk tiers and the approval loop — 0.1 draft

Status: proposal draft (2026-09-10). Foundation already implemented: hash-bound `ActionProposal`s with 5-minute expiry, `ApprovalPolicy` allowlist, manifest `confirmationPolicy: app_minimum`, session capability revocation, digest-bound cloud consent, full execution-event audit log. This document defines the *tier vocabulary* new categories must declare, so the loop stays predictable as scope grows.

## Tiers

| Tier | Definition | Examples | Loop behavior |
|---|---|---|---|
| **T0 — silent local read** | App-private or permission-granted device reads with no side effects; results stay in-session | notes search, clock, next alarm, today's calendar, weather (network-gated), SMS inbox read, call log, installed-app inventory, VPN-active status | Auto-execute; logged. Permission *grants* happen once in Settings → Phone access, not per call |
| **T1 — reversible local write** | Mutates only Petologic-owned data; undo exists or is trivial | note create/delete (today), notification dismiss (spam triage), local tags/labels | Inline proposal in chat/Sprite; approve → execute transactionally; decline → honest "nothing happened" to the model |
| **T2 — handoff** | Hands the user to a system surface that completes the action; Petologic reports *handoff*, never external success | share sheet, dialer (`ACTION_DIAL`), calendar insert UI, Play Protect deep link, VPN settings, Play Store page | Explicit confirm with exact payload preview; after handoff, report "handed off — verify there" |
| **T3 — outbound comms** | Sends content to people or external services | SMS send, email send via connected account, Telegram/WhatsApp direct sends, breach-check API calls carrying an identifier | Per-item explicit approval with the full payload visible; batch sends are never autonomous; expiry + hash binding mandatory |
| **T4 — security posture change** | Alters device security/network routing or takes on a designated system role | Orbot start/stop, VPN connect/disconnect handoffs, accepting CallScreening designation, uninstall requests, indicator-list refresh | Approval **with one-paragraph consequence statement** ("what breaks when Tor is on"), then execute/handoff, then status readback ("Orbot reports started") |
| **T5 — never autonomous** | Regardless of any setting | Silent sends, bulk deletes outside Petologic data, cloud transmission of `LOCAL_ONLY` memory, disabling Play Protect/security settings, arbitrary code, remote device control | Hard-refused in code (`ApprovalPolicy`/manifest reject), logged, explained to the user |

## Rules that do not change with scale

1. **Consent binds to content.** Like `CloudConsent`, any T3/T4 approval is invalid the moment the payload changes (digest check), expires in minutes, and is scoped to the session.
2. **Writes are transactional.** Commit + audit-log entry in one Room transaction; pending proposals are cancelled on process death, never replayed silently.
3. **The model never authorizes itself.** Model-provided confidence, names outside the manifest, or arguments that changed after display are all rejected in code.
4. **Revocation beats approval.** If the user flips a session capability off mid-task (`memoryRead` checks already do this), the tool re-checks at execution time and refuses.
5. **Handoffs are not successes.** The pet says what it did ("opened the dialer with the number") — never what the other app did.
6. **Everything lands in the quest log.** Approval, denial, expiry, refusal and handoff events are all recorded per session, per current `execution_events` behavior.

## Overlay (Sprite) special case

The overlay cannot safely display a full payload review in a 304 dp bubble. Current behavior is correct and becomes the standing rule: T1+ actions in overlay context show **"Open the app to review this request"** and pause the tool loop on a deferred until the user acts in the full app. No tap-through approvals from the bubble.

## Escalation of *risk*, not just compute

Tiny/Maxx is a compute decision; tiers are a safety decision. A Maxx request inherits the same tier checks — cloud text is never parsed as a tool call (already enforced), and any action proposed after a Maxx reply still enters the loop at its own tier. Tier is declared per tool in the manifest (`confirmationPolicy` may later carry per-tool tiers); it can only be *raised* by a role manifest, never lowered below `app_minimum`.
