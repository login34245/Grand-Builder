# Grand Builder AI Notes

This file is a handoff for future AI agents working on this repository, including Codex and Claude.

## Project

- Mod: `Grand Builder`, Fabric Minecraft mod for Minecraft `1.21.11`.
- Main repository: `https://github.com/login34245/Grand-Builder`.
- Main jar name used by the user: `grand_builder-1.1.0.jar`.
- After every meaningful mod change, build the jar and copy it to:
  - `C:\Users\Admin\Downloads\grand_builder-1.1.0.jar`
  - `C:\Users\Admin\AppData\Roaming\.minecraft\versions\mega shorts mod\mods\grand_builder-1.1.0.jar`
- The user also expects the GitHub repo/release to be updated when a new jar is produced.

## User Preferences

- Respond in Russian unless there is a strong reason not to.
- The user prefers direct implementation over long planning.
- Names should not sound generic or AI-generated. Use grounded, punchy names.
- Effects should feel high-quality, distinct, and handcrafted.
- For experimental effects, do not make every mode look like the UFO example. Each mode should have its own idea, staging, sound, and visual language.
- Strong preference for real 3D geometry in world space, cinematic timing, light, depth, and restrained camera accents. Particle silhouettes and flat HUD stripes are not a substitute.
- Avoid relying only on vanilla-looking effects when the user asks for experimental mode visuals.
- The user explicitly named `https://www.youtube.com/@notrofls/shorts` as a creative direction for future updates. Treat it as a reference, not a request to copy the creator's assets or exact scenes. Two examples inspected through browser keyframes: `https://www.youtube.com/shorts/ZJlrCHMb4ko` (an ordinary Minecraft scene cuts to an isolated door on black, then returns to gameplay) and `https://www.youtube.com/shorts/4dAI5rdp2FI` (an antimatter premise with players and an extreme corrupted-looking finale). These were visual/keyframe checks, not a full audio or frame-by-frame analysis.
- For this direction, prioritize legible physical block/object transformations, distinctive staging, close-up-worthy details, and clear anticipation/contact/recovery timing over extra particle noise. The mod should supply the 3D action; Flashback and the user's editing/music supply the final video rhythm. Verify effect recording/playback in Flashback before claiming suitability for that workflow. This reference does not authorize changing the existing speed budgets or automatically implementing new modes.

## Interface Requirements

- The Builder Console must adapt to game resolution and GUI scale. Buttons and text must not go outside the screen.
- The mod supports English and Russian through normal Minecraft localization files:
  - `src/main/resources/assets/grand_builder/lang/en_us.json`
  - `src/main/resources/assets/grand_builder/lang/ru_ru.json`
- Do not add a separate in-mod language switch button. The user wants Minecraft's own language setting to control the mod language.
- The UI should hide speed controls for modes where speed does not matter.

## Current Experimental Modes

- `Standard`
- `UFO Invasion` / `НЛО-вторжение`: instant staged UFO reveal, strong arrival effects.
- `Rift Bloom` / `Расцвет разлома`: instant reveal with rift-style effects.
- `Meteor Forge` / `Метеорная ковка`: instant reveal with meteor/forge-style effects.
- `Clockwork Drive` / `Заводной ход`: timed clockwork build mode.
- `Aurora Weave` / `Полярная вязь`: ambient aurora-style build mode.
- `Herobrine` / `Херобрин`: a white-eyed, voxel-faced 3D builder teleports through real placement targets with synchronized arm swings and short afterimages. At high speeds, render representative targets from each full-rate build batch, not a slow single-block queue.
- `Builder Charge` / `Строительный заряд`: a finned metal charge follows an arcing flight path, flashes a red countdown light, then bursts into a 3D pressure shell, radial streaks, flying casing panels and an expanding ground shockwave. A fitted assembly cage and rising scan outline the instant house reveal. Visual-only is the default; the user explicitly requested an optional real destructive blast (see below).
- `Lightning Strike` / `Грозовой разряд`: custom branching 3D lightning strikes the exact next block position; installation or removal occurs at its contact tick. No lightning entity, fire, or vanilla lightning renderer.
- `Reverse Build` / `Перевёртыш`: selectable top/front/right/left/back start side, relative to the structure's orientation. No bottom option. A fitted 3D guide plane travels in the real placement direction; fluids still install last.
- `Dismantle` / `Разборка`: instantly install the selected structure, hold it briefly, then remove it gradually. Its style selector (standard/Herobrine/lightning/charge) appears only for this mode. Terrain adaptation is disabled and hidden here; removal speed stays meaningful even for charge dismantling.
- `Flying Blocks` / `Летящие блоки`: textured blocks fly along curved paths from the perimeter, spin, and settle at their exact placement targets.
- `Reverse Collapse` / `Обратный обвал`: a ground-level rubble pile rises and reassembles into the selected structure.
- `Assembly Workshop` / `Сборочный цех`: a fitted industrial gantry, moving trolley/claw, and roller conveyors feed textured blocks into place.
- `From Model to Home` / `Дом из макета`: the actual selected structure appears as a miniature on a drafting platform, grows, unfolds its walls, and lifts its roof before the final assembly.

