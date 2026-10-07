# World regen / reset

**Verdict.** Build option (b): a thin `/worlds regen <id> [<seed>|random]` that runs the existing delete path and then the existing create path, replaying the generator that mc-worlds already stores. It adds no new world-generation or file-handling mechanism. Chunky does not cover this. Chunky is a pre-generator. Its `trim` command deletes chunk records for a selection: region, entities and POI. It is meant for cleaning up past a border, it always keeps the same seed, and it leaves the dimension's `data/` folder alone. That folder holds the world border, raids, forceloads, weather, per-world clocks and the End dragon fight. Chunky's own warning also says the server must be fully restarted after a trim. On mc-worlds, Chunky's world lookup goes through `getLevel`, so naming a lazy world in `/chunky trim` loads that world, and the trim then runs on a loaded world. Chunky is the right tool for the step *after* a regen (pre-generating the fresh world), not for the reset. Running `/worlds delete` then `/worlds create` today gets most of the way. It loses the world's provider, its load-on-startup flag and any custom spawn. The admin must remember the original preset or dimension arguments and the seed. Worlds created through the provider API cannot be recreated by command at all. And the create fails with "already exists" if it runs before the asynchronous delete finishes. Only mc-worlds holds the data needed to replay a world correctly (`level_stem`, `provider`, `lazy`), so this is not something another mod already does well. It also matches vanilla: the result is the same as stopping the server, deleting `dimensions/<ns>/<path>/` and starting it again.

## Summary

| Item | Recommendation |
|---|---|
| Command | `/worlds regen <id>` (same seed) · `/worlds regen <id> <seed>` · `/worlds regen <id> random` |
| Mechanism | `CustomServerLevel.stop(true)` (same as delete) → after `WorldsManager.unloadWorld` deletes the folder → `loadOrCreateWorld(id, copiedLevelData)` (same as create) |
| Kept | `level_stem`, `provider`, `generate_structures`, `prepare_spawn`, `lazy` (loadOnStartup), seed unless overridden; custom spawn only when the seed is unchanged |
| Reset | Entire dimension folder (`region/`, `entities/`, `poi/`, `data/`); `game_time` |
| Untouched (global) | Gamerules, scoreboard, random sequences, maps, player data |
| Players inside | Evicted exactly as `/worlds delete` does; not returned (success message has a clickable Teleport) |
| Confirmation | None, consistent with `/worlds delete` (open question) |
| New code | 1 command class, a copy-with-seed on `WorldGeneratorData`, a pending-recreate hook in `WorldsManager`, ~5 lang keys |
| Datagen / API | None / optional `WorldsManager.regenerate(...)` used by the command |
| Chunky's role | Complementary: pre-generate the regenerated world (recipe below) |

## Use cases & demand

- **Resource/mining worlds** reset monthly so the main world stays intact. On Bukkit this is a whole plugin category: ResourceWorldResetter, CyberWorldReset, Karta WorldReset, WorldResetPlugin and BubbleReset. `/mv regen` is built into Multiverse-Core.
- **End or Nether resource worlds.** A reset should also bring back the dragon fight, which lives in the dimension's `data/` folder, not in its chunks. Trim cannot do this.
- **Event, arena and build-contest worlds** wiped between rounds.
- **Fabric today.** No mod resets an arbitrary runtime-created dimension:
  - *Resource World* (Aethro) ships its own single resource dimension with timed new-seed resets, and only for 1.21.x.
  - *World Reset* (Libreh/Worldless) swaps the server's main overworld/nether/end for pooled dimensions, for speedrun or minigame play.
  - *ResetWorld* deletes the whole world folder and stops the server.
  - senseiwells' Fabric *Multiverse* has create/delete/clone/tp but no regen.
- **mc-worlds' own tracker** has no regen request; its 5 issues are all closed bugs. Demand here is inferred from the ecosystem, not requested.

## Comparison: Chunky trim vs delete+create vs regen

