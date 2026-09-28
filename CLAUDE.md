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
- `Herobrine` / `Херобрин`: a white-eyed, voxel-faced 3D builder teleports beside the next real block and places it with a synchronized arm swing; short translucent afterimages mark his previous positions.
- `Builder Charge` / `Строительный заряд`: a finned metal charge follows an arcing flight path, flashes a red countdown light, then bursts into a 3D pressure shell, radial streaks, flying casing panels and an expanding ground shockwave. A fitted assembly cage and rising scan outline the instant house reveal. Visual-only is the default; the user explicitly requested an optional real destructive blast (see below).
- `Lightning Strike` / `Грозовой разряд`: custom branching 3D lightning strikes the exact next block position; installation or removal occurs at its contact tick. No lightning entity, fire, or vanilla lightning renderer.
- `Reverse Build` / `Перевёртыш`: selectable top/front/right/left/back start side, relative to the structure's orientation. No bottom option. A fitted 3D guide plane travels in the real placement direction; fluids still install last.
- `Dismantle` / `Разборка`: instantly install the selected structure, hold it briefly, then remove it gradually. Its style selector (standard/Herobrine/lightning/charge) appears only for this mode. Terrain adaptation is disabled and hidden here; removal speed stays meaningful even for charge dismantling.

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
- `verifyEffectGeometry` is part of `check` and covers 630 mode/phase/size/frame samples, 240 actor samples, 80 lightning samples and all 20 direction/orientation combinations: finite coordinates, quad grouping, bounded meshes, 3D extents, motion, distinct scenes, opacity, stable mode IDs, exact doubled placement cadence and held-block contact behavior for both construction and removal.
- Verified in an isolated integrated Minecraft 1.21.11 test world: all five scenes, arrival/reveal transitions, two viewing angles, 20 captured frames, and successful custom shader compilation. Not tested with Iris shader packs, remote multiplayer, or other rendering mods. Additive blending uses premultiplied RGB in the fragment shader; omitting this makes transparent beams opaque white.
- Development `runClient` may hit a Windows Gradle shortened-classpath loader conflict. An explicit Java argument file with the full classpath works; argument files on this machine need Windows-1251 encoding for Cyrillic paths. QA harnesses under `build/` and test worlds under `run/` are ignored and must not enter the shipped jar.

## Publishing

- September 2026 delivery: the new 3D-effects jar was built, copied to both requested local folders, and source changes were pushed to `main`. The GitHub `v1.1.0` release asset was not replaced because release-publishing access was unavailable; do not assume that asset matches the current sources.
- Latest local delivery includes doubled Herobrine speed, menu ETA, lightning, directional construction, dismantling and optional destructive charge. Both requested jar copies match SHA-256 `06C580515CC118E6D92744C7B11294F0697FFAC102C56DD475BB854F0EAB118E`. English/Russian menu layouts, dangerous-choice confirmation/reset and all 13 integrated-world scenarios passed. The release asset limitation above still applies.

- If committing, keep commits focused and do not revert unrelated user changes.
- The user has been using tag/release `v1.1.0`; previous updates force-moved this tag and replaced the release asset.
- When updating the release body, mention user-facing changes clearly in Russian.

## Placement Actor (September 2026)