## Editable Preview Controls

- Hold the Builder Core and create a preview. Default bindings: `R` rotates clockwise by 90 degrees; arrow keys move by one block relative to the player's horizontal viewing direction; `Page Up` / `Page Down` raise/lower it. All seven bindings are registered with Minecraft's normal Controls menu in the Grand Builder category.
- Rotation uses a persistent integer grid pivot. Four rotations return to the exact original origin; translating moves the pivot too. Confirmation builds at the edited origin/orientation, not the original selection.
- Movement repeats after eight held ticks, then every four ticks. Rotation is one action per press. Controls are inactive while a chat/menu is open or no preview is pending; queued inactive clicks are discarded.
- The server checks ownership, held core, dimension, world height, and configured preview radius. Confirmation revalidates the adjusted bounds. Invalid changes leave the previous preview intact and send a localized message.
- Allow up to three distinct preview actions per server tick, with no duplicate action that tick. Do not reintroduce a global two-tick cooldown: it discards legitimate rapid rotation/movement and diagonal inputs.

## Kinetic Construction Modes

- Append IDs without reordering existing modes: Flying Blocks = 11, Reverse Collapse = 12, Assembly Workshop = 13, From Model to Home = 14. Their short setup sequences take 12/32/32/48 active ticks, respectively, then use exactly the standard selected block budget and cycle delay. Speed controls remain visible; Insane still installs 512 work entries/tick by default. ETA includes the remaining setup once. Existing instant reveals and 12-second Clockwork behavior are unchanged.
- `KineticBuildPayload` supplies dimension, scene UUID, real placement cursor/budget/delay/age/setup, and a bounded prototype containing exact world targets and rotated native block-state IDs. Full prototypes are sent initially and every 200 active ticks; ordinary updates carry timing only. Install the same new jar on client and server.
- `KineticBlockRenderer` uses baked Minecraft block models, atlas UVs, native solid/cutout/translucent layers, target biome tint, and transformed normals. These are textured render-only block instances, not map-color cubes or falling-block entities. No visual collision, dropped items, or terrain damage. Special block-entity renderers and fluids are not animated by this model path, but their real placement/NBT still follows the normal build logic.
- Exclude air, terrain preparation, unchanged ordinary blocks, forbidden targets, and fully enclosed opaque interior cells from the visual prototype. Sample the remaining visible cells evenly if above 3072; global extracted native block frames are capped at 4096. These caps affect visuals only: every real work entry is still processed at the chosen speed.
- Cache model extraction in a bounded 512-entry, resource-reload-aware cache. Immutable frame snapshots separate game-state extraction from drawing. Scene stop/reveal clears ghost blocks; pause freezes motion, and disconnect/dimension changes clear state.
- Wait for every target chunk before advancing setup or extracting the first prototype, without loading chunks just for visual sampling. Retain a deduplicated chunk-anchor cache. Viewers returning within range receive a fresh prototype; missing chunks and owner-offline pauses must not advance the animation or ETA.
- Four original synthesized sound accents live under `sounds/effects/`: `block_flight.ogg`, `rubble_pull.ogg`, `assembly_servo.ogg`, and `model_unfold.ogg`. No externally downloaded audio or extra asset license is required.
- Latest isolated integrated-game QA passes all 48 new mode/speed combinations, exact cursor/ETA checks, pause/setup freezing, protected chest and air cleanup, custom caps, live speed changes, and unloaded-chunk waits. Preview checks cover reversible rotation/translation, world-height rejection, edited placement, and actual client/server key rebinding. All four textured 3D scenes were rendered and captured in Minecraft 1.21.11. Local harness: `build/qa-src/dev/grandbuilder/qa/QaKinetic.java`; successful log: `build/qa-kinetic-final5.log`. Neither ships in the jar.
- The user's main workflow is recording construction with Flashback and editing it into YouTube Shorts. Flashback playback, Iris shader packs, and remote multiplayer have NOT been tested for these new render-only scenes; do not promise compatibility without a dedicated recording/playback test.