| | Chunky `trim` | `/worlds delete` + `/worlds create` (today) | Proposed `/worlds regen` | Multiverse `/mv regen` (Bukkit, reference) |
|---|---|---|---|---|
| Scope | Chunks inside/outside a shape | Whole world | Whole world | Whole world |
| What is deleted | Region files wholly in the selection are deleted. Otherwise the chunk's header entry is zeroed in `region/`, `poi/` and `entities/` (`TrimCommand.java:177-265`) | Whole `dimensions/<ns>/<path>/` (`WorldsManager.java:136-147`) | Same as delete | World folder (`WorldManager.java:918`) |
| Per-dimension `data/` (border, raids, forceloads, weather, clocks, dragon fight, wandering trader) | **Kept**, so it goes stale | Reset | Reset (border: open question) | Border, gamerules and config kept by default (`WorldManager.java:933-948`) |
| Seed | Same only (no seed option; the generator is untouched) | Any, but must be re-typed; random if omitted (`WorldsCreator.java:95-97`) | Same by default, explicit, or `random` | Same by default; `--seed` / `--seed <v>` (`RegenWorldOptions.java:210-217`) |
| Generator/preset | Unchanged | Must re-type preset/dimension; provider is forced to `mc-worlds:default` (`WorldsLevelData.java:154-157`) | Copied from stored `level_stem` + `provider` | Copied (`WorldManager.java:906-916`) |
| loadOnStartup / spawn | Unchanged | Reset to lazy / re-prepared (`WorldsLevelData.java:150`, `WorldSettingsData.java:18-21`) | `lazy` kept; spawn kept only if same seed | Spawn kept only if same seed (`WorldManager.java:904`) |
| Safe on a loaded world | **No**. "You will be required to restart your server fully" (Chunky `lang/en.json:29`). Runs on Chunky's own threads (`TaskScheduler.java:16-30`) while vanilla keeps up to 256 region files open (`RegionFileStorage.java:23-24`) and rewrites each file's cached header on every chunk write (`RegionFile.java:42-73,308-310`), so trimmed entries can come back | Yes (state machine), but create fails until the delete finishes | Yes; waits for the unload | Yes |
| Unloaded mc-worlds world | Lookup `server.getLevel(id)` (`FabricServer.java:34-46`) **loads it** via `MinecraftServerMixin.java:38-64`; tab completion lists only loaded worlds (`FabricServer.java:49-53`) | Delete loads it first (`DeleteCommand.java:33`) | Same as delete | n/a |
| Players inside | Not handled | Evicted to world spawn (`StoppingLevelState.java:39-52`) | Same | Must use `--remove-players`; they are returned after (`RegenCommand.java:128-131`) |
| Confirmation | `/chunky confirm` (`TrimCommand.java:143-144`) | None | None (open question) | `/mv confirm` (`RegenCommand.java:89-93`) |
| Fabric / MC 26.3 | Yes | Yes | Yes | No (Bukkit only) |

## Vanilla alignment

- **A dimension is a folder.** `DimensionType.getStorageFolder` resolves to `<world>/dimensions/<ns>/<path>` (`net/minecraft/world/level/dimension/DimensionType.java:113-115`). Chunks are in `region/` (`ChunkMap.java:173`), POI in `poi/` (`ChunkMap.java:201-202`) and entities in `entities/` (`ServerLevel.java:252-253`). Per-dimension SavedData is in `data/` (`ServerChunkCache.java:93-101`). Vanilla's only way to reset a dimension is to delete that folder while the server is stopped. Regen does the same thing at runtime through the same close path vanilla uses: `ServerLevel.close` → `ServerChunkCache.close` → `IOWorker.close` (`ServerLevel.java:1893-1896`, `ServerChunkCache.java:307-312`, `IOWorker.java:231-242`).
- **What lives in a dimension's `data/` folder in 26.3:**
  - `minecraft:raids` (`ServerLevel.java:280`)
  - `minecraft:world_border` (`ServerLevel.java:1566-1569`)
  - `minecraft:chunk_tickets`, i.e. forceloads (`ServerChunkCache.java:102`)
  - `minecraft:ender_dragon_fight` on End-like types (`ServerLevel.java:301-303`)
  - `minecraft:weather` (mc-worlds per-world: `CustomServerLevel.java:58`)
  - `mc-worlds:world_clocks` (`PerWorldClocks.java:17-28`)
  - `minecraft:wandering_trader` (`WanderingTraderSpawnerMixin.java:40-45`)
  
  Structure starts and references are in chunk NBT (`SerializableChunkData.java:149,451`), so they go with `region/`.
