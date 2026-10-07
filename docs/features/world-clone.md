# /worlds clone

**Verdict.** Build it as a second front-end to [import](world-import.md)'s transfer routine (`WorldTransfer`).

`/worlds clone <from> <to>` works like this:

1. Take a consistent snapshot of the source mc-worlds world, using vanilla's own backup recipe scoped to one level:
   - If the source is loaded: `ServerLevel.save(…, flush=true, …)` (= `/save-all flush`), then `level.noSave = true` (= `/save-off`) for the duration of the copy, then restore it.
   - If it is unloaded: mark it busy so lazy-loading can't open it mid-copy.
2. Copy its one dimension folder, `dimensions/<ns>/<path>`, into staging off the server thread. In 26.x that folder holds the region, entity and POI files and *all* per-dimension SavedData: tickets, raids, world border, dragon fight, and mc-worlds' per-world weather and clock.
3. Atomically move it to the new id.
4. Register a Codec-copy of the source's `WorldsLevelData` with the spawn's dimension rewritten.

Everything is copied and nothing is reset. A clone is a copy, and vanilla commands already reset any per-world piece: `/execute in <to> run weather|time|worldborder|forceload …` all act per world in mc-worlds. So there are no `--reset-*` flags. No mixins, no Codec changes. The only new policy hooks are the shared busy guard plus one condition in `ActiveLevelState` and one in `DeleteCommand`.

