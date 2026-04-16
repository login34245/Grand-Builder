# Grand Builder Testing Checklist

## Singleplayer
- [ ] Open Builder Console with right-click and with `B`.
- [ ] Select each built-in structure and create preview.
- [ ] Confirm preview via right-click and via `Enter`.
- [ ] Cancel preview via `X` and menu button.
- [ ] Pause/resume during active build.
- [ ] Speed up/down during active build.
- [ ] Rollback completed build (double-confirm flow).
- [ ] Capture custom structure and build it.

## Dedicated Server
- [ ] Server starts with config file auto-generated.
- [ ] `/grandbuilder status` returns expected values.
- [ ] `/grandbuilder reload` applies config changes.
- [ ] Non-op player blocked when permission mode is `ops_only`.
- [ ] Allowed dimension works; blocked dimension denies build.

## Multiplayer
- [ ] Two players can run builds up to configured concurrency limit.
- [ ] Concurrency limit blocks additional starts with clear message.
- [ ] Action cooldown prevents packet spam abuse.
- [ ] Owner disconnect pauses build when configured.
- [ ] Owner reconnect resumes build correctly.

## Creative / Survival
- [ ] Creative behavior matches config (`allowCreativeWithoutCore`).
- [ ] Survival requires core in hand (default).
- [ ] Optional survival core consumption works if enabled.

## Large Structures & Performance
- [ ] External structure near `maxBlocksPerBuild` previews and builds.
- [ ] Build respects `maxBlocksPerTick`.
- [ ] Unloaded chunk areas trigger waiting behavior (no crash/freeze).
- [ ] Build resumes after chunks are loaded.

## Rollback Safety
- [ ] Rollback requires sneak when configured.
- [ ] Rollback requires second confirmation in window.
- [ ] Rollback restores block entities where valid.
- [ ] Missing/unloaded rollback positions are skipped with message.

## Preview / Confirm Flow
- [ ] Clear status shown for preview-ready mode.
- [ ] Preview cancels when core leaves hand.
- [ ] Preview cancels on dimension change.
- [ ] Confirm without sneak is blocked when configured.

## Invalid Data Handling
- [ ] Invalid schematic file does not crash server.
- [ ] Unknown block states are safely ignored/fallback.
- [ ] Oversized block entity NBT is skipped safely.

## Localization
- [ ] Full EN strings show correctly.
- [ ] Full RU strings show correctly (UTF-8, no mojibake).
- [ ] Keybind/category labels localized correctly.