- **What is global and not per world:** random sequences, gamerules, boss bars and the scheduled-function queue (`MinecraftServer.java:346-348,363-364`), plus maps (`ServerLevel.java:1590-1597`). Multiverse's `--reset-gamerules` has no equivalent here, because 26.x gamerules are server-wide.
- **The seed is not part of the stored generator.** The `LevelStem` codec is type + generator (`LevelStem.java:12-15`), and the noise generator is biome source + settings (`NoiseBasedChunkGenerator.java:61-66`). Terrain comes from the level seed (`ChunkMap.java:181-190`), which mc-worlds supplies per world (`CustomServerLevel.java:141-144`). So "same stem, new seed" behaves exactly like vanilla.
- **Precedent.** Multiverse's regen is literally delete + create with the creation options copied, and it keeps the spawn only when the seed is unchanged (`WorldManager.java:901-931`). The proposal follows that shape.

## Current state in mc-worlds

Paths are relative to `src/main/java/dev/wroud/mc/worlds/`. The working tree is clean. The `DeleteCommand` changes mentioned in the brief are already committed in `1ca2524`.

- **Delete is asynchronous.** `DeleteCommand.delete` calls `customLevel.stop(true)` and reports success immediately (`command/DeleteCommand.java:31-52`). Then:
  - `stop(true)` sets `deleteOnClose` in any state and, for the world-spawn world, moves the spawn to the Overworld (`server/level/CustomServerLevel.java:146-158`).
  - `StoppingLevelState` evicts players on the server thread and does not reach Stopped while players remain or the chunk map still has work (`server/level/state/StoppingLevelState.java:24-52`).
  - At `END_SERVER_TICK`, stopped levels are removed from the map, saved with `noSave = deleteOnClose`, closed, and `UNLOAD` fires (`McWorldMod.java:51-75`).
  - `WorldsManager.unloadWorld` deletes the folder. If that fails it falls back to `forceDeleteOnExit`. It then removes the `WorldsData` entry (`manager/WorldsManager.java:129-153`).
- **Same-id recreate timing.** `/worlds create` calls `validLevelId` → `server.getLevel` (`manager/WorldsCreator.java:115-121`).
  - While the old level is stopping it is still registered, so create throws `world_already_exists` (`command/CreateCommand.java:167-170`).
  - The same happens before a freshly lazy-loaded level is put in the map, because `WorldsManager` returns the existing handle (`WorldsManager.java:65-68`, `CustomServerLevel.java:65-71`).
  - Once the `END_SERVER_TICK` cleanup has run, the entry is gone and create succeeds.
  - Typed by hand this is usually fine. In one function tick or command chain it fails.
- **Recoverable creation parameters.**
  - `WorldGeneratorData` persists the *resolved* `level_stem`, plus `seed`, `generate_structures`, `prepare_spawn`, `lazy` and `provider` (`manager/level/data/WorldGeneratorData.java:22-30`). An undecodable stem is kept as a raw `Dynamic` and `getLevelStem()` returns null (`:19-20,55-57`).
  - The preset or dimension *arguments* are not stored. They are not needed, because the resolved stem is stored. The resolved stem is also more exact than replaying `from-preset <p>` without a dimension, which picks `findFirst()` (`WorldsCreator.java:72-76`).
  - The seed is visible with `/execute in <id> run seed`: vanilla reads `getLevel().getSeed()` (`net/minecraft/server/commands/SeedCommand.java:13`), and that is per world here.
- **What delete+create loses.**
  - `WorldsCreator` always builds `getDefault(...)`: default provider, `lazy=true`, `prepare_spawn=true` and fresh settings (`WorldsCreator.java:102`, `manager/level/data/WorldsLevelData.java:146-157`). So `loadOnStartup` (`command/SettingsCommand.java:74-94`), any custom spawn (`SettingsCommand.java:130-168`) and a third-party provider are lost.
  - `game_time` restarts at 0 (`WorldsLevelData.java:23`). That is correct for a reset.