## Clockwork Drive / Заводной ход

- Russian display name should stay `Заводной ход`.
- English display name should stay `Clockwork Drive`.
- This mode should build any selected structure in `12` seconds.
- The day/night cycle should change quickly but smoothly while the build is active.
- Current target feel: fast time-lapse, not jerky jumps.
- The mode has a custom ticking sound:
  - `src/main/resources/assets/grand_builder/sounds.json`
  - `src/main/resources/assets/grand_builder/sounds/effects/clock_tick.ogg`
- If the build pauses or the owner goes offline, world time should be restored to normal.
- Speed selection should be hidden for this mode.

## Build / Verify Commands

Use PowerShell in the repo root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
.\gradlew.bat compileJava compileClientJava
.\gradlew.bat verifyEffectGeometry
.\gradlew.bat build
```

Copy the built jar:

```powershell
$jar = Resolve-Path 'build\libs\grand_builder-1.1.0.jar'
$downloads = Join-Path $env:USERPROFILE 'Downloads\grand_builder-1.1.0.jar'
$modsDir = Join-Path $env:APPDATA '.minecraft\versions\mega shorts mod\mods'
New-Item -ItemType Directory -Path $modsDir -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination $downloads -Force
Copy-Item -LiteralPath $jar -Destination (Join-Path $modsDir 'grand_builder-1.1.0.jar') -Force
```

## World-Space Effects (September 2026)

- `GrandBuilderWorldEffects` renders custom mesh geometry using Fabric 1.21.11 world extraction and drawing events. Do not use the old 1.21.1 rendering API found in global Gradle caches.
- `EffectGeometry` contains five distinct animated scenes: a solid flying saucer with a conical beam, a vertical rift with orbiting shards, a faceted meteor with a tapering trail and flying debris, a clock mechanism with teeth/hands/gimbal rings, and curved aurora curtains.
- Custom shader resources live in `assets/grand_builder/shaders/core/effect.vsh` and `.fsh`. Three materials separate depth-writing solid bodies, translucent surfaces, and additive emissive geometry. Emissive is self-lit, not a promise of real dynamic lighting or screen-space bloom.
- `BuildEffectPayload` now carries scene UUID, dimension, world bounds, mode, phase, duration, age, progress, and intensity. Update both client and server jars together. The server synchronizes nearby viewers every 10 ticks, including pause/offline state, and sends reveal/stop events.
- Client scenes are bounded (16 active scenes, 256-block range, 60-tick stale timeout). Immutable frame snapshots separate game state extraction from mesh drawing.
- `CameraEffectsMixin` applies brief render-only shake/roll. It must never change the player's aim or stored yaw/pitch. The HUD only supplies a short low-opacity exposure flash.
- Rotation and phase age are separate so the reveal transition does not reset the moving objects. Pause freezes motion; dimension changes and disconnects clear scenes.
- Experimental geometry no longer uses the server's old particle-built UFO/rings/curtains or per-block particle floods. Standard-mode placement particles, clock ticks, and sound accents remain.
- `verifyEffectGeometry` is part of `check` and covers 990 mode/phase/size/frame samples, 100 actor samples, 80 lightning samples, 540 kinetic block poses, and all 20 direction/orientation combinations: finite coordinates, quad grouping, bounded meshes, 3D extents, motion, distinct scenes, opacity, stable mode IDs and exact block contact. Preview tests check four-rotation reversibility and translation around the persistent pivot. Pure `BuildCadence` checks cover all 12 default speeds, partial batches, per-tick caps, countdown progression and overflow.
- Verified in an isolated integrated Minecraft 1.21.11 test world: all five scenes, arrival/reveal transitions, two viewing angles, 20 captured frames, and successful custom shader compilation. Not tested with Iris shader packs, remote multiplayer, or other rendering mods. Additive blending uses premultiplied RGB in the fragment shader; omitting this makes transparent beams opaque white.
- Development `runClient` may hit a Windows Gradle shortened-classpath loader conflict. An explicit Java argument file with the full classpath works; argument files on this machine need Windows-1251 encoding for Cyrillic paths. QA harnesses under `build/` and test worlds under `run/` are ignored and must not enter the shipped jar.

## Publishing

- September 2026 delivery: the new 3D-effects jar was built, copied to both requested local folders, and source changes were pushed to `main`. The GitHub `v1.1.0` release asset was not replaced because release-publishing access was unavailable; do not assume that asset matches the current sources.
- Latest local delivery adds four kinetic construction modes and editable, rebindable previews while retaining the standard speed alignment, menu ETA, lightning, directional construction, dismantling and optional destructive charge. Current jar SHA256: `EE87B407161E702D683A6AE46145FD44C29CF7CBEC787CBF17266031F06B3122`. The release asset limitation above still applies. Delivery hashes from older revisions are not the current jar.

- If committing, keep commits focused and do not revert unrelated user changes.
- The user has been using tag/release `v1.1.0`; previous updates force-moved this tag and replaced the release asset.
- When updating the release body, mention user-facing changes clearly in Russian.

## Placement Actor (September 2026)

- Append new enum modes without reordering existing IDs. Herobrine is ID 6; Builder Charge is ID 7; Lightning is ID 8; Reverse Build is ID 9; Dismantle is ID 10.
- Latest request supersedes the earlier "double Herobrine speed" requirement: Herobrine, lightning, directional construction and every dismantle style must use the SAME block budget/cycle delay as standard construction. "Insane" must really mean 512 blocks/tick at the default cap, not one block per animation. Never restore square-root speed scaling or a serial per-block animation bottleneck.
- `BuildCadence` is shared by real budgets and ETA, and respects custom speed profiles and `maxBlocksPerTick`. Actor windup ends at the standard cycle boundary; its 3-tick visual recovery is client-only and must not stall the next batch. Windups are capped at 24 ticks even with longer custom delays. Speed changes during a windup retime it immediately.
- Render at most six representative placement effects per cycle, with one early windup and bounded afterimages/lightning volleys; ALL work entries still execute at the real speed. Do not send or render hundreds of actors/bolts every tick. Air, unchanged ordinary blocks and forbidden placements do not receive fake gestures; visual sampling must never read an unloaded target and accidentally load its chunk.
- `HerobrinePlacementPayload` carries scene ID, dimension, monotonic placement sequence, exact target, actor position, block state ID, duration and cycle age. Register it on both client and server. Held cubes use the target block's map color; they are stylized geometry, not the full textured block model.
- The server handles air cleanup, already-present ordinary blocks and forbidden replacements without fake gestures. Placement still uses the shared chunk, replacement and block-entity guards. Pauses and owner-offline pauses freeze the actor; rollback stops its scene; disconnect/dimension changes clear it.
- Actor render state belongs to its bounded parent scene, with at most two 6-tick afterimages. Afterimages use the non-depth-writing translucent material. Do not spawn real NPC entities, modify player aim, or fill the world with particle silhouettes.
- Builder Charge waits 72 active ticks before the single-server-tick reveal and uses a 44-tick visual aftermath. Speed controls are hidden. Missing target chunks still pause installation, and rollback uses the normal snapshots.
- Historical QA before speed alignment verified the earlier single-block actor (180 ticks, then 90 ticks for 36 blocks). These timings are obsolete. Charge QA verified all 1602 test-structure blocks appearing at tick 72 with a neighboring sentinel untouched; its instant-reveal timing is unchanged. Iris/remote multiplayer remain untested.

## Build Timing, Ordering and Removal (Latest User Request)

- Keep remaining time visible in the menu header before starting, during preview, during construction/removal, and on pause. Pre-start estimates use a debounced server request keyed by the entire selection and monotonically increasing request ID; stale responses must not overwrite the current choice.
- Before selecting a site, the time is approximate: terrain preparation, protected/already-present blocks, missing chunks and server tick rate may change it. Construction estimates include air work entries because they consume the same per-cycle budget as standard construction. Dismantle pre-estimates count non-air blocks; preparation then refines this to the actually changed removal targets. Clockwork remains 240 active ticks and instant reveals keep their fixed arrival sequences. Do not advertise wall-clock guarantees during lag or unloaded-chunk waits.
- `BuildOptions` normalizes irrelevant settings away: start side only for Reverse, removal style only for Dismantle, destructive explosion only for Builder Charge. A dangerous blast choice resets on mode change and menu reopening. No extra options row should appear for ordinary modes.
- Dismantle captures original states/NBT, instantly installs under the normal replacement/chunk guards, holds for 12 ticks, then gradually restores the originals. Remove only blocks actually changed by this job, and only if their installed state and block-entity NBT are still unchanged. Preserve blocks edited by the player during the animation; restore blueprint air-cleanup originals only where the space is still air. Do not erase a pre-existing identical structure.
- Standard and Herobrine/lightning dismantling proceed top-down. Charge dismantling waits for its 72-tick arrival, then removes from the center outward at the selected speed, with a fitted pressure/removal front. Charge dismantling does NOT enable real terrain damage. Its explosion must not replay on pause/resume or the final STOP event.
- Optional destructive Builder Charge uses a bounded 4-8 strength native `ServerExplosion` before the instant house installation, with actual block/entity damage and player knockback. It is off by default and requires a separate localized confirmation before the normal preview/placement confirmation. Warn about damage to nearby structures, entities and drops.
- Snapshot the whole bounded blast area for block rollback, including air and block-entity NBT, before detonating. Wait for all blast chunks and use a one-shot latch. Direct explosion physics avoids the normal explosion particle packet, so custom 3D visuals remain dominant. Native TNT chain reactions, drops and entity damage are NOT fully reversible; never promise a complete world undo.
- New payloads: estimate request/response and lightning strike. Existing build request, scene, and actor payloads gained option/removal fields. Upgrade client and server together. Scene data carries removal state, facing/start-side encoding and the blast option; render extraction remains immutable and bounded.
- Earlier integrated QA passed 13 functional scenarios: pause/ETA freeze, pre-start server ETA, all five directions, four removal styles with original-state restoration and player-edit preservation, both explosion choices and rollback. Menu bounds/overlap checked at GUI sizes 640x360 and 427x240. The old 90/72-tick actor/lightning timings were superseded by standard speed alignment. QA lives only under ignored `build/` and `run/`; it must not ship. Iris, remote multiplayer and third-party rendering mods remain untested.
- Speed-alignment integrated QA compares 96 combinations (standard, Herobrine, lightning, reverse and four removal styles across all 12 presets), including exact cursor increments, live ETA, pauses, protected chest and air cleanup. It also checks configured caps, speed changes mid-windup, a custom 40-tick delay, and safe pause/resume at an unloaded chunk without loading it during visual sampling. Actual rendered demos build 8192 blocks in 16 active ticks at Insane, with matching pre-start ETA, bounded actors/bolts, and a matching-speed lightning dismantle. Use `build/qa-src/dev/grandbuilder/qa/QaSpeedParity.java` locally; it is excluded from the shipped jar.
