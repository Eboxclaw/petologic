# Paladino pet format and authoring — 2026-09-07

## Decision

Keep one Paladino role and one shared model service. The visual pet is an asset package. Its appearance cannot supply prompts, tools, credentials or executable scripts. Tiny/Maxx remain execution modes of the same role. Android owns overlay permission, rendering and lifecycle; desktop window APIs cannot be copied into Android.

The supplied idle GIF and static PNG are preserved unchanged in Android resources. They are usable now. They are not a complete Codex/OpenPets spritesheet or an installable desktop pet package.

## Research and compatibility

[OpenPets pet format](https://docs.openpets.dev/pet-format/) separates identity, assets and reaction mapping. [OpenPets lifecycle](https://docs.openpets.dev/pets/) describes persistent companions, safe package installation and fallback animation. Adopt those boundaries, retaining Paladino as the sole launch character.

[ChatGPT Pets](https://learn.chatgpt.com/docs/pets?surface=app) documents floating appearance, activity states, reduced motion and custom creation through its bundled hatch-pet skill. It is product documentation, not a complete animation export schema. Its unread Ready state requires actual unread tracking; do not infer it merely because an assistant message exists.

The exact layout below was checked against [OpenPets codex-pets-core.ts at commit 18fd4ed](https://github.com/OpenPetsHQ/openpets/blob/18fd4ed2a9cb52e2b29fe2fa3b4fa42b6d483028/apps/desktop/src/codex-pets-core.ts). This describes that importer, not an unconditional guarantee for every ChatGPT build:

- Metadata: id matching folder, displayName, description, spritesheetPath exactly spritesheet.webp. V2 adds spriteVersionNumber: 2.
- Cells: 192 × 208 pixels, 8 columns. Legacy layout: 9 rows. V2: 11 rows, total 1536 × 2288.
- V2: one decodable WebP image with alpha; neutral pose at zero-based row 0, column 6.
- Do not guess row meanings or frame durations from these dimensions. Verify the renderer's state table before exporting each animation.

## Applied in Android

PetReaction converts controller state into presentation: pending tool/cloud approval → Needs input; error → Error; otherwise active inference → Working; otherwise Idle. This precedence prevents a running indicator from hiding an approval request. Expanded overlay and in-app bubble display this state. Collapsed overlay displays a small status badge and accessible description, without leaking prompt text. Status is not inserted into the conversation as fabricated assistant speech.

The current renderer uses the supplied idle GIF, or static PNG with animation disabled. A tested fallback resolver preserves semantic status when an animation is unavailable. Distinct working/waiting/error cycles are still missing; no other cycle is claimed to exist.

## Animation production handoff

1. Keep original PNG and GIF as immutable source references. Preserve silhouette, sword/shield placement, palette and transparent background.
2. Author a neutral pose and each missing cycle separately: review/working, waiting for input, success, error, attention and movement only where the verified target state table requires them. No extra launch characters.
3. Deliver individual numbered transparent frames in folders per animation plus timing metadata and a looping preview. Keep feet aligned, scale consistent and weapons inside the cell; review seams at last → first frame.
4. Confirm exact target row/frame counts against the pinned renderer; map cycles explicitly. Never fill missing states with repeated idle frames and call them complete.
5. Produce the WebP sheet and pet.json only after frames pass review. Validate dimensions, alpha, decoding, cell boundaries, state mapping and reduced-motion neutral pose. Smoke-test in the target desktop app before claiming compatibility.
6. Android can retain per-cycle animation resources while a future atlas decoder consumes the same reviewed source frames. Decode off the UI thread, bound memory, share cached immutable assets and stop animation when hidden/locked.

## Future package import gate

External package import is not implemented. Before enabling it: strict metadata and version allowlist; bounded encoded/decoded sizes and pixel count; no absolute paths, traversal, symlinks, duplicate/case-colliding entries or scripts; atomic staging and install; SHA-256 integrity; built-in fallback after malformed assets. An imported pet must never modify the role manifest. Keep model-file discovery separate from visual-asset discovery.

## Exit gates and next sprint

- Current: reaction priority/fallback unit tests, app + instrumentation APK compilation, actual model conversations in Chat and floating overlay with persisted transcripts.
- Next: renderer state-table audit and complete per-cycle frame handoff; locked-screen/permission-revocation/reduced-motion tests; physical-device rotation/inset validation of the implemented persisted drag position; widget launcher round trip.
- Later: validated package import/export and desktop compatibility smoke test. No catalogue, social, premium or networking scope.

The inference investigation and measured outcomes belong in ../evidence/2026-09-07-model-and-sprite.md. A model file being present alone is not proof that app inference works.