- **Tab completion.** `/worlds delete` suggests only loaded worlds: `CUSTOM_WORLD_SUGGESTIONS` → `getWorldIds()` → live handles (`command/WorldsCommands.java:54-56`, `WorldsManager.java:43-45`). Regen should suggest saved ids from `WorldsData` instead (`WorldsCommands.java:44-45`).
- **Lazy-load trap** (memory `project_getlevel_lazy_load_loops.md`): `getLevel` loads saved worlds (`mixin/MinecraftServerMixin.java:38-64`). Any regen code that only needs *data* must read `WorldsData`, not call `getLevel`. Only the stop step needs the live level, as `/worlds delete` does.
- **No datagen for lang.** `DataGenerator` registers only `DimensionTypeTagsProvider` (`src/datagen/java/dev/wroud/mc/worlds/DataGenerator.java:11-15`). `en_us.json` is hand-written.

## Proposed design

### Command surface

```
/worlds regen <id>              regenerate with the current seed
/worlds regen <id> <seed>       regenerate with this seed (long)
/worlds regen <id> random       regenerate with WorldOptions.randomSeed()
```

The command is gated by `Commands.hasPermission(Commands.LEVEL_ADMINS)` and registered in `WorldsCommands.register` (`WorldsCommands.java:63-69`). It suggests every saved custom world id. The `random` literal sits beside the `LongArgumentType` node.

### Flow (composition only)

1. Look up `WorldsData.getLevelData(id)`. If there is none, throw `unknown_world`. If `getLevelStem() == null`, refuse with `regen.exception.unreadable_generator`, because the recreated world would be skipped (`WorldsManager.java:84-94`).
2. Build the next `WorldsLevelData` and do not mutate the old one:
   - `generator` → copy with the new seed. Add `WorldGeneratorData#withSeed(long)` that reuses the private constructor (`WorldGeneratorData.java:45-53`), so the raw stem `Either`, `provider`, `lazy`, `generate_structures` and `prepare_spawn` carry over unchanged.
   - `settings` → if the seed is unchanged, copy `respawn` + `initialized` (the Multiverse rule). Otherwise use `new WorldSettingsData()`, so `SpawnPreparationHelper` re-prepares the spawn (`state/SpawnPreparationHelper.java:59-82`).
   - `game_time` → 0.
3. Get the live level the way delete does (`DeleteCommand.java:33`). If `isDeleteOnClose()` is already set, refuse with `regen.exception.in_progress`. Otherwise register a pending regen `{id → nextData, source}` in `WorldsManager`, then call `customLevel.stop(true)`. That gives the same player eviction and spawn-world handling, including the `delete.spawn_reset` message (`DeleteCommand.java:37-46`).
4. In `WorldsManager.unloadWorld`, after `removeLevelData` (`WorldsManager.java:150`), if a pending regen exists for `location`:
   - If the dimension folder still exists (delete failed), drop the regen and report `regen.exception.files_remain`. Never load new data over old region files.
   - Otherwise run `server.execute(() -> loadOrCreateWorld(id, nextData))`. Then call `LevelActivationUtil.executeWhenLevelReady` → `regen.success` with the clickable Teleport. Factor `WorldsCreator.java:104-112` into an overload that takes a prepared `WorldsLevelData`, so create and regen share one path.
5. `/worlds delete <id>` clears any pending regen for `id`.

### Kept vs reset

| Data | Where | Regen |
|---|---|---|
| `level_stem`, `provider`, `generate_structures`, `prepare_spawn` | `WorldsData` → `core` | Kept |
| `lazy` (loadOnStartup) | `core.lazy` | Kept |
| `seed` | `core.seed` | Kept unless `<seed>` / `random` |
| `settings.respawn`, `settings.initialized` | `WorldsData` → `settings` | Kept if seed unchanged, else reset |
| `game_time` | `WorldsData` | Reset (0) |
| Chunks, entities, POI, structure refs | `region/`, `entities/`, `poi/` | Reset |
| World border, weather, per-world clocks, raids, forceloads, dragon fight, wandering trader | `data/` | Reset (border: see open questions) |
| Gamerules, scoreboard, maps, player inventories/positions, advancements | Server-global | Untouched |

