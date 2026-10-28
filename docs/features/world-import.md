# /worlds import

**Status.** Shipped in 1.11.0. The design below is the one in the code; it is a thin orchestration of vanilla's own world-opening pipeline: copy, run vanilla's file fixer, read the save with vanilla's readers, move, register.

`/worlds import <id> <folder> [<dimension> | all]` takes a save folder that sits in the server's own saves directory: the `LevelStorageSource` base dir, which is the server root on a dedicated server and `saves/` in singleplayer. The steps:

1. On the server thread: claim the server's single import slot, check the folder (vanilla folder-name, symlink and session-lock rules) and the target ids (saved data and the target folder only, no `getLevel`).
2. Copy `level.dat`, `level.dat_old`, `data/` and the chosen dimensions into `<world>/mc-worlds/staging/<uuid>/`, off the server thread.
3. Upgrade the staged save exactly as a dedicated server does at startup: `LevelSummary` checks, then `DataFixers.getFileFixer().fix(...)`. For a pre-26.1 save this also converts the legacy `region/`, `DIM-1/` and `DIM1/` layout to `dimensions/minecraft/<dim>/`.
4. Resolve each dimension's generator, seed, spawn and game time: the source's mc-worlds entry, then the dimension's own `world_gen_settings.dat` (Paper 26.1+), then the save root's.
5. Carry the save's weather, clock and differing game rules into the dimension's own `data/`.
6. If the save has filled maps, take fresh server map ids on the server thread and rewrite every reference in the staged chunks with a `RegionStorageUpgrader` pass.
7. On the server thread: move each staged `dimensions/<ns>/<path>` folder into the server world (rolled back if a move fails), store the maps, register each world as initialized and load it.

