# Per-world "Generate structures"

**Verdict.** Don't implement a per-world toggle for now. Document the vanilla datapack route instead. Almost every reason people give for wanting a structure-less world next to normal ones is about creative/build flat worlds, void/skyblock/lobby/minigame worlds, or a hub main world. Vanilla already covers all of these through the generator, using a datapack `world_preset` with a flat generator and `"structure_overrides": []`, which `/worlds create … from-preset` already accepts with no code change. The only case presets cannot cover is **normal noise terrain with no structures, next to worlds that keep them** (for example a loot-free resource world). In vanilla the noise generator always uses every structure set, filtered only by biome, so the only datapack fix is global. The evidence for that case on Fabric is thin: two niche plugins/mods with 43–74 downloads, one 4★ Fabric world manager, and a 2012 Multiverse issue with 1 👍.

Implementing it correctly needs three injection points, because vanilla gates structures in three separate places. A partial gate produces "ghost" structures: `/locate` finds them and structure mob-spawn overrides apply, but nothing is built. That is the symptom reported on Paper 1.16 in Multiverse-Core #2310. Keep the dead `generate_structures` field and API as they are. It is never serialized, and mc-stargate calls the API. Fix the README so it says mc-worlds worlds **inherit** the server's Generate Structures option. The design below is on file in case a concrete request for structure-less noise terrain arrives.

## Summary

| Item | Finding / decision |
|---|---|
| Does mc-worlds honour the stored flag today? | No. `WorldsCreator.java:102` always passes `true` and nothing calls `getGenerateStructures()`. All worlds follow the server-wide option. |
| Is the field ever written to disk? | No. It always equals the codec default, and DFU omits default values (DFU `Codec.java:303-307`). |
| Vanilla scope of the option | Whole save, fixed at creation (`WorldGenSettings` is `SavedData`, `WorldGenSettings.java:14`). Per-dimension control lives in the **generator**. |
| Use cases covered by presets | Flat/creative without villages, void/skyblock/lobby, minigame arenas, structure-less hub main world |
| Use case not covered | Noise terrain with zero (or fewer) structures next to structured worlds |
| Well-defined per world? | Yes. All three vanilla gates have a `ServerLevel` in hand, and `StructureManager` already carries the options per level. |
| Cost if implemented | 2 `@ModifyExpressionValue` mixins + 1 per-level `WorldOptions` (ctor mixin or `CustomServerLevel` override), tri-state codec, `create` argument |
| Recommendation | Document the datapack route and the inheritance. Leave the field and API untouched. Revisit when there is concrete demand. |

## Use cases & demand

| Use case | Source | Covered by `from-preset` + vanilla datapack? |
|---|---|---|
| **Creative/build superflat with no villages**, next to a survival world | Multiverse-Core #2310 comment by sdocy503: *"I'm trying to make a superflat world with no villages … I only have one world that doesn't need structures while the rest do need structures."* xgamingserver: villages *"spawning too close to your players' builds"*. minecraft.wiki Superflat: in Java *"villages generate relatively frequently, and strongholds can also be found"*. | **Yes.** vanilla `minecraft:flat` has strongholds + villages (`FlatLevelGeneratorSettings.java:209-211`). A datapack `world_preset` with a flat generator and `"structure_overrides": []` has none (`FlatLevelSource.java:44-49`). |
| **Void / skyblock / lobby / minigame arena** | Multiverse-Core #2339: *"a flat, no structure generation hub world"*. WorldResetter (74 downloads): turning structures off *"makes new worlds generate faster"* for minigame servers. Fabric managers ship void/flat generators instead of a toggle: multiverse-dimensions `multiverse:void`/`multiverse:flat`, Melius WorldManager `fantasy:void`. | **Yes.** Vanilla's own "The Void" and "Redstone Ready" flat presets have no structure sets (`FlatLevelGeneratorPresets.java:183`, `:173-176`). The same JSON as a `world_preset` works with `from-preset`. |
| **Structure-less hub as the main world, while mc-worlds worlds keep structures** (the inverse problem) | Multiverse-Core #2310 / #2339 and SPIGOT-5901: the main world's setting leaked to every world (*"when I create a new end, no end cities generate"*). | **Yes**, with no setting at all. Make the main world flat with `level-type=minecraft:flat` and `generator-settings={…,"structure_overrides":[]}` (`DedicatedServerProperties.java:139-141, 273`) and keep `generate-structures=true`. Today `generate-structures=false` silently strips structures from **every** mc-worlds world. |
| **Resource/mining world on normal terrain, without villages or loot** (economy) | Multiverse-Core #789 (2012, 1 👍): *"I have like 100 villages in my multiverse created worlds and I hate it."* Multiverse-Core 5 `--no-structures`. WorldResetter `/wr settings structures`. DREG (43 downloads) restricts structures per dimension. | **No.** The noise generator uses every registered structure set, filtered only by the biome source (`ChunkGenerator.java:117-119`, `ChunkGeneratorStructureState.java:57-72`). Overriding `structure_set` or `has_structure/*` tags in a datapack is global and also hits the main world. #789 also wants *villages only* off, which an all-or-nothing toggle overshoots. |
| **Performance** (faster chunk generation) | WorldResetter page | Partly. Flat/void presets cost nothing. For noise terrain, structure starts are a minor part of chunk generation. |