### i18n keys (`src/main/resources/assets/mc-worlds/lang/en_us.json`)

| Key | Text |
|---|---|
| `dev.wroud.mc.worlds.command.regen.regenerating` | `Regenerating world %s with seed %s, please wait` |
| `dev.wroud.mc.worlds.command.regen.success` | `World %s regenerated. ` (followed by the existing `create.success.teleport` click, `CreateCommand.java:153-161`) |
| `dev.wroud.mc.worlds.command.regen.exception.in_progress` | `World %s is already being deleted or regenerated` |
| `dev.wroud.mc.worlds.command.regen.exception.unreadable_generator` | `World %s cannot be regenerated: its generator could not be read` |
| `dev.wroud.mc.worlds.command.regen.exception.files_remain` | `World %s was deleted but its files could not be removed; restart the server and create it again` |

The existing keys `command.exception.unknown_world` and `command.delete.spawn_reset` are reused (`en_us.json:3,10`).

### Datagen, docs

- **Datagen:** none. Lang is hand-maintained (`DataGenerator.java:11-15`).
- **README:** add a "Regen Command" section after Delete (`README.md:48-50`) with the recipe below.
- **CHANGELOG:** add an `Unreleased → Added` line.

### Recommended recipe (README), composing with Chunky and vanilla

```
/worlds regen minecraft:mining random
/execute in minecraft:mining run worldborder set 4000        # border is per-dimension data
/execute in minecraft:mining run forceload add 0 0           # keep the world active while nobody is in it
/chunky world minecraft:mining
/chunky radius 2000
/chunky start
# when Chunky reports completion:
/execute in minecraft:mining run forceload remove 0 0
```

The `forceload` lines exist because Chunky's ticket types use flags `2` and `2|4` (`FabricWorld.java:40-41`) and do not set `FLAG_KEEP_DIMENSION_ACTIVE` (`8`, `TicketType.java:15`). An empty mc-worlds world therefore keeps counting `emptyTime` (`ServerLevel.java:417-424`), and `ActiveLevelState` stops it after 1200 ticks (`state/ActiveLevelState.java:21-25`). Vanilla's `FORCED` ticket has flags `15` (`TicketType.java:22`), so it keeps the world active. This comes from reading the code only and is not confirmed at runtime; test 10 below checks it.

## Edge cases & risks

- **Offline players whose saved position is in the regenerated world** log in at their old coordinates in new terrain. Vanilla uses the saved position verbatim when the dimension exists (`net/minecraft/server/network/config/PrepareSpawnTask.java:52-61`). The mc-worlds fix only covers a *missing* dimension (`mixin/fixes/PrepareSpawnTaskMixin.java:32-38`). Multiverse regen and Chunky trim behave the same. Document it; don't fix it in v1.
- **Beds and anchors in the old world:** vanilla's missing-respawn-block fallback applies (`ServerPlayer.java:1003-1016`).
- **World-spawn world:** `stop(true)` moves the spawn to the Overworld (`CustomServerLevel.java:147-152`). Regen inherits that unless the maintainer decides otherwise (see open questions).
- **Unloaded lazy world:** regen loads it just to stop it, as delete does today (fix `1ca2524`). The cost is one load; the result is correct.
- **Delete failure** (for example Windows file locks → `forceDeleteOnExit`, `WorldsManager.java:139-147`): regen must check that the folder is gone before recreating, or new settings would load old chunks (step 4).
- **Server stops mid-regen:** the pending regen is in memory only and is lost. Today's delete has the same window: `removeLevelData` runs even when the folder was not deleted (`WorldsManager.java:134-151`). After a restart the world is gone and its folder orphaned. This is a pre-existing problem; note it, don't widen it.
- **Concurrent commands:** a second regen or a `create` with the same id during the window is refused (`in_progress` / `world_already_exists`). A delete cancels a pending regen.
- **External references by id** keep pointing at the new terrain: maps (global, `ServerLevel.java:1590-1597`), lodestone compasses, and mc-stargate links. This is expected for a reset.
- **Pre-existing and unrelated to regen:** vanilla seeds `StructureCheck` and the End-gateway shuffle from the *server's* seed (`ServerLevel.java:287-303`), while chunk generation uses the per-world seed (`ChunkMap.java:181-190`). Any custom world whose seed differs from the main seed already has this mismatch, and a new-seed regen does not make it worse. Worth a separate look with `/locate`.
- **Scheduling:** don't add timers. Admins can use console/RCON cron or a datapack `/schedule`. A datapack needs `function-permission-level=3`, because the default is `GAMEMASTER` (`DedicatedServerProperties.java:93-98`) and `/worlds` requires `LEVEL_ADMINS` (`Commands.java:168`).

