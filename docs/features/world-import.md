# /worlds import

**Verdict.** Build it. Keep it a thin orchestration of vanilla's own world-opening pipeline, not a bespoke copier.

`/worlds import <id> <folder> [<dimension>]` takes a save folder that sits in the server's own saves directory: the `LevelStorageSource` base dir, which is the server root on a dedicated server and `saves/` in singleplayer. The steps:

1. Validate the folder with vanilla's symlink and session-lock rules.
2. Copy `level.dat`, `data/` and the chosen dimension into a staging folder, off the server thread.
3. Upgrade the staged save exactly as a dedicated server does at startup: `LevelSummary` checks, then `DataFixers.getFileFixer().fix(...)`. This also converts the legacy `region/`, `DIM-1/` and `DIM1/` layout to `dimensions/minecraft/<dim>/`.
4. Pre-upgrade its chunks under their *original* dimension id.
5. Atomically move that one `dimensions/<ns>/<path>` folder into the server world.
6. Register a `WorldsLevelData` built from the source's `world_gen_settings.dat` (generator and seed) and `level.dat` (spawn and game time).

Everything that 26.x stores server-globally is deliberately left out: players, advancements, maps, scoreboard, gamerules, datapacks. The feature needs no new mixin and no Codec change. It adds one command and one shared transfer routine, which [clone](world-clone.md) reuses, plus a "busy" guard in `WorldsManager`.

Paths: mc-worlds code is under `src/main/java/dev/wroud/mc/worlds/`. Vanilla code is MC **26.3-rc-1** decompiled Mojmap (`minecraft-common-6974b2190e-26.3-rc-1-sources.jar`) under `net/minecraft/`. Per memory, rc-1 → 26.3 needed no mod changes.

## Summary

| Item | Decision |
|---|---|
| Command | `/worlds import <id> <folder>` imports the source overworld. `/worlds import <id> <folder> <dimension>` imports any source dimension. |
| Source location | One folder name inside the server's `LevelStorageSource` base dir (vanilla's saves dir). No absolute paths, no `..`, no archives |
| Source validation | `level.dat` present; `allowed_symlinks.txt` rules; not locked by another game; not the server's own world |
| Version policy | Same checks as dedicated-server startup (manual conversion, other series), plus refuse downgrades |
| Upgrade | Vanilla `FileFixerUpper` on the staged copy, then vanilla `RegionStorageUpgrader` for the chunks with the source dimension's datafix context |
| Generator for new chunks | Source `world_gen_settings.dat` entry for that dimension, with its seed. Refuse if it can't be decoded |
| Spawn / time | `level.dat` `spawn` if it is in that dimension, `level.dat` `Time` → `game_time`, `initialized=true` |
| Copied with the dimension | `region/`, `entities/`, `poi/` and the dimension's `data/` (tickets, raids, border, dragon fight), plus optionally source weather and clock |
| Not imported | Player data, advancements, stats, maps, scoreboard, command storage, bossbars, gamerules, difficulty, datapacks, `generated/` |
| Threading | Validation on the server thread → one worker thread (copy, fix, upgrade, atomic move) → registration and load on the server thread |
| Permission | Recommended `LEVEL_OWNERS` (touches the server disk). Alternative `LEVEL_ADMINS` like the rest |
| Codec / mixins / datagen / API | None / none / none / none |

## Use cases & demand

- **Bring a singleplayer world onto the server** as one more world, with the source save untouched. This is the core of Multiverse-Core `/mv import` and Melius `/wm import` (table below).
- **Hosted-panel archives.** Melius #12: *"I am using World Manager on a Fabric server to manage an archive of community worlds… via the /wm import GUI"*. Melius #11: a user uploads a folder or `.zip` to a Pterodactyl panel and import fails with "Failed to find/read level.dat".
- **Maps people download.** Melius #2, "How can I import my map?", was answered by adding `/wm import` in 1.2.0.
- **Nether and End of a vanilla save.** Multiverse-Core #2389 (2020): *"I successfully imported a vanilla world save but this only creates an overworld dimension. I want the corresponding Nether and End."* That is the reason for the optional `<dimension>` argument.
- **Legacy layouts.** Multiverse-Core #3298: importing from `world/DIM*` and `world/dimensions` broke in MV5. Here, vanilla's own file fixer handles both layouts.
- **Migration.** Today the README tells World Manager users to `/worlds create` with the *same id, type and seed* so mc-worlds adopts the folder, and only classic Overworld/End/Nether work (`README.md:83-87`). Import replaces guesswork with reading `world_gen_settings.dat`. The `mc-worlds/worlds_data.dat` fallback also covers moving an mc-worlds world between servers.
- The maintainer approved this on 2026-10-07, with clone to be built on top of it.

## How other mods do it

The brief listed Multiverse (Fabric) as having import. **It doesn't.** All six branches have only `create`, `clone`, `delete`, `teleport` and `list` (`MultiverseCommand.kt:64-140` @ `dbe0c79`), and no `import` literal on any branch.

