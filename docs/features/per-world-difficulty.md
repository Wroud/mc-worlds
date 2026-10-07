# Per-world difficulty

**Verdict.** Implement it, but it is small and low priority, and it does not depend on per-world gamerules. Difficulty is a dial with dozens of graded effects: mob damage scaling, how far starvation can take you, hunger depletion, regional difficulty (mob gear and enchantments), door breaking, zombie reinforcements, villager infection, poison and wither durations, and raid and phantom group sizes. Gamerules are on/off switches. At best they approximate the *spawning* half of Peaceful, and they can never make a world harder than the server. That leaves one use case that only difficulty can serve: a world that is **harder or easier than the rest of the server**, such as a Hard adventure or End world next to a Normal or Peaceful main world, or a Peaceful survival world with no hunger. This is real usage. Bukkit/Paper have per-world difficulty natively, every comparable world manager (Multiverse-Core, Multiworld, WorldEngine, Fantasy) exposes it, and Spigot and Multiworld bug reports come from people using it. The peaceful-hub and creative-build cases are mostly covered by gamerules, once those are per-world. On the vanilla side, every read site already goes through the level's own `LevelData`, and the client is already sent the difficulty of the level it is in. So the feature needs:
- one changed getter,
- one optional persisted field,
- two write-path wraps that match Paper's own `DifficultyCommand` patch line for line,
- `/worlds settings difficulty [inherit]`.

It needs no read-path mixins and no mechanics vanilla doesn't have. Nobody has asked for it in the mc-worlds tracker, so it should not displace other work. Ideally it ships with the same inherit/override UX that per-world gamerules end up with.

## Summary