Paths: mc-worlds code is under `src/main/java/dev/wroud/mc/worlds/`. Vanilla code is MC **26.3-rc-1** decompiled Mojmap under `net/minecraft/`. Staging, the atomic install, the id checks, the single-job guard, startup cleanup, shutdown cancellation and the shared i18n keys are specified in [world-import.md § Proposed design](world-import.md#proposed-design) and are not repeated here.

## Summary

| Item | Decision |
|---|---|
| Command | `/worlds clone <from> <to>` |
| Source | Any saved mc-worlds world (`WorldsData`), loaded or not. Vanilla dimensions: open question (they would reuse import's level-data synthesis) |
| Snapshot | Loaded: on the server thread, `save(null, true, false)`, then `noSave = true` until the copy finishes, then restore it. Unloaded: busy, and `loadOrCreateWorld` refuses it |
| Copied | The whole `dimensions/<ns>/<path>` folder, with nothing excluded |
| Level data | `WorldsLevelData.CODEC` round-trip: stem (incl. undecodable passthrough), seed, provider, `generate_structures`, `prepare_spawn`, `game_time`, `initialized`. Respawn dimension → `<to>`. `lazy` → default `true` (open question) |
| Reset flags | None. Use vanilla `/execute in <to> run …` |
| Threading | Snapshot on the server thread → copy and atomic move on the transfer worker → restore `noSave`, register and load on the server thread |
| Permission | `LEVEL_ADMINS`, the same as `create` |
| Codec / mixins / datagen / API | None / none / none / none |

## Use cases & demand

- **Iterate on a build or test redstone on a copy.** This is the use case the Fabric Multiverse README gives (`README.md:111-112` @ `dbe0c79`): *"useful when iterating on a build or for when you need to test the behavior of a redstone contraption."*
- **Templates.** Keep a lobby or minigame arena as a template and clone a fresh copy per round or event. Multiverse-Core #3502 describes an internal server holding *"templates for mini-game worlds"*.
- **Creative copy of a survival world**, or a "before" copy before a risky WorldEdit or datapack change, without stopping the server.
- **Consistency is the recurring pain point.**
  - Multiverse-Core added "force save world to disk before cloning" (#3210) and then made it the default (#3212).
  - It added cloning *unloaded* worlds later (#3422).
  - Fabric Multiverse tells users to run `/save-all` themselves and warns that a full clone "may hang your server" (`README.md:114-115, 130-132`).
- **How the reference mods clone** is in the import doc's comparison table ("Clone" row):
  - Melius WorldManager has no clone.
  - Fabric Multiverse copies synchronously with no flush.
  - Multiverse-Core saves with flush, then copies the whole folder synchronously, skipping `uid.dat`/`session.lock`.
- The maintainer agreed (2026-10-07) that clone must be built on import.

## Vanilla alignment

- **Snapshot = vanilla's documented backup procedure, scoped to one level.**
  - `/save-off` is just `level.noSave = true` for every level (`server/commands/SaveOffCommand.java:13-15` → `MinecraftServer.java:2051-2062`).
  - `/save-all flush` is `ServerLevel.save(null, true, …)` per level (`MinecraftServer.java:587-606`).
  - Clone does both for the source level only, and restores the previous `noSave` value, so an admin's global `/save-off` survives.
- **Unit of copy = vanilla's unit of dimension storage.** In 26.x `getDimensionPath(key)` holds everything a dimension owns:
  - `region/`, `entities/` and `poi/` (`DimensionType.java:113-115`, `ServerLevel.java:250-257`)
  - its own `data/` (`ServerChunkCache.java:93-101`)
  - Server-global things (players, maps, scoreboard, gamerules) live elsewhere, so they are neither duplicated nor touched (see the import doc's internals).
- **No exclusion list.** `uid.dat`, `session.lock` and `level.dat` sit at the world root, never in a dimension folder. Multiverse-Core needs `CLONE_IGNORE_FILES` only because it copies whole Bukkit world folders (`WorldManager.java:94-97` @ `fe49efe`).
- **No reset flags.** Each per-world piece already has a vanilla command that acts on the executing level in mc-worlds:
  - weather (`mixin/WeatherCommandMixin.java:15-37`)
  - time and clocks (`mixin/TimeCommandMixin.java:14-20`)
  - `worldborder` and `forceload`, which are vanilla per-level SavedData
  - Multiverse-Core's `--reset-world-config/--reset-gamerules` reset Multiverse's *own* per-world config, which mc-worlds doesn't have.

## Current state in mc-worlds

- **Saved vs loaded worlds.**
  - `WorldsData.getLevelsData()` returns a copy of every saved world (`manager/WorldsData.java:52-54`).
  - `WorldsManager.getWorldIds()` returns only *loaded* ones (`manager/WorldsManager.java:43-45`).
  - `CUSTOM_WORLD_SUGGESTIONS` uses the loaded set (`command/WorldsCommands.java:54-56`), so `<from>` needs a saved-worlds suggestion provider.
- **Lazy load.**
  - `getLevel` turns a saved id into `loadOrCreate` (`mixin/MinecraftServerMixin.java:38-64`). A copy of an *unloaded* world can be corrupted by any `getLevel` during the copy: `/worlds tp`, `/execute in`, or vanilla internals (memory `project_getlevel_lazy_load_loops.md`).
  - `loadOrCreateWorld` already skips ids in `unreadableGenerators` (`WorldsManager.java:36, 70-72`). The busy set goes next to it.
- **Unload and save behaviour that could race a copy:**
  - `ActiveLevelState` stops an empty world after 1200 ticks unless it holds the world spawn (`server/level/state/ActiveLevelState.java:21-26`).
  - `StoppingLevelState` **forces `noSave = false`** while draining chunks (`StoppingLevelState.java:30-33`).
  - The end-tick handler then `save(null, true, isDeleteOnClose())` and closes the level (`McWorldMod.java:51-75`), ignoring `noSave`.
  - So a loaded source must be pinned against auto-stop for the copy's duration.
- **States.** Init → Activation → Active → Stopping → Stopped (`server/level/CustomServerLevel.java:62-63, 111-133`). `isDeleteOnClose()` is at `:115-117`, and `stop(true)` marks a delete (`:146-158`).
- **Per-world state that lives in the dimension folder:**
  - weather, as `<dim>/data/minecraft/weather.dat` (`CustomServerLevel.java:58`)
  - clocks, as `<dim>/data/mc-worlds/world_clocks.dat` (`server/level/PerWorldClocks.java:17-28`)
  - Both are copied with the folder, so the clone starts with the source's weather and time.
- **WorldsLevelData.**
  - `CODEC` (`manager/level/data/WorldsLevelData.java:20-24`).
  - `setSpawn` (`:79-82`), `setLazy` (`:70-72`), `getRespawnData` (`:74-77`).
  - `WorldGeneratorData` keeps an undecodable stem as `Either.right(Dynamic)` passthrough (`WorldGeneratorData.java:19-20, 55-57`). A Codec round-trip preserves it, so a clone of a world whose generator can't be decoded right now is still faithful.
  - The respawn's `GlobalPos.dimension` is set to the world's own key by `/worlds settings spawn` (`command/SettingsCommand.java:157-158`). `RespawnData.DEFAULT` means "no custom spawn" (`:107-111`).
- **Providers with a `null` stem** rebuild it from `(id, seed)`: `WorldsManager.java:84-86`. mc-stargate keys its spec by world id and, when missing, rolls it from the seed (`mc-stargate/src/main/java/dev/wroud/mc/stargate/server/level/StargateLevelProvider.java:31-40`). Its worlds are created with a `null` stem (`mc-stargate/.../StargateWorldsManager.java:37-39`).
- **Delete** resolves with `getLevel` and `stop(true)` (`command/DeleteCommand.java:33-38`). The folder goes only after stop and close (`WorldsManager.java:134-147`). It must refuse a busy id.
- **Create's success message and teleport link** (`command/CreateCommand.java:139-165`) are the template for clone's messages.

## Vanilla internals (MC 26.3-rc-1)

- **Flush.** `ServerLevel.save(progress, flush, noSave)` runs, in order:
  - `saveLevelData(flush)`, which is `SavedDataStorage.saveAndJoin()` when flushing (`ServerLevel.java:878-906`)
  - `chunkSource.save(flush)` → `ChunkMap.saveAllChunks(true)`: saves every accessible chunk, flushes POI, processes unloads and **joins the IO worker** (`ChunkMap.java:414-434`)
  - `entityManager.saveAll()`
- **noSave.**
  - Public field (`ServerLevel.java:208`).
  - Gates chunk-unload saving (`ChunkMap.java:450-452`) and autosave (`MinecraftServer.java:596`).
  - POI ticking runs before that gate (`ChunkMap.java:446-448`), so a vanilla `save-off` backup and this clone have the same, community-accepted consistency.
  - Server stop resets `noSave = false` on all levels before the final save (`MinecraftServer.java:645-649`). An interrupted clone therefore never leaves the source unsaved.
- **What the dimension folder contains** (`SavedDataStorage.java:56-57` path rule):
  - `chunk_tickets` (`TicketStorage.java:40-42`)
  - `raids` (`ServerLevel.java:280`)
  - `ender_dragon_fight` (`ServerLevel.java:301-304`, `EnderDragonFight.java:120-122`)
  - `world_border` (`ServerLevel.java:1566-1570`, `WorldBorder.java:25-27`)
  - The dragon fight stores the dragon's UUID (`EnderDragonFight.java:100, 113`), which is consistent with the copied entities.
- **Global data a clone shares with its source:**
  - Maps live in `server.getDataStorage()` (`ServerLevel.java:1590-1601`).
  - A copied map item keeps its `map_id`, and `MapItem` only updates a map while the holder is in the map's recorded dimension (`world/item/MapItem.java:89`). Maps carried in the clone keep showing, and only update from, the source.
- **Entity UUIDs** are tracked per level (`PersistentEntitySectionManager.java:34, 62-63`). Identical UUIDs in source and clone are legal and only warn within one level.

## Proposed design

### Command

```
/worlds clone <from> <to>
```

- **`<from>`:** `IdentifierArgument` with a new `SAVED_WORLD_SUGGESTIONS` (`WorldsData` keys).
- **`<to>`:** `IdentifierArgument`.
- **Permission:** `requires(LEVEL_ADMINS)`. It reads nothing outside the server's own world.
- **Messages:** `clone.started`, then `clone.success` plus the existing Teleport link.

### Validation (server thread, never `getLevel`)

1. **`<from>`:**
   - In `WorldsData`, else `UNKNOWN_WORLD_EXCEPTION` (`WorldsCommands.java:58-59`).
   - Not busy.
   - If loaded (`MinecraftServerAccessor.getLevels()`), it must be a `CustomServerLevel` that `isActive()`, so not initializing, stopping or `isDeleteOnClose()`. Otherwise refuse with `busy`.
2. **`<to>`:** the import doc's target-id checks (WorldsData, loaded keys, path segments, the folder must not exist, not reserved).
3. **No other transfer job running.**

### Pipeline

Steps marked † are import's `WorldTransfer` steps.

| # | Thread | Step |
|---|---|---|
| 1 | server | Reserve `<to>` † and mark `<from>` busy. Encode the source `WorldsLevelData` with `WorldsLevelData.CODEC` over `RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())`, in the same tick as the snapshot so `game_time` matches the chunks. If loaded: `level.save(null, true, false)`, `prevNoSave = level.noSave`, `level.noSave = true` |
| 2 | worker † | Copy `getDimensionPath(fromKey)` → `<world>/mc-worlds/staging/<uuid>/dim` with the cancellable tree copy and space check |
| 3 | worker † | `Files.move(staged, getDimensionPath(toKey), ATOMIC_MOVE)`, then delete staging |
| 4 | server | If loaded and still present: `level.noSave = prevNoSave`. Unmark busy. Decode the copy, `setSpawn(RespawnData.of(toKey, pos, yaw, pitch))` unless it is `RespawnData.DEFAULT`, `setLazy(true)`. `McWorld.loadOrCreate(to, data)` → `executeWhenLevelReady` → success. Release `<to>` † |

- **Failure in 2–3:** run step 4's restore and unmark, delete staging, send `clone.failed`. The source is never written by the clone. It was only flushed, which is a normal save.
- **Server stop mid-copy:** cancel. Vanilla resets `noSave` and saves the source normally. Staging is cleaned at the next start.

### What the clone gets

| Data | Where | Clone |
|---|---|---|
| Blocks, entities, POI | `<dim>/region`, `entities`, `poi` | copied |
| Forceloaded/persistent chunk tickets | `<dim>/data/minecraft/chunk_tickets.dat` | copied (`/execute in <to> run forceload remove all`) |
| Raids | `<dim>/data/minecraft/raids.dat` | copied |
| World border | `<dim>/data/minecraft/world_border.dat` | copied (`/execute in <to> run worldborder …`) |
| Dragon fight (End-like) | `<dim>/data/minecraft/ender_dragon_fight.dat` | copied |
| Weather | `<dim>/data/minecraft/weather.dat` | copied (`/execute in <to> run weather …`) |
| Time and clocks | `<dim>/data/mc-worlds/world_clocks.dat` | copied (`/execute in <to> run time …`) |
| Other mods' per-dimension data | `<dim>/data/<modid>/…` | copied |
| Generator: stem, seed, provider, `generate_structures`, `prepare_spawn` | `worlds_data.dat` | copied (Codec) |
| Spawn | `worlds_data.dat` | copied, dimension rewritten to `<to>` |
| `game_time`, `initialized` | `worlds_data.dat` | copied |
| Load on startup (`lazy`) | `worlds_data.dat` | reset to `true` (open question) |
| Players inside, player data, maps, scoreboard, gamerules | global | not copied (shared server state) |

### Files

| File | Change |
|---|---|
| `command/CloneCommand.java` (new) | Tree, validation, messages |
| `manager/WorldTransfer.java` (from import) | `cloneWorld(server, from, to, callbacks)`: snapshot, copy, install, register |
| `manager/WorldsManager.java` | Busy set (shared with import). `isBusy(id)` |
| `server/level/state/ActiveLevelState.java` | Add `&& !manager.isBusy(id)` to the auto-stop condition (`:21-23`) |
| `command/DeleteCommand.java` | Refuse a busy id before `stop(true)` |
| `command/WorldsCommands.java` | `.then(CloneCommand.build())`, `SAVED_WORLD_SUGGESTIONS` |
| `lang/en_us.json`, `README.md`, `CHANGELOG.md` | Keys below, a "Clone Command" section, an "Added" entry |

No Codec change: the round-trip uses the existing `WorldsLevelData.CODEC` and its setters. No datagen, because translations are hand-written (`src/datagen/java/dev/wroud/mc/worlds/DataGenerator.java:11-15`). No API change.

### i18n keys

| Key | en_us |
|---|---|
| `dev.wroud.mc.worlds.command.clone.started` | `Cloning %s into %s, please wait` |
| `dev.wroud.mc.worlds.command.clone.success` | `World %s cloned into %s. ` (followed by `…create.success.teleport`) |
| `dev.wroud.mc.worlds.command.clone.failed` | `Cloning %s failed: %s` |
| `dev.wroud.mc.worlds.command.clone.exception.source_busy` | `World %s is loading, stopping or being deleted; try again shortly` |
| shared (import doc) | `…command.exception.folder_exists`, `…command.exception.busy`, `…create.exception.world_already_exists`, `…command.exception.unknown_world` |

## Edge cases & risks

- **Writes during a `noSave` copy.** POI ticking and entity-section unloads can still write a few sections while the copy runs, which is the same as a vanilla `save-off` backup. The worst case is a POI or entity section slightly newer than its chunk in the clone. If exactness matters more than convenience, use the fallback in open question 1.
- **Flush cost.** `save(…, true, …)` blocks the tick until the IO worker drains, the same as `/save-all flush`. It is proportional to dirty chunks, not world size.
- **Long copies keep the source pinned.** It won't auto-stop and can't be deleted. Players can keep playing in it, but their changes after the snapshot are not in the clone, as expected.
- **Unloaded source during the copy.** `/worlds tp <from>` and `/execute in <from>` fail as an unknown world until the copy ends, because `loadOrCreateWorld` returns `null` for busy ids. Acceptable for a short window. A dedicated "busy" message would need `TeleportCommand` to distinguish it.
- **Null-stem providers.**
  - The clone keeps the provider and a `null` stem, so the provider rebuilds by the *new* id.
  - mc-stargate rolls the spec from the seed when the id has none (`StargateLevelProvider.java:35-37`), and the seed is copied, so new chunks normally match.
  - A provider whose generator depends on the id itself would generate different terrain past the copied area.
  - Option: store the loaded source's `LevelStem` in the clone when it is Codec-serializable.
- **Duplicate UUIDs** of copied entities (villagers, pets, the dragon) across the two levels are legal. A UUID selector (`/tp <uuid>`) returns the first level in `getAllLevels()` order that has it (`commands/arguments/selector/EntitySelector.java:148-153`).
- **Maps** in the clone keep showing the source world (`MapItem.java:89`).
- **Spawn.** Overworld-like clones copy the per-world spawn. Nether- and End-like clones ignore it, as their sources do (`command/WorldLocation.java:36-51`).
- **The world-spawn world** can be cloned, and stays pinned anyway. The clone does **not** become the world spawn, because the server respawn data still names `<from>`.
- **Disk space** is checked up front, as for import. A clone doubles the dimension's size.

## Open questions for the maintainer

1. **Snapshot of a loaded source.**
   - Recommended: the per-level `save-off` + `save-all flush` described above.
   - Fallback: require the source to be unloaded, by stopping it when it is empty and refusing when players are inside. That gives a perfectly quiescent copy, but the world-spawn world and occupied worlds can't be cloned.
2. **Load on startup.** Reset to lazy, the default of `create` and import (recommended), or copy the source's flag?
3. **Vanilla dimensions as source** (`/worlds clone minecraft:overworld creative`)? The mechanism is the same: the overworld is always loaded, so it takes the `noSave` path.
   - Its level data would come from the server's `WorldGenSettings` and respawn data.
   - Its weather and clocks are server-global, so they would be transplanted into the clone's folder exactly like import steps 5 and 7.
   - Recommended as a follow-up once import's level-data builder exists.
4. **Provider hook.** Should `ServerLevelProvider` get a default `onCloned(server, from, to)` so mods like mc-stargate can copy id-keyed data they keep elsewhere, such as `StargatePresetData`? Not needed for vanilla-provider worlds.

## Manual test plan

Run `./gradlew runServer` and connect `./gradlew runClient`.

1. **Loaded source with a player inside.**
   - `worlds create a`, teleport, build something, and *don't* save.
   - Run `worlds clone a b`. Expect "Cloning…" and then "World a cloned into b. [Teleport]".
   - Teleport to `b`: the last-placed blocks are present (the flush worked).
   - Back in `a`, keep building. After the clone, the log shows normal autosaves for `a` again (`noSave` restored).
   - Run `/save-off` before a second clone: `a` stays unsaved afterwards (previous value restored).
2. **Unloaded source.**
   - Leave `a` and wait for the unload (about 60 s).
   - Clone it as `c`. During the copy, `worlds tp a` fails, with no `Loading world: …a` line in the log.
   - After the copy, `a` and `c` both load.
3. **Per-world state carried over.**
   - In `a`, set `weather thunder`, `time set midnight`, `worldborder set 200` and `forceload add 0 0`. Clone it as `d`.
   - `d` has the same weather, time and border, and chunk 0,0 is forceloaded.
   - `/execute in d run weather clear` does not change `a`.
4. **End-like world.** `worlds create e from-dimension minecraft:the_end`, kill the dragon, clone it as `f`. `f` has no dragon and its exit portal is open.
5. **Refusals:**
   - Unknown `<from>`.
   - Existing `<to>`.
   - An orphan folder `world/dimensions/minecraft/g` for `<to>`.
   - `<from>` while it is initializing (clone right after `create`).
   - A clone while another clone or import runs.
   - `worlds delete a` while `a` is being cloned.
6. **Interruption.**
   - `kill -9` during the copy. On restart, staging is gone, `<to>` is not registered, and `<from>` loads intact.
   - A normal stop mid-copy saves `<from>`.
7. **Persistence and delete.** Restart: both worlds exist. `worlds delete b` removes only `b`'s folder.
8. **mc-stargate** (if installed): clone a dialed address world. New chunks beyond the copied area continue the same terrain.

## Sources

- Fabric Multiverse README (clone use case, `/save-all` advice, "may hang"): https://github.com/senseiwells/Multiverse/blob/dbe0c79daf25203d544cf890c2c228a4bad198f2/README.md
- Fabric Multiverse clone implementation: https://github.com/senseiwells/Multiverse/blob/dbe0c79daf25203d544cf890c2c228a4bad198f2/src/main/kotlin/me/senseiwells/multiverse/commands/MultiverseCommand.kt
- Multiverse-Core docs (`/mv clone`): https://mvplugins.org/core/fundamentals/commands-usage/
- Multiverse-Core clone source: https://github.com/Multiverse/Multiverse-Core/blob/fe49efe276ec50b069280b4de9d9eb29966d57d7/src/main/java/org/mvplugins/multiverse/core/world/WorldManager.java · https://github.com/Multiverse/Multiverse-Core/blob/fe49efe276ec50b069280b4de9d9eb29966d57d7/src/main/java/org/mvplugins/multiverse/core/commands/CloneCommand.java
- Multiverse-Core PRs and issues: https://github.com/Multiverse/Multiverse-Core/pull/3210 · https://github.com/Multiverse/Multiverse-Core/pull/3212 · https://github.com/Multiverse/Multiverse-Core/pull/3422 · https://github.com/Multiverse/Multiverse-Core/issues/3502
- Import design (shared routine, comparison table): [world-import.md](world-import.md)