Demand quality:
- **Multiverse-Core #2310** has 1 👍 and 33 comments, not 33 👍. The thread is a platform **bug** report (SPIGOT-5901: the server ignored per-world settings), not a feature request.
- **On Bukkit the toggle is free platform API** (`WorldCreator.generateStructures(boolean)`), which is why Multiverse-Core exposes it (`CreateCommand.java:92`).
- **On Fabric, most world managers don't offer it.** Melius WorldManager, multiverse-dimensions and Multiworld have no toggle and rely on the choice of generator. The exception is senseiwells/Multiverse (4★), which has `has-structures`. It is built on Arcade, whose mixin gates only `StructureManager`, so `/locate` and the structure starts still follow the server option.
- **The most common "creative + survival on one server" questions are about inventories, not structures** (SpigotMC thread 426404).

## Vanilla alignment

- **Vanilla has "Generate structures" only as a save-wide, creation-time option.** It is the Create World toggle in singleplayer and `generate-structures` in `server.properties` (`DedicatedServerProperties.java:136-138`). It is stored in `WorldGenSettings` saved data (`WorldGenSettings.java:14-19`; renamed from `generate_features` in `LevelDatToSavedDataPreparationFix.java:85`) and is never changed in place.
- **Vanilla's per-dimension structure control is the generator.**
  - Flat: `structure_overrides` (`FlatLevelGeneratorSettings.java:38`). Omitted means all sets, `[]` means none (`FlatLevelSource.java:44-49`).
  - Noise: the biome source, through the biomes listed in each structure's `biomes` field (`ChunkGeneratorStructureState.java:66-72`).

  A per-world flag would scope a save-level option to one dimension, which vanilla never does.
- **There is precedent, so this would not be a new policy.** mc-worlds already scopes the sibling `WorldOptions` field `seed` per world (`WorldGeneratorData.java:24`, `CustomServerLevel.java:141-144`). It **inherits** the other sibling, `bonus_chest`, from the server (`SpawnPreparationHelper.java:170-174`). `generate_structures` currently behaves like `bonus_chest`. The data model merely suggests it behaves like `seed`.
- **The vanilla-first answer is the data route.** `/worlds create <id> from-preset <ns>:<preset>` already takes any datapack `world_preset` (`CreateCommand.java:50-51`), and the full stem is stored inline per world (`WorldGeneratorData.java:19-23`, `WorldsManager.java:84-86`).

## Current state in mc-worlds

- **The field is declared and persisted.** `WorldGeneratorData.java:25` has `Codec.BOOL.optionalFieldOf("generate_structures", true)`, the field is at `:34`, and the constructors are at `:39-53`. It was added in `0f3ed3a` ("support nether and end"), carried through the API refactor `ac5d37b`, and has never had a reader.
- **It is exposed through `WorldsLevelData`.** The getter `getGenerateStructures()` is at `WorldsLevelData.java:62-64` and has no callers in mc-worlds or mc-stargate. The factories `getDefault(…, boolean generateStructures)` are at `:146-157`.
- **The only writer hardcodes `true`.** `WorldsCreator.java:102` calls `WorldsLevelData.getDefault(id, levelStem, seed, true)`.
- **mc-stargate calls the public API with `true`.** See `mc-stargate/src/main/java/dev/wroud/mc/stargate/StargateWorldsManager.java:39-40`: `WorldsLevelData.getDefault(StargateLevelProvider.MY_PROVIDER, …, null, seed, true)`.
- **The key never reaches disk.** DFU's `optionalFieldOf(name, default)` encodes `Optional.empty()` when the value equals the default (DFU 10.0.21 `Codec.java:303-307`), and the value is always `true`. Every existing world therefore has no `generate_structures` key, so the semantics can change later without a data migration.
- **Levels are built with server-wide options.**
  - `CustomServerLevel` calls the plain `ServerLevel` constructor (`CustomServerLevel.java:52-54`).
  - mc-worlds has no mixin touching `WorldGenSettings`/`WorldOptions` (`src/main/resources/worlds.mixins.json`). Its only `getWorldGenSettings()` use is the bonus chest (`SpawnPreparationHelper.java:171`).
  - The existing pattern for per-world state is an override with a null fallback (`clockManager()`, `CustomServerLevel.java:79-83`).