Old chunks upgrade lazily on load, as in vanilla. A generic mixin gives overworld-like mc-worlds levels with the overworld height the `minecraft:overworld` datafix context, which also fixes existing worlds (reproduced and fixed, see [Reproduction results](#reproduction-results-2026-10-29)).

Everything else that 26.x stores server-globally is left out: players, advancements, scoreboard, difficulty, datapacks. No Codec change.

The design was re-checked on 2026-10-29 by four scoped research passes, web research and Docker-built Paper saves; a review of that plan was then accepted in full and is reflected here. The maintainer's decisions are under [Decisions](#decisions-2026-10-29).

Paths: mc-worlds code is under `src/main/java/dev/wroud/mc/worlds/`, the importer under `manager/importer/`. Vanilla code is MC **26.3-rc-1** decompiled Mojmap (`minecraft-common-6974b2190e-26.3-rc-1-sources.jar`) under `net/minecraft/`; rc-1 → 26.3 needed no mod changes. Runtime checks use the 26.3 release server (DataVersion 5023).

## Summary

| Item | As built |
|---|---|
| Command | `/worlds import <id> <folder>` imports the source Overworld, or the only dimension with chunks. `… <dimension>` imports one dimension. `… all` imports every dimension with chunks as `<id>`, `<id>_nether`, `<id>_the_end`, `<id>_<path>` (`minecraft` namespace) or `<id>_<ns>_<path>` |
| Source location | One folder name in the server's `LevelStorageSource` base dir (next to `world/`). If it has no `level.dat` but exactly one subfolder does, that subfolder is used. A `.zip` file there, or a quoted `http(s)://` URL to a zip, in the same argument (see [Zip and URL sources](#zip-and-url-sources)). No absolute paths, no `..`, no other archives |
| Source validation | Vanilla's folder-name rules; `level.dat` or `level.dat_old`; `allowed_symlinks.txt` rules; not locked by another game; not the server's own world |
| Version policy | Same checks as dedicated-server startup (manual conversion, other series), plus refuse newer saves |
| Upgrade | Vanilla `FileFixerUpper` on the staged copy (only saves before 26.2 have anything to fix). Chunks upgrade lazily on load, with the overworld datafix context from the mixin |
| Generator for new chunks | In order: the source's `data/mc-worlds/worlds_data.dat` entry (another mc-worlds server), the dimension's own `data/minecraft/world_gen_settings.dat` (Paper 26.1+), the save root's. When the vanilla reader returns nothing, the file is decoded by hand so the seed survives. A vanilla dimension with no decodable stem falls back to the server's own stem for that dimension, with a warning. A custom one uses the server's stem with the same id when the server has it (the same datapack installed), without a warning; otherwise it is refused (`datapack_dimension` before the copy when nothing in the save can describe it, else after resolving) |
| Spawn / time | The mc-worlds entry, else Paper's `data/paper/level_overrides.dat`, else `level.dat` `spawn` (same-dimension rule plus the Bukkit rule) and `Time`. Always `initialized=true` |
| Carried with the dimension | `region/`, `entities/`, `poi/` and the dimension's `data/` (tickets, raids, border, dragon fight) |
| Carried from the save's data | Weather, the world clock, and the game rules that differ from the server's (as per-world overrides), from the dimension's own `data/` first, then the save root for every imported dimension. Filled maps, under fresh server ids |
| Not imported | Player data, advancements, stats, scoreboard, command storage, bossbars, difficulty, datapacks, `generated/` |
| Threading | Checks on the server thread → `Util.ioPool()` (copy, fix, resolve, state) → server (map ids) → IO (map pass) → server (install). One import per server at a time |
| Chunk datafix context | Mixin: an mc-worlds level whose type is overworld-like and spans -64..320 upgrades its chunks and structure starts as `minecraft:overworld` |
| Permission | `LEVEL_ADMINS`, like every other `/worlds` subcommand |
| Maps | New ids from `getFreeMapId()`; map files re-keyed or locked; every `minecraft:map_id` in the staged `region/` and `entities/` rewritten. Skipped when the save has no map files |
| Codec / datagen / API | None / none / none |

## Use cases & demand

- **Bring a singleplayer world onto the server** as one more world, with the source save untouched. This is the core of Multiverse-Core `/mv import` and Melius `/wm import` (table below).
- **Hosted-panel archives.** Melius #12: *"I am using World Manager on a Fabric server to manage an archive of community worlds… via the /wm import GUI"*. Melius #11: a user uploads a folder or `.zip` to a Pterodactyl panel and import fails with "Failed to find/read level.dat".
- **Maps people download.** Melius #2, "How can I import my map?", was answered by adding `/wm import` in 1.2.0.
- **Nether and End of a vanilla save.** Multiverse-Core #2389 (2020): *"I successfully imported a vanilla world save but this only creates an overworld dimension. I want the corresponding Nether and End."* That is the reason for the optional `<dimension>` argument.
- **Legacy layouts.** Multiverse-Core #3298: importing from `world/DIM*` and `world/dimensions` broke in MV5. Here, vanilla's own file fixer handles both layouts.
- **Migration.** Today the README tells World Manager users to `/worlds create` with the *same id, type and seed* so mc-worlds adopts the folder, and only classic Overworld/End/Nether work (`README.md:83-87`). Import replaces guesswork with reading `world_gen_settings.dat`. Moving an mc-worlds world between servers is covered by reading the source's `worlds_data.dat` (decision 7). `create` keeps adopting an existing folder.
- The maintainer approved this on 2026-10-07, with clone to be built on top of it.

### Demand and coverage (checked 2026-10-29)

| Signal | Numbers |
|---|---|
| Multiworld (IsaiahMC, 86.5K Modrinth downloads, the largest Fabric world manager) | Import is its most-requested missing feature: 5 open issues, #2 (5 👍), #37 (5 👍), #41, #72, #151. Only per-world inventories (#4, #26) and a version-support request get more reactions. No maintainer reply |
| Multiworld #151 (Dec 2025 – Sep 2026) | A Paper/Multiverse owner with several world folders, each with its own `level.dat`, asks for import. The workaround in the thread (create an empty world, stop the server, overwrite `region/entities/poi/data`) is called cumbersome, and a commenter says missing import is *"the main reason they haven't switched from Multiverse"* |
| Multiverse-Core | Core command; 149 issues mention import. #2389 asks for the save's Nether and End, not only its Overworld |
| Forge precedent | ForgeEssentials #1303 refused import because modded block ids broke; #1304 is a crash from copying `region/` + `level.dat` by hand |

| Fabric/NeoForge coverage | Downloads | What it covers |
|---|---|---|
| melius World Manager | 15.2K | Folder or zip/rar/tar, Overworld only (see the table below) |
| World Switcher | 734 | NeoForge 1.21.1 only, Overworld only |
| Multiworld, Multiverse Dimensions | — | No import |

What users expect: one command, no restart and no hand-copying of files; the Nether and End of a save as well as its Overworld (the `<dimension>` argument); generator and seed read from the save instead of typed (Multiverse-Core requires `-g` and warns that a wrong generator damages the world); same-or-newer version and the same mods installed, which hosting guides already tell users.

### Which dimensions, and where saves come from (checked 2026-10-29)

**Dimensions.** The evidence is thin (Reddit and SpigotMC searches returned nothing usable); it comes from guides and issue trackers.

| Use case | Dimensions wanted | Evidence |
|---|---|---|
| A map, build, lobby or one world from an old host | Overworld only | Multiworld #41 (*"import the map from the old server as a dimension or a world"*), #72, #2; host guides use `/mv import my_world normal` |
| An old survival or SMP world moved to a new server | Overworld and Nether, End less often | Multiverse-Core #2389 (maintainer: "Good idea!", closed in May 2026 because Paper 26.1 changed the layout, not because it was built); a blog post renaming a save into `resourceserver`, `resourceserver_nether`, `resourceserver_the_end` by hand |
| A standalone Nether world | That Nether | `/mv import my_hell nether` appears in guides as its own case |

- No complaint that a tool imports too much was found. Melius imports every dimension but asks for one id each.
- When users import the Nether too, they expect portals to pair up (Multiverse-NetherPortals FAQ). mc-worlds sends a nether portal in any overworld-like world to the server's own Nether (`mixin/fixes/NetherPortalBlockMixin.java:27, 43`), so an imported `<id>_nether` is reachable only by `/worlds tp`. World-set portals stay out of scope.
- The `<name>`, `<name>_nether`, `<name>_the_end` convention is everywhere: the Bukkit folder split, NetherPortals auto-linking, inventory-group examples.
- Rough estimate (inference): 25–40% of imports want the save's Nether or End. Hence the Overworld by default and `all` as an opt-in.

**Source location.**
- Every host guide checked (XGamingServer, Oxygenserv, Akliz, Falix) describes the same flow: zip the world, upload through the panel or SFTP, **Unarchive** in the panel. Pterodactyl Wings extracts into the archive's directory and keeps the archive's own layout (`server/filesystem/compress.go`), so the save lands next to `world/`.
- The most-named failure is a nested folder, `X/X/level.dat`, from zipping the folder instead of its contents (XGamingServer calls it a "Common gotcha"; the MineZip plugin page describes it).
- Multiverse-Core #1172: a user put a zip inside `world/`; the maintainer's "extract it next to `world`" was accepted.
- Melius #11: both `Worlds/Farm` and `Farm.zip` failed on Pterodactyl, so accepting arbitrary paths and archives did not prevent failures.
- No request for absolute paths or other drives was found. Multiverse's one "other location" request (#766) was met by the server-wide `world-container` setting.
- Multiverse-Core #3502 asks for tab completion of nested folders. The maintainer worries about deep scans and suggests one or two levels, skipping folders that are already worlds.

### Paper 26.1+ layout (tested 2026-10-29)

Built in Docker with `itzg/minecraft-server:java25-alpine`, Paper 26.3 build 227 and Multiverse-Core 5.8.2: `mv create survival normal --seed 999`, `flatworld normal --world-type flat`, `hell nether`, `amp normal --world-type amplified --seed 42`, `voidend the_end`, then `save-all flush` and stop. On disk:

- **One save.** Every Multiverse world is a dimension of the main save in the `minecraft` namespace: `world/dimensions/minecraft/{survival,flatworld,hell,amp,voidend}/`, next to `overworld`, `the_nether` and `the_end`. There are no sibling folders.
- **No root `world_gen_settings.dat`.** The root `data/minecraft/` holds only `custom_boss_events`, `random_sequences`, `scoreboard`, `stopwatches` and `world_clocks`.
- **Per-dimension vanilla files.** Each dimension's `data/minecraft/` has its own `world_gen_settings.dat`, `game_rules.dat`, `weather.dat`, `world_clocks.dat`, plus `world_border`, `raids`, `chunk_tickets`, `scheduled_events`, and `ender_dragon_fight` for End-type worlds. All are DataVersion 5023, the vanilla 26.3 format.
- **Each `world_gen_settings.dat` is a full `WorldGenSettings`** with the world's own seed (999 for `survival`, 42 for `amp`) and all three vanilla entries. The world's real generator sits under the entry of its environment: `flatworld`'s `minecraft:overworld` entry is `minecraft:flat`, `amp`'s is noise with `minecraft:amplified`. A Nether world's file (`hell`) has the same shape as an Overworld world's.
- **The environment is not in the save.** Only `plugins/Multiverse-Core/worlds.yml` records `environment: nether`. `ender_dragon_fight.dat` marks End-type worlds; chunk biomes identify the rest: `hell` and `the_nether` chunks hold `minecraft:nether_wastes`, `voidend` and `the_end` hold `minecraft:the_end`, the rest overworld biomes.
- **Paper's own per-world data.** `data/paper/level_overrides.dat` has the world's `spawn` (pos, yaw, pitch, **no dimension**), `game_time`, `difficulty_settings`, `initialized` and `game_type`. `data/paper/metadata.dat` has a UUID and `persistent_data_container.dat` plugin data, and `paper-world.yml` sits beside `region/`. These move with the folder and are ignored.
- **Root `level.dat`** has the server overworld's spawn and `Time`, `DataVersion` 5023 and `Bukkit.Version`.

So `/worlds import survival world minecraft:survival` (or `all`) works with the per-dimension lookup and the environment detection below. This is the Multiworld #151 migration.

### Paper before 26.1 (tested 2026-10-29)

Built the same way with Paper 1.21.11 build 132 and Multiverse-Core 5.8.2 (worlds `survival` seed 999, `flatworld` flat, `amp`, `hell` nether, `voidend` the_end). On disk:

- **Sibling folders, each a separate save** with its own `level.dat`: `world/`, `world_nether/`, `world_the_end/`, `survival/`, `flatworld/`, `amp/`, `hell/`, `voidend/`. Overworld-type worlds keep chunks in root `region/` (plus `entities/` and `data/`); Nether-type worlds only have `DIM-1/region`, End-type worlds only `DIM1/region`. This is the legacy layout, so vanilla's file fix turns each into one `dimensions/minecraft/{overworld,the_nether,the_end}` and the environment is known from the folder.
- **`level.dat` `Data.WorldGenSettings`** holds the world's own seed (999 for `survival`) and the real generator under its environment's key (`flatworld`'s `minecraft:overworld` entry is `minecraft:flat`). DataVersion 4671, below 4772, so the full file fix runs.
- **The spawn names the Bukkit world, not a vanilla dimension**: `survival`'s `level.dat` spawn has `dimension: minecraft:survival`, `hell`'s `minecraft:hell`; only `world_nether` and `world_the_end` say `minecraft:the_nether` / `minecraft:the_end`. Hence the Bukkit rule under [Level data](#level-data-for-the-imported-world-no-codec-change).
- **Extra files** `uid.dat` (raw 16 bytes, not NBT), `paper-world.yml` and `session.lock` sit beside `level.dat`; none of them is copied.

So `/worlds import survival survival` and `/worlds import hell hell` (the Nether is picked because it is the only non-empty dimension) need no argument (tests 15 and 5).

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
- Copy off-thread into staging and install with atomic moves. Every reference copies synchronously or in place.
- Refuse occupied target folders, not just loaded ids.
- Report the real decode error instead of "level.dat missing".

## Vanilla alignment

- **Source = a vanilla save in vanilla's saves directory.**
  - The dedicated server already treats `--universe` (default `.`) as its saves dir and `level-name` as one entry in it (`server/Main.java:81,125-131`).
  - The integrated server's saves dir is `<gameDir>/saves` (`client/Minecraft.java:595`).
  - `LevelStorageSource.findLevelCandidates()` is vanilla's own scan of that dir: directories holding `level.dat` or `level.dat_old` (`LevelStorageSource.java:204-224`). The suggestions use the same test.
- **Same safety rules as opening a world.**
  - Folder names: `SharedConstants.ILLEGAL_FILE_CHARACTERS` and `FileUtil.isPathPartPortable`, as vanilla's world names; capitals and spaces are allowed.
  - Symlinks: `allowed_symlinks.txt` through `DirectoryValidator.validateDirectory`. That is what `validateAndCreateAccess` enforces (`LevelStorageSource.java:117-132, 383-391`).
  - Session lock: `DirectoryLock.isLocked` (`DirectoryLock.java:66-74`), as the world list uses it (`LevelStorageSource.java:233`).
- **Same upgrade path as dedicated-server startup:** `getUnfixedDataTagWithFallback` → `fixAndGetSummaryFromTag` → refuse `requiresManualConversion` / `!isCompatible` → `DataFixers.getFileFixer().fix(access, tag, progress)` (`server/Main.java:130-153`). It runs on the *staged copy*, so a failed or interrupted upgrade never touches the source.
- **Same saved-data reading as the server:** `SavedDataStorage.get(type)` datafixes each file on read (`SavedDataStorage.java:85-96, 106-125`), so the importer decodes `world_gen_settings` and the mc-worlds world list, and the level its `game_rules` and `world_clocks`, with vanilla's own types and fixers. The fallbacks use the same storage's `readTagFromDisk`, which datafixes too.
- **Same chunk upgrade as vanilla:** lazily on load through `ChunkMap.upgradeChunkTag` (`ChunkMap.java:913-926`). The mixin only makes the datafix context say what vanilla would say for the same terrain.
- **Same storage layout.** In 26.x each dimension, vanilla ones included, is `dimensions/<ns>/<path>/` with its own `region/`, `entities/`, `poi/` and `data/` (`DimensionType.java:113-115`, `ServerChunkCache.java:93-101`, `ServerLevel.java:250-257`). Importing a dimension means moving exactly that folder.
- **Same per-world state formats.** mc-worlds' per-world weather, clock and game-rule files use vanilla's own `SavedDataType` codecs and datafix types (`CustomServerLevel.java:58`, `PerWorldClocks.java:16-20`, `PerWorldGameRules.java:29-33`), so the save's files carry over by a file copy plus a one-time switch of id on first load.
- **Not invented.**
  - No merging of global state.
  - No archive formats beyond zip, the only one the JDK reads (the dedicated server does not ship commons-compress, which Realms uses on the client for tar.gz).
  - No safe-spot spawn search beyond what mc-worlds already does.
  - No portal linking between imported dimensions.

## Current state in mc-worlds (before import)

- **Storage of a custom world.** It is `session.getDimensionPath(ResourceKey(DIMENSION, id))`, i.e. `<world>/dimensions/<ns>/<path>`. `loadOrCreateWorld` only logs whether the folder already exists (`manager/WorldsManager.java:96-102`). Region, entities, poi and the per-dimension `data/` are created by vanilla's `ServerLevel` constructor, which `CustomServerLevel` calls through `super(...)` (`server/level/CustomServerLevel.java:52-54`). A pre-existing folder is simply adopted, which is how the README's World Manager workaround works, so `create` must keep accepting an existing folder.
- **Per-world state mc-worlds adds on top of vanilla:**
  - Per-level `WeatherData`, stored as `<dim>/data/minecraft/weather.dat` (`CustomServerLevel.java:58`).
  - A per-level clock `mc-worlds:world_clocks`, i.e. `<dim>/data/mc-worlds/world_clocks.dat`. It reuses `ServerClockManager`'s codec and datafix type (`server/level/PerWorldClocks.java:17-21`).
  - Per-level game rule overrides `mc-worlds:game_rules` (`PerWorldGameRules.java:29-33`).
- **Registration and persistence:**
  - `WorldsData` is a `SavedData` holding `unboundedMap(Identifier, WorldsLevelData)` (`manager/WorldsData.java:17-22`). It is always dirty (`:32-35`), and `addLevelData` is at `:37-39`.
  - `WorldsLevelData.CODEC` = `core` (`WorldGeneratorData`) + `settings` + `game_time` (`manager/level/data/WorldsLevelData.java:20-24`).
  - `WorldGeneratorData` holds `level_stem` (`Either<LevelStem, Dynamic>` passthrough), `seed`, `generate_structures`, `prepare_spawn`, `lazy` and `provider` (`WorldGeneratorData.java:19-30`).
  - `WorldSettingsData` holds `respawn` and `initialized` (`WorldSettingsData.java:9-13, 23-26`).
  - **Import needs no new field.**
- **Create flow.**
  - `WorldsCreator.createWorld` validates the id, builds `WorldsLevelData.getDefault(…)` with `lazy=true`, then loads on the server thread and waits for `Active` (`WorldsCreator.java:90-116`).
  - The command prints "creating" and later "created, [Teleport]" (`command/CreateCommand.java:131-170`).
- **Lazy loading and unload.**
  - `getLevel` on a saved id calls `loadOrCreate` (`MinecraftServerMixin.java:38-64`).
  - `loadOrCreateWorld` returns `null` for ids in `unreadableGenerators` (`WorldsManager.java:36, 70-72`), before it registers anything (`:125`). The old `validLevelId` called `server.getLevel` and read that `null` as "free", so `create` over such an id was accepted, printed "creating" and never answered. The stored entry was **not** overwritten (reproduced as R1.4 below). The data-only `validLevelId` now refuses it with `world_already_exists`.
  - Empty worlds stop after 1200 ticks unless they hold the world spawn (`state/ActiveLevelState.java:21-26`).
  - Stopped levels are saved and closed at end of tick (`McWorldMod.java:51-75`), then the handle is dropped (`:77-81` → `WorldsManager.java:129-153`). `McWorld` itself is dropped on `SERVER_STOPPED` (`McWorldMod.java:53-55`).
- **First load.** An uninitialized world runs `InitializationLevelState` (`CustomServerLevel.java:62-63`), which **overwrites the spawn** (`state/SpawnPreparationHelper.java:59-82`) and may place a **bonus chest** (`:164-188`). Imported worlds are therefore registered `initialized=true`.
- **File-handling precedent (delete).** The folder is removed only after stop and close, with `FileUtils.deleteDirectory`, falling back to `forceDeleteOnExit` (`WorldsManager.java:134-147`). So an orphan folder can survive a failed delete; import refuses an existing target folder.
- **File fixes.** `DataFixersMixin` appends `McWorldsDataFileFix` (moves `data/mc-worlds.dat` → `data/mc-worlds/worlds_data.dat`) at `last vanilla fix + 1` = 4900 (`mixin/filefix/DataFixersMixin.java:25-30`). It is part of `DataFixers.getFileFixer()` and runs on staged 26.1.x and older imports, where it is harmless.
- **Spawn used by `/worlds tp`.** Overworld-like worlds use `levelData.getRespawnData().pos()` snapped to `MOTION_BLOCKING_NO_LEAVES` + 1 (`abstractions/TeleportTransitionAbstraction.java:22-27, 33-38`). Nether-like and End-like worlds ignore it (`command/WorldLocation.java:36-51`).
- **Seeds.** Terrain uses `CustomServerLevel.getSeed()`, the per-world seed (`CustomServerLevel.java:141-144`). `generate_structures` is stored but has **no reader** (`WorldsLevelData.java:62-64`).

## Vanilla internals (MC 26.3-rc-1)

- **Layout migration (the 26.1 file fix).**
  - `DimensionStorageFileFix` moves the root `region`/`entities`/`poi` → `dimensions/minecraft/overworld`, `DIM-1` → `the_nether` and `DIM1` → `the_end` (`util/filefix/fixes/DimensionStorageFileFix.java:59-67`), per-dimension `data/{chunks,raids,world_border}.dat` into `dimensions/<dim>/data/minecraft/` (`:27-46`), and global `scoreboard`, `idcounts` → `maps/last_id`, `map_N` → `maps/N` and `random_sequences` under `data/minecraft/` (`:47-59`).
  - `LevelDatToSavedDataFileFix` splits level.dat (`:40-99`): `world_gen_settings`, `weather_data`, `game_rules` and `world_clocks` go to `data/minecraft/*.dat` (`:55-57, 70-78, 157-168`), the dragon fight to `dimensions/minecraft/the_end/data/minecraft/ender_dragon_fight.dat` (`:43-45`), the world border to each dimension's `data/` (`:61-69`), the singleplayer `Player` tag to `players/data` (`:113-132`).
  - `PlayerStorageFileFix` moves `advancements`/`playerdata`/`stats` under `players/` (`:14-16`).
  - All of these are registered at 4772/4773, plus a fix at 4899 (`util/datafix/DataFixers.java:1589-1596, 1606-1607`). `FileRelation.DIMENSIONS` discovers *every* `dimensions/<ns>/<path>` (`util/filefix/access/FileRelation.java:20-21, 53-75`).
- **Which saves run the file fixer.**
  - `requiresFileFixing` compares the save's DataVersion (counted as 0 below 4772) with the fixer's latest version (`FileFixerUpper.java:66-72`). mc-worlds raises that latest version to 4900 (`DataFixersMixin.java:27`).
  - So saves **before 26.1** (below 4772) take the full path; **26.1.x** saves (4772–4899) run the remaining fixes, the mod's included; **26.2** (4903) and **26.3** (5023) saves need no file fix, and the step touches no file and only datafixes the level tag (`:83, 118-121`).
  - Otherwise it builds the result in `<world>/filefix/new_world` (`:87-100`) and swaps folders as siblings `<uuid> upgraded` and `<uuid> OUTDATED` **in the parent directory**, here `staging/` (`:263-278`). It needs atomic moves (`:283-285`), hard-links files where it can (`:292-296`), and deletes `session.lock` and `level.dat_old` in the result (`:223-224`). A failed cleanup can leave `<uuid> upgraded` behind. Hence: never on the source, delete the whole `staging/`, and resolve staged paths through the access after the fix.
  - `LevelSummary.backupStatus()` returns `FILE_FIXING_REQUIRED` before `DOWNGRADE`, so `isDowngrade()` is true only for newer saves (`LevelSummary.java:116-131`).
  - Running the file fixer does not upgrade chunks in general. Its one chunk-touching caller, `LegacyStructureFileFix`, handles only chunks with legacy structure data (`:131-147`).
- **level.dat and world gen settings.**
  - `PrimaryLevelData.parse` reads `Time` (game time) and `spawn` (`RespawnData`: dimension, pos, yaw, pitch) (`world/level/storage/PrimaryLevelData.java:100, 105`). `RespawnData.DEFAULT` is overworld (0,0,0) (`LevelData.java:33-34`). Day time lives in `world_clocks.dat` in 26.x.
  - `WorldGenSettings.TYPE` is a `SavedDataType` (`levelgen/WorldGenSettings.java:19`) = `WorldOptions` (`seed`, …) + `WorldDimensions` (`:15-24`, `WorldOptions.java:13-15`).
  - `WorldDimensions.CODEC` is `unboundedMap(ResourceKey<LevelStem>, LevelStem.CODEC)`, and its constructor **throws when the overworld entry is missing** (`WorldDimensions.java:38-49`). If the overworld stem fails to decode (unknown dimension type or biome source), the throw is caught in `SavedDataStorage` and `get()` returns `null` (`SavedDataStorage.java:85-96`); the seed is lost with it. The importer then decodes the raw tag by hand. A failing non-overworld entry is dropped by `resultOrPartial` (test 8).
  - **Datapack dimensions are not in `world_gen_settings.dat`.** A 26.3 save with the datapack dimension `test:extra` lists only the three vanilla stems; the dimension exists only as `level.dat` `DataPacks.Enabled` and its `dimensions/test/extra/` folder. Under decision 4 it uses the server's own stem with the same id when the server has that dimension (the same datapack installed), and is otherwise refused before any copy with `datapack_dimension`.
- **Version checks.** `fixAndGetSummaryFromTag` fixes a copy of the tag and builds a `LevelSummary` (`LevelStorageSource.java:537-542, 319-336`). `isCompatible()` compares only the version *series* (`LevelSummary.java:141-143`), so a dedicated server opens newer worlds; import refuses them.
- **Lazy upgrade on load and its dimension context.**
  - Chunks: `ChunkMap.readChunk` → `upgradeChunkTag` with context `{dimension: this.level.dimension(), generator}` (`ChunkMap.java:909-926`). `StructureCheck` builds the same context from its `dimension` field, which it uses for nothing else (`StructureCheck.java:51, 78, 152`); `ServerLevel` passes it at `ServerLevel.java:288`.
  - Entities (`EntityStorage.java:70`) and POI (`SectionStorage.java:306`): no fix reads `__context`.
  - `SimpleRegionStorage.upgradeChunkTag` returns the tag untouched if its version is ≥ current (`SimpleRegionStorage.java:50-69`). A chunk fixed with the wrong context cannot be fixed again later.
  - **Three fixes compare the context with `"minecraft:overworld"`:** `ChunkHeightAndBiomeFix` (2832 = 1.18: sections from -4, below-zero retrogen for `minecraft:noise`, blending; `ChunkHeightAndBiomeFix.java:126-182`), `BlendingDataRemoveFromNetherEndFix` (3108), and `BlendingDataFix` (4997 = 26.3: drops `blending_data`, re-adds it for overworld chunks with height 384 and min y -64; `BlendingDataFix.java:35-51`).
  - In an mc-worlds world the context is the custom id, so an imported Overworld was upgraded as a non-overworld: no blending data, and pre-1.18 chunks with air below y=0. Reproduced on HEAD (R1 below). The overworld branch hardcodes -64..320, so it is right exactly for dimension types with `min_y = -64` and `height = 384`.
- **What is per-dimension vs global** (`SavedDataStorage` file = `<folder>/<ns>/<path>.dat`, `SavedDataStorage.java:56-57`):
  - Per-dimension (`<dim>/data/`): `chunk_tickets`, `raids`, `ender_dragon_fight`, `world_border`; in mc-worlds also `weather`, `mc-worlds:world_clocks` and `mc-worlds:game_rules`.
  - Global (`<world>/data/`, `MinecraftServer.java:333-364, 435-437`): `world_gen_settings`, `random_sequences`, `weather`, `game_rules`, `world_clocks`, `custom_boss_events`, `scheduled_events`, scoreboard, command storage, stopwatches, and **maps**: `getMapData`/`setMapData`/`getFreeMapId` use `server.getDataStorage()` (`ServerLevel.java:1590-1601`), ids are `maps/<n>` (`maps/MapId.java:22-24`), the counter `maps/last_id` (`MapIndex.java:15-17, 28-31`).
  - Vanilla `GameRules` stores every rule (`GameRules.java:95-105`), and mc-worlds treats any stored rule as an override (`LayeredGameRules.java:32-46`), so only the rules that differ from the server's are written.
- **Paths and ids.**
  - `getDimensionPath` → `id.resolveAgainst(<world>/dimensions)` (`LevelStorageSource.java:509-511`, `DimensionType.java:113-115`); `resolveAgainst` only keeps the path under the root (`resources/Identifier.java:152-163`).
  - `FileUtil.isValidPathSegment` rejects `.`/`..`, illegal characters, capitals and spaces (`util/FileUtil.java:157-159`). It fits id segments, not save folder names.
- **Copying.** `RegionFile` opens files READ+WRITE (`RegionFile.java:66-68`), so the source is never opened with it: staging copies with commons-io, and the empty check reads headers through a READ-only channel.
- **Entity UUIDs** are tracked per level (`PersistentEntitySectionManager.java:34, 62-63`), so the same UUID in two levels only warns within one level.
- **Player spawn adjustment.** Vanilla players use `PlayerSpawnFinder.findSpawn`, which honours `respawn_radius` (`ServerPlayer.java:383-387`). Non-player entities snap to the heightmap (`Entity.java:1444-1449`).

## Design as built

### Command surface

```
/worlds import <id> <folder>               the source Overworld, or its only dimension with chunks
/worlds import <id> <folder> <dimension>   one dimension: minecraft:the_nether, minecraft:the_end, or any dimensions/<ns>/<path> in the source
/worlds import <id> <folder> all           every dimension with chunks
```

`command/ImportCommand.java:51-71`; one `run(withDimension, all)` helper builds each `executes`.

- **`<id>`:** `IdentifierArgument`, the same as `create`.
- **`<folder>`:** `StringArgumentType.string()`; names with spaces need quotes. Suggestions (`SourceSave.suggestFolders`, `:163-177`, built on `Util.ioPool()`): vanilla's `findLevelCandidates` plus base-dir folders with exactly one such subfolder, minus the server's own level and `mc-worlds`, escaped with `escapeIfRequired`.
- **`<dimension>`:** `IdentifierArgument`, suggested on `Util.ioPool()` from the source's folder names only (`SourceSave.suggestDimensions`, `:179-199`; no validation, lock or header reads): every `dimensions/<ns>/<path>` that has `region/`, `entities/` or `data/`, plus the legacy root `region/`, `DIM-1/` and `DIM1/`. In discovery (`:235-255`) the legacy root `region/` brings `entities/` and `poi/`, and a modern folder wins over a legacy one.
- **Default dimension** (`SourceSave.targets`, `:57-94`): the Overworld if it has chunks; otherwise the only dimension with chunks (a Bukkit `world_nether` folder); otherwise `choose_dimension`, listing the candidates.
- **Empty dimensions are not imported.** A dimension is empty when no `r.<x>.<z>.mca` of at least 8192 bytes has a non-zero entry in its 4096-byte location table, read through a READ-only channel (`RegionHeaders.java:27-74`). Naming one refuses with `empty_dimension`; `all` skips them and lists them in its success messages.
- **`all`:** ids follow the Bukkit convention (`SourceSave.derivedId`, `:96-111`): Overworld `<id>`, `minecraft:the_nether` `<id>_nether`, `minecraft:the_end` `<id>_the_end`, other `minecraft:` dimensions `<id>_<path>` (Paper 26.1+ `minecraft:survival` → `<id>_survival`), others `<id>_<ns>_<path>`, with `/` → `_`. Two source dimensions that derive the same id refuse with `id_collision`. Every id is checked before anything is copied; one taken id refuses the whole import. The messages say that portals in these worlds lead to the server's own Nether and End.
- **Permission:** `LEVEL_ADMINS`.
- **Messages:** `started`, then one line per world (`success` with the Teleport link, or `result.not_loaded`) and notes (warnings, `success.nested`, `success.portals`, `success.skipped_empty`), with `sendSuccess(…, true)` so ops and the log see them. `WorldImporter` returns them as `Line(message, failure)`; `result.not_loaded` lines and failures go out with `sendFailure` and are logged at ERROR (`ImportCommand.java:94-119`). Every phase is also logged at INFO, so RCON users can follow the log.
- **No generator, seed or spawn flags.**

### Validation (no `getLevel`)

`ImportCommand.importWorld` (`:73-92`) and the first two stages of `WorldImporter.run`:

1. **Busy.** The command refuses with `busy` while `McWorld.currentImport()` is set, naming the running import and its phase (`starting`, `copying`, `checking`, `upgrading`, `level data`, `maps`, `installing`). `WorldImporter.run` claims the single slot with `tryBeginImport` (`McWorld.java:38-55`) and releases it in one executor-less `whenComplete` at the end of the chain, so it is freed even when the server is stopping, and `McWorld` is dropped on `SERVER_STOPPED`.
2. **Id format** (command thread, `WorldsCreator.validLevelIdFormat`, `:126-132`): every id segment passes `FileUtil.isValidPathSegment`, otherwise `invalid_id`. `create` uses the same check.
3. **Folder** (`SourceSave.resolve`, `:121-160`, on `Util.ioPool()`):
   - Vanilla folder-name rules (`folderPath`, `:201-216`): not empty, `.` or `..`; `FileUtil.isPathPartPortable`; no `SharedConstants.ILLEGAL_FILE_CHARACTERS`; resolves directly inside the base dir. Otherwise `invalid_folder`.
   - Not the server's own level (`own_world`).
   - `level.dat` or `level.dat_old` present. If not, and the folder has no `region/` and exactly one subfolder with `level.dat`, that subfolder is the save (`nested`). If not, an existing `<folder>.zip` is imported as a zip; `<folder>.tar.gz`/`.tgz`/`.tar`/`.rar`/`.7z` gives `archive`, anything else `not_found`. A folder name that is a regular file is opened as a zip (below).
   - `getWorldDirValidator().validateDirectory(path, true)` is empty (`symlink`).
   - `!DirectoryLock.isLocked(save)` (`locked`). In singleplayer the open world throws `OverlappingFileLockException`, reported as `own_world`.
4. **Targets** (`SourceSave.targets` on the IO pool, then `WorldsCreator.validImportTarget` per id on the server thread before anything is copied, and again in `install`):
   - `validLevelId` (data only, shared with `create`, `WorldsCreator.java:134-143`): the id format above; the id is not in `WorldsData` and not in `server.levelKeys()`. Refused with `world_already_exists`.
   - `validImportTarget` (import only, `:145-153`) also refuses an existing `getDimensionPath(key)` with `folder_exists`. `create` keeps adopting existing folders.
   - Datapack dimensions (`LevelDataResolver.generatorAvailable`, `:74-78`, per source dimension, on the IO pool): a dimension passes when it is vanilla, the server has a dimension with the same id, or the save stores its generator. That last flag is computed at discovery from raw NBT keys without decoding (`SourceSave.storedGenerators`, `:257-290`): the dimension has its own `data/minecraft/world_gen_settings.dat` (Paper 26.1+), the root `world_gen_settings.dat` `data.dimensions` has its key, `level.dat` `Data.WorldGenSettings.dimensions` has its key (saves before 26.1 keep datapack dimensions there), or the save has an mc-worlds world list (`data/mc-worlds/worlds_data.dat` or the legacy `data/mc-worlds.dat`). Otherwise it is refused with `datapack_dimension` before anything is copied. `resolve` uses the same predicate to choose between `generator` and `datapack_dimension`.

### Zip and URL sources

Added 2026-10-29 (decisions 5 and 11). The `<folder>` argument also takes a zip file beside the server world, or a quoted URL; nothing else changes in the command.

- **Detection.** Text starting with `http://` or `https://` (any case) is a URL; anything else is a folder or a file. A folder name that is a regular file is opened as a zip; `<folder>` with no such folder but an existing `<folder>.zip` uses the zip. The zip check reads the magic bytes (`PK\3\4` or `PK\5\6`), not the extension, so `.mcworld` and extension-less downloads are handled the same way.
- **Zip in place** (`SourceSave.openArchive`). The zip is opened read-only with vanilla's `Util.ZIP_FILE_SYSTEM_PROVIDER` (the JDK zip file system, which vanilla requires at startup), retried with `Cp437` names when the UTF-8 read fails (Windows Explorer zips with non-ASCII names). The existing discovery runs on the zip's root `Path` unchanged: the nested-save rule (vanilla's `makeWorldBackup` zips put everything under `<levelId>/`; Finder's `__MACOSX/` has no `level.dat`), `RegionHeaders` (reads 4 KB per region file), `NbtIo`. The lock and symlink checks are skipped for zips. A zip without a save gives `no_save`, which names Bedrock; an unreadable one gives `bad_archive` (the JDK refuses entries with `.` or `..` path elements at open, and a truncated file has no end header). The `FileSystem` is held in `ImportJob.archive()`, closed right after the copy and again, idempotently, in `finish`.
- **Copy** (`StagingStage`). The parts are relative strings, resolved on each file system, so a zip `Path` is never mixed with a default-FS one. One `walkFileTree` per part, with `FOLLOW_LINKS` so a symlinked folder that `allowed_symlinks.txt` permits is copied rather than left empty, records each file's target and size (one stat per file) and creates directories as they are met, skipping `session.lock`; each target must stay under the staged folder after `normalize()`. The disk check sums the recorded sizes against `ImportJob.diskBudget()`: vanilla's `LevelStorageAccess.estimateDiskSpace()` minus 64 MiB, the level vanilla calls low disk space (`checkForLowDiskSpace`), so an import never takes the live world's last free space. `ImportJob.copy` (64 KB buffer, cancel check per buffer, a byte limit, throttled progress) is shared with the download; for a file the limit is its declared size, so the copy fails a file that yields more bytes than declared, so a zip cannot claim small entries and write more. Only `level.dat`, `level.dat_old`, `data/` and the chosen dimensions are inflated; the copy is the extraction, so the data is written once.
- **URL** (`ArchiveDownload`, modelled on vanilla `HttpUtil.downloadFile`, which has no timeouts, an `int` size cap and interrupt-only cancel). `HttpURLConnection` through `server.getProxy()`; URLs parsed with vanilla `Util.parseAndValidateUntrustedUri` (http and https only) and accepted with any authority, so hosts with `_` work; manual redirects (at most 5, relative `Location` resolved, http↔https allowed, other schemes and unparsable `Location` values give `invalid_url`); connect timeout 15 s, read timeout 30 s; `User-Agent: mc-worlds`, `Accept-Encoding: identity` and a refusal of any other `Content-Encoding`; non-200 → `failed` with the status; `text/html` → `share_page` (Google Drive, MediaFire and OneDrive share links); the budget is `ImportJob.diskBudget()` (Content-Length checked up front, the running count while streaming), so an endless or oversized body stops with `disk_space` and 64 MiB still free; a body shorter than Content-Length fails. Transport problems are thrown as `IOException` and reported by `job.failure` as `failed`. Progress goes to the phase every 5 s (`downloading 115/386 MB`). The file is `staging/download.zip`, deleted with staging.
- **Labels.** `ImportCommand` computes the label once (`ArchiveDownload.label`, string-based so it also works on text `URI` cannot parse) and passes it to `WorldImporter.run`; the raw text only reaches `plan`. Messages, the busy text and the log show the URL without `user:password@`, the query and the fragment.
- **Chat limit.** Chat input is capped at 256 characters, so long signed URLs (Discord CDN, S3) fit only in the server console.

### Pipeline

`WorldImporter.run` (`manager/importer/WorldImporter.java:51-76`), a chain of immutable records (`Plan` → `Prepared` → `Remapped`) sharing one `ImportJob` (`ImportJob.java`: server, folder, phase callback, the staged access, and one vanilla `UpgradeProgress` whose `isCanceled()` is also true once `!server.isRunning()`):

| # | Thread | Step |
|---|---|---|
| 1 | command | Busy and id format (above) |
| 2 | IO (`Util.nonCriticalIoPool()`, vanilla's daemon download pool) | `plan`: delete the whole `<world>/mc-worlds/staging/`; for a URL, `ArchiveDownload.download` into `staging/download.zip`; `SourceSave.resolve` or `openArchive`, `targets`, the datapack check |
| 3 | server | `validate` (`:90-95`): `validImportTarget` per id; `started` |
| 4 | IO | `StagingStage.stage` (`StagingStage.java`). One `walkFileTree` (following links, which `validateDirectory` has already allowed) over `level.dat`, `level.dat_old`, `data/` and the chosen dimensions' parts (modern folders, or the legacy root `region`/`entities`/`poi`, `DIM-1/`, `DIM1/`) records each file's target and size and creates the directories. Refuse with `disk_space` when the sizes exceed `ImportJob.diskBudget()`. Copy each file into `staging/<uuid>/` with `ImportJob.copy` (see [Zip and URL sources](#zip-and-url-sources)), skipping `session.lock` |
| 5 | IO | `LevelStorageSource.createDefault(staging).validateAndCreateAccess(uuid)`; the access goes into the job at once. `getUnfixedDataTagWithFallback()` → `fixAndGetSummaryFromTag`. Refuse `requiresManualConversion()` or `!isCompatible()` (`incompatible`, also for unreadable NBT) and `isDowngrade()` (`newer_version`) (`:129-144`) |
| 6 | IO | `DataFixers.getFileFixer().fix(access, tag, job.progress())`. A no-op for 26.2+ saves. The result is a `StagedImport(access, levelTag, rootData)` (`StagedImport.java`): `rootData` is one `SavedDataStorage` on the staged root `data/`, reused by every `resolve` and by the map pass |
| 7 | IO | `prepare` (`:97-113`): `LevelDataResolver.resolve` per dimension (`LevelDataResolver.java:80-159`): generator, seed, spawn, game time (below); then `copyRootState` (`:161-176`): the root `game_rules.dat`, `world_clocks.dat` and `weather.dat` are copied into `<dim>/data/minecraft/` of **every** imported dimension that has neither that vanilla file nor the matching mc-worlds file (`mc-worlds:game_rules`, `mc-worlds:world_clocks`). A dimension's own files win: an mc-worlds or Paper 26.1+ dimension keeps what it has |
| 8 | server | Only if the staged root has `data/minecraft/maps/<n>.dat`: one `getFreeMapId()` per source map (`MapRemapper.allocate`, `:66-72`) |
| 9 | IO | `MapRemapper.remap` (`:74-103`), see [Maps](#maps) |
| 10 | server | `install` (`WorldImporter.java:131-180`): re-check every target with `validImportTarget`; `createDirectories(parent)` and `Files.move(…, ATOMIC_MOVE)` for each staged dimension; if a move fails, move the already-moved folders back and fail. Then `WorldsCreator.registerAndLoad` per world (`WorldsCreator.java:120-124`: `addLevelData`, then `loadOrCreate` directly, no validation). A world that registers but does not load is reported with `result.not_loaded` as a failure line |
| 11 | any | `finish` (`:210-223`): the only owner of cleanup. Close the staging access, then delete the whole `staging/` on the IO pool while the server runs. Then release the slot |

**Game rules and clocks on first load.** The level, not the importer, converts the vanilla files (`CustomServerLevel.loadOrConvert`, `CustomServerLevel.java:182-212`). When `mc-worlds:game_rules` or `mc-worlds:world_clocks` is missing in the level's `data/` but `data/minecraft/game_rules.dat` or `world_clocks.dat` exists, it is read once with `readTagFromDisk` and the mc-worlds type's codec (without caching it under the vanilla type), stored under the mc-worlds type, and the vanilla file is deleted after the next `scheduleSave`. Game rules keep only the rules available on the server whose value differs from the live server rules (`GameRuleMap.setFromIf`, `PerWorldGameRules.create`); clocks are taken as they are (`PerWorldClocks.create`). A created world has neither file, so nothing changes for it.

- **Any failure:** the access is closed, `staging/` deleted, the slot released, and `failed` (or the specific refusal) sent; `ImportCommand.report` unwraps the `CompletionException`. The server world's dimensions are only written by the moves in step 10, and the moves and registration run in that one server task, so nothing can load a half-copied folder. Map files are written in step 9, so an import that fails at step 10 leaves its new map ids allocated with files no item references.
- **Server stop.** One cancel token, `ImportJob.progress()`, goes to the download and copy loops, the file fixer and the map upgrader. The download and the copy stop at the next 64 KB buffer, the file fixer and the map pass stop through the token, and every stage checks it first (after `stopped=true` the server executes queued tasks inline on the calling thread, where `isRunning()` is already false). Staging is left on disk and deleted by the next import. The `plan` stage runs on a daemon pool, so a download stalled in `read()` does not hold the server stop or JVM exit; a `disconnect()` from a `SERVER_STOPPING` hook was tried and blocked the server thread on the connection's lock until the read timeout. Whether a large copy still delays JVM exit is test 9.
- **Crash between move and registration** (only a JVM crash inside step 10) leaves an unregistered folder; the next import with that id refuses with `folder_exists`, and `/worlds create` adopts it.
- **With `all`:** the root state (vanilla, older Paper) goes to every imported dimension, as the save's game rules apply to every dimension in vanilla. Paper 26.1+ and mc-worlds dimensions bring their own. One dimension the importer cannot resolve fails the whole `all` import; a datapack dimension the server does not have is caught by the validation, so nothing is copied.

### Level data for the imported world (no Codec change)

`LevelDataResolver.resolve`, first hit wins:

1. **mc-worlds source** (`:90-101`): the entry for the source dimension id in the staged root `data/mc-worlds/worlds_data.dat`, taken whole (generator, seed, `generate_structures`, provider). A passthrough or missing `level_stem` (the source had a generator this server can't decode) refuses with `generator`. Its spawn is kept, re-keyed, when it names that dimension; otherwise `RespawnData.DEFAULT`. `initialized=true`.
2. **Generator** (`:103-130`, `readGenerator` `:194-216`): `get(WorldGenSettings.TYPE)` from the dimension's own `data/` (Paper 26.1+), then the root's, taking the stem under the dimension's key. When `get()` returns `null` or lacks the entry, the file is read with `readTagFromDisk` (datafixed) and `WorldOptions.CODEC` and the one `LevelStem` entry are decoded separately, so the seed survives a broken overworld entry (V1). For a non-vanilla dimension with its own file, the key is its environment (below).
3. **No stem:** when the server has a stem with the dimension's key, that stem is used: with `warning.generator` for a vanilla dimension, silently for a custom one (a datapack dimension whose datapack the server also has). Without a server stem, a dimension that passes `generatorAvailable` (vanilla, or its generator is stored in the save) refuses with `generator` and the decode error, any other with `datapack_dimension`. Without any `WorldOptions`, a random seed.
4. **Spawn and game time** (`:132-150`):
   - Paper's `data/paper/level_overrides.dat` in the dimension: `spawn.pos`, `yaw`, `pitch` and `game_time`, read by hand because its `spawn` has no dimension.
   - Otherwise `level.dat` `spawn` and `Time`. The spawn is kept when its dimension is the imported one, or (**Bukkit rule**, `keepsSpawn`, `:178-188`) when it names no dimension of the save and the import is the save's only non-empty dimension (`minecraft:survival` in a Paper 1.21 `survival/` save). Otherwise `RespawnData.DEFAULT`.
   - Every kept spawn is re-keyed with `RespawnData.of(newKey, pos, yaw, pitch)`. Nether and End worlds ignore it in `/worlds tp`, as for `create`.
5. The result is `new WorldsLevelData(new WorldGeneratorData(stem, seed, generateStructures, true, true, DEFAULT), new WorldSettingsData(spawn, true), gameTime)`: `initialized=true` skips the spawn rewrite and the bonus chest; `game_time` keeps villager and brain timestamps coherent; `lazy=true` as in `create`.

**Environment of a Paper 26.1+ dimension** (`environment`, `:218-254`). Paper keeps the world's real generator under the vanilla key of its environment, and the environment itself only in Multiverse's `worlds.yml`. The importer uses `ender_dragon_fight.dat` in the dimension's `data/` as the End. Otherwise it walks the staged region files in order, reads each stored chunk through a `SimpleRegionStorage` on the staged copy, upgrades it with the overworld context (`ChunkMap.getChunkDataFixContextTag(Level.OVERWORLD, …)`), skips chunks before the `biomes` status (`SerializableChunkData.getChunkStatusFromTag`) (proto chunks default to plains), and takes the first chunk with a biome the server knows: `#is_nether` → Nether, `#is_end` → End, anything else → Overworld. If no chunk resolves, it refuses with `generator`.

### Chunk datafix context mixin

`DimensionDetectionUtil.chunkFixContextDimension(key, type)` (`util/DimensionDetectionUtil.java:43-49`) returns `Level.OVERWORLD` when the type is overworld-like (the vanilla type or the `mc-worlds:overworld_like` tag) **and** has `minY() == -64` and `height() == 384`, otherwise the key. The `(level, key)` overload (`:39-41`) applies it only to a `CustomServerLevel`. Used by:

- `mixin/fixes/ChunkDataFixContextMixin.java:20-30`: `@ModifyArg` on the `dimension` argument of `getChunkDataFixContextTag` in `ChunkMap.upgradeChunkTag`, for `CustomServerLevel`s.
- `mixin/fixes/ServerLevelMixin.java:40-50`: `@ModifyArg` on the `dimension` argument of `new StructureCheck(...)` in the `ServerLevel` constructor, for `CustomServerLevel`s.
- The importer's map pass, which has no level.

That range is what `ChunkHeightAndBiomeFix` (section -4) and `BlendingDataFix` (`384, -64`) write, so the overworld branch is correct exactly for those types. Any other level keeps its own id. Below-zero retrogen still applies only to the `minecraft:noise` generator, which vanilla reads from the same context. Existing overworld-like worlds get the same treatment for chunks not loaded since an MC upgrade; chunks already upgraded under the custom id stay as they are.

### Maps

`MapRemapper` (`manager/importer/MapRemapper.java`):

- **Ids.** `sourceMapIds` lists the staged `data/minecraft/maps/<n>.dat` (after the file fix, `idcounts`/`map_N` are already there). `allocate` takes one `getFreeMapId()` per file on the server thread, giving an old → new table.
- **Map files** (`writeMap`, `:113-130`): read with `readTagFromDisk` (datafixed as `SAVED_DATA_MAP_DATA`); in the NBT, `dimension` is set to the new key when that source dimension is imported, otherwise `locked` is set so `MapItem.update` never redraws it (`MapItem.java:90`); then written on the IO thread straight to the server's `data/minecraft/maps/<new id>.dat` in `SavedDataStorage`'s format (`NbtUtils.addCurrentDataVersion`, `NbtIo.writeCompressed`). The server reads it on first `getMapData`. An unreadable file is logged and skipped; its new id stays allocated and empty. Item ids with no map file stay unchanged.
- **References** (`remap`, `:74-103`): per imported dimension, one vanilla `RegionStorageUpgrader.Builder` copy over `region` (`DataFixTypes.CHUNK`, context from `chunkFixContextDimension(newKey, stem type)` plus `ChunkMap.getChunkDataFixContextTag` with the stem's generator) and one over `entities` (`ENTITY_CHUNK`), each with a tag modifier at the current version and `trackProgress(job.progress())` as the cancel token. The modifier (`replaceMapIds`, `:132-163`) walks only `block_entities`, `entities` and `Entities`, with every nested item component under them, and replaces every int `minecraft:map_id` found in the table: item frames, chests, shulker and bundle contents, villager offers. Per-chunk errors follow vanilla's `forceUpgrade`: an IO error is logged and the chunk skipped, any other exception fails the import.
- **Cost.** Vanilla's upgrader writes a chunk when it changed **or is older than the current version**, so the pass rewrites, and fully datafixes with the right context, every staged chunk older than 26.3 whenever the save has maps. On a big old save that is a full upgrade with a write per chunk (test 10).

### Files

| File | Lines | Role |
|---|---|---|
| `command/ImportCommand.java` (new) | 128 | Brigadier tree, suggestions on the IO pool, busy and id-format checks, result messages |
| `manager/importer/WorldImporter.java` (new) | 244 | Orchestration across threads, import slot, install with rollback, result lines, finish and staging cleanup |
| `manager/importer/ArchiveDownload.java` (new) | 134 | URL detection and labels, the download into staging |
| `manager/importer/SourceSave.java` (new) | 415 | Folder rules, zips opened in place, nested and archive detection, symlink and lock checks, dimension discovery and the stored-generator flag, suggestions, default dimension and `all` ids |
| `manager/importer/RegionHeaders.java` (new) | 76 | READ-only region header scan: `hasStoredChunk`, `storedChunks` |
| `manager/importer/StagingStage.java` (new) | 196 | Steps 4–6: staging, disk check, copy loop for folders and zips, summary checks, file fix |
| `manager/importer/StagedImport.java` (new) | 10 | Record: staged access, fixed level tag, root `SavedDataStorage` |
| `manager/importer/LevelDataResolver.java` (new) | 309 | Step 7: mc-worlds entry, generator with raw fallback, environment, spawn and time, root state copy, `generatorAvailable` |
| `manager/importer/MapRemapper.java` (new) | 164 | Map ids, map files, the map-id upgrader pass |
| `manager/importer/ImportException.java` (new) | 58 | Keyed exception → `Component`; `stopped` and `failed` factories |
| `ImportJob`, `ResolvedDimension`, `SourceDimension` (new) | 41, 14, 9 | Job context with the cancel token; records |
| `mixin/fixes/ChunkDataFixContextMixin.java` (new) | 31 | `ChunkMap` context |
| `mixin/fixes/ServerLevelMixin.java` | +13 | `StructureCheck` context |
| `util/DimensionDetectionUtil.java` | +16 | `chunkFixContextDimension` (two overloads), `isOverworldLikeDimension(Holder)` |
| `server/level/CustomServerLevel.java`, `PerWorldGameRules.java`, `PerWorldClocks.java` | +44, +10, +2 | `loadOrConvert`: vanilla `game_rules.dat`/`world_clocks.dat` → per-world files on first load |
| `manager/WorldsCreator.java` | +47 | `validLevelIdFormat`, data-only `validLevelId`, `validImportTarget`, `registerAndLoad`, translation keys on `InvalidLevelIdException` |
| `command/TeleportCommand.java`, `CreateCommand.java`, `ListCommand.java` | +14, −8, −4 | Shared `/worlds tp` link and command string; `create` shows the exception's key |
| `manager/WorldsData.java`, `util/filefix/fixes/McWorldsDataFileFix.java` | +1, +2 | Shared `LEGACY_FILE` name |
| `McWorld.java` | +23 | Import slot and phase |
| `command/WorldsCommands.java`, `worlds.mixins.json` | +1 each | Register the command and the mixin |
| `lang/en_us.json` | +29 | Keys below |
| `README.md`, `CHANGELOG.md`, `AGENTS.md` | done | "Import Command" section; the World Manager note keeps `create` adoption and adds import |

Datagen: none. Language files are hand-written (`DataGenerator.java:11-15`). No public API change.

### i18n keys

Prefix `dev.wroud.mc.worlds.command.import.` unless a full key is given. Every key the importer and `ImportCommand` use is in `en_us.json`; stop reasons use `reason.stopped` everywhere. The first argument of `failed` is the save folder, except in `LevelDataResolver.copyRootState`, which names the source dimension.

| Key | en_us |
|---|---|
| `started` | `Importing %s from %s, please wait` |
| `success` | `World %s imported from %s (%s). ` (followed by the existing `…create.success.teleport` link) |
| `success.nested` | `Found the save in subfolder %s of %s` |
| `success.portals` | `Portals in these worlds lead to the server's Nether and End` |
| `success.skipped_empty` | `Skipped empty dimensions: %s` |
| `result.not_loaded` | `World %s was registered but could not be loaded: %s` |
| `warning.generator` | `%s has no generator this server can read; new chunks use the server's %s generator` |
| `warning.global` | `Difficulty and player data are server-wide and were not imported` (sent when the save's difficulty differs from the server's) |
| `exception.not_found` | `No world save named %s next to the server world` |
| `exception.archive` | `%s is not a zip archive; extract it next to the server world first` |
| `exception.bad_archive` | `%s is not a readable zip archive: %s` |
| `exception.no_save` | `%s has no Java Edition world save (no level.dat); Bedrock worlds are not supported` |
| `exception.invalid_url` | `Invalid download URL: %s` |
| `exception.share_page` | `%s returned a web page, not a file; use a direct download link` |
| `exception.invalid_folder` | `Invalid folder name: %s` |
| `exception.own_world` | `%s is this server's own world` |
| `exception.locked` | `World %s is open in another game` |
| `exception.symlink` | `World %s contains symbolic links that are not allowed (see allowed_symlinks.txt)` |
| `exception.newer_version` | `World %s was saved by a newer Minecraft version (%s)` |
| `exception.incompatible` | `World %s cannot be opened by this version (%s)` |
| `exception.no_dimension` | `World %s has no dimension %s` |
| `exception.empty_dimension` | `Dimension %s of %s has no chunks (never visited); use /worlds create for a fresh world` |
| `exception.choose_dimension` | `World %s has several dimensions: %s. Add one of them, or all` |
| `exception.id_collision` | `Dimensions %s of %s would all be imported as %s; import them one at a time with their own ids` |
| `exception.generator` | `Cannot read the generator of %s in %s: %s` |
| `exception.datapack_dimension` | `Dimension %s of %s comes from a datapack this server does not have; install the same datapack on the server first` |
| `exception.disk_space` | `Not enough disk space to import %s` |
| `exception.busy` | `Another import is still running: %s` |
| `failed` | `Importing %s failed: %s` |
| `reason.stopped` | `the server is stopping` (second argument of `failed` when the server stops mid-import) |
| `reason.see_log` | `see the server log` (second argument of `result.not_loaded` when loading returns no level) |
| `dev.wroud.mc.worlds.command.exception.folder_exists` | `A folder for world %s already exists on disk` |
| `dev.wroud.mc.worlds.command.exception.invalid_id` | `Invalid world id: %s` (an id segment that is not a valid folder name; `create` and `import`) |
| `dev.wroud.mc.worlds.command.create.exception.world_already_exists` (reused) | `A world with that id already exists!` |

## Edge cases & risks

- **Adventure maps lose global state.** Players, inventories, advancements, stats, scoreboard and teams, command storage, bossbars, scheduled functions, difficulty, datapacks and `generated/` structure templates are all server-global in 26.x and are not imported. Command blocks keep working, but any `execute in minecraft:overworld`, `setworldspawn` or absolute-coordinate command acts on the server's real dimensions. Document this as "imports the terrain, not the game."
- **Maps.** Without the remap, item frames and chests keep their `map_id`, which resolves against the *server's* global maps (`ServerLevel.java:1590-1597`): colliding ids show **another map's image** (reproduced, R1.3). The remap covers everything inside the imported dimensions; maps in player inventories and ender chests stay behind with the player data. The pass also rewrites every older chunk (see [Maps](#maps)).
- **Chunks already upgraded under a custom id** (existing worlds, or a world adopted by hand before the mixin ships) keep their wrong upgrade.
- **Overworld-like types with another height** keep their own id as context, so their pre-26.3 chunks lose blending data. Vanilla has no correct answer for them either (X2 `d2:low` check).
- **Undecodable generator.** A vanilla dimension falls back to the server's generator with a warning; existing chunks are untouched and only new chunks differ. A datapack dimension the save does not describe (26.1+ saves list it only in `DataPacks.Enabled`) uses the server's stem with the same id when the server has the same datapack, and is otherwise refused with `datapack_dimension` before anything is copied. One the save stores (`level.dat` `WorldGenSettings` before 26.1, which the file fix moves to the root `world_gen_settings.dat`) imports with its stored generator. A custom dimension without a decodable stem and without a server stem of that id is refused the same way.
- **Newer saves** are refused. A dedicated server would open them and silently drop unknown content on the next save (`SimpleRegionStorage.java:52-54`).
- **Size and IO.** A multi-GB copy runs on one `Util.ioPool()` thread and competes with the live server's region IO (test 10).
- **Atomic moves.** `FileFixerUpper` throws `AtomicMoveNotSupported` on filesystems without them (`FileFixerUpper.java:283-285`). Staging inside the world dir keeps every move on one filesystem.
- **Per-dimension data comes along:** forceloaded chunks (reactivated on load: `state/ActivationLevelState.java:24-29`), active raids, the world border and the End dragon state. Each can be reset with `/execute in <id> run forceload remove all | worldborder …`.
- **Indoor spawns.** mc-worlds snaps overworld-like spawns to the heightmap (`TeleportTransitionAbstraction.java:33-38`), not vanilla's `PlayerSpawnFinder`. An adventure map whose spawn is inside a building lands players on the roof. Pre-existing; a separate fix.
- **Importing the same save twice** duplicates entity UUIDs across two levels, which vanilla tolerates.
- **Identifier aliasing.** `x:../minecraft/the_nether` stays under `dimensions/`, so `resolveAgainst` accepts it. The segment check in `validLevelId` closes this for import and create.
- **Integrated server.** In singleplayer the base dir is `saves/`, so another singleplayer world can be imported by name; the open world is refused as `own_world`.

## Decisions (2026-10-29)

1. **Chunk context: a mixin**, not a per-import `RegionStorageUpgrader` pre-pass.
2. **The mixin covers overworld-like types too**, decided in code per level: overworld-like and `minY -64`, `height 384`.
3. **Dimensions: the Overworld by default, `<dimension>` for one, `all` as an opt-in** with Bukkit-style suffixed ids, refused on a collision.
4. **Undecodable generator:** fall back to the server's generator for a vanilla dimension with a warning. A custom dimension, including a datapack dimension, uses the server's dimension with the same id when the server has it (no warning), and is refused with `datapack_dimension` otherwise, before any copy for a datapack dimension. Vanilla dimensions without a stem keep falling back with `warning.generator`.
5. **Source location: the server root only**, one nested level of descent, tab completion at most one level deep. Amended 2026-10-29: a zip there is imported in place; other archives (tar.gz, rar, 7z) keep the "extract it first" error.
6. **Permission: `LEVEL_ADMINS`**.
7. **Another mc-worlds server's worlds are supported**, through the source's `worlds_data.dat`.
8. **Paper 26.1+ is supported**: per-dimension generator, state and spawn, with the environment from `ender_dragon_fight.dat` or chunk biomes. Paper before 26.1 needs only the Bukkit spawn rule on top of the vanilla legacy path.
9. **Maps are carried** under fresh ids with one `RegionStorageUpgrader` pass, only when the save has maps. Rejected: copying ids above the server's `last_id` (collides whenever both sides made maps); a lazy remap at load (needs a permanent table, not idempotent); a per-world map namespace (breaks maps carried between worlds).
10. **Empty dimensions are not imported**: refused when named, skipped and listed with `all`.
11. **URLs (2026-10-29)**: the same `<folder>` argument takes a quoted `http(s)://` URL to a zip, downloaded into staging and then imported as a zip. Zip only; http and https with no other address limits (the command is admin-only); no download cache, so a failed import deletes the download.

## Manual test plan

All runtime checks go through the `mc-server-probe` skill from the repository root. `R=.agents/skills/mc-server-probe/scripts/run.sh`; a universe is `.probe/server/universes/<u>`, and its server's saves dir is that folder, so a source goes next to `world/` as `.probe/server/universes/<u>/<folder>` (`U` below = `.probe/server/universes/imp`). Create it with `mkdir -p $U`, copy sources in, and never pass `--fresh` to it afterwards (that deletes the copied sources). Probe hooks used below: `@levels`, `@spawn`, `@chunk <dim> <cx> <cz>` (the stored chunk: `dataVersion`, `status`, `blending`, `sections`, biomes), `@map <id>` (`dimension`, `locked`, `center`, `colorsHash`) and `@await <regex> [ticks]`; `--cmd` cannot contain `;` or `+` inside one command. After a `kill -9`, restore `cp .probe/server/server.properties.orig versions/latest/run/server.properties`. A stored chunk only changes on disk after it is modified (one `setblock` per chunk), then `save-all flush; @await Saved the game 200`.

### Fixtures

Built 2026-10-28/29 in the session scratchpad (`F` below); rebuild them from these recipes. Docker on Apple Silicon, image `itzg/minecraft-server`, seed 12345 unless noted.

- **Vanilla** (`build.sh <name> <tag> <version> [rcon …]`: `docker run -e TYPE=VANILLA -e VERSION=<v> -e SEED=12345 -e MAX_TICK_TIME=-1 …`, `forceload add -64 -64 63 63` in the Overworld and `0 0 31 31` in the Nether and End, the extra commands, `save-all flush`, stop, copy `world/`; rerun alone and resave if a stop was killed). `v1_17_1` (java17, DataVersion 2730, legacy `Level` chunks, no `poi/`), `v1_20_4` (java17, 3700), `v1_21_11` (java21, 4671), `v26_1_2` (java25-alpine, 4790: runs the **partial** file fix), `v26_2` (4903: no file fix), `v26_3_extra` (5023, plus a world datapack `extra` with dimension `test:extra`, pack format 121, forceloaded), `v26_3_nested` (a plain 26.3 world at `Nested World/Nested World/level.dat`).
- **Paper** (`TYPE=PAPER`, `java25-alpine`, `PLUGINS=<Multiverse-Core 5.8.2 jar>`): `paper26_3` (Paper 26.3 build 227; `mv create` survival `--seed 999`, flatworld flat, hell nether, amp amplified `--seed 42`, voidend the_end, emptynether nether whose `region/` is then deleted by hand), `paper1_21_11` (Paper 1.21.11 build 132, same worlds without emptynether, one sibling save each), `big26_3` (Paper 26.3 build 227 with Chunky 1.5.3: `chunky radius 2400`, 90,601 chunks, 987 MB; `MEMORY=1536M` on an 8 GB Docker VM).
- **Probe-built** (in this repo with the probe): `mcw-world` (an mc-worlds save with `imp:src`, flat preset, seed 1234, `advance_time false`, mc-worlds spawn and world spawn at 5 -60 5, a gold block at 3 -60 3), `maps-src` (an Overworld with maps 0–4 from `minecraft:chests/shipwreck_map`, placed in a chest, an item frame, a shulker in a chest and a bundle in a chest in chunk 0,0; map 4 unreferenced), universe `fx4-maps-dst` (a server with its own maps 0–2), and `d2-type-datapack` (dimension type `d2:low`, `min_y 0`, `height 256`, tagged `overworld_like`, with dimension `d2:low`).
- Copy: `cp -R $F/vanilla/v26_3_extra $U/v26_3`, `cp -R $F/paper/paper26_3/world $U/paper26_3`, `cp -R $F/paper/paper1_21_11/{survival,flatworld,hell,world_nether} $U/`, `cp -R $F/probe/mcw-world $U/`, `cp -R $F/paper/big26_3/world $U/big26_3`, and the other vanilla fixtures under their own names.

`$A` below is `@await imported|failed 1200`, the end of an import that started; a refusal prints at once, before any copy.

### Reproduction results (2026-10-29)

On HEAD `2605463` (a separate worktree) by copying fixture region files into a world made with `worlds create imp:ow from-dimension minecraft:overworld 12345`, then forceload, one `setblock` per chunk, `save-all flush` and `@chunk`; the fixed runs (X2) used the same commands on the working tree. The baseline is the same fixture opened as the server's own world.

| Check | Vanilla | HEAD | With the mixin |
|---|---|---|---|
| R1.1 blending, `v1_21_11` chunks | `blending=-4..20` | `blending=none` | `-4..20` |
| R1.2 depth, `v1_17_1` chunks | deepslate at y -30, bedrock at -64, `blending=0..16`, biomes kept | air below y 0, the old bedrock floor at y 0, biomes all plains | identical to vanilla |
| `d2:low` world (`min_y 0`, height 256), `v1_17_1` chunks | — | — | own context kept: `sections=0..15`, no blending |
| `StructureCheck`: `execute in imp:ow positioned 0 64 0 run locate structure #minecraft:village` | `[160, ~, 880]` | same | same |
| R1.3 map ids, `maps-src` chunks copied into `fx4-maps-dst` | — | the frame's `map_id 1` shows the server's map 1 (`ce4056e5`, not the source's `21a7f11e`) | remap: test 17 |
| R1.4 `worlds create imp:d2` after its datapack type was removed | — | accepted, "please wait", never answers; `worlds_data.dat` and the folder unchanged | `world_already_exists` at once |

So both chunk-context problems are real and visible on disk after the chunk is saved, the mixin matches vanilla, and the "overwrite hole" was not reproduced (the real defect was the silent hang).

### Tests

1. **26.3 save, Overworld.** `$R --universe imp --cmd "worlds import imp:a v26_3; $A; @levels; execute in imp:a run seed; execute in imp:a run worlds settings spawn; @chunk imp:a 0 0; execute in imp:a positioned 0 64 0 run locate structure #minecraft:village"`. Expect the phases in the log, `success` with the Teleport link, seed 12345, the save's spawn, `dataVersion=5023`, and the village the fixture gives when opened as vanilla. Walking past the explored edge (mc-client) shows no seam.
2. **Source untouched.** `find $U/v26_3 -type f -exec shasum {} + | sort` before and after test 1 and test 5c is identical; `ls $U` has no `" upgraded"` or `" OUTDATED"`; `test ! -e $U/world/mc-worlds/staging`.
3. **State.** Build `v26_3_state` with `build.sh v26_3_state java25-alpine 26.3 "gamerule keep_inventory true" "time set 6000" "weather thunder 1000000"`. `worlds import imp:st v26_3_state; $A; execute in imp:st run gamerule keep_inventory; execute in imp:st run gamerule advance_time; execute in imp:st run time query time`: `keep_inventory` is true as a world override, `advance_time` is inherited, the time continues from the source's. `world/dimensions/imp/st/data/minecraft/weather.dat` holds the source's thunder (read both files with nbtlib).
4. **Nether, End and `all`.**
   - `worlds import imp:n v26_3 minecraft:the_nether; $A; execute in imp:n positioned 0 64 0 run locate biome minecraft:nether_wastes` succeeds. `worlds import imp:e v26_3 minecraft:the_end` carries the source's `data/minecraft/ender_dragon_fight.dat` with the dimension.
   - `worlds import imp:s "Nested World" all; $A; @levels` gives `imp:s`, `imp:s_nether`, `imp:s_the_end` and the portal note. After `worlds create imp:t_nether`, `worlds import imp:t "Nested World" all` refuses with `world_already_exists` and imports nothing.
   - A copy of `v26_3` with `dimensions/minecraft/x_y` and `dimensions/minecraft/x/y` (copies of `the_nether`): `worlds import imp:c <copy> all` refuses with `id_collision`.
5. **Legacy and Bukkit layouts.**
   - `worlds import imp:l v1_20_4; $A; @chunk imp:l 0 0` (log shows vanilla's upgrade of the staged copy; the chunk is still 3700 on disk). Then forceload, `setblock`, flush and `@chunk imp:l 0 0`: `dataVersion=5023` with `blending`.
   - `worlds import imp:ln v1_20_4 minecraft:the_nether`: the staged copy has `DIM-1/` but no root `region/`; the import succeeds.
   - `worlds import imp:bn world_nether` picks the Nether without an argument.
   - 5c. `worlds import imp:p v26_1_2`: the log shows the partial file fix, test 2's checks pass. `worlds import imp:q v26_2` runs no file fix.
6. **Folder handling.** `cp -R "$F/vanilla/v26_3_nested/Nested World" "$U/"`: `worlds import imp:nest "Nested World"` imports and sends `success.nested`. `touch $U/Map.tar.gz`: `worlds import imp:z Map` refuses with `archive`.
7. **Refusals** (each one `worlds import …; $A`):
   - `imp:x nope` → `not_found`; `imp:x "../world"` → `invalid_folder`; `imp:x world` → `own_world`.
   - an existing id → `world_already_exists`; an id in `unreadableGenerators` (the R1.4 recipe) → `world_already_exists`; `mkdir -p $U/world/dimensions/imp/x` then `imp:x v26_3` → `folder_exists`.
   - `ln -s v26_3 $U/linked`, `imp:x linked` → `symlink`.
   - hold the lock from the host: `python3 -c "import fcntl,time;f=open('$U/v26_3/session.lock','a+');fcntl.lockf(f,fcntl.LOCK_EX);time.sleep(600)" &`, then `imp:x v26_3` → `locked`.
   - a copy of `v26_3` with `level.dat` `DataVersion` set to 9999 (nbtlib) → `newer_version`.
   - a copy of `v26_3` without `dimensions/minecraft/overworld/region` → `choose_dimension` listing the rest.
   - `imp:x paper26_3 minecraft:emptynether` → `empty_dimension`; with `all` it is skipped and listed.
   - `worlds import imp:b1 big26_3; worlds import imp:b2 v26_3` → `busy` naming `imp:b1 from big26_3 (copying)`.
   - `chmod -R 000 $U/v26_3/data` → `failed`, and `world/mc-worlds/staging` is gone (restore with `chmod -R u+rwX`).
8. **Generators.**
   - `worlds import imp:x v26_3 test:extra` → `generator` (no entry in `world_gen_settings.dat`).
   - A copy of `v26_3` whose `data/minecraft/world_gen_settings.dat` `minecraft:the_nether` entry has `type: d2:low` (a type the server lacks): the Overworld import keeps its own stem (partial decode).
   - The same with the `minecraft:overworld` entry broken: the import succeeds with `warning.generator` and still has seed 12345 (raw fallback).
9. **Interruption.**
   - `$R --universe imp --cmd "worlds import imp:big big26_3; wait:40; stop"` exits within seconds, leaves no `world/dimensions/imp/big`, and `imp:big` is not registered.
   - Run the same import, `pkill -9 -f universes/imp` during the copy, restore `server.properties`, restart with `--cmd "worlds list; @levels"`: `imp:big` is not registered, the source is intact, and the next import deletes the leftover `staging/`.
10. **Responsiveness.** `worlds import imp:big big26_3; wait:20; tick query; wait:200; tick query; @await imported|failed 12000`, and the same on an old save with maps for the map pass. Record mspt and the phase timestamps.
11. **Lazy lifecycle.** After test 1: `wait:1300; @levels` (no `imp:a`), `execute in imp:a run seed` reloads it; a restart keeps it in `worlds list`; `worlds delete imp:a` removes `world/dimensions/imp/a`.
12. **Singleplayer** (mc-client, not the probe): in `./gradlew runClient`, open world A and run `/worlds import imp:b B` for another save B; `/worlds import imp:c A` is refused (`own_world`).
13. **Global state is not imported.** Build the source with the extra rcon command `scoreboard objectives add src dummy`; after the import `scoreboard objectives list` has no `src`.
14. **Paper 26.1+.** `worlds import mv:survival paper26_3 minecraft:survival; $A; execute in mv:survival run seed` → 999, spawn (`worlds settings spawn`) equals `level_overrides.dat`. `mv:flat` from `minecraft:flatworld` generates flat (`@chunk` past the explored edge after a forceload), `mv:hell` from `minecraft:hell` is nether-type (`locate biome minecraft:nether_wastes`), `mv:amp` from `minecraft:amp` has seed 42, `mv:void` from `minecraft:voidend` is End-type. `worlds import mv paper26_3 all` gives `mv`, `mv_nether`, `mv_the_end`, `mv_survival`, `mv_flatworld`, `mv_hell`, `mv_amp`, `mv_voidend` and lists `minecraft:emptynether` as skipped.
15. **Paper before 26.1.** `worlds import old:survival survival` → seed 999 and the source spawn (Bukkit rule); `old:flat flatworld` generates flat; `old:hell hell` picks the Nether without an argument and is nether-type; test 2's checksums hold.
16. **mc-worlds source.** `worlds import imp:moved mcw-world imp:src; $A; execute in imp:moved run seed; execute in imp:moved run worlds settings spawn; execute in imp:moved if block 3 -60 3 minecraft:gold_block; execute in imp:moved run time query time; wait:100; execute in imp:moved run time query time` → seed 1234, spawn 5 -60 5, `Test passed`, the time is frozen (`advance_time false` carried as a world override).
17. **Maps.** In universe `fx4-maps-dst`: `cp -R $F/probe/maps-src .probe/server/universes/fx4-maps-dst/`, then `--cmd "worlds import imp:m maps-src; $A; @map 0; @map 3; @map 4; @map 5; @map 6; @map 7; execute in imp:m run forceload add 0 0; wait:40; execute in imp:m run data get block 0 120 0 Items; execute in imp:m run data get entity @e[type=item_frame,limit=1] Item; execute in imp:m run data get block 8 120 0 Items; execute in imp:m run data get block 12 120 0 Items"`. The server's maps keep their hashes; the five source maps appear under fresh ids above the server's old `last_id`, with their source hashes and `dimension=imp:m` (adjust the `@map` ids to the log); the chest, frame, shulker and bundle each reference the new id. A second import gets different ids. A save without maps has no `maps` phase in the log. Holding an imported map in the server Overworld does not redraw it (mc-client).

18. **Zips** (universe `zdst`; the source is a probe universe's `world` after `gamerule keep_inventory true` and a forceloaded Nether chunk, zipped as `flat.zip` (contents), `nested.zip` (`My World/` inside), `finder.zip` (`ditto -c -k --sequesterRsrc --keepParent`, with an xattr so `__MACOSX/` exists), `cp437.zip` (`Welt Ä/` written with a Cp437 `ZipOutputStream`), `bedrock.mcworld` (a zip without `level.dat`), `old.tar.gz` and a text file named `fake.zip`). `flat.zip all` → `z:flat`, `z:flat_nether` (Nether chunk 0 0 stored, `keep_inventory true`); `nested.zip`, `nested` (no folder, uses `nested.zip`), `finder.zip`, `cp437.zip` → imported with `success.nested`; `bedrock.mcworld` → `no_save`; `old.tar.gz`, `old`, `fake.zip` → `archive`. The zips' checksums are unchanged and `world/mc-worlds/` is gone afterwards. Passed 2026-10-29.
19. **URLs** (a local `http.server` with test routes; `--cmd` needs `'"<url>"'` because `JAVA_TOOL_OPTIONS` strips one level of quotes). A 302 to a relative `/files/flat.zip?token=…` with `all` → imported, labels show `http://127.0.0.1:8765/redirect`, and the token appears nowhere in the log except the probe's echo of the command; a nested zip → imported; 404 → `failed: HTTP 404 Not Found`; `text/html` → `share_page`; a body shorter than Content-Length → `Premature EOF`; `Content-Encoding: gzip` → refused; a tar.gz → `archive`; a redirect loop → `too many redirects`; `"http://"` → `invalid_url`; a closed port → `Connection refused`. A 386 MB zip over loopback imported in under a second; throttled to about 23 MB/s, the busy message read `downloading 115/386 MB` and the log had a line every 5 s. A body that stalls after the headers fails with `Read timed out` after 600 ticks; `stop` during that stall exits at once (whole probe run 9 s; 36 s with the rejected `disconnect()` hook). Passed 2026-10-29.
20. **Review findings** (2026-10-29, universe `zfill` moved onto a 300 MB APFS disk image and symlinked back; each reproduced on the unfixed code, then rerun after the fix):
    - An endless body (no Content-Length): before, `No space left on device` with the volume sampled at 6.7 MB free; after, `disk_space` with at least 64.3 MB free throughout.
    - A save whose `dimensions/minecraft/overworld/region` is a symlink allowed by `[prefix]/Volumes/mcz/` in the universe's `allowed_symlinks.txt`: before, imported with an empty `region/` (`@chunk` `not stored`); after, all four region files and chunk 0 0 stored.
    - `"https://user:tok@files_cdn.example.com/w.zip#frag"`: before, `invalid_url` with the credentials and fragment in the message and log; after, accepted and labelled `https://files_cdn.example.com/w.zip` (it then fails on the unknown host).
    - A redirect to `Location: /files/fl at.zip`: before, an ERROR with an `IllegalArgumentException` stack trace; after, `invalid_url`.
    - Tests 18 and 19 rerun on the same universe pass, with no ERROR lines and no token in the log.

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
- Demand and coverage check (2026-10-29):
  - Multiworld: https://modrinth.com/mod/multiworld · issues https://github.com/IsaiahMC/multiworld/issues/2 · https://github.com/IsaiahMC/multiworld/issues/37 · https://github.com/IsaiahMC/multiworld/issues/41 · https://github.com/IsaiahMC/multiworld/issues/72 · https://github.com/IsaiahMC/multiworld/issues/151
  - World Switcher (NeoForge): https://modrinth.com/mod/world-switcher
  - ForgeEssentials: https://github.com/ForgeEssentials/ForgeEssentials/issues/1303 · https://github.com/ForgeEssentials/ForgeEssentials/issues/1304
  - Hosting guides: https://shockbyte.com/help/knowledgebase/articles/how-to-import-a-world-using-multiverse-core · https://proximamp.com/wiki/transfer-singleplayer-world-to-server
- Dimensions and source location (2026-10-29):
  - Multiverse-Core issues: https://github.com/Multiverse/Multiverse-Core/issues/1172 · https://github.com/Multiverse/Multiverse-Core/issues/766 · https://github.com/Multiverse/Multiverse-Core/issues/3502
  - Multiverse-NetherPortals docs: https://mvplugins.org/netherportals/
  - Importing a singleplayer map onto a server: https://www.rubenerd.au/importing-a-single-player-minecraft-map-onto-a-server
  - Upload guides: https://xgamingserver.com/docs/minecraft/upload-world · https://www.oxygenserv.com/en/how-to-upload-custom-world-minecraft-server · https://help.akliz.net/docs/upload-a-world-to-your-minecraft-server · https://falixnodes.net/help/minecraft/general/managing-your-world
  - MineZip: https://www.curseforge.com/minecraft/bukkit-plugins/minezip
- Test fixtures: Paper 26.3 build 227 and 1.21.11 build 132 (https://fill.papermc.io/v3/projects/paper), Multiverse-Core 5.8.2 (https://modrinth.com/plugin/multiverse-core), Chunky 1.5.3 (https://modrinth.com/plugin/chunky), image `itzg/minecraft-server`.
- Maps: https://hub.spigotmc.org/jira/browse/SPIGOT-765 · https://forums.papermc.io/threads/maps-not-visible-in-items-frames.1556 · https://bugs.mojang.com/browse/MC-270760
- Pterodactyl Wings archive extraction: https://github.com/pterodactyl/wings (`server/filesystem/compress.go`)