| Item | Decision |
|---|---|
| Default | Every mc-worlds world **inherits** the server difficulty (today's behaviour, and what vanilla Nether/End do via `DerivedLevelData`) |
| Override | Optional `difficulty` in the world's `WorldSettingsData`; absent means inherit |
| Read path | `WorldsLevelData.getDifficulty()` returns the override, else the server value. All vanilla reads and client syncs follow with no mixins |
| Set | Vanilla `/difficulty <d>` and the World Options screen, used *inside an mc-worlds world*, set that world's override. In vanilla dimensions or from the console they stay global (same split as `/weather` and `/time` today) |
| Clear / inspect | `/worlds settings difficulty` (shows value and whether it is inherited), `/worlds settings difficulty inherit` |
| Global `/difficulty` | Unchanged. Inheriting worlds follow immediately and overridden worlds keep their value |
| Lock | Stays global (`isDifficultyLocked` keeps delegating) |
| Hardcore | An override is forced to HARD, as vanilla does. A per-world *hardcore* mode is out of scope (it is a login-time client flag) |
| New mixins | `DifficultyCommandMixin` (2 injection points) and `ServerGamePacketListenerImplMixin` (1) |
| Datagen / API | No datagen (lang is hand-written). Optional API: `CustomServerLevel#setDifficulty(@Nullable Difficulty)` |

## Use cases & demand

There are no requests in the mc-worlds tracker: issues #1–#8 cover deletion, LuckPerms, tp, `/setworldspawn`, weather and the 26.3 port. Every comparable tool supports per-world difficulty, except melius World Manager and senseiwells' Multiverse, which offer only gamerule inheritance. I could not search Reddit: the fetcher is blocked there and the Reddit connector was down.

Throughout this doc, "Gamerules" means "once gamerules are per-world". Today they are global even in mc-worlds worlds: `ServerLevel.getGameRules()` returns `server.getGameRules()` (`ServerLevel.java:1941-1942`).

| Use case | Evidence | Covered by gamerules? |
|---|---|---|
| **Harder world beside a normal one**, e.g. Hard End or adventure world while the server is Peaceful or Easy | SPIGOT-7469: the reporter wants the End on Hard while the server is Peaceful. Cybrancee multi-world guide: "a survival world with hard difficulty and PvP on". WorldEngine and Multiworld both list per-world difficulty | **No.** Damage ×1.5, lethal starvation, regional difficulty, door breaking, reinforcements, 100% villager infection, longer poison and wither, and larger raids have no gamerule (see table below). Gamerules can only turn things *off* |
| **Peaceful survival / "cozy" world** next to a normal one (new players, builders who still play survival) | Multiworld #174: main worlds are Peaceful and the user keeps a new world Peaceful with `/mw difficulty PEACEFUL`. SPIGOT-8080: a Peaceful primary world next to normal secondary worlds. Multiverse-Core has a separate non-vanilla `hunger` world flag. In vanilla, only Peaceful stops hunger (`FoodData.java:39`) | **Partly.** `spawn_monsters`, `spawn_patrols`, `spawn_phantoms`, `raids` and `spawner_blocks_work` cover spawning. Nothing covers no hunger depletion, Peaceful regen, zero mob damage, removing existing hostiles, or blocking `/summon` and eggs |
| **Peaceful hub / lobby / spawn world** | Multiverse guide: "hub you spawn into". Usually Adventure mode and small | **Mostly.** The spawning gamerules plus `pvp=false` and `mob_griefing=false` cover it. Hunger still drains slowly, which hubs rarely care about |
| **Creative build / plot world** | Multiverse guide: `/mv modify build set difficulty peaceful` next to `gamemode creative`. multiverse-dimensions targets "building in creative" and offers only gamerules | **Yes.** Creative players are immune to hunger and mob damage, and mobs ignore them (`TargetingConditions.java:73`, `NeutralMob.java:92`). `spawn_monsters=false` and `mob_griefing=false` cover the rest |
| **Resource / mining world** | Multiverse guide: "resource world that resets monthly". mcMMO #5304: a server's resource worlds turn hostile spawning off through spawn config, not difficulty | **Yes.** It is usually the same difficulty as the main world and needs only spawn rules |
| **Minigame arenas / runtime worlds** (mod-driven) | Fantasy (NucleoidMC): `RuntimeLevelConfig.setDifficulty` plus `setMirrorOverworldDifficulty`. Its README says "World-wide values such as difficulty and game rules can be configured per-level" | **Partly.** PvE arenas need the dial. Such mods would call an API rather than commands |
| **"Hardcore world" on a normal server** | No source found | **No, and difficulty doesn't serve it either.** Hardcore is a `WorldData` flag sent once in the login packet (`PlayerList.java:171`). The client keeps it across dimension changes (`ClientPacketListener.java:525,1242`). Per-world HARD is the only vanilla-aligned part |
| Counter-signal: players expect *consistency* | Nomi-CEu #297: a player reports difficulty "changing" between the overworld and a void dimension as a bug | This supports **inherit by default**, with overrides explicit and visible |

## What difficulty affects

These are 26.3-rc-1 common sources. Paths are relative to `net/minecraft/`.

| Effect | Behaviour | Source | Gamerule equivalent |
|---|---|---|---|
| Damage to players from scaled sources (mobs, explosions) | Peaceful 0. Easy `min(d/2+1, d)`. Hard ×1.5 | `world/entity/player/Player.java:692-703` | **None** (`pvp` is player vs player only) |
| Hunger depletion | None in Peaceful | `world/food/FoodData.java:34-41` | **None** (no hunger gamerule exists in `GameRules.java:24-88`) |
| Starvation | Easy stops at 10 HP, Normal at 1 HP, Hard kills | `world/food/FoodData.java:60-66` | **None** |
| Passive regen | Peaceful heals and refills saturation | `server/level/ServerPlayer.java:748` | `natural_health_regeneration` can only turn it off |
| Hostile spawning (natural, eggs, `/summon`, spawners, trial spawners) | Blocked in Peaceful | `world/entity/EntityType.java:300-301`, `world/entity/SpawnPlacements.java:85`, `server/commands/SummonCommand.java:74`, `world/level/BaseSpawner.java:127`, `.../trialspawner/TrialSpawner.java:157` | Partly: `spawn_monsters` (`ServerLevel.java:1888-1889`) and `spawner_blocks_work`. `/summon` and eggs are not covered |
| Existing hostiles | Discarded in Peaceful | `world/entity/Mob.java:688-690` | **None** |
| Mobs targeting players | Off in Peaceful | `world/entity/ai/targeting/TargetingConditions.java:73`, `world/entity/LivingEntity.java:949` | **None** |
| Regional ("local") difficulty: mob armour, enchantments, loot pickup, special multiplier | Scales with base difficulty, inhabited time, moon and day count | `world/DifficultyInstance.java:43-61`, `server/level/ServerLevel.java:690-699`, `world/entity/Mob.java:961,970,1075`, `.../skeleton/AbstractSkeleton.java:123`, `.../zombie/Zombie.java:428` | **None** |
| Door breaking | Zombies on Hard, vindicators on Normal and Hard | `.../zombie/Zombie.java:95`, `.../illager/Vindicator.java:52`, `world/entity/ai/goal/BreakDoorGoal.java:35-37` | `mob_griefing` can only turn it off |
| Zombie reinforcements | Hard only | `.../zombie/Zombie.java:263-266` | **None** |
| Villager → zombie villager on kill | Easy 0%, Normal 50%, Hard 100% | `.../zombie/Zombie.java:394-395` | **None** |
| Poison and wither durations | Cave spider, bee, wither skull: none on Easy, longer on Hard | `.../spider/CaveSpider.java:34-36`, `world/entity/animal/bee/Bee.java:233-235`, `.../WitherSkull.java:80-82` | **None** |
| Ranged attack rate and arrow damage | Faster and stronger with difficulty | `world/entity/monster/RangedAttackMob.java:10`, `.../arrow/AbstractArrow.java:711`, `.../breeze/Shoot.java:92` | **None** |
| Raids and Bad Omen | Raids stop in Peaceful. Wave count depends on difficulty. Bad Omen does nothing in Peaceful | `world/entity/raid/Raid.java:274-276,747-749`, `world/effect/BadOmenMobEffect.java:21` | `raids` can only turn it off |
| Phantoms and patrols | Group size scales with difficulty | `world/level/levelgen/PhantomSpawner.java:37-48`, `world/level/levelgen/PatrolSpawner.java:40` | `spawn_phantoms` / `spawn_patrols` are on/off only |
| Lightning and fire | Lightning lights fire only on Normal and Hard. Skeleton-trap chance and fire spread odds scale | `world/entity/LightningBolt.java:91-92`, `server/level/ServerLevel.java:558-560`, `world/level/block/FireBlock.java:184` | `fire_spread_radius_around_player` is coarse |
| Special summons and spawns | No Wither, Warden, Creaking, endermite or portal zombified piglin in Peaceful | `world/level/block/WitherSkullBlock.java:54,82`, `.../SculkShriekerBlockEntity.java:129`, `.../CreakingHeartBlockEntity.java:111`, `.../ThrownEnderpearl.java:101`, `world/level/block/NetherPortalBlock.java:63-66` | Mostly (`spawn_wardens`, `spawn_monsters`). The Wither is not covered |

## Vanilla alignment

- **Inherit is what vanilla already does.** The Nether and End get a `DerivedLevelData` (`server/MinecraftServer.java:467`) whose `getDifficulty()`/`isDifficultyLocked()` delegate to `WorldData` (`world/level/storage/DerivedLevelData.java:74-81`). `WorldsLevelData` does the same today. An override is the single place where an mc-worlds world stops delegating.
- **The vanilla read hook is per level.** `LevelAccessor.getDifficulty()` defaults to `getLevelData().getDifficulty()` (`world/level/LevelAccessor.java:48-49`), and `Level.getLevelData()` returns the level's own data (`world/level/Level.java:907-908`). Changing one getter changes every gameplay read.
- **mc-worlds precedent.** `WeatherCommandMixin` and `TimeCommandMixin` make vanilla commands act on `source.getLevel()`. The per-world spawn is already persisted in `WorldSettingsData` because vanilla has no per-dimension slot for it, and difficulty is in the same position: it lives only in `level.dat` (`PrimaryLevelData.java:269-286`) and has no `SavedData` to reuse.
- **Paper patches exactly the lines this design wraps.** `DifficultyCommand.java.patch` changes line 34 to `source.getLevel().getDifficulty()` and line 38 to `server.setDifficulty(source.getLevel(), …)`. Paper's per-level setter forces HARD on hardcore, refreshes that level's spawn flags and sends the packet to `level.players()` only. Paper also sets the lock on *all* levels, disables the client World Options difficulty packet ("don't allow clients to change this"), and turns off `DedicatedServer.forceDifficulty()`. SPIGOT-6330 was the bug from fixing only half of this: the query read the world while the "already set" check read the server.
- **Fantasy uses the same technique.** `RuntimeLevelData extends DerivedLevelData` and overrides only `getDifficulty()`, mirroring the overworld when `mirrorOverworldDifficulty` is set.
- **What to avoid.** A per-world `hunger` flag (Multiverse) is a mechanic vanilla doesn't have. A per-world lock and per-world hardcore need client-side or UI policy vanilla doesn't have.

## Current state in mc-worlds

Paths are relative to `src/main/java/dev/wroud/mc/worlds/`.

- `manager/level/data/WorldsLevelData.java:94-97`: `getDifficulty()` returns `worldData.getDifficulty()`. `:99-102` `isDifficultyLocked()` and `:89-92` `isHardcore()` also delegate. `worldData` is the server's (`manager/WorldsManager.java:104`).
- `manager/level/data/WorldsLevelData.java:20-24`: the `CODEC` has `core`, `settings` (optional, default `new WorldSettingsData()`) and `game_time`.
- `manager/level/data/WorldSettingsData.java:9-13`: the `CODEC` has `respawn` and `initialized`, both optional. This is the natural home for an optional `difficulty`.
- `manager/WorldsData.java:32-35`: `isDirty()` is always true, so a setter needs no `setDirty`.
- `server/level/CustomServerLevel.java:103,143` already casts `this.levelData` to `WorldsLevelData`. `CustomServerLevel.java:66` puts the level into the server's `levels` map, so `MinecraftServer.getAllLevels()` and `sendDifficultyUpdate` see it.
- `command/SettingsCommand.java:29-50` holds the `loadOnStartup` and `spawn` subcommands (`LEVEL_ADMINS`, `:31`). Each subcommand resolves the world from `source.getLevel()` (`:54-61`).
- `mixin/WeatherCommandMixin.java:15-21` and `mixin/TimeCommandMixin.java:14-20` are the precedent for routing vanilla commands per world.
- `mixin/ExecuteCommandMixin.java:20-29` lets `/execute in <mc-worlds id>` target custom worlds, which is how the console reaches a world.
- Nothing else in `src/` mentions difficulty. `src/datagen/java/dev/wroud/mc/worlds/DataGenerator.java:14` registers only `DimensionTypeTagsProvider`. `en_us.json` is hand-written.

## Vanilla internals (MC 26.3-rc-1)

Paths are relative to `net/minecraft/`. Lines marked (client) are from the clientOnly sources jar.

**Reads.** All reads are per level. There are ~60 call sites of `level.getDifficulty()`, `getLevelData().getDifficulty()` and `getCurrentDifficultyAt()`. `ServerLevel.getCurrentDifficultyAt` (`server/level/ServerLevel.java:690-699`) uses `this.getDifficulty()` and the level's own clock (`world/level/Level.java:880-881`). Only two places read the server value directly:
- `server/commands/DifficultyCommand.java:34`, the "already set" check, which needs fixing.
- The JSON-RPC management API, `server/jsonrpc/internalapi/MinecraftServerSettingsServiceImpl.java:38-46`. It is global by design and stays that way.

**Writes**
- `server/MinecraftServer.java:1278-1284` `setDifficulty(d, ignoreLock)`: checks the lock, writes `worldData` (HARD if hardcore, `:1280`), calls `updateMobSpawningFlags()` and sends every player an update.
- `server/MinecraftServer.java:1290-1293` `updateMobSpawningFlags()` uses `level.isSpawningMonsters()`, which in 26.3 depends on gamerules only (`server/level/ServerLevel.java:1888-1889`). A per-world difficulty change therefore needs no spawn-flag refresh.
- `server/MinecraftServer.java:1296-1299` `setDifficultyLocked` is global. `:1301-1304` `sendDifficultyUpdate` sends **`player.level().getLevelData()`**, so each player gets their own level's value.
- `server/commands/DifficultyCommand.java:25-29` is the query and reads `source.getLevel().getDifficulty()`, so it is already per world. `:32-41` is the setter: it compares against `server.getWorldData()` (`:34`), calls `server.setDifficulty(d, true)` (`:38`, which ignores the lock), and sends vanilla `commands.difficulty.success/failure`. It requires `LEVEL_GAMEMASTERS` (`:25`).
- `server/network/ServerGamePacketListenerImpl.java:2140-2149` `handleChangeDifficulty` is the World Options screen. It requires gamemaster or singleplayer owner and calls `server.setDifficulty(d, false)` (`:2147`, which respects the lock). `:2166-2171` `handleLockDifficulty` calls `setDifficultyLocked` (`:2169`).
- Startup: `MinecraftServer.java:402` calls `forceDifficulty()`, which is a no-op in `:417` but on dedicated servers re-applies `server.properties` every boot (`server/dedicated/DedicatedServer.java:344-347`; the default is `easy`, `DedicatedServerProperties.java:65-67`). JSON-RPC set writes `server.properties` (`DedicatedServer.java:339-342`).
- Debug world: locked Peaceful (`MinecraftServer.java:535-537`).

**Client sync.** The server sends the destination level's value every time a player arrives:
- Login: `server/players/PlayerList.java:158,184`. Hardcore goes in the login packet, `:171`.
- Respawn: `PlayerList.java:417,421`.
- Dimension change: `server/level/ServerPlayer.java:1144-1145`, sent right after the respawn packet.

**Client.**
- `ClientLevel.ClientLevelData` holds `difficulty` and `difficultyLocked` (`client/multiplayer/ClientLevel.java:1211-1258`) (client).
- The handler updates them and refreshes any `HasDifficultyReaction` screen (`client/multiplayer/ClientPacketListener.java:1800-1806`) (client).
- A new `ClientLevelData` on dimension change copies the old difficulty and hardcore flag (`:1242`). The login packet starts at `NORMAL` and uses the packet's hardcore flag (`:525`) (client).
- World Options, reachable from the pause screen in multiplayer too (`client/gui/screens/PauseScreen.java:160-162`), shows the client level's difficulty. It disables the controls when the world is locked or hardcore, or the player is not a gamemaster (`client/gui/screens/WorldOptionsScreen.java:681-707,724-754`), and sends the change and lock packets (`:405-415`) (client).
- F3 "Local Difficulty" reads the integrated server level (`client/gui/components/debug/DebugEntryLocalDifficulty.java:23-31`) (client).
- **Nothing on the client assumes difficulty is global.** It is per connection and is overwritten on every level change.

## Proposed design

### Storage (`WorldSettingsData`)
```java
public @Nullable Difficulty difficulty;
// CODEC: add
Difficulty.CODEC.optionalFieldOf("difficulty").forGetter(wsd -> Optional.ofNullable(wsd.difficulty))
```
Absent means inherit. Old saves decode to inherit, so no DataFixer is needed. An older mc-worlds drops the field on save.

### Read (`WorldsLevelData.java:94-97`)
```java
@Override
public Difficulty getDifficulty() {
  Difficulty override = this.settings.difficulty;
  return override == null || this.worldData.isHardcore() ? this.worldData.getDifficulty() : override;
}
public @Nullable Difficulty getDifficultyOverride() { return this.settings.difficulty; }
public void setDifficultyOverride(@Nullable Difficulty difficulty) { this.settings.difficulty = difficulty; }
```
`isDifficultyLocked()` and `isHardcore()` stay delegating.

### Per-world setter (`CustomServerLevel`, which also serves as the public API)
```java
public void setDifficulty(@Nullable Difficulty difficulty) {
  WorldsLevelData data = (WorldsLevelData) this.levelData;
  data.setDifficultyOverride(difficulty != null && data.isHardcore() ? Difficulty.HARD : difficulty);
  var packet = new ClientboundChangeDifficultyPacket(data.getDifficulty(), data.isDifficultyLocked());
  this.players().forEach(player -> player.connection.send(packet));
}
```
This mirrors `MinecraftServer.setDifficulty`, scoped to one level (as Paper does). It has no `updateMobSpawningFlags`, because spawn flags are gamerule-only in 26.3.

### Mixins (register both in `worlds.mixins.json`)
`DifficultyCommandMixin` follows the same Paper lines (`DifficultyCommand.java:34,38`):
```java
@ModifyExpressionValue(method = "setDifficulty", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/world/level/storage/WorldData;getDifficulty()Lnet/minecraft/world/Difficulty;"))
private static Difficulty mcworlds$current(Difficulty original, CommandSourceStack source) {
  return source.getLevel() instanceof CustomServerLevel level ? level.getDifficultyOverride() : original;
}

@WrapOperation(method = "setDifficulty", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/server/MinecraftServer;setDifficulty(Lnet/minecraft/world/Difficulty;Z)V"))
private static void mcworlds$set(MinecraftServer server, Difficulty difficulty, boolean ignoreLock,
    Operation<Void> original, CommandSourceStack source) {
  if (source.getLevel() instanceof CustomServerLevel level) level.setDifficulty(difficulty);
  else original.call(server, difficulty, ignoreLock);
}
```
The comparison is against the *override*, so `/difficulty normal` in a world that inherits Normal *pins* it, and running it again reports vanilla's "already set". See Q3.

`ServerGamePacketListenerImplMixin` wraps `handleChangeDifficulty`, `ServerGamePacketListenerImpl.java:2147` (`@Shadow public ServerPlayer player;`, line 255):
```java
@WrapOperation(method = "handleChangeDifficulty", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/server/MinecraftServer;setDifficulty(Lnet/minecraft/world/Difficulty;Z)V"))
private void mcworlds$set(MinecraftServer server, Difficulty difficulty, boolean ignoreLock, Operation<Void> original) {
  if (this.player.level() instanceof CustomServerLevel level) {
    if (ignoreLock || !level.getLevelData().isDifficultyLocked()) level.setDifficulty(difficulty);
  } else original.call(server, difficulty, ignoreLock);
}
```
Without this mixin, an op who changes difficulty from World Options inside an overridden world changes the *server*, and the button snaps back: the reply carries the level's value and `onDifficultyChanged` refreshes with force. Paper disables the packet instead (Q1).

### Commands
- Vanilla `/difficulty` needs no change for queries. In an mc-worlds world, set goes to that world through the mixin. From the console or in vanilla dimensions it stays global. `/execute in <id> run difficulty <d>` targets a world from the console. `/execute in minecraft:overworld run difficulty <d>` sets the server value from inside a custom world, which is the same idiom Paper users already use.
- `SettingsCommand.build()`:
  ```
  /worlds settings difficulty            -> query (effective value, "(inherited from the server)" if no override)
  /worlds settings difficulty inherit    -> level.setDifficulty(null)
  ```
  Resolve the world with `source.getLevel() instanceof CustomServerLevel`. Otherwise throw `UNKNOWN_WORLD_EXCEPTION`, as the other settings do. Explicit `peaceful|easy|normal|hard` literals here are optional (Q4).

### What global `/difficulty` does
It is unchanged (`MinecraftServer.java:1278-1284`). Inheriting worlds see the new value on their next read. `sendDifficultyUpdate` sends every player their own level's value, so players in inheriting worlds update live, and players in overridden worlds get an unchanged value, which is harmless.

### i18n (`src/main/resources/assets/mc-worlds/lang/en_us.json`)
```json
"dev.wroud.mc.worlds.command.settings.difficulty.query": "World %s difficulty is:",
"dev.wroud.mc.worlds.command.settings.difficulty.query.inherited": "(inherited from the server)",
"dev.wroud.mc.worlds.command.settings.difficulty.inherit": "World %s now follows the server difficulty:"
```
These follow the existing "…:" plus GOLD value style (`SettingsCommand.java:65-69`). Vanilla `/difficulty` keeps its own `commands.difficulty.*` keys. If Q4 is accepted, add `…difficulty.success`: "World %s difficulty set to:".

### Datagen, docs, API
- Datagen: none.
- Docs: README "Settings Command" section, plus a CHANGELOG `Changed` entry for the `/difficulty`-in-custom-world behaviour change.
- API: optionally list `CustomServerLevel#setDifficulty(@Nullable Difficulty)` in `API.md` for level providers (Fantasy precedent).

## Edge cases & risks

- **Behaviour change.** Today, `/difficulty hard` typed while standing in an mc-worlds world changes the whole server. After this change it changes only that world. It needs a changelog line. The workarounds are the console or `execute in minecraft:overworld`.
- **Mixed model.** The vanilla Nether and End keep sharing the server value (`DerivedLevelData`). mc-worlds Nether and End worlds can differ. This matches how weather already works.
- **Dedicated restarts.** Inheriting worlds follow `server.properties` on every boot (`DedicatedServer.java:344-347`). Overrides persist in `worlds_data`. Vanilla's global `/difficulty` silently reverts on restart while per-world overrides do not, so document the asymmetry. Paper removed `forceDifficulty`. mc-worlds should not.
- **Hardcore.** Overrides are forced to HARD by both the setter and the getter, and the client disables the UI (`WorldOptionsScreen.java:736-739`). A per-world hardcore mode is not feasible without client mixins, because of the login-time flag.
- **Lock.** It is global. The vanilla command ignores it (`:38` passes `true`). The UI respects it, and the mixin keeps that.
- **Peaceful switch.** Existing hostiles are discarded on their next despawn check (`Mob.java:688-690`) and raids stop (`Raid.java:274-276`), per world, automatically.
- **Lazy and unloaded worlds.** The override lives in `WorldsLevelData`, so it survives unload (1200 ticks, `CustomServerLevel.java:36`) and is applied on reload. Deleting a world removes it.
- **Third-party mods** that read `server.getWorldData().getDifficulty()` or `server.overworld().getDifficulty()` instead of `level.getDifficulty()` will see the server value in overridden worlds. Vanilla itself does this only at `DifficultyCommand.java:34`, which is wrapped, and in JSON-RPC, which is intentionally global.
- **Mixin stability.** The targets (`DifficultyCommand.setDifficulty`, `handleChangeDifficulty`) have barely changed in years. The read path has no mixins.
- **Performance.** `getDifficulty()` is hot (mob AI, spawning). The added null check is negligible.

## Open questions for the maintainer

1. **Should vanilla `/difficulty` and World Options in an mc-worlds world set that world** (recommended; this is the `WeatherCommandMixin` and Paper model)? The alternatives are `/worlds settings difficulty <d>` only, which would leave `/difficulty` reporting "set to Hard" while the world stays Peaceful (SPIGOT-6330), or disabling the World Options packet as Paper does.
2. **Ship now or bundle with per-world gamerules?** Technically the two are independent. Bundling them gives one "inherit unless overridden" story (`/worlds settings … inherit`) and one changelog entry.
3. **Pinning.** Should `/difficulty normal` in a world that inherits Normal pin it (recommended: compare against the override) or fail with vanilla's "already set" (compare against the effective value)?
4. Should `/worlds settings difficulty` also accept explicit values, for discoverability, or only `inherit`? And should `CustomServerLevel#setDifficulty` be documented as public API?

## Manual test plan

There is no test suite. Use `./gradlew runServer` (dev `server.properties` defaults to `difficulty=easy`) plus `./gradlew runClient` connected to it for the UI steps. The server console can be driven through a FIFO for the headless parts.

1. `/worlds create dt`, then `/worlds tp dt`. `/difficulty` shows Easy. `/worlds settings difficulty` shows Easy (inherited).
2. `/difficulty hard` in `dt` succeeds and `/difficulty` shows Hard. `/execute in minecraft:overworld run difficulty` still shows Easy. Running `/difficulty hard` again gives "already set".
3. In `dt`, a summoned zombie hits for ×1.5. `/difficulty peaceful` makes the zombie despawn, `/summon zombie` fails with the Peaceful error, and the food bar doesn't drop while sprinting. In the overworld, `/summon zombie` works.
4. Run `difficulty normal` in the server console. The overworld becomes Normal and `dt` stays Peaceful. A second world, `dt2`, with no override, becomes Normal for a player already standing in it (World Options updates without relogging).
5. `/worlds settings difficulty inherit` in `dt` makes it Normal immediately on the client.
6. Set `dt` to Hard, `stop`, and restart. `dt` is still Hard. The overworld and `dt2` are back to Easy (`server.properties`).
7. Leave `dt` for more than 60 s so it unloads, then `/worlds tp dt`. It is still Hard.
8. Sync: log out in `dt` and back in, and World Options shows Hard. Die in `dt` with the spawn in the overworld, and after respawn World Options shows the overworld value. Walk through a portal or `/worlds tp` between worlds, and the value follows.
9. World Options as op in `dt`: changing to Normal changes only `dt`. In singleplayer (`runClient`), lock the difficulty: World Options can no longer change it, and `/difficulty` still works (vanilla ignores the lock).
10. Hardcore: on a fresh dev world with `hardcore=true`, `/difficulty easy` in `dt` leaves it Hard.
11. `/worlds delete dt`, then recreate `dt`. It inherits again and logs no errors.

## Sources

Demand and comparable tools:
- mc-worlds issues (none about difficulty): https://github.com/wroud/mc-worlds/issues
- Multiworld (Fabric), `/mw difficulty`: https://modrinth.com/mod/multiworld · https://github.com/IsaiahMC/multiworld · https://github.com/IsaiahMC/multiworld/blob/master/Multiworld-Common/src/main/java/me/isaiah/multiworld/command/DifficultyCommand.java
- Multiworld #174, "Difficulty resets on server restart": https://github.com/IsaiahMC/multiworld/issues/174
- WorldEngine ("Difficulty — peaceful through hard, per world"): https://modrinth.com/project/qjqEpxGC
- melius World Manager (no difficulty): https://modrinth.com/mod/melius-worldmanager
- multiverse-dimensions and senseiwells/Multiverse (gamerule inheritance only): https://modrinth.com/mod/multiverse-dimensions · https://github.com/senseiwells/Multiverse
- Fantasy (NucleoidMC): https://github.com/NucleoidMC/fantasy · https://github.com/NucleoidMC/fantasy/blob/main/src/main/java/xyz/nucleoid/fantasy/RuntimeLevelData.java · https://github.com/NucleoidMC/fantasy/blob/main/src/main/java/xyz/nucleoid/fantasy/RuntimeLevelConfig.java
- Multiverse-Core world properties (difficulty, separate `hunger` flag): https://github.com/Multiverse/Multiverse-Core/wiki/World-properties
- Multiverse use-case guide (hub, creative plot, resource, minigame, peaceful build world): https://www.gameserverkings.com/knowledge-base/minecraft/multiverse-multiple-worlds/
- Multi-world guide ("survival world with hard difficulty and PvP on"): https://cybrancee.com/blog/how-to-run-multiple-minecraft-worlds-on-one-server/
- Multi-world plugins store per-world difficulty: https://loafhosts.com/guides/minecraft-difficulty-resets-to-peaceful-every-restart
- SPIGOT-8080 (Peaceful primary world next to normal secondary worlds): https://hub.spigotmc.org/jira/browse/SPIGOT-8080
- SPIGOT-7469 (End on Hard while server Peaceful): https://hub.spigotmc.org/jira/browse/SPIGOT-7469
- SPIGOT-6330 (`/difficulty` query vs "already set" mismatch in non-main worlds): https://hub.spigotmc.org/jira/browse/SPIGOT-6330
- mcMMO #5304 (resource worlds with hostiles disabled via spawn config): https://github.com/mcMMO-Dev/mcMMO/issues/5304
- Nomi-CEu #297 (player expects the same difficulty across dimensions): https://github.com/Nomi-CEu/Nomi-CEu/issues/297

Paper and Bukkit:
- Bukkit `World#setDifficulty` / `getDifficulty`: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/World.html
- Paper `WorldDifficultyChangeEvent` (hardcore always HARD): https://jd.papermc.io/folia/1.21.11/io/papermc/paper/event/world/WorldDifficultyChangeEvent.html
- Paper moderator: `server.properties` difficulty is used only at world creation, then per world: https://forums.papermc.io/threads/understanding-how-difficulty-works.751/
- Paper source patches: https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/server/commands/DifficultyCommand.java.patch · https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/server/MinecraftServer.java.patch · https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/server/network/ServerGamePacketListenerImpl.java.patch · https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/server/dedicated/DedicatedServer.java.patch

Not reachable: Reddit (blocked to the fetcher, and the Reddit connector failed to connect), the Aternos Spigot difficulty article (403), and the Paper "Fix Per World Difficulty" commit mirror (connection refused; the live patches above supersede it).