- **Command and docs.**
  - `create` has 8 terminal nodes: `<id>`, `<id> <seed>`, and the `from-preset` and `from-dimension` branches (`CreateCommand.java:36-110`).
  - `settings` literals are camelCase (`loadOnStartup`, `SettingsCommand.java:32`). Keys look like `dev.wroud.mc.worlds.command.settings.loadOnStartup.query` (`en_us.json:13-14`).
  - Datagen only emits dimension-type tags (`DataGenerator.java:11-15`), and the language file is hand-written.
  - The README example `/worlds create flatworld from-preset minecraft:flat` (`README.md:42`) yields villages + strongholds, and the README never mentions structures.

## Vanilla internals (MC 26.3-rc-1)

**There are three gates. Each reads the server-wide value, and each has a level in hand:**

1. **Structure starts.** `ChunkStatusTasks.java:44-57` (`generateStructureStarts`) checks `level.getServer().getWorldGenSettings().options().generateStructures()` at `:48` before calling `createStructures`. References (`:66-73`) are ungated and become no-ops without starts. Loading existing chunks (`:59-64`) is ungated.
2. **Locate and map lookups.** `ChunkGenerator.java:160-169` (`findNearestMapStructure`) returns `null` at `:167` when the server option is false. All of these route through it:
   - `/locate` (`LocateCommand.java:99`)
   - eyes of ender (`EnderEyeItem.java:87-90`: no structure means `CONSUME` with no eye thrown)
   - dolphins (`Dolphin.java:414`)
   - explorer maps (`ExplorationMapFunction.java:95`), including cartographer trades (`VillagerTrades.java:627, 645, 672, 1593`)
   - `ServerLevel.findNearestMapStructure` (`ServerLevel.java:1536-1550`)
3. **Piece placement.** `ChunkGenerator.java:338-383` (`applyBiomeDecoration`) places pieces only if `structureManager.shouldGenerateStructures()` (`:366`). That reads `StructureManager.worldOptions` (`StructureManager.java:33-40, 93-95`), which is propagated to every `WorldGenRegion` (`:42-47`).

**How `ServerLevel` wires it up:**
- `ServerLevel.java:285-300` takes `server.getWorldGenSettings().options()` once in the constructor. It builds `StructureCheck` with the **server seed** (`:287-297`) and `StructureManager(this, options, …)` (`:300`). `serverLevelData` is already assigned at `:246`, so a constructor injection can see the mc-worlds data.
- `structureManager()` is public and non-final (`:340-342`). The field is read nowhere else in `ServerLevel` (only `:300`, `:341`).
- `getWorldGenSettings()` has exactly four callers: `ServerLevel.java:285`, `:1795` (`getSeed`, already overridden by mc-worlds), `ChunkGenerator.java:167` and `ChunkStatusTasks.java:48`.

**Structure-dependent behaviour that follows the starts:**
- **Structure spawn overrides** (monument guardians, outpost pillagers, …): `ChunkGenerator.java:456-478`.
- **Fortress mobs**: `NaturalSpawner.java:315-323`.
- With starts absent, all of these switch off consistently. With starts present but no placement, they fire in empty terrain.

**Not gated:**
- `/place structure` (`PlaceCommand.java:262-302`).
- Stronghold ring precomputation (`ServerLevel.java:274`).
- Dragon fight / end gateways, which are features rather than structures. `EnderDragonFight.java:154-165` only uses the seed to shuffle gateways the first time.
- Features such as dungeons, geodes and fossils. Multiverse-Core #2310: *"spawner generate but not stronghold/mineshaft/village"*.