## Open questions for the maintainer

1. **Default seed.** Should `/worlds regen <id>` keep the seed, like Multiverse and the "regenerate" wording, or pick a random one, like `/worlds create <id>`? The recommendation is to keep it and use an explicit `random` literal.
2. **World border.** Reset it (vanilla folder semantics, smallest code) or carry it over like Multiverse's default? Carrying it over means reading the old `WorldBorder` settings and applying them through the vanilla setters `setCenter`, `setSize`, `setDamagePerBlock`, `setSafeZone`, `setWarningTime` and `setWarningBlocks` (`WorldBorder.java:163-272`). The recommendation is to reset it in v1 and document `/execute in … run worldborder set`, since resource-world admins script that anyway.
3. **World-spawn world.** Regenerate it and move the spawn to the Overworld (inherited from delete, with a message), or refuse? Or, when the seed is unchanged, restore the spawn into the new world?
4. **Confirmation.** Should there be a confirmation step? `/worlds delete` has none. Chunky, Multiverse-Core and senseiwells' Fabric Multiverse all confirm destructive actions. If yes, add it to delete and regen together.
5. **Returning evicted players.** Should players who were evicted be sent back after the regen, like Multiverse's `--remove-players`? The recommendation is no; the clickable Teleport is enough.
6. **Public API.** Expose `WorldsManager.regenerate(id, seed, callbacks)` so other mods can schedule resets, or keep it command-only?

## Manual test plan

Run on `./gradlew runServer` (world at `versions/latest/run/world/`). The console can be driven through a FIFO or the `mc-server-probe` skill. `res` means `minecraft:res`.

1. **Baseline.** Run `/worlds create res 123` and `/worlds tp res`. Place blocks. Run `/worlds settings loadOnStartup true`, `/worlds settings spawn here`, `/execute in minecraft:res run weather thunder` and `/execute in minecraft:res run time set 18000`.
2. **Same-seed regen with a player inside.** Run `/worlds regen res`. Expected:
   - The player is moved to world spawn.
   - The log shows `Saving chunks for level`, then `Creating new world: minecraft:res` (`WorldsManager.java:101`).
   - `regen.success` appears with a working Teleport.
   - On teleport back, the blocks are gone, `/execute in minecraft:res run seed` shows `123`, and `loadOnStartup` is still `true`.
   - The custom spawn is kept. Weather, time and border are back to defaults.
   - The `dimensions/minecraft/res/data/` files have fresh mtimes.
3. **Explicit seed.** Run `/worlds regen res 456`. Expected: the seed is `456`, the terrain is different, `Preparing spawn: minecraft:res` is logged, and the custom spawn is reset.
4. **Random seed.** Run `/worlds regen res random`. Expected: the printed seed matches `/seed` in the world.
5. **Preset and dimension worlds.**
   - `/worlds create flat from-preset minecraft:flat` → regen → still flat.
   - `/worlds create e from-dimension minecraft:the_end` → kill the dragon → regen → the dragon fight is back.
6. **Lazy world.** Leave `res` empty until `CustomServerLevel` auto-stops (60 s), then regen. Expected: it loads, stops and is recreated, with no load/unload loop in the log.
7. **Spawn world.** Run `/execute in minecraft:res run setworldspawn`, then regen. Expected: the behaviour matches the decision on open question 3, and no `Loading world` loop.
8. **Races.**
   - Two regens of `res` back to back → the second reports `in_progress`.
   - Regen then an immediate `/worlds delete res` → no recreate.
   - Regen then `/worlds create res` in the same tick → `world_already_exists`.
