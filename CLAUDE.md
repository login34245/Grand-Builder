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
- `verifyEffectGeometry` is part of `check` and covers 450 mode/phase/size/frame samples, finite coordinates, quad grouping, bounded mesh counts, 3D extents, motion, distinct scenes, and opacity.
- Verified in an isolated integrated Minecraft 1.21.11 test world: all five scenes, arrival/reveal transitions, two viewing angles, 20 captured frames, and successful custom shader compilation. Not tested with Iris shader packs, remote multiplayer, or other rendering mods. Additive blending uses premultiplied RGB in the fragment shader; omitting this makes transparent beams opaque white.
- Development `runClient` may hit a Windows Gradle shortened-classpath loader conflict. An explicit Java argument file with the full classpath works; argument files on this machine need Windows-1251 encoding for Cyrillic paths. QA harnesses under `build/` and test worlds under `run/` are ignored and must not enter the shipped jar.

## Publishing

- If committing, keep commits focused and do not revert unrelated user changes.
- The user has been using tag/release `v1.1.0`; previous updates force-moved this tag and replaced the release asset.
- When updating the release body, mention user-facing changes clearly in Russian.