**Generator-level control:**
- Flat: `FlatLevelGeneratorSettings.java:38` (`structure_overrides`, lenient optional) and `FlatLevelSource.java:44-49`.
- Normal: `ChunkGenerator.java:117-119` → `ChunkGeneratorStructureState.createForNormal` (`ChunkGeneratorStructureState.java:57-64`), which lists all sets.
- `minecraft:flat` = `FlatLevelGeneratorSettings.getDefault`, with strongholds + villages (`WorldPresets.java:150-151`, `FlatLevelGeneratorSettings.java:206-212`).
- Flat-level-generator presets (`minecraft:the_void`, …) are a different registry from world presets. `from-preset` takes `Registries.WORLD_PRESET` (`CreateCommand.java:51`), so `minecraft:the_void` can't be passed directly.
- A `world_preset` must contain `minecraft:overworld` (`WorldPreset.java:19-23, 50-52`). A datapack `dimension/` entry is unsuitable because vanilla loads every `LEVEL_STEM` as its own level (`MinecraftServer.java:423, 462`).

**Per-level seed vs `StructureCheck` (adjacent finding, not verified at runtime):**
- Structure *generation* in mc-worlds worlds uses the per-world seed (`ChunkMap.java:181, 190` → `CustomServerLevel.getSeed()`).
- `StructureCheck` predicts with the server seed (`StructureCheck.java:108, 118-133`).
- For worlds whose seed differs from the server's, the fast check in `/locate` and explorer maps can return `START_NOT_PRESENT` for real structures. False positives are re-verified by loading the chunk (`ChunkGenerator.java:312-321`), so the symptom would be missed or farther results, never ghosts.

## Recommended alternative

### 1. README guidance (replaces the toggle)

- Under "Create from preset", state that worlds **inherit the server's Generate Structures option**: `generate-structures` in `server.properties`, or the Create World toggle in singleplayer, fixed when the save was created. `generate-structures=false` therefore removes structures from every mc-worlds world.
- State that `minecraft:flat` includes villages and strongholds.
- For a structure-less world, add a datapack preset and run `/worlds create build from-preset example:flat_no_structures`. Put the datapack in `<save>/datapacks/` (in dev: `versions/latest/run/world/datapacks/`).

`example_presets/pack.mcmeta` (data pack format 121 = MC 26.3-rc-1, `SharedConstants.java:31`):
```json
{ "pack": { "description": "mc-worlds structure-less presets", "min_format": 121, "max_format": 121 } }
```

`example_presets/data/example/worldgen/world_preset/flat_no_structures.json`:
```json
{
  "dimensions": {
    "minecraft:overworld": {
      "type": "minecraft:overworld",
      "generator": {
        "type": "minecraft:flat",
        "settings": {
          "biome": "minecraft:plains",
          "layers": [
            { "block": "minecraft:bedrock", "height": 1 },
            { "block": "minecraft:dirt", "height": 2 },
            { "block": "minecraft:grass_block", "height": 1 }
          ],
          "lakes": false,
          "features": false,
          "structure_overrides": []
        }
      }
    }
  }
}
```

Variants:
- **Void.** Use `"biome": "minecraft:the_void"`, `"layers": [{ "block": "minecraft:air", "height": 1 }]` and `"features": true`. This mirrors vanilla's The Void preset, `FlatLevelGeneratorPresets.java:183`.
- **Selective.** Use `"structure_overrides": ["minecraft:strongholds"]`.
- **Structure-less hub main world.** Use the same `settings` object as `generator-settings` with `level-type=minecraft:flat`, and leave `generate-structures=true`.

### 2. Code: leave as is

- Keep `generate_structures` in the codec, `getGenerateStructures()`, and the `getDefault(…, boolean)` overloads unchanged.
- Removing them would break mc-stargate (`StargateWorldsManager.java:39-40`) and any third-party `ServerLevelProvider` user, and would gain nothing on disk.
- Because the key is never written, absent can later be reinterpreted as "inherit" at no migration cost.

### 3. Design on file (only if the verdict is reopened for noise terrain)

**Data.**
- Change the field to a tri-state `Optional<Boolean>` with `Codec.BOOL.optionalFieldOf("generate_structures")`. Absent means inherit the server value, which matches today's behaviour exactly for all existing worlds.
- The effective value is `stored.orElse(server.getWorldGenSettings().options().generateStructures())`.
- Keep `getDefault(…, boolean)` mapping to an explicit value, and add an inherit overload. Decide what mc-stargate's `true` should mean (see open questions).