9. **Persistence.** Restart the server. Expected: `res` keeps its new seed, and `loadOnStartup` is still respected.
10. **Chunky interop** (drop Chunky into `versions/latest/run/mods`).
    - With no players in `res`: `/chunky world minecraft:res`, `/chunky radius 300`, `/chunky start`. Watch for an auto-stop around 60 s to confirm or refute the ticket-flag finding.
    - Repeat with `forceload add 0 0`.
    - Separately, run `/chunky trim minecraft:res` on an unloaded `res` and confirm it logs `Loading world: minecraft:res`.
11. **Offline player.** Log out underground in `res`, regen, then log back in. Record what happens; this is a documented limitation.

## Sources

- Chunky on Modrinth (loaders, MC 26.3 support, "Pre-generates chunks"): https://modrinth.com/plugin/chunky
- Chunky source, read at commit `ab45b8b` (https://github.com/pop4959/Chunky):
  - `common/src/main/java/org/popcraft/chunky/command/TrimCommand.java`
  - `common/src/main/java/org/popcraft/chunky/util/TaskScheduler.java`
  - `common/src/main/java/org/popcraft/chunky/platform/World.java`
  - `fabric/src/main/java/org/popcraft/chunky/platform/FabricServer.java`
  - `fabric/src/main/java/org/popcraft/chunky/platform/FabricWorld.java`
  - `common/src/main/resources/lang/en.json`
- Chunky trim history:
  - `delete` was renamed to `trim` in commit `168bf37` (2021-03-24, Chunky 1.2.x).
  - POI/entities support came in `95e82db` (2021-06-07).
  - Inside/inhabited options came in `66179d4`; the wiki says these are "since 1.3.90".
- Chunky wiki, Commands (trim purpose, backup warning, restart requirement): https://github.com/pop4959/Chunky/wiki/Commands
- Multiverse-Core command reference (`/mv regen` flags and defaults): https://mvplugins.org/core/fundamentals/commands-usage/
- Multiverse-Core source, read at commit `fe49efe` (https://github.com/Multiverse/Multiverse-Core):
  - `src/main/java/org/mvplugins/multiverse/core/world/WorldManager.java`
  - `src/main/java/org/mvplugins/multiverse/core/commands/RegenCommand.java`
  - `src/main/java/org/mvplugins/multiverse/core/world/options/RegenWorldOptions.java`
- Fabric reset mods:
  - World Reset: https://modrinth.com/mod/worldreset-fabric (source https://github.com/Libreh/Worldless, read at `98a2f84`)
  - Resource World (Aethro): https://modrinth.com/mod/aethro-resource-world
  - ResetWorld: https://modrinth.com/mod/resetworld
  - senseiwells Multiverse (Fabric): https://github.com/senseiwells/Multiverse
- Bukkit resource-world reset plugins (demand):
  - https://modrinth.com/plugin/resourceworldresetter
  - https://modrinth.com/plugin/cyberworldreset
  - https://modrinth.com/plugin/karta-worldreset
  - https://modrinth.com/plugin/worldresetplugin
  - https://modrinth.com/plugin/bubblereset
- Modrinth searches used: https://api.modrinth.com/v2/search?query=world%20reset&facets=[["categories:fabric"]] (also `resource world`, `mining world`, `regenerate world`; plugin facet for Bukkit)
- mc-worlds issues (no regen request): https://github.com/wroud/mc-worlds/issues
- Minecraft 26.3-rc-1 decompiled Mojmap sources (Loom cache `minecraft-common-6974b2190e-26.3-rc-1-sources.jar`):
  - `ServerLevel`, `ServerChunkCache`, `ChunkMap`, `IOWorker`, `RegionFile`, `RegionFileStorage`
  - `DimensionType`, `LevelStem`, `NoiseBasedChunkGenerator`, `MinecraftServer`
  - `WorldBorder`, `TicketType`, `TicketStorage`
  - `PrepareSpawnTask`, `ServerPlayer`, `SeedCommand`, `SerializableChunkData`
  - `DedicatedServerProperties`, `Commands`
