# Pet assignment matrix — 0.1 draft

Status: proposal draft (2026-09-10). The v1 plan's identity contract is the chassis for the whole roster: **one manifest per pet**, each pet can only *narrow* the app-minimum policy, cloud is a subrole of the active pet (never a second persona), and all pets share the same local model, runtime and memory namespace rules — "future pets share the same local brain rather than download a separate one." `TinyPetCatalog` already holds one entry (`paladino`) and presentation preferences can never grant tools, which is exactly the seam a roster needs.

## The party (as marketed today)

| Slot | Site lore | Working name | Category ownership | Manifest `allowedTools` sketch |
|---|---|---|---|---|
| 1 — owned | **0xPaladino**, Guardian, "protects data, loyalty, intelligence, action" | 0xPaladino (shipped) | Conversational core · private notes/memory · device reads (clock, alarms, calendar read, weather) · first-responder for everything not owned elsewhere · data-protection guardrail (keeps `LOCAL_ONLY` out of Maxx) | notes.*, clock.read, alarm.next, calendar.today, weather.current (current set) |
| 2 — sealed (premium) | Duelist, "slices noise out of your inbox and your notifications" | TBD | Messaging triage · SMS/call log reads · spam SMS/call identification · Telegram/WhatsApp notification triage · email triage (once an account connection exists) · drafts and approved sends | sms.read/send, calls.read, telegram.triage, whatsapp.triage, spam.screen, email.* |
| 3 — sealed (premium) | Focus keeper, "bends your calendar into order, breathes through long tasks" | TBD | Calendar and time: inserts/edits (approved), reminders, scheduling, focus/do-not-disturb suggestions, long-task companionship | calendar.insert/edit, reminders.*, dnd.suggest, weather.current (for plans) |
| 4 — sealed (secret) | Shadow paladin, "the other side of the mirror", unlocks at Paladino LVL 99 | TBD | Security & OSINT: installed-app risk review, spam-call screening role, Orbot/Tor and VPN status + approved route changes, breach/exposure self-checks, link safety pre-checks | device.apps/scan/orbit/vpn, calls.screen, osint.audit |

## Ownership rules

1. **Owner vs. delegate.** Category ownership decides which pet's manifest registers the category's tools and which persona narrates results. The active *executor* is always the same runtime; switching pets switches manifest + persona + memory-namespace filter, not the model.
2. **Paladino is the default and the guardian.** Before pets 2–4 ship, Paladino answers everything with his current narrow toolset. After they ship, Paladino keeps read access to triage *metadata* (e.g., "you had 4 spam texts today") but not deep triage — that's the Duelist's edge. This matches the lore (guardian protects; duelist fights noise) and keeps any single manifest small.
3. **Routing.** Phase 1 keeps the current deterministic router + explicit pet selection. Phase 2 (NEXT-02 semantic routing) adds a local classifier that proposes the right pet for a request, shown to the user ("Duelist can handle this — switch?"). A pet never *takes* a request outside its manifest silently.
4. **Memory.** Shared Room store, per-pet namespace filter (the `sessionId`/namespace pattern already enforced in `MemoryRepository`), `LOCAL_ONLY` sensitivity respected roster-wide. Cross-pet reads are explicit, logged, and default off.
5. **Escalation.** Cloud subroles (`reason`, `summarize_context`, `plan`) inherit the active pet's identity and can only narrow permissions — unchanged from the plan §"Paladino manifest and future readiness". Phone-built-in AI (NEXT-08 adapter) follows the same inheritance.
6. **Progression.** Unlock conditions (e.g., Shadow at LVL 99) are presentation-layer only — `TinyPetManager` prefs — and must never gate safety behavior: a locked pet's tools are absent from its manifest, not merely hidden.

## What each pet must prove before shipping (per-category gates)

Inherits the repo's standard: EN/PT real-model held-out evaluations, 100% unapproved-action blocking, emulator evidence, and the physical-device gates from the plan. Concretely: Duelist needs the notification-listener consent flow and spam-benchmark set; Focus needs durable reminders through Doze/reboot (NEXT-06); Shadow needs the CallScreening designation flow and an honest VPN status contract.