- Append new enum modes without reordering existing IDs. Herobrine is ID 6; Builder Charge is ID 7; Lightning is ID 8; Reverse Build is ID 9; Dismantle is ID 10.
- The user requested Herobrine to run exactly twice as fast at EVERY speed preset. `HerobrineTiming` now halves the old cycle (1-12 ticks); odd old durations alternate floor/ceil across placements so each pair takes exactly the old single-cycle duration. Server placement happens at `contactTick`, not on packet arrival. Speed labels and ETA use this actual cadence, including the remaining tail of an in-flight animation.
- `HerobrinePlacementPayload` carries scene ID, dimension, monotonic placement sequence, exact target, actor position, block state ID, duration and cycle age. Register it on both client and server. Held cubes use the target block's map color; they are stylized geometry, not the full textured block model.
- The server handles air cleanup, already-present ordinary blocks and forbidden replacements without fake gestures. Placement still uses the shared chunk, replacement and block-entity guards. Pauses and owner-offline pauses freeze the actor; rollback stops its scene; disconnect/dimension changes clear it.
- Actor render state belongs to its bounded parent scene, with at most two 6-tick afterimages. Afterimages use the non-depth-writing translucent material. Do not spawn real NPC entities, modify player aim, or fill the world with particle silhouettes.
- Builder Charge waits 72 active ticks before the single-server-tick reveal and uses a 44-tick visual aftermath. Speed controls are hidden. Missing target chunks still pause installation, and rollback uses the normal snapshots.
- Integrated Minecraft QA verified Herobrine's 36 real placements at exact contact ticks, 180 active ticks at Normal speed, pause/resume, protected chest and air cleanup. Charge QA verified all 1602 test-structure blocks appearing at tick 72 with a neighboring sentinel block untouched. Captured arrival, placement and blast frames from two views. Iris/remote multiplayer remain untested.

## Build Timing, Ordering and Removal (Latest User Request)

- Keep remaining time visible in the menu header before starting, during preview, during construction/removal, and on pause. Pre-start estimates use a debounced server request keyed by the entire selection and monotonically increasing request ID; stale responses must not overwrite the current choice.
- Before selecting a site, the time is approximate: terrain preparation, protected/already-present blocks, missing chunks and server tick rate may change it. Normal estimates respect both cycle delay and `maxBlocksPerTick`; actor estimates omit air entries, including in previews and active jobs. Clockwork remains 240 active ticks. Do not advertise wall-clock guarantees during lag or unloaded-chunk waits.
- `BuildOptions` normalizes irrelevant settings away: start side only for Reverse, removal style only for Dismantle, destructive explosion only for Builder Charge. A dangerous blast choice resets on mode change and menu reopening. No extra options row should appear for ordinary modes.
- Dismantle captures original states/NBT, instantly installs under the normal replacement/chunk guards, holds for 12 ticks, then gradually restores the originals. Remove only blocks actually changed by this job, and only if their installed state and block-entity NBT are still unchanged. Preserve blocks edited by the player during the animation; restore blueprint air-cleanup originals only where the space is still air. Do not erase a pre-existing identical structure.
- Standard and Herobrine/lightning dismantling proceed top-down. Charge dismantling waits for its 72-tick arrival, then removes from the center outward at the selected speed, with a fitted pressure/removal front. Charge dismantling does NOT enable real terrain damage. Its explosion must not replay on pause/resume or the final STOP event.
- Optional destructive Builder Charge uses a bounded 4-8 strength native `ServerExplosion` before the instant house installation, with actual block/entity damage and player knockback. It is off by default and requires a separate localized confirmation before the normal preview/placement confirmation. Warn about damage to nearby structures, entities and drops.
- Snapshot the whole bounded blast area for block rollback, including air and block-entity NBT, before detonating. Wait for all blast chunks and use a one-shot latch. Direct explosion physics avoids the normal explosion particle packet, so custom 3D visuals remain dominant. Native TNT chain reactions, drops and entity damage are NOT fully reversible; never promise a complete world undo.
- New payloads: estimate request/response and lightning strike. Existing build request, scene, and actor payloads gained option/removal fields. Upgrade client and server together. Scene data carries removal state, facing/start-side encoding and the blast option; render extraction remains immutable and bounded.
- Integrated Minecraft QA for the new modes passed 13 scenarios: Herobrine 36 placements in 90 active ticks at Normal (formerly 180), pause/ETA freeze, pre-start server ETA, lightning 36 placements in 72 ticks, all five build directions, four dismantle styles with original block restoration and player-edit preservation, both explosion choices, and block rollback after each. Menu bounds/overlap checked at GUI sizes 640x360 and 427x240. QA lives only under ignored `build/` and `run/`; it must not ship. Iris, remote multiplayer and third-party rendering mods remain untested.
