# Modrinth / CurseForge Copy

## Short Description
Build massive structures with cinematic animation, safe preview confirmation, live speed control, and rollback.

## Long Description
Grand Builder is a Fabric mod focused on one thing: making large structure placement feel cinematic without sacrificing server safety.

Use Builder Core to open the Builder Console, choose a structure, preview it in-world, confirm placement, and control the build while it runs.  
The full flow is designed for real multiplayer usage: clear status, chunk-aware placement, permissions, build limits, and rollback confirmation.

### Core Features
- Animated large-structure building
- Preview before start
- Confirm/cancel preview flow
- Pause/resume and live speed controls
- Rollback with safety confirmation
- Custom structure capture around player
- External structures from `grand_builder/structures`
- Supports `.nbt`, `.schem`, `.schematic`, `.litematic`
- RU/EN localization

### Multiplayer / Server Safety
- Permission mode (`ops_only` by default)
- Dimension allow/block lists
- Max blocks, radius, and concurrency limits
- Per-tick placement budget
- Action cooldown against spam
- Chunk-aware placement behavior
- Replace rules (`air_only`, `replaceable`, `all`)

### Installation
1. Install Fabric Loader for Minecraft `1.21.11`.
2. Install Fabric API.
3. Put `grand_builder-<version>.jar` into `mods`.

### External Structures Folder
- `<root>/grand_builder/structures`
  - `root` = game directory (singleplayer/client) or server root (dedicated server)

### Compatibility Notes
- Minecraft `1.21.11`
- Fabric only
- Java `21+`

### Known Issues
- First scan of very large structure folders can take noticeable time.
- If target chunks are not loaded, build can wait until chunks load (configurable).

## Suggested Release Feature Bullets
- Cinematic animated mega-builds
- Safer preview/confirm workflow
- Live control: speed, pause/resume, rollback
- Dedicated-server friendly guardrails
- External schematic support in four common formats