**Per-level options.** Use one of these:
- (a) A `@ModifyExpressionValue` on `WorldGenSettings.options()` in `ServerLevel.<init>` (`ServerLevel.java:286`) returning `original.withStructures(effective)` (`WorldOptions.java:67-69`). Seed is unchanged. Apply it only when `(Object) this instanceof CustomServerLevel`. This is Arcade's approach on 26.3.
- (b) Override `structureManager()` in `CustomServerLevel` with a null fallback, like `clockManager()`, plus an `@Accessor("structureCheck")` on `ServerLevelAccessor`.

**Two gates.** Add `@ModifyExpressionValue` on `Lnet/minecraft/world/level/levelgen/WorldOptions;generateStructures()Z` in:
- `ChunkStatusTasks#generateStructureStarts` (static; capture the `WorldGenContext` arg)
- `ChunkGenerator#findNearestMapStructure` (capture `ServerLevel`)

Both return `level.structureManager().shouldGenerateStructures()`, which keeps a single source of truth. Put them in `mixin/` (not `fixes/`) and register them in `worlds.mixins.json`.

**Commands.**
- Add a trailing `generate-structures <true|false>` (`BoolArgumentType`) on all 8 `create` terminals through one helper. The literal sits beside `<seed>`, so there is no ambiguity, and the name matches `server.properties`.
- Creation-time only. Add an optional read-only `/worlds settings generateStructures`, with no setter, because changing it mid-life cuts structures at chunk borders.

**i18n.** `dev.wroud.mc.worlds.command.settings.generateStructures.query` = `"World %s generate structures is:"`. No other new keys.

**Datagen / API.** No datagen. The API change is the tri-state getter, plus the `getDefault` overloads above.

## Edge cases & risks

- **Ghost structures from partial gating.**
  - If only `StructureManager` is per-world (Arcade/senseiwells), starts are still created (`ChunkStatusTasks.java:48`) and `/locate` still answers (`ChunkGenerator.java:167`), but nothing is placed.
  - Spawn overrides (`ChunkGenerator.java:456-478`) and fortress mob checks (`NaturalSpawner.java:315-323`) then fire in empty terrain.
  - Multiverse-Core #2310 reports the same symptom on Paper (*"i can actually use the locate command and it will find structures. However they still wont generate"*). Any implementation must gate all three.
- **Inheritance surprise (exists today).** `generate-structures=false` (for example for a lobby) also strips structures from all mc-worlds worlds, including End cities in a custom End. This is the Multiverse #2339 complaint. Fix it with documentation.
- **Override vs AND.** If a per-world `true` overrides a server `false`, an admin who relied on the global off switch gets structures in newly created worlds. The tri-state default avoids that for existing worlds only.
- **Progression in structure-less worlds.**
  - No strongholds: eyes of ender do nothing (`EnderEyeItem.java:87-90`).
  - No fortress or bastion: no blaze rods.
  - No End cities: no elytra.
  - This is vanilla behaviour with the option off, but it would be scoped to one world while portals may link to structured ones.
- **Changing the value after creation.** Structures get cut at old/new chunk borders. Keep it creation-only, as vanilla does.
- **Datapack route caveats.**
  - The datapack must be enabled when you run `create`. After that the stem is stored inline (`WorldGeneratorData.java:19-23`) and no longer needs the preset id. Biome, block and structure-set ids must still resolve, otherwise the world is skipped (`WorldsManager.java:84-93`).
  - `from-preset` without `<dimension>` takes the first key of the preset map (`WorldsCreator.java:72-76`), so keep custom presets to the overworld only.
- **`StructureCheck` seed (adjacent).** Implementing option (a) with `withStructures` leaves this as it is. Switching to a full per-level `WorldOptions` with the world seed would also change it. Decide separately.

## Open questions for the maintainer