| | Melius WorldManager (Fabric, `61fe96b`, 26.1–26.3) | Multiverse (Fabric, senseiwells, `dbe0c79`, 26.3) | Multiverse-Core (Bukkit/Paper, `fe49efe`) |
|---|---|---|---|
| Command | `/wm import <path>` (player only), then a chest GUI. Pick dimensions and type a new id per dimension. Op level 4. [IC#L78-L90] | **No import** | `/mv import <name> <env> [-g gen] [--biome] [--no-adjust-spawn] [-f] [-gs json]`. Permission `multiverse.core.import` [MVI#L44-L47] |
| Source location / validation | `gameDir.resolve(path)`: absolute paths and `..` accepted, unvalidated. Folder, zip, rar or tar.gz [IC#L50-L55, L92] | — | Imports **in place**. Folder must be in the world container (Paper 26.1+: `<level>/dimensions/<ns>/<key>`). [WFR#L38-L42]. Name regex. 2 of {level.dat, DIM1, DIM-1, data, region, …} must exist [WNC#L35-L45] |
| Which dimension | Every dimension listed in the source `WorldGenSettings`. Files come from the *running server's* `getDimensionPath(originalId)`, so, reading the code (not run), legacy `region/`/`DIM*` saves copy nothing [IC#L130-L136] | — | One per command (`env`). On 26.1+ a legacy layout returns `REQUIRES_MIGRATION` and Paper converts it [WNC#L183-L188] |
| level.dat / generator | Reads `Data.WorldGenSettings` or `world_gen_settings.dat`: seed and `dimensions`, decoded per entry against server registries. Undecodable entries are dropped [IC#L155-L212] | — | The generator must be given (`-g`). No seed option. Bukkit reads the save's own level.dat [MVI#L46-L47; docs] |
| Data version | No DataVersion check anywhere in the import path. Chunks rely on on-load fixing [IC#L88-L212] | — | Left to Bukkit/Paper |
| Copy | Synchronous on the server thread, straight into the target with `REPLACE_EXISTING`. No temp dir or rename [AE#L55-L70] | — | None (in place) |
| `data/`, players | Only the dimension's `data`, `region`, `entities`, `poi` [IC#L49]. Global data and players skipped. **Zip-slip**: entry names are not normalized [AE#L56-L62] | — | Whole Bukkit world folder stays as-is |
| Spawn | `Optional.empty()`, so vanilla logic applies [IC#L204] | — | level.dat spawn, safe-spot adjusted unless `--no-adjust-spawn` [MVI#L46-L47; docs] |
| Existing id / failure | Loaded id silently skipped, though the count still says "imported" [IC#L124-L125, L146]. IOException leaves partial files | — | Refuses known (loaded or unloaded) names [MVW#L342-L345] |
| Clone | None (only `/wm export`) | `/multiverse clone <from> <to> [tickrate] [region-from] [region-to]`. Sync on the server thread, no save first (README says "run `/save-all`"), no temp dir, existence checked against loaded keys only [MV#L101-L117, L226-L270, L324-L372] | `/mv clone <world> <new> [--reset-world-config --reset-gamerules --reset-world-border --no-save]`. Saves with flush first by default, copies the whole folder synchronously, skips `uid.dat`/`session.lock`, no temp dir [MVW#L94-L97, L829-L865] |

Key: IC = Melius `command/ImportCommand.java`, AE = `extractor/ArchiveExtractor.java`, MV = senseiwells `commands/MultiverseCommand.kt`, MVI = MV-Core `commands/ImportCommand.java`, WNC = `world/helpers/WorldNameChecker.java`, WFR = `world/helpers/WorldFolderResolver.java`, MVW = `world/WorldManager.java`. Full URLs are under Sources.

Lessons:
- Validate a single path segment, which the Fabric mods don't.
- Use vanilla's layout migration instead of the running server's path. As written, Melius would import legacy saves empty.
- Copy off-thread into staging with an atomic install. Every reference copies synchronously or in place.
- Refuse occupied target folders, not just loaded ids.
- Report the real decode error instead of "level.dat missing".

## Vanilla alignment

- **Source = a vanilla save in vanilla's saves directory.**
  - The dedicated server already treats `--universe` (default `.`) as its saves dir and `level-name` as one entry in it (`server/Main.java:81,125-131`).
  - The integrated server's saves dir is `<gameDir>/saves` (`client/Minecraft.java:595`).
  - `LevelStorageSource.findLevelCandidates()` is vanilla's own scan of that dir: directories holding `level.dat` or `level.dat_old` (`LevelStorageSource.java:204-224`).
- **Same safety rules as opening a world.**
  - Symlinks: `allowed_symlinks.txt` through `DirectoryValidator.validateDirectory`. That is what `validateAndCreateAccess` enforces (`LevelStorageSource.java:117-132, 383-391`).
  - Session lock: `DirectoryLock.isLocked`, as the world list uses it (`LevelStorageSource.java:233`).
- **Same upgrade path as dedicated-server startup:** `getUnfixedDataTagWithFallback` → `fixAndGetSummaryFromTag` → refuse `requiresManualConversion` / `!isCompatible` → `DataFixers.getFileFixer().fix(access, tag, progress)` (`server/Main.java:131-153`). It runs on the *staged copy*, so a failed or interrupted upgrade never touches the source.
- **Same chunk upgrade as `--forceUpgrade`:** `RegionStorageUpgrader` with the per-dimension datafix context (`WorldUpgrader.java:69-72, 134-144`), limited to the imported dimension.
- **Same storage layout.** In 26.x each dimension, vanilla ones included, is `dimensions/<ns>/<path>/` with its own `region/`, `entities/`, `poi/` and `data/` (`DimensionType.java:113-115`, `ServerChunkCache.java:93-101`, `ServerLevel.java:250-257`). Importing a dimension means moving exactly that folder.
- **Not invented.**
  - No merging of global state.
  - No zip extraction (vanilla has no world-zip import).
  - No safe-spot spawn search beyond what mc-worlds already does.
  - No per-world gamerules (still under investigation).

## Current state in mc-worlds

- **Storage of a custom world.** It is `session.getDimensionPath(ResourceKey(DIMENSION, id))`, i.e. `<world>/dimensions/<ns>/<path>`. `loadOrCreateWorld` only logs whether the folder already exists (`manager/WorldsManager.java:96-102`). Region, entities, poi and the per-dimension `data/` are created by vanilla's `ServerLevel` constructor, which `CustomServerLevel` calls through `super(...)` (`server/level/CustomServerLevel.java:52-54`). A pre-existing folder is simply adopted, which is how the README's World Manager workaround works.
- **Per-world state mc-worlds adds on top of vanilla:**
  - Per-level `WeatherData`, stored as `<dim>/data/minecraft/weather.dat` (`CustomServerLevel.java:58`).
  - A per-level clock `mc-worlds:world_clocks`, i.e. `<dim>/data/mc-worlds/world_clocks.dat`. It reuses `ServerClockManager`'s codec and datafix type (`server/level/PerWorldClocks.java:17-21, 23-28`).
- **Registration and persistence:**
  - `WorldsData` is a `SavedData` holding `unboundedMap(Identifier, WorldsLevelData)` (`manager/WorldsData.java:17-22`). It is always dirty (`:32-35`), and `addLevelData` is at `:37-39`.
  - `WorldsLevelData.CODEC` = `core` (`WorldGeneratorData`) + `settings` + `game_time` (`manager/level/data/WorldsLevelData.java:20-24`). Its public constructor is at `:36-40`.
  - `WorldGeneratorData` holds `level_stem` (`Either<LevelStem, Dynamic>` passthrough), `seed`, `generate_structures`, `prepare_spawn`, `lazy` and `provider` (`WorldGeneratorData.java:19-30`), with a public constructor at `:39-43`.
  - `WorldSettingsData` holds `respawn` and `initialized` (`WorldSettingsData.java:9-13, 23-26`).
  - **Import needs no new field.**
- **Create flow.**
  - `WorldsCreator.createWorld` builds `WorldsLevelData.getDefault(…)` with `lazy=true` (`WorldsCreator.java:102`, `WorldsLevelData.java:146-152`).
  - It then loads on the server thread and waits for `Active` (`WorldsCreator.java:104-112`).
  - The command prints "creating" and later "created, [Teleport]" (`command/CreateCommand.java:139-165`).
  - **`validLevelId` uses `server.getLevel`** (`WorldsCreator.java:115-121`), which lazy-loads saved worlds (`mixin/MinecraftServerMixin.java:38-64`). Import must use a side-effect-free check instead (memory `project_getlevel_lazy_load_loops.md`).
- **First load.**
  - An uninitialized world runs `InitializationLevelState` (`CustomServerLevel.java:62-63`).
  - That state **overwrites the spawn** (`state/SpawnPreparationHelper.java:59-82`) and may place a **bonus chest** if the *server's* `generateBonusChest` is set (`:164-188`).
  - Imported worlds must therefore be registered `initialized=true`.
- **Lazy loading and unload.**
  - `getLevel` on a saved id calls `loadOrCreate` (`MinecraftServerMixin.java:38-64`).
  - `loadOrCreateWorld` already has a skip-list pattern (`unreadableGenerators`, `WorldsManager.java:36, 70-72`), which the busy guard can copy.
  - Empty worlds stop after 1200 ticks unless they hold the world spawn (`state/ActiveLevelState.java:21-26`).
  - Stopped levels are saved and closed at end of tick (`McWorldMod.java:51-75`), then the handle is dropped (`:77-81` → `WorldsManager.java:129-153`).
- **File-handling precedent (delete).**
  - `DeleteCommand` resolves the level with `getLevel` and calls `stop(true)` (`command/DeleteCommand.java:33-38`).
  - The folder is removed only after stop and close, with `FileUtils.deleteDirectory`, falling back to `forceDeleteOnExit` (`WorldsManager.java:134-147`).
  - So an orphan folder can survive a failed delete. Import must refuse an existing target folder.
- **File fixes.**
  - `DataFixersMixin` appends `McWorldsDataFileFix` (moves `data/mc-worlds.dat` → `data/mc-worlds/worlds_data.dat`) at `last vanilla fix + 1` (`mixin/filefix/DataFixersMixin.java:25-30`, `util/filefix/fixes/McWorldsDataFileFix.java:16-22`).
  - It is therefore part of `DataFixers.getFileFixer()` and runs on staged imports too, where it is harmless (memory `project_current_version.md`).
- **Spawn used by `/worlds tp`.**
  - Overworld-like worlds use `levelData.getRespawnData().pos()` snapped to `MOTION_BLOCKING_NO_LEAVES` + 1 (`abstractions/TeleportTransitionAbstraction.java:22-27, 33-38`).
  - Nether-like and End-like worlds ignore it, using a portal search or the End platform (`command/WorldLocation.java:36-51`).
- **Seeds.**
  - Terrain uses `CustomServerLevel.getSeed()`, the per-world seed (`CustomServerLevel.java:141-144`; consumed at vanilla `ChunkMap.java:181-183`).
  - `generate_structures` is stored but has **no reader** (`WorldsLevelData.java:62-64`). Vanilla takes it from the server's `WorldOptions` (`ServerLevel.java:285-300`).
- **Commands and i18n.**
  - Every `/worlds` subcommand is `LEVEL_ADMINS` and registered in `WorldsCommands.register` (`command/WorldsCommands.java:61-69`).
  - Translations are hand-written in `src/main/resources/assets/mc-worlds/lang/en_us.json:1-19`.
  - Datagen only has `DimensionTypeTagsProvider` (`src/datagen/java/dev/wroud/mc/worlds/DataGenerator.java:11-15`).

## Vanilla internals (MC 26.3-rc-1)

- **Layout migration (the 26.1 file fix).**
  - `DimensionStorageFileFix` moves the root `region`/`entities`/`poi` → `dimensions/minecraft/overworld`, `DIM-1` → `the_nether` and `DIM1` → `the_end` (`util/filefix/fixes/DimensionStorageFileFix.java:60-67`).
  - It moves per-dimension `data/{chunks,raids,world_border}.dat` into `dimensions/<dim>/data/minecraft/` (`:27-46`).
  - It moves global `scoreboard`, `idcounts` → `maps/last_id`, `map_N` → `maps/N` and `random_sequences` under `data/minecraft/` (`:47-59`).
  - `LevelDatToSavedDataFileFix` splits level.dat (`:40-99`):
    - `world_gen_settings` goes to `data/minecraft/world_gen_settings.dat` and is data-fixed on the way (`:73-75, 157-168`).
    - `weather_data` goes to `data/minecraft/weather.dat` (`:55-57`), `game_rules` to `data/minecraft/game_rules.dat` (`:70-72`) and `world_clocks` to `data/minecraft/world_clocks.dat` (`:76-78`).
    - The dragon fight goes to `dimensions/minecraft/the_end/data/minecraft/ender_dragon_fight.dat` (`:43-45`), and the world border to each dimension's `data/` (`:61-69`).
    - The singleplayer `Player` tag goes to `players/data` (`:113-132`).
  - `PlayerStorageFileFix` moves `advancements`/`playerdata`/`stats` under `players/` (`:14-16`).
  - All of these are registered at 4772/4773, plus a fix at 4899 (`util/datafix/DataFixers.java:1589-1596, 1606-1607`). `FILE_FIXER_INTRODUCTION_VERSION = 4772` (`FileFixerUpper.java:51`).
  - `FileRelation.DIMENSIONS` discovers *every* `dimensions/<ns>/<path>`, mc-worlds folders included (`util/filefix/access/FileRelation.java:20-21, 53-66`).
- **Running the file fixer.**
  - `FileFixerUpper.fix(access, levelTag, progress)` is public (`FileFixerUpper.java:75-124`). It works on a copy-on-write view under `<world>/filefix/` (`:189-207`) and deletes `session.lock` and `level.dat_old` in the result (`:223-224`).
  - It swaps folders as siblings `<name> upgraded` and `<name> OUTDATED` in the parent dir (`:268-278`) and **requires atomic moves** (`:283-285`).
  - It cleans a stale `filefix` dir on the next run (`:140-142`).
  - The dedicated server's `Main` calls it after the summary checks (`server/Main.java:142-153`). The client calls it when opening a world (`client/gui/screens/worldselection/WorldOpenFlows.java:425`).
  - `getLevelDataAndDimensions` throws if file fixing is still required (`LevelStorageSource.java:149-151`).
- **level.dat and world gen settings.**
  - `PrimaryLevelData.parse` reads `Time` (game time) and `spawn` (`RespawnData`: dimension, pos, yaw, pitch) (`world/level/storage/PrimaryLevelData.java:100, 105`). `RespawnData.DEFAULT` is overworld (0,0,0) (`LevelData.java:33-34`).
  - Vanilla loads `WorldGenSettings` with `readExistingSavedData`, which uses `NbtIo.readCompressed` → `data` → codec, **without datafixing** (`LevelStorageSource.java:154-157, 165-178`).
  - `WorldGenSettings` = `WorldOptions` (`seed`, …) + `WorldDimensions` (`levelgen/WorldGenSettings.java:15-24`, `WorldOptions.java:13-15`).
  - `WorldDimensions.CODEC` is `unboundedMap(ResourceKey<LevelStem>, LevelStem.CODEC)` and requires an overworld (`WorldDimensions.java:38-48`). **One undecodable dimension, such as a datapack dimension the server lacks, fails the whole map.**
- **Version checks.**
  - `fixAndGetSummaryFromTag` fixes a copy of the tag and builds a `LevelSummary` (`LevelStorageSource.java:537-542, 319-336`).
  - `isCompatible()` compares only the version *series* (`LevelSummary.java:141-143`, `DataVersion.java:12-14`), so a dedicated server opens newer worlds.
  - The client flags them as `DOWNGRADE` (`LevelSummary.java:116-131`).
- **Lazy upgrade on load.**
  - Chunks: `ChunkMap.readChunk` → `upgradeChunkTag` with context `{dimension: this.level.dimension(), generator}` (`ChunkMap.java:909-926`).
  - Entities: `EntityStorage.java:70`.
  - POI: `SectionStorage.java:306` (default version 1945).
  - `SimpleRegionStorage.upgradeChunkTag` returns the tag untouched if its version is ≥ current (`SimpleRegionStorage.java:50-69`). **Newer chunks are not refused, just not understood.**
  - So imported chunks *do* upgrade lazily. **But two fixes branch on `"minecraft:overworld".equals(context.dimension)`:**
    - `ChunkHeightAndBiomeFix` (2832 = 1.18): overworld gets sections down to -64, below-zero retrogen and blending (`ChunkHeightAndBiomeFix.java:126-131`, `DataFixers.java:1079-1081`).
    - `BlendingDataFix` (4997 = 26.3; `DataFixers.java:438, 1612-1613`) adds `blending_data` to overworld chunks and *removes* it for any other dimension (`BlendingDataFix.java:35-51`).
    - In an mc-worlds world the context is the custom id, so an imported overworld is upgraded as a non-overworld.
  - `WorldUpgrader` builds the context from the dimension key and its generator (`WorldUpgrader.java:69-72, 134-144`).
  - `RegionStorageUpgrader` and its `Builder` are public (`init` resolves `getDimensionPath(key)/<folder>`, and `upgrade()` is synchronous and cancellable via `UpgradeProgress`: `RegionStorageUpgrader.java:79-129, 277-357`). `new UpgradeProgress()` needs no notification service (`UpgradeProgress.java:39-41`).
- **What is per-dimension vs global** (`SavedDataStorage` file = `<folder>/<ns>/<path>.dat`, `SavedDataStorage.java:56-57`):
  - Per-dimension (`<dim>/data/`, `ServerChunkCache.java:93-101`):
    - `chunk_tickets` (`TicketStorage.java:40-42`)
    - `raids` (`ServerLevel.java:280`)
    - `ender_dragon_fight` (`ServerLevel.java:301-304`)
    - `world_border` (`ServerLevel.java:1566-1570`)
  - Global (`<world>/data/`, `MinecraftServer.java:333`):
    - `world_gen_settings`, `random_sequences`, `weather`, `game_rules`, `world_clocks`, `custom_boss_events`, `scheduled_events` (`:336-364`)
    - scoreboard, command storage and stopwatches (`:435-437`)
    - **maps**: `ServerLevel.getMapData`/`setMapData`/`getFreeMapId` all use `server.getDataStorage()` (`ServerLevel.java:1590-1601`), ids are `maps/<n>` (`maps/MapId.java:22-24`), and the counter is `maps/last_id` (`MapIndex.java:15-17, 28-31`).
- **Initialization.** Spawn search and the bonus chest run only when `!levelData.isInitialized()` (`MinecraftServer.java:438-457`).
- **Paths and ids.**
  - `getDimensionPath` → `DimensionType.getStorageFolder` → `id.resolveAgainst(<world>/dimensions)` (`LevelStorageSource.java:509-511`, `DimensionType.java:113-115`).
  - `resolveAgainst` only ensures the normalized path stays under the root (`resources/Identifier.java:152-163`).
  - `FileUtil.isValidPathSegment` rejects `.`/`..` and illegal chars (`util/FileUtil.java:157-159`).
  - `LevelStorageAccess.parent()`, `getLevelId()`, `getBaseDir()`, `getWorldDirValidator()` are public (`LevelStorageSource.java:493-503, 375-377, 398-400`).
- **Entity UUIDs** are tracked per level (`PersistentEntitySectionManager.java:34, 62-63`), so the same UUID in two levels only warns within one level.
- **Player spawn adjustment.** Vanilla players use `PlayerSpawnFinder.findSpawn`, which honours `respawn_radius` (`ServerPlayer.java:383-387`, `GameRules.java:72`). Non-player entities snap to the heightmap (`Entity.java:1444-1449`).

## Proposed design

### Command surface

```
/worlds import <id> <folder>               source dimension minecraft:overworld
/worlds import <id> <folder> <dimension>   e.g. minecraft:the_nether, minecraft:the_end, or any dimensions/<ns>/<path> in the source
```

- **`<id>`:** `IdentifierArgument`, the same as `create`.
- **`<folder>`:** `StringArgumentType.string()`. Save names often contain spaces, which need quoting.
  - Suggestions: `findLevelCandidates()` directory names minus `storageSource.getLevelId()`, passed through `StringArgumentType.escapeIfRequired`.
- **`<dimension>`:** `IdentifierArgument`, with static suggestions of the three vanilla ids.
- **Permission:** `LEVEL_OWNERS` recommended (see open questions). Works from console and RCON, because messages go through `sendSuccess`/`sendSystemMessage` like `create`.
- **One dimension per command.** Importing a save's Nether too means a second command with another id.
- **No generator, seed or spawn flags in v1** (see open questions).

### Validation (server thread, before any IO job; no `getLevel`)

1. **Target id:**
   - Not in `WorldsData`.
   - Not in `MinecraftServerAccessor.getLevels()` or `server.levelKeys()`.
   - Not reserved by a running job.
   - Every `/`-separated path segment passes `FileUtil.isValidPathSegment`.
   - `getDimensionPath(key)` must not exist ("folder already exists on disk").
2. **Folder:**
   - `FileUtil.isValidPathSegment(folder)` (one segment, no `..`).
   - `!folder.equals(storageSource.getLevelId())`. Cloning the server's own dimensions is clone's job.
   - `level.dat` or `level.dat_old` exists (the same filter as `findLevelCandidates`).
   - `getWorldDirValidator().validateDirectory(path, true)` is empty.
   - `!DirectoryLock.isLocked(path)` ("open in another game").
3. **No other import or clone job is running.** Keep it to one job at a time: IO stays predictable and the guard stays simple.

### Pipeline

Shared parts live in `WorldTransfer` and are reused by clone. They are marked †.

| # | Thread | Step |
|---|---|---|
| 1 | server | Validate, reserve `id` in `WorldsManager` †, send `import.started` |
| 2 | worker † | Create `staging = <world>/mc-worlds/staging/<uuid>/`. Copy `level.dat`, `level.dat_old`, `data/`, and the dimension's folders: modern `dimensions/<ns>/<path>/`, plus for the vanilla three their legacy homes (overworld: root `region`/`entities`/`poi`; nether: `DIM-1/`; end: `DIM1/`; mapping from `DimensionStorageFileFix.java:60-64`). Copy file contents. Symlinks were already accepted by the allowlist and are resolved, so the staged copy has none. Skip `session.lock` and check a cancel flag per file. Check `getUsableSpace()` against the summed size first |
| 3 | worker | `LevelStorageSource.createDefault(<world>/mc-worlds/staging).validateAndCreateAccess(uuid)` → `getUnfixedDataTagWithFallback()` → `fixAndGetSummaryFromTag`. Refuse `requiresManualConversion()`, `!isCompatible()` or `isDowngrade()` |
| 4 | worker | `DataFixers.getFileFixer().fix(access, tag, new UpgradeProgress())`. For a 26.1+ save it only data-fixes level.dat in memory. For older saves it rewrites the staged layout |
| 5 | worker | Resolve the generator: read `data/minecraft/world_gen_settings.dat` the way `readExistingSavedData` does. Decode `WorldOptions` and **only** `dimensions.<dimension>` with `LevelStem.CODEC` over `RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())`, so an unrelated broken datapack dimension doesn't block the import. Fallback: an entry for `<dimension>` in the source's `data/mc-worlds/worlds_data.dat` (`WorldsData` codec), for moving mc-worlds worlds between servers. Otherwise refuse with the decode error |
| 6 | worker | Pre-upgrade chunks under the source key: `RegionStorageUpgrader.Builder(DataFixers.getDataFixer()).setDataFixType(DataFixTypes.CHUNK).setType("chunk").setFolderName("region").setDataFixContextTag(ChunkMap.getChunkDataFixContextTag(sourceKey, stem.generator().getTypeNameForDataFixer())).build(0)` → `init(sourceKey, access)` → `upgrade()`. This is `WorldUpgrader.java:134-144` for one dimension. Entities and POI keep upgrading lazily, because their fixes ignore context |
| 7 | worker | Optional transplant: staged `data/minecraft/weather.dat` → `<dim>/data/minecraft/weather.dat`, and `data/minecraft/world_clocks.dat` → `<dim>/data/mc-worlds/world_clocks.dat`. Only when the target is absent. These are the same SavedData types mc-worlds reads per world, and they are datafixed on read |
| 8 | worker † | Close the access. `createDirectories(target.getParent())`, then `Files.move(stagedDim, target, ATOMIC_MOVE)`. Delete the staging dir |
| 9 | server † | Build `WorldsLevelData` (below), then `McWorld.loadOrCreate(id, data)` → `LevelActivationUtil.executeWhenLevelReady` → `import.success` with the Teleport link (copy of `CreateCommand.java:152-163`). Release the reservation |

- **Any failure:** delete staging, release the reservation, send `import.failed` with the message. The target is only ever written by the atomic move in step 8, and registration happens after it.
- **Server stop:** on `SERVER_STOPPING`, set the cancel flag and call `UpgradeProgress.setCanceled()`. Later `server.execute` callbacks are dropped.
- **Startup:** delete any leftover `<world>/mc-worlds/staging/` in the `WorldsManager` constructor. This mirrors `FileFixerUpper`'s stale-`filefix` cleanup.
- **Crash between steps 8 and 9:** leaves an unregistered folder. The next import with that id is refused with `folder_exists`; the admin deletes it or adopts it with `/worlds create`.

### Level data for the imported world (no Codec change)

```java
var generator = new WorldGeneratorData(stem, options.seed(), options.generateStructures(),
    true, true, DefaultServerLevelProvider.DEFAULT);
var spawn = levelSpawn.dimension().identifier().equals(sourceDimension)
    ? RespawnData.of(newKey, levelSpawn.pos(), levelSpawn.yaw(), levelSpawn.pitch())
    : RespawnData.DEFAULT;
var data = new WorldsLevelData(generator, new WorldSettingsData(spawn, true), levelTime);
```

- **Seed:** the source's. Terrain beyond the explored area continues seamlessly.
- **`initialized=true`:** it is an existing world. This skips the spawn rewrite and bonus chest.
- **`game_time`:** `level.dat` `Time`. This keeps villager and brain timestamps coherent.
- **`lazy=true`:** the same as `create`.
- **`generate_structures`:** stored for fidelity, though it has no reader today.

### Files

| File | Change |
|---|---|
| `manager/WorldTransfer.java` (new, ~150 lines) | Reserve/release, staging dir, cancellable tree copy, space check, atomic install, startup cleanup, single-job guard |
| `manager/WorldImporter.java` (new, ~150 lines) | Steps 2–7: source part selection, vanilla open/fix/summary, generator/spawn/time resolution, chunk pre-upgrade, weather/clock transplant |
| `command/ImportCommand.java` (new) | Brigadier tree, validation, messages |
| `command/WorldsCommands.java` | `.then(ImportCommand.build())`, `SAVE_FOLDER_SUGGESTIONS` |
| `manager/WorldsManager.java` | `Set<Identifier> busy`. `loadOrCreateWorld` returns `null` for busy ids (next to the `unreadableGenerators` check, `:70-72`). Startup staging cleanup |
| `McWorldMod.java` | `SERVER_STOPPING` → cancel the running job |
| `lang/en_us.json`, `README.md`, `CHANGELOG.md` | Keys below. A "Import Command" section. Keep the World Manager note, pointing to import |

Datagen: none. Language files are hand-written (`DataGenerator.java:11-15`). No public API change.

### i18n keys

| Key | en_us |
|---|---|
| `dev.wroud.mc.worlds.command.import.started` | `Importing %s as %s, please wait` |
| `dev.wroud.mc.worlds.command.import.upgrading` | `Upgrading %s from Minecraft %s` |
| `dev.wroud.mc.worlds.command.import.success` | `World %s imported from %s. ` (followed by the existing `…create.success.teleport` link) |
| `dev.wroud.mc.worlds.command.import.exception.not_found` | `No world save named %s next to the server world` |
| `dev.wroud.mc.worlds.command.import.exception.invalid_folder` | `Invalid folder name: %s` |
| `dev.wroud.mc.worlds.command.import.exception.own_world` | `%s is this server's own world` |
| `dev.wroud.mc.worlds.command.import.exception.locked` | `World %s is open in another game` |
| `dev.wroud.mc.worlds.command.import.exception.symlink` | `World %s contains symbolic links that are not allowed (see allowed_symlinks.txt)` |
| `dev.wroud.mc.worlds.command.import.exception.newer_version` | `World %s was saved by a newer Minecraft version (%s)` |
| `dev.wroud.mc.worlds.command.import.exception.incompatible` | `World %s cannot be opened by this version (%s)` |
| `dev.wroud.mc.worlds.command.import.exception.no_dimension` | `World %s has no dimension %s` |
| `dev.wroud.mc.worlds.command.import.exception.generator` | `Cannot read the generator of %s in %s: %s` |
| `dev.wroud.mc.worlds.command.import.exception.disk_space` | `Not enough disk space to import %s` |
| `dev.wroud.mc.worlds.command.import.failed` | `Importing %s failed: %s` |
| `dev.wroud.mc.worlds.command.exception.folder_exists` (shared with clone) | `A folder for world %s already exists on disk` |
| `dev.wroud.mc.worlds.command.exception.busy` (shared with clone) | `Another import or clone is still running` |
| reuse `dev.wroud.mc.worlds.command.create.exception.world_already_exists` | `A world with that id already exists!` |

## Edge cases & risks

- **Adventure maps lose global state.** Players, inventories, advancements, stats, scoreboard and teams, command storage, bossbars, scheduled functions, gamerules, difficulty, datapacks and `generated/` structure templates are all server-global in 26.x and are not imported.
  - Command blocks keep working in the imported level. But any `execute in minecraft:overworld`, `setworldspawn` or absolute-coordinate command in the map acts on the server's real dimensions.
  - Document this as "imports the terrain, not the game."
- **Maps.** Item frames and chests keep their `map_id`, which resolves against the *server's* global maps (`ServerLevel.java:1590-1597`). Missing ids show blank. Colliding ids show **another map's image**. Not handled in v1.
- **Old chunks and dimension context.** Without step 6, chunks older than 1.18 imported from an overworld are upgraded as a non-overworld: no sections below y=0 and no blending, so there are visible seams. Every chunk older than 26.3 loses `blending_data`. A save whose `level.dat` is current can still hold old chunks in areas nobody revisited, so step 6 cannot be skipped based on `level.dat`. Step 6 reads every chunk once, which is roughly the cost of the copy.
- **Undecodable generator.** A custom datapack worldgen or dimension type the server lacks makes import refuse with the real codec error. Overriding the generator is an open question. If added, it must keep the source dimension type's `min_y`/`height`, or the stored sections misalign.
- **Newer saves.** These are refused. A dedicated server would open them and silently drop unknown content on the next save (`SimpleRegionStorage.java:52-54`).
- **Experimental features.** `enabled_features` in the source beyond the server's: warn, using `LevelSummary.isExperimental()`.
- **Size and IO.** A multi-GB copy competes with the live server's region IO. Run it on one dedicated low-priority thread, as `WorldUpgrader` does with its own thread (`WorldUpgrader.java:60`), not on `Util.ioPool()` (`Util.java:262-264`).
- **Atomic moves.** `FileFixerUpper` throws `AtomicMoveNotSupported` on filesystems without them (`FileFixerUpper.java:283-285`). Staging inside the world dir keeps every move on one filesystem.
- **Per-dimension data comes along.** That includes forceloaded chunks (re-activated on load: `state/ActivationLevelState.java:26-29`), active raids, the world border and the End dragon state. This is correct for a dimension, and each can be reset with `/execute in <id> run forceload remove all | worldborder …`.
- **Indoor spawns.** mc-worlds snaps overworld-like spawns to the heightmap (`TeleportTransitionAbstraction.java:33-38`), not vanilla's `PlayerSpawnFinder` (`ServerPlayer.java:383-387`). An adventure map whose spawn is inside a building therefore lands players on the roof. This is pre-existing and should be a separate fix, but it hurts imports most.
- **Importing the same save twice** duplicates entity UUIDs across two levels. Vanilla tracks them per level, so it is harmless.
- **Identifier aliasing.** `x:../minecraft/the_nether` stays under `dimensions/`, so `resolveAgainst` accepts it. The segment check closes this for import. `create` has the same gap today.
- **Integrated server.** In singleplayer the base dir is `saves/`, so another singleplayer world can be imported by name. Its lock check also stops importing the world that is currently open.

## Open questions for the maintainer

1. **Chunk pre-upgrade (step 6).**
   - Option (a), recommended: always run it, which gives the result vanilla would give for that save.
   - Option (b): skip it and document seams for pre-1.18 and pre-26.3 overworld chunks.
   - Option (c): refuse sources older than 1.18. (b) still applies to their pre-26.3 chunks.
2. **Source location.**
   - The server's saves dir is recommended: vanilla's notion, and shared by Multiverse-Core's world container.
   - Alternatives: a dedicated `imports/` folder, or absolute paths.
   - Should `.zip` uploads be supported? Melius does. Vanilla has no zip import, and zip-slip is a real risk (Melius `ArchiveExtractor.java:56-62`).
3. **Permission.** `LEVEL_OWNERS` (reads arbitrary saves on disk, like `/save-off` at `SaveOffCommand.java:13`) or `LEVEL_ADMINS` like every other `/worlds` subcommand?
4. **Generator override.** Add `from-preset <preset> [<dimension>]` / `from-dimension <dimension>` (same type height only) for sources whose generator can't be decoded, or to make new chunks void? Recommendation: wait for a user report.
5. **Maps.** Skip them (v1), or copy only map files whose id is above the server's `maps/last_id` and raise `last_id`? That still collides for ids at or below it.
6. **Weather and clock transplant (step 7).** Keep it (two file copies, same SavedData types), or start the imported world with fresh weather and time?

## Manual test plan

Run `./gradlew runServer`. The dev server's universe is `versions/latest/run`, with `level-name=world` (`versions/latest/run/server.properties:28`), so source saves go in `versions/latest/run/<folder>`. Copy them from `versions/latest/run/saves/` (the dev client's singleplayer saves). The `mc-server-probe` skill can script the console part.

1. **26.3 save, overworld.**
   - Run `worlds import imp:a "My World"`.
   - Expect "Importing…" and then "World imp:a imported… [Teleport]". The main thread keeps ticking during the copy, with no `Can't keep up!` lasting the whole copy.
   - Teleport: builds are present, and spawn is the save's spawn.
   - Walk past the explored edge: new terrain continues with no seam.
2. **Nether and End.**
   - Run `worlds import imp:n "My World" minecraft:the_nether` and the same with `minecraft:the_end`.
   - Portals and End platform logic work. A save with the dragon killed has no new dragon.
3. **Legacy save.** Use a 1.20.x save (root `region/`, `DIM-1/`).
   - The log shows vanilla's "Starting upgrade for world" for the staged copy.
   - The import succeeds and the source folder is byte-identical afterwards (`diff -r` against a backup).
4. **Pre-1.18 save** (1.17.1).
   - With step 6, terrain below y=0 is generated under old chunks and the edges blend.
   - Without it, document the seam.
5. **Refusals:**
   - Unknown folder.
   - `"../world"`.
   - The server's own `world`.
   - Existing id.
   - Existing orphan folder `world/dimensions/imp/x`.
   - A symlinked save without `allowed_symlinks.txt`.
   - A save open in a second vanilla server (`--universe versions/latest/run --world <folder>`) holding `session.lock`.
   - A save from a newer snapshot.
   - A save whose `world_gen_settings.dat` references a missing datapack dimension *only for another dimension*: the overworld import must still succeed.
   - A second import while one runs: `busy`.
6. **Interruption.**
   - Kill the server mid-copy (`kill -9`). On restart, `world/mc-worlds/staging` is gone, `imp:*` is not registered, and the source is intact.
   - Stop the server normally mid-import: shutdown is not blocked for long.
7. **Lazy lifecycle.**
   - Leave the imported world. After about 60 s it unloads, and `worlds tp imp:a` reloads it.
   - Restart: it is still registered (`world/data/mc-worlds/worlds_data.dat`).
   - `worlds delete imp:a` removes the folder.
8. **Singleplayer.** In `./gradlew runClient`, open world A and run `/worlds import imp:b B` for another save B. Then `/worlds import imp:c A` is refused (`own_world`).
9. **Global state is not imported.** Scoreboard objectives, gamerules and player inventory from the source don't appear. Player inventories on the server are unchanged.

## Sources

- Melius WorldManager: https://modrinth.com/mod/melius-worldmanager. Source at `61fe96b`:
  - https://github.com/DrexHD/WorldManager/blob/61fe96b39732e56e21e566547ead238896101365/src/main/java/me/drex/worldmanager/command/ImportCommand.java
  - https://github.com/DrexHD/WorldManager/blob/61fe96b39732e56e21e566547ead238896101365/src/main/java/me/drex/worldmanager/extractor/ArchiveExtractor.java
- Melius issues: https://github.com/DrexHD/WorldManager/issues/2 · https://github.com/DrexHD/WorldManager/issues/11 · https://github.com/DrexHD/WorldManager/issues/12
- Multiverse (Fabric): https://modrinth.com/mod/multiverse-dimensions. Source: https://github.com/senseiwells/Multiverse/blob/dbe0c79daf25203d544cf890c2c228a4bad198f2/src/main/kotlin/me/senseiwells/multiverse/commands/MultiverseCommand.kt
- Multiverse-Core docs: https://mvplugins.org/core/fundamentals/commands-usage/
- Multiverse-Core source at `fe49efe`:
  - https://github.com/Multiverse/Multiverse-Core/blob/fe49efe276ec50b069280b4de9d9eb29966d57d7/src/main/java/org/mvplugins/multiverse/core/commands/ImportCommand.java
  - https://github.com/Multiverse/Multiverse-Core/blob/fe49efe276ec50b069280b4de9d9eb29966d57d7/src/main/java/org/mvplugins/multiverse/core/world/helpers/WorldNameChecker.java
  - https://github.com/Multiverse/Multiverse-Core/blob/fe49efe276ec50b069280b4de9d9eb29966d57d7/src/main/java/org/mvplugins/multiverse/core/world/helpers/WorldFolderResolver.java
  - https://github.com/Multiverse/Multiverse-Core/blob/fe49efe276ec50b069280b4de9d9eb29966d57d7/src/main/java/org/mvplugins/multiverse/core/world/WorldManager.java
- Multiverse-Core issues: https://github.com/Multiverse/Multiverse-Core/issues/2389 · https://github.com/Multiverse/Multiverse-Core/issues/3298
