# Skill registry and per-turn activation — Sprint A evidence (2026-09-11)

Implements plan 13 Sprint A on top of the plan-12 runtime refactor.

## Runtime refactor

- `runtime/LfmToolCallParser.kt` replaces `NoteToolProtocol`: the allowed tool-name set is a parameter derived from the live Koog registry (`tools.map{it.name}`), the Pythonic call/list syntax stays primary (LFM2.5 native format), JSON fallback unchanged, literal-only argument decoding with the 12 000-char cap. `looksLikeToolCall` generalized from the two hard-coded names to any snake_case identifier prefix.
- `runtime/LfmToolDescriptorSchemer.kt` builds the model-facing single-string-argument JSON array; the hand-written schema construction is gone from `PaladinoAgent`.
- `NoteToolProtocol.kt` deleted; its entire test contract migrated to `LfmToolCallParserTest` (all original cases preserved, plus allowlist cases: a registered `security_scan` parses, the same syntax fails when the tool is not registered for the turn, and an empty registry refuses everything).

## Skill layer

- `skills/SkillTypes.kt` — `SkillState` (OFF/AUTO/PINNED), `SkillToolSpec`, `SkillDefinition`, and the pure `activateSkills` computation: PINNED always, AUTO only on a word-boundary, diacritic-insensitive router-term match, OFF never; per-tool toggles and ungranted Android permissions remove tools from the activation (they never reach the Koog registry).
- `skills/SkillRegistry.kt` — `MemorySkill` (the existing notes tools) as the first resident; SharedPreferences persistence (`skills` store); `activationsFor()` glue.
- `SessionController.generate()` resolves activations once per turn and passes only the surviving tools as non-null `AgentTools` lambdas plus the joined prompt stubs; the agent's registry gates now key off tool nullity. Session-level caps (`memoryRead/memoryWrite/toolCalls`) still re-check at execution time.
- `SkillsCard` in Settings: per-skill Off/Auto/Always selector, per-tool switches (only while not OFF), and a token/tool estimate line.

## Tests

50 JVM tests green (35 app + 15 core), including 10 new: 5 parser allowlist cases and 5 activation cases covering the plan-13 acceptance gate (unrelated topic on AUTO → no activations, no tools).

## Emulator UI (Paladino_API35, debug build)

- `settings-skills-always.png` — Skills card first in Settings; Memory on "Always" (the default, preserving pre-registry behavior), both tools on, estimate "Idle ~0 tokens · active ~77 tokens · 2 tools exposed".
- `settings-skills-off.png` — after tapping Off the tool rows and estimate collapse; state and hidden tools persist across an app restart; restored to Always afterwards.

## Limits

No real-model regression run on the emulator this sprint (its LFM model was wiped by the 0.1.6 release smoke test; JVM `KoogLoopTest` covers the agent loop with a scripted turn). Security Guard (Sprint B) will be the first AUTO-default skill and the first consumer of the per-turn activation beyond the notes resident.