1. **Do you want to support normal noise terrain with no structures, next to structured worlds** (loot-free resource or economy worlds)? It is the only case presets cannot cover. If not, the verdict stands.
2. **If you implement it, may a per-world `true` override a server-wide `false`?** That would let a structure-less hub keep structured mc-worlds worlds (the Multiverse #2339 ask). Or should per-world values only be able to turn structures *off*?
3. **What should happen to the dead API?** Should `getDefault(…, boolean)` / `getGenerateStructures()` be `@Deprecated` now, or stay reserved for the tri-state? And should mc-stargate's `true` mean "inherit"?
4. **Should mc-worlds ship built-in presets** (`mc-worlds:flat_no_structures`, `mc-worlds:void`) as bundled data, so admins needn't write a datapack? That is a vanilla mechanism with zero mixins. A related option is to let `from-preset` also accept flat-level-generator presets such as `minecraft:the_void`.
5. **Should the `StructureCheck` server-seed mismatch be its own ticket**, verified at runtime with `/locate` in a custom-seeded world?

## Manual test plan

Run `./gradlew runServer` (world at `versions/latest/run/world`, `generate-structures=true`). The `mc-server-probe` skill can script the console steps.

1. **Baseline inheritance.**
   - Run `/worlds create flat_default from-preset minecraft:flat`.
   - In that world, `/locate structure minecraft:village_plains` and `/locate structure minecraft:stronghold` both succeed.
2. **Datapack route.**
   - Drop `example_presets/` into `world/datapacks/` and run `/reload`. Check `/datapack list` shows it enabled.
   - Run `/worlds create flat_bare from-preset example:flat_no_structures`, then `/worlds tp flat_bare`.
   - `/locate structure #minecraft:village` fails with "Could not find". An eye of ender is consumed with no eye thrown. Flying ~1000 blocks shows no villages.
3. **Persistence.**
   - Restart the server, `/worlds tp flat_bare`, and repeat step 2. Expect the same result.
   - Remove the datapack, restart, and check the world still loads from the stored stem with no "Skipping world" log line.
4. **Void variant.** Create from a void preset and confirm the void platform and no structures.
5. **Server-wide false (documents the inheritance).**
   - On a copy of the run directory with a **fresh** save, set `generate-structures=false`.
   - Run `/worlds create normal_copy` and check that `/locate structure minecraft:village_plains` fails there too.
6. **Only if implemented.** Test all of these:
   - `create x generate-structures false`: `/locate` fails, no pieces, no guardians in deep ocean, eye of ender inert.
   - `create y` (inherit) on a `true` server keeps structures.
   - Restart preserves both.
   - `/place structure minecraft:village_plains` still works in `x`.

## Sources

- Multiverse-Core #2310 "Structures generate only if the main world has structures" (1 👍, 33 comments): https://github.com/Multiverse/Multiverse-Core/issues/2310
- Multiverse-Core #2339 "Main world overrides any structure-gen settings for other worlds": https://github.com/Multiverse/Multiverse-Core/issues/2339
- Multiverse-Core #789 "Don't spawn villages (setting)" (2012, 1 👍): https://github.com/Multiverse/Multiverse-Core/issues/789
- SPIGOT-5901 (per-world structure setting ignored; resolved 2020-07-16): https://hub.spigotmc.org/jira/browse/SPIGOT-5901
- Multiverse-Core 5 `CreateCommand` (`--no-structures` → `generateStructures`): https://github.com/Multiverse/Multiverse-Core/blob/main/src/main/java/org/mvplugins/multiverse/core/commands/CreateCommand.java
- Bukkit `WorldCreator.generateStructures`: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/WorldCreator.html
- senseiwells/Multiverse README (`has-structures`): https://github.com/senseiwells/Multiverse
- Arcade `ServerLevelMixin` (per-level `WorldOptions`, branch 26.3): https://github.com/CasualChampionships/arcade/blob/26.3/arcade-dimensions/src/main/java/net/casual/arcade/dimensions/mixins/level/ServerLevelMixin.java
- Melius WorldManager: https://modrinth.com/mod/melius-worldmanager
- Multiverse Dimensions: https://modrinth.com/mod/multiverse-dimensions
- Multiworld: https://modrinth.com/mod/multiworld
- DimEnsion Restriction Generation (DREG): https://modrinth.com/mod/derg
- WorldResetter: https://modrinth.com/mod/worldresetter-plugin
- minecraft.wiki Superflat: https://minecraft.wiki/w/Superflat
- xgamingserver, "Preventing Villages from Generating": https://xgamingserver.com/blog/preventing-villages-from-generating-on-your-minecraft-server/
- SpigotMC "Creative world and Survival world on same server": https://www.spigotmc.org/threads/creative-world-and-survival-world-on-same-server.426404/
