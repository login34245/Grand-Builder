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
- Strong preference for 3D-looking effects where Minecraft particles, geometry-like rings, beams, arcs, or layered motion can support it.
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
.\gradlew.bat compileJava compileClientJava
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

## Release Notes

- If committing, keep commits focused and do not revert unrelated user changes.
- The user has been using tag/release `v1.1.0`; previous updates force-moved this tag and replaced the release asset.
- When updating the release body, mention user-facing changes clearly in Russian.
