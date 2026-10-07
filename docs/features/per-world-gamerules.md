# Per-world gamerules

**Status: shipped in 1.10.0.** Differences from this design: the `/worlds settings gamerule` subcommand was replaced by extending vanilla `/gamerule` in created worlds (bare `/gamerule` lists overrides, `/gamerule <rule>` names the origin, `/gamerule <rule> inherit` clears; vanilla dimensions unchanged). Optional mixin 6 shipped and also covers the Game Rules screen broadcast. Side effects are scoped to the changed world through a `ScopedValue`, which also limits JSON-RPC notifications to server-wide changes. Overrides of `#mc-worlds:global` rules are pruned on load and after `/reload`. With the mod on the client, Game Rules screen tooltips show each rule's origin (`mc-worlds:game_rule_origins` payload). No pin command: a world gets the server's value through the fallback.

**Verdict.** Implement it, in a reduced scope. Each mc-worlds world gets a sparse layer of overrides on top of the server's game rules. With no overrides, a world **inherits** every rule, which is exactly today's behaviour, so no migration is needed. The vanilla Overworld, Nether and End are untouched and keep sharing the server rules, as in vanilla. The answer to the maintainer's concern ("some rules users expect synced, some not") is that nothing diverges unless an admin explicitly overrides it in that world. Five command-system rules always stay global, because their vanilla read sites do not resolve to one world. The design rests on:
- vanilla data: the override map is a vanilla `GameRuleMap` `SavedData` in the world's own storage, the same pattern as per-world clocks;
- the vanilla command: `/gamerule` run inside a world, or through `/execute in <world> run gamerule …`, sets that world's override, so `GameRuleCommand` needs no mixin. This matches how `/time` and `/weather` already behave;
- three small correctness mixins that are mandatory, not optional: keep_inventory must use the death world (otherwise items are lost or XP is duplicated), client-synced flags must be re-sent on world change, and `onGameRuleChanged` broadcasts must be scoped;
- one change to an existing clock mixin.

The full-separate-rule-set model (Paper/Bukkit, senseiwells' Multiverse `has-custom-gamerules`, WorldGameRules) is rejected. Its best-documented failure mode is "I set keepInventory and it doesn't work in the Nether". There is no demand in the mc-worlds tracker, but every comparable world manager offers per-world rules. mc-worlds already has per-world time and weather, so the missing per-world `advance_time`/`advance_weather` switches are the most visible gap.

## Summary

| Item | Decision |
|---|---|
| Scope | mc-worlds worlds (`CustomServerLevel`) only. Vanilla dimensions always use the server rules |
| Model | Per-rule "inherit unless overridden": `LayeredGameRules extends GameRules` with `parent = server.getGameRules()` and `overrides = GameRuleMap` |
| Storage | `SavedDataType` `mc-worlds:game_rules` in the world's own `SavedDataStorage`. It reuses `GameRuleMap.TYPE`'s constructor, codec and `DataFixTypes.SAVED_DATA_GAME_RULES`, just as `PerWorldClocks.TYPE` does. `WorldsLevelData`/`CODEC` are not changed |
| Set | Vanilla `/gamerule <rule> <value>` (also the in-game Game Rules screen and Fabric enum rules), with the source in an mc-worlds world, writes an override |
| Global-only rules | Game rule tag `#mc-worlds:global` (datagen): `send_command_feedback`, `log_admin_commands`, `max_command_sequence_length`, `max_command_forks`, `max_block_modifications`. In a custom world, `/gamerule` writes these through to the server rules, which is today's behaviour |
| Query | Vanilla `/gamerule <rule>` prints the **effective** value for the source world (unchanged code) |
| Inspect / clear | `/worlds settings gamerule` (list overrides), `/worlds settings gamerule <rule>` (value and origin), `/worlds settings gamerule <rule> inherit` (drop the override). The `inherit` word matches the planned `/worlds settings difficulty inherit` |
| New mixins | `ServerPlayerGameRulesMixin` (1 injector), `MinecraftServerGameRulesMixin` (2 injectors) |
| Changed mixins | `PlayerListMixin` (+1 `@Inject`), `ServerClockInstanceMixin` (owner-aware) |
| Optional | `GameRuleCommandMixin` to name the world in the success message. **Dropped:** fragile `@Local` hook for a cosmetic message; `/worlds settings gamerule` shows where a value comes from |

## Use cases & demand

- **Users expect most rules to be shared across dimensions.** On Bukkit-family servers every world, Nether and End included, has its own rules, and this produces recurring "it doesn't work in the Nether/End" reports:
  - SPIGOT-169: "after restarting server, players lose their inventory in Nether and The_End world but no in World". A commenter replied: "Bukkit's gamerules have always been per world".
  - SPIGOT-4971: "gamerule only applied to overworld… fire spreads in the nether and the end… a long-standing problem with SpigotMC, but not with Vanilla". md_5 closed it as Invalid: "You must set the gamerule in each world by going to it and running the command".
  - Plugins exist only to undo this: GlobalGamerule ("Sync gamerules to all worlds!") on Hangar, and Global Gamerule on BukkitDev (666 downloads).

  This is the evidence for "inherit by default" and for leaving vanilla dimensions shared.
- **Admins also want some rules to differ per world.** Every comparable tool exposes per-world rules:
  - Multiverse-Core: `/mv gamerule set <rule> <value> [worlds|*]`, `reset`, `list` (`GameruleCommand.java:54-58,105-109,147-151`). Its `reset` restores the *vanilla default* (`:124`), not a server value.
  - Multiworld: `/mw gamerule`, plus a mixin that makes vanilla `/gamerule` act on the source world when it is not in the `minecraft` namespace (`MixinGameruleCommand.java:45-57,63-75`). Its 1.21.11 port is unfinished: `GameruleCommand2.java:29-31` is a `// TODO`, so it falls back to the now-global `world.getGameRules()`.
  - senseiwells' Multiverse (26.3): `/multiverse create from … <has-custom-gamerules?>` (README `:38-44`). With `true` the world gets a **fresh default** rule set, `GameRules(server.overworld().enabledFeatures())` (`MultiverseCommand.kt:170-171`). The override is Arcade's `CustomLevel.getGameRules()` (`CustomLevel.kt:155-156`). It has no keep-inventory or client-flag handling.
  - WorldGameRules (Fabric, 4.3K downloads): **every** dimension gets a full rule set. It `@Overwrite`s `ServerLevel.getGameRules` (`ServerLevelMixin.java:70-71`), stores rules in per-dimension `SavedData` (`:58`), and replaces vanilla `/gamerule` with a dimension-selector command. It redirects `restoreFrom` to `oldPlayer.level().getGameRules()` (`keep_inventory/ServerPlayerMixin.java:14-22`), the same fix proposed here. It dropped `advance_time`/`advance_weather` in 26.1 because they "require extra work" (CHANGELOG 1.3.7). mc-worlds already has that work.
  - Fantasy (NucleoidMC, 26.3): `DelegatingGameRules` layers overrides over `server.getGameRules()` (`DelegatingGameRules.java:15-41`; `RuntimeLevel.java:33-41`). This is the same model as proposed here, but runtime-only.
  - Custom Worlds (Fabric/NeoForge): `/cw config gamerule [<rule> [<value>|reset]]`, with override and reset semantics.
- Typical per-world use cases:
  - keepInventory or no PvP in a creative/hub world but not in survival;
  - frozen time or weather in a lobby (`advance_time`/`advance_weather`);
  - no mob spawning or griefing in a build world;
  - hidden death messages in an arena (`show_death_messages`).
- **mc-worlds tracker:** no gamerule requests. Issues #1, #3–#6 cover deletion, LuckPerms, tp, `/setworldspawn` and weather (`gh issue list -R wroud/mc-worlds`).

## Vanilla alignment

- **Data:** vanilla already stores rules as `SavedData` (`GameRuleMap extends SavedData`, `TYPE` = `minecraft:game_rules`, `world/level/gamerules/GameRuleMap.java:18-23`). The override layer is a second instance of that type, under the mod's id, in the world's own storage. This is exactly what `PerWorldClocks.TYPE` does for `ServerClockManager` (`PerWorldClocks.java:17-21`). Vanilla's datafixer type comes along for free, so future rule renames are applied to overrides too.
- **Commands:** vanilla `/gamerule` already reads and writes `source.getLevel().getGameRules()` (`server/commands/GameRuleCommand.java:44,50,56`). Overriding `getGameRules()` on `CustomServerLevel`, the way `getWeatherData()` and `clockManager()` are overridden today, makes `/gamerule`, `/execute in <world> run gamerule`, datapack functions and `execute store result … run gamerule` per-world with **zero command mixins**. The in-game Game Rules screen (`ServerGamePacketListenerImpl.java:812-829,1970-1978`) and Fabric's enum-rule command (`EnumRuleCommand`, which calls `source.getLevel().getGameRules().set(…)`, checked with javap on `fabric-game-rule-api-v1-4.0.10+3434d6d902`) follow automatically.
- **Side effects:** vanilla's `MinecraftServer.onGameRuleChanged` is reused rather than re-implemented. Two filters only *narrow* which players and levels it touches, so new client-synced rules that Mojang adds in later versions are scoped automatically.
- **Things vanilla has no equivalent for:** "inherit" and "clear override". The design needs nothing else that vanilla lacks: no per-world copies at creation and no multi-world selectors (`/execute in` covers that).

## Current state in mc-worlds

- `CustomServerLevel` overrides `getWeatherData()` (`server/level/CustomServerLevel.java:74-77`, built at `:58-59`) and `clockManager()` (`:79-83`, falling back to `super` while the field is null during the super-constructor). It does **not** override `getGameRules()`. `GameRules` is imported (`:28`) but unused. Every custom world currently shares the server rules (`ServerLevel.java:1941-1943`).
- Per-world clocks (`PerWorldClocks.java:17-28`) already read their **owner's** rules:
  - `ServerClockManagerMixin.java:40-46` wraps `getGlobalGameRules()` in `ServerClockManager.tick` and returns `owner.getGameRules()`. Once `getGameRules()` is per-world, `advance_time` is per-world for ticking with no new code.
  - `ServerClockInstanceMixin.java:14-19` returns `server.getGameRules()` in `packNetworkState`, which is the client-facing "is the clock running" flag. This is global and must become owner-aware.
- `MinecraftServerTimeSyncMixin.java:28-38` already wraps the `advance_time` broadcast in `onGameRuleChanged` and sends each player their own clock (`PerWorldClocks.broadcastFullSync`).
- Per-world time and weather commands:
  - `TimeCommandMixin.java:14-20`: `/time …`, including `pause`/`resume` (`TimeCommand.java:103-104`), acts on `source.getLevel().clockManager()`. A per-world *time freeze* is therefore already possible without gamerules.
  - `WeatherCommandMixin.java:15-37`: `/weather` acts on the source world.
  - Weather advancement (`ServerLevel.java:720`) and sleep-skip (`:375-385`) read `this.getGameRules()`, so `advance_weather` and `players_sleeping_percentage` become per-world automatically. Sleep-wake already moves the world's own clock (`ServerLevelTimeMixin.java:13-18`).
- Spawn flags for a custom world are set from its own rules on activation (`server/level/state/ActivationLevelState.java:32`, `setSpawnSettings(isSpawningMonsters())`).
- All custom worlds are `CustomServerLevel`. API providers must return a subclass (`manager/ServerLevelProvider.java:17`), so one override covers them all.
- Commands: `/worlds settings` acts on the current world (`command/SettingsCommand.java:29-50`) and requires `LEVEL_ADMINS` (`command/WorldsCommands.java:65`). Vanilla `/gamerule` requires `LEVEL_GAMEMASTERS` (`GameRuleCommand.java:22`).
- Tag precedent: `tags/DimensionTypeTags.java:8-16` plus `datagen/.../DimensionTypeTagsProvider`, generated into `versions/latest/src/main/generated/data/mc-worlds/tags/dimension_type/`.

## Vanilla internals (MC 26.3)

Paths are relative to `net/minecraft/` in the decompiled **26.3-rc-1** sources. The loom cache has no 26.3-stable sources jar, and the rc-1 → stable bump needed no mod code changes. Client paths are from `minecraft-clientOnly-…-26.3-rc-1-sources.jar`.

- **Storage and ownership**
  - `server/MinecraftServer.java:348-349` builds the single server `GameRules` from `savedDataStorage.computeIfAbsent(GameRuleMap.TYPE)`.
  - `server/level/ServerLevel.java:1941-1943`: `getGameRules()` returns `this.server.getGameRules()`. The method is public and non-final, and the `ServerLevel` constructor does not call it directly (`:230-312`).
  - `MinecraftServer.java:2104-2107`: `@Deprecated getGlobalGameRules()` returns `overworld().getGameRules()`. Its only callers are `ServerClockManager.java:61,197`, and the mod already wraps both.
- **`GameRules` API** (`world/level/gamerules/GameRules.java`)
  - The class is not final. `get` (`:120-127`) and `set` (`:129-138`) are public, and `set` stores the value and then calls `server.onGameRuleChanged(rule, value)`. `copy` (`:140-142`), `setAll` (`:144-150`, which reads `other.rules` directly) and `visitGameRuleTypes` (`:156-161`) touch the private map. `GameRules(List)` (`:112-114`) gives an empty map.
  - The `GameRules(FeatureFlagSet, GameRuleMap)` constructor (`:95-106`) **fills in defaults and mutates the map**, so an override map must never go through it.
  - `GameRuleMap`: `of()` is empty (`:34-36`), `set`/`remove` mark it dirty (`:56-68`), and `CODEC` is a dispatched map that serialises only the keys present (`:19-20`). `util/datafix/DataFixTypes.java:28` defines `SAVED_DATA_GAME_RULES`.
- **Command:** `server/commands/GameRuleCommand.java:41-53`
  - reads the source level's rules;
  - throws `commands.gamerule.not_set` if the effective value already equals the new one (`:46-47`);
  - calls `set(…, server)` (`:50`);
  - prints `commands.gamerule.set` (`:51`).

  `queryRule` prints the effective value (`:55-59`).
- **Change side effects:** `MinecraftServer.java:2074-2102`
  - `:2075` sends the JSON-RPC management notification;
  - `:2076-2081` sends `reduced_debug_info` entity events 22/23 to `getPlayerList().getPlayers()`;
  - `:2082-2087` sends `limited_crafting`/`immediate_respawn` game events to `getPlayers()`;
  - `:2088-2096` handles `locator_bar` over `getAllLevels()`;
  - `:2097-2098` handles `spawn_monsters` via `updateMobSpawningFlags()`, which computes each level from its own rules (`:1290-1294`);
  - `:2099-2101` handles `advance_time` via `broadcastAll(clockManager().createFullSyncPacket())`.

  Fabric's game-rule API injects at `RETURN` of `onGameRuleChanged` and fires `GameRuleEvents.changeCallback(rule)` with `(value, server)` only (javap, `fabric-game-rule-api-v1` `MinecraftServerMixin`).
- **Client-synced rules are sticky on the client**
  - Login sends them from the login level: `server/players/PlayerList.java:164-180`, read on the client at `ClientPacketListener.java:558-560`.
  - On respawn or dimension change the client copies reduced-debug and death-screen flags from the old player (`ClientPacketListener.java:1309-1310`), and the server does **not** re-send them (`PlayerList.java:418-426`; `ServerPlayer.java:1144-1167`).
  - The death screen is decided client-side from that flag (`ClientPacketListener.java:1792-1796`).
  - The client `doLimitedCrafting` flag is not copied on respawn and has **no reader** in the 26.3 client (`LocalPlayer.java:173,537-543`). Enforcement is server-side (`world/inventory/RecipeCraftingHolder.java:29`).
- **One hook covers login, respawn and teleport:** `PlayerList.sendLevelInfo(player, level)` (`PlayerList.java:647-660`) is called from `placeNewPlayer` (`:212`), `respawn` (`:425`) and `ServerPlayer.teleport` (`ServerPlayer.java:1166`).
- **The keep_inventory split**
  - On death, `ServerPlayer.die` calls `dropAllDeathLoot(this.level(), …)` (`ServerPlayer.java:916`), which leads to `Player.dropEquipment` (`world/entity/player/Player.java:559-565`) and the XP reward (`:1593-1595`). Both read the **death** world.
  - On respawn, `PlayerList.respawn` builds the new player **in the respawn level** (`PlayerList.java:387-394`), and `restoreFrom` reads `this.level()`, i.e. the **respawn** world (`ServerPlayer.java:1644`), before calling `transferInventoryXpAndScore` (`:1668-1674`).
  - `oldPlayer.level()` is still the death world at that point, because `removePlayerImmediately` (`PlayerList.java:390`) does not clear it.
- **Clock network state:** `world/clock/ServerClockManager.java:196-200` computes the rate from `getGlobalGameRules().get(ADVANCE_TIME)`. It is called from `modifyClock` (`:116`) and `createFullSyncPacket` (`:126`). Instances are created only in `init()` from the `WORLD_CLOCK` registry (`:39-50`).
- **Tags on static registries are loaded from datapacks** (`server/WorldLoader.java:37`, and on reload `MinecraftServer.java:1509`), and `Registries.GAME_RULE` exists (`core/registries/Registries.java:169`). A `#mc-worlds:global` game_rule tag therefore works.
- **The in-game Game Rules screen sends only changed entries** (`client/gui/screens/options/InWorldGameRulesScreen.java:48-55`), so opening it inside a custom world does not override every rule.

## Rule classification

Legend:
- **W** (world-simulation): read via the level where the thing happens. Overridable.
- **P** (player-scoped): read via the player's level. Overridable. The "world that applies" column says which world wins.
- **S** (server/admin-scoped): read sites do not resolve to one world, or the rule controls server-wide output. Global-only (`#mc-worlds:global`).

Paths are relative to `net/minecraft/`. A bare file name repeats a path already given earlier in the table. The table lists all 59 rules in `world/level/gamerules/GameRules.java:24-88`.

| Rule | Class | Read site(s) | World that applies / note |
|---|---|---|---|
| advance_time | W | `server/level/ServerLevel.java:378`; `world/clock/ServerClockManager.java:61` (already owner rules via mod mixin), `:197` | The world's own clock. `:197` needs the `ServerClockInstanceMixin` change. Per-world `/time pause` already exists |
| advance_weather | W | `ServerLevel.java:383,720` | The world's own weather (`getWeatherData` override) |
| allow_entering_nether_using_portals | W | `ServerLevel.java:1979-1981` via `world/entity/Entity.java:2712` | Source world. Only applies when the destination is `minecraft:the_nether` |
| block_drops | W | `world/level/block/Block.java:428,436`; `world/level/block/BeehiveBlock.java:287`; `world/level/block/InfestedBlock.java:51` | Block's world |
| block_explosion_drop_decay | W | `ServerLevel.java:1229` | Explosion's world |
| command_blocks_work | W | `ServerLevel.java:1987-1989` via `world/level/BaseCommandBlock.java:101`; `server/network/ServerGamePacketListenerImpl.java:671,700` | Block's world (the editor checks the editor's world) |
| command_block_output | W | `BaseCommandBlock.java:187` | Block's world |
| drowning_damage | P | `world/entity/player/Player.java:667` | World where the damage happens |
| elytra_movement_check | P | `ServerGamePacketListenerImpl.java:1251-1252` | Player's current world |
| ender_pearls_vanish_on_death | P | `world/entity/projectile/throwableitemprojectile/ThrownEnderpearl.java:158-161` | Owner's world at death |
| entity_drops | W | `world/entity/vehicle/VehicleEntity.java:72`; `world/entity/decoration/ItemFrame.java:225`; `world/entity/decoration/painting/Painting.java:173`; `world/entity/item/FallingBlockEntity.java:181,225,232`; `world/entity/Leashable.java:149` | Entity's world |
| fall_damage | P | `Player.java:669` | World where the damage happens |
| fire_damage | P | `Player.java:671` | World where the damage happens |
| fire_spread_radius_around_player | W | `ServerLevel.java:1928` | Fire's world |
| forgive_dead_players | W | `server/level/ServerPlayer.java:911`; `world/entity/NeutralMob.java:123`; `world/entity/ai/behavior/StopBeingAngryIfTargetDead.java:20` | Death world and mob's world (the same world in practice) |
| freeze_damage | P | `Player.java:673` | World where the damage happens |
| global_sound_events | W | `ServerLevel.java:1089` | World that emits the sound (it reaches all players) |
| immediate_respawn | P, client-synced | `server/players/PlayerList.java:165` (login); `MinecraftServer.java:2082-2087`; client `ClientPacketListener.java:1792` | World where the player dies. **Needs a re-send on world change** |
| keep_inventory | P | `Player.java:561,1594` (death world); `ServerPlayer.java:1644` (**respawn** world) | **Death world. Needs the `restoreFrom` fix**, otherwise items are lost (death world true, respawn world false) or XP is duplicated (death world false, respawn world true) |
| lava_source_conversion | W | `world/level/material/LavaFluid.java:199` | Fluid's world |
| limited_crafting | P, client-synced | `world/inventory/RecipeCraftingHolder.java:29`; `PlayerList.java:167`; `MinecraftServer.java:2082-2087` | Player's current world. Enforced server-side; the client flag is unused (`LocalPlayer.java:541`) |
| locator_bar | P | `server/waypoints/ServerWaypointManager.java:100-102`; `MinecraftServer.java:2088-2096` | Player's current world. The change handler touches **all** levels and must be scoped |
| log_admin_commands | **S** | `commands/CommandSourceStack.java:500` | Source world, but the output goes to the server console |
| max_block_modifications | **S** | `server/commands/CloneCommands.java:176` (source world even when cloning across dimensions, `:169-170`); `server/commands/FillCommand.java:142`; `server/commands/FillBiomeCommand.java:127` | Ambiguous across dimensions |
| max_command_forks | **S** | `commands/Commands.java:401` | World of the top-level command source only; `execute in` does not change it |
| max_command_sequence_length | **S** | `Commands.java:400`; `world/level/block/CommandBlock.java:175,204` | Top-level source vs block world. Mixed |
| max_entity_cramming | W | `world/entity/LivingEntity.java:3214`; `world/effect/OozingMobEffect.java:38` | Entity's world |
| max_minecart_speed | W | `world/entity/vehicle/minecart/NewMinecartBehavior.java:480` | Cart's world (feature-flagged, `GameRules.java:51-53`) |
| max_snow_accumulation_height | W | `ServerLevel.java:594` | World |
| mob_drops | W | `world/entity/LivingEntity.java:598,1529`; `world/entity/monster/Monster.java:129`; `world/entity/boss/enderdragon/EnderDragon.java:517,535` | Mob's world |
| mob_explosion_drop_decay | W | `ServerLevel.java:1230` | Explosion's world |
| mob_griefing | W | `ServerLevel.java:1230`; `world/level/ServerExplosion.java:300,306`; `world/entity/Mob.java:473` and about 40 entity/block sites, all on the actor's own level | Actor's world |
| natural_health_regeneration | P | `world/food/FoodData.java:44`; `ServerPlayer.java:748` | Player's current world |
| player_movement_check | P | `ServerGamePacketListenerImpl.java:1251-1252` | Player's current world |
| players_nether_portal_creative_delay | P | `world/level/block/NetherPortalBlock.java:118-119` | Portal's world |
| players_nether_portal_default_delay | P | `NetherPortalBlock.java:118-119`; `world/level/ClipContext.java:75` | Portal's world |
| players_sleeping_percentage | W | `ServerLevel.java:375,654,660` | World (sleep is per world, and wake moves that world's clock) |
| projectiles_can_break_blocks | W | `world/entity/projectile/Projectile.java:431` | Projectile's world |
| pvp | P | `ServerLevel.java:1983-1985` via `ServerPlayer.java:996-1000` | Attacker's world |
| raids | W | `world/entity/raid/Raids.java:81,108` | World |
| random_tick_speed | W | `server/level/ServerChunkCache.java:370` | World (`ServerWatchdog.java:54` reports the server value in crash reports) |
| reduced_debug_info | P, client-synced | `PlayerList.java:166`; `MinecraftServer.java:2076-2081`; client `ClientPacketListener.java:558,1309` | Player's current world. **Needs a re-send on world change** |
| respawn_radius | P | `server/level/PlayerSpawnFinder.java:55` | Respawn world |
| send_command_feedback | **S** | `CommandSourceStack.java:491-492` (source world); `ServerPlayer.java:347` (player's world); `server/commands/GameModeCommand.java:41`; `world/level/BaseCommandBlock.java:177`; `world/level/block/CommandBlock.java:142` | Mixed. An override would make admin feedback depend on where a command was typed |
| show_advancement_messages | P | `server/PlayerAdvancements.java:178` | Player's world at award time. The broadcast is server-wide; `server.properties` sets the server value (`server/dedicated/DedicatedServer.java:247-249`) |
| show_death_messages | P | `ServerPlayer.java:879` (broadcast server-wide at `:900`); `world/entity/TamableAnimal.java:225` | Death world |
| spawner_blocks_work | W | `ServerLevel.java:1991-1993` via `world/level/BaseSpawner.java:95`; `world/level/block/entity/trialspawner/TrialSpawner.java:152`; `world/item/SpawnEggItem.java:55` | Spawner's world |
| spawn_mobs | W | `ServerChunkCache.java:369`; `world/level/NaturalSpawner.java:350`; `ServerLevel.java:559,1889`; `TrialSpawner.java:157` | World |
| spawn_monsters | W | `ServerLevel.java:1889` (spawn flags: `ActivationLevelState.java:32`, `MinecraftServer.java:1290-1294`) | World |
| spawn_patrols | W | `world/level/levelgen/PatrolSpawner.java:22` | World |
| spawn_phantoms | W | `world/level/levelgen/PhantomSpawner.java:27` | World |
| spawn_wandering_traders | W | `world/entity/npc/wanderingtrader/WanderingTraderSpawner.java:46` | World |
| spawn_wardens | W | `world/level/block/entity/SculkShriekerBlockEntity.java:130` | World |
| spectators_generate_chunks | W | `server/level/ChunkMap.java:1026` | World |
| spread_vines | W | `world/level/block/VineBlock.java:165` | World |
| tnt_explodes | W | `world/level/block/TntBlock.java:72,84,123`; `world/entity/item/PrimedTnt.java:128`; `core/dispenser/DispenseItemBehavior.java:204` | World |
| tnt_explosion_drop_decay | W | `ServerLevel.java:1231` | World |
| universal_anger | W | `world/entity/NeutralMob.java:109`; `world/entity/monster/piglin/PiglinAi.java:536,598,687` | Mob's world |
| water_source_conversion | W | `world/level/material/WaterFluid.java:77` | World |

Totals: 35 W, 19 P, 5 S. Only keep_inventory, immediate_respawn, reduced_debug_info and locator_bar need extra work. Every other P rule is read through the level where the effect happens, so it is already unambiguous.

## Proposed design

### Model and persistence

New `server/level/PerWorldGameRules.java`, next to `PerWorldClocks`:

```java
public static final SavedDataType<GameRuleMap> TYPE = new SavedDataType<>(
    McWorldMod.id("game_rules"),
    GameRuleMap.TYPE.constructor(),
    GameRuleMap.TYPE.codec(),
    GameRuleMap.TYPE.dataFixType());
public static final TagKey<GameRule<?>> GLOBAL = TagKey.create(Registries.GAME_RULE, McWorldMod.id("global"));
```

- The file lives at `dimensions/<ns>/<world>/data/mc-worlds/game_rules.dat`. It is deleted with the world and loaded only while the world is loaded. Existing worlds have no file, so they inherit everything, with no migration.
- `LayeredGameRules extends GameRules` is built with `super(List.of())` and holds `parent` (`server.getGameRules()`) and `overrides` (the `GameRuleMap` above):
  - `get(rule)`: the override if present, else `parent.get(rule)`. This is one extra identity-map lookup on the hot path, with no tag check.
  - `set(rule, value, server)`:
    - If `rule` is in `#mc-worlds:global`, call `parent.set(rule, value, server)`.
    - Otherwise call `overrides.set(rule, value)`, then `server.onGameRuleChanged(rule, value)`. This mirrors `GameRules.java:129-138` and Fantasy's `DelegatingGameRules.java:26-31`.
  - `inherit(rule, server)`: `overrides.remove(rule)`, then `server.onGameRuleChanged(rule, parent.get(rule))` so side effects resync.
  - `availableRules()` and `visitGameRuleTypes(v)` delegate to `parent`.
  - `copy(features)` returns a **flattened** vanilla `GameRules` (`parent.copy` plus the overrides applied with a `null` server).
  - `setAll(GameRules, server)` iterates `other.availableRules()`, because vanilla reads the private `other.rules` (`GameRules.java:144-146`).
  - On load, drop override entries for rules that `parent` does not have (feature-disabled or removed mod rules).
- `CustomServerLevel` changes:
  - `this.gameRules = PerWorldGameRules.create(this)` after `super(...)`.
  - `@Override getGameRules()` returns the layered instance, or `super.getGameRules()` while the field is still null. This is the same shape as `clockManager()` (`CustomServerLevel.java:79-83`).
- `WorldsLevelData`/`CODEC` (`WorldsLevelData.java:20-24`) are **not** changed. Rejected because:
  - the central `worlds_data.dat` would then carry a second, non-vanilla rule codec;
  - it would lose `SAVED_DATA_GAME_RULES` datafixing;
  - overrides would persist after `/worlds delete`.

### Commands

- **Set:** vanilla. `/gamerule keep_inventory true` inside world `mc-worlds:arena`, or `/execute in mc-worlds:arena run gamerule keep_inventory true` from anywhere. To set the **server** value, run it in a vanilla dimension, e.g. `/execute in minecraft:overworld run gamerule …`. This is the same split as `/time` and `/weather`.
- **Query:** vanilla `/gamerule <rule>` prints the effective value (`GameRuleCommand.java:55-59`).
- **Mod subcommands**, added to `SettingsCommand.build()`. They use `LEVEL_ADMINS`, as the rest of `/worlds` does.
  - `/worlds settings gamerule`: lists this world's overrides as `rule = value (server: value)`, or says that it uses the server values.
  - `/worlds settings gamerule <rule>`: effective value, and whether it is overridden or inherited. `<rule>` is a `ResourceArgument` over `Registries.GAME_RULE`.
  - `/worlds settings gamerule <rule> inherit`: drops the override.
- **Pinning:** a world cannot pin a value equal to the current server value. Vanilla refuses no-op sets (`GameRuleCommand.java:46-47`) and the design does not bypass that. See the open questions.

### Mixins and overrides

1. **`CustomServerLevel.getGameRules()`**: a plain override, not a mixin. `ServerLevel.java:1941` is non-final.
2. **`mixin/ServerPlayerGameRulesMixin`** (new): keep_inventory follows the death world.
   ```java
   @ModifyExpressionValue(method = "restoreFrom", at = @At(value = "INVOKE",
       target = "Lnet/minecraft/server/level/ServerLevel;getGameRules()Lnet/minecraft/world/level/gamerules/GameRules;"))
   private GameRules mcworlds$deathWorldRules(GameRules original, ServerPlayer oldPlayer, boolean restoreAll) {
       return oldPlayer.level().getGameRules();
   }
   ```
   This is the single call at `ServerPlayer.java:1644`. It is a no-op when both worlds share the server rules. WorldGameRules uses the same `@Redirect` in production.
3. **`PlayerListMixin`** (existing): add `@Inject(method = "sendLevelInfo", at = @At("TAIL"))`. It calls `PerWorldGameRules.sendClientRules(player, level.getGameRules())`, which sends:
   - `ClientboundEntityEventPacket(player, reduced ? 22 : 23)`;
   - `ClientboundGameEventPacket(IMMEDIATE_RESPAWN, 1|0)`;
   - `ClientboundGameEventPacket(LIMITED_CRAFTING, 1|0)`.

   These are the same packets as `MinecraftServer.java:2076-2087`. One hook covers login, respawn and teleport. Vanilla already sends entity events right after a respawn packet (`ServerPlayer.java:1147`, `PlayerList.java:426`), so it is safe. As a side benefit it fixes vanilla's dropped client `doLimitedCrafting` after respawn.
4. **`mixin/MinecraftServerGameRulesMixin`** (new): scopes vanilla side effects to where the value applies. It handles both global changes (players in overriding worlds are skipped) and per-world changes (players elsewhere are skipped).
   ```java
   @ModifyExpressionValue(method = "onGameRuleChanged", at = @At(value = "INVOKE",
       target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
   private List<ServerPlayer> mcworlds$playersWithValue(List<ServerPlayer> original, GameRule<?> rule, Object value)
   @ModifyExpressionValue(method = "onGameRuleChanged", at = @At(value = "INVOKE",
       target = "Lnet/minecraft/server/MinecraftServer;getAllLevels()Ljava/lang/Iterable;"))
   private Iterable<ServerLevel> mcworlds$levelsWithValue(Iterable<ServerLevel> original, GameRule<?> rule, Object value)
   ```
   - Each filter keeps only entries where `level.getGameRules().get(rule)` equals `value`.
   - Fast path: return `original` when no loaded custom world has any override.
   - Targets: `getPlayers()` at `MinecraftServer.java:2079,2087` and `getAllLevels()` at `:2089`.
   - `spawn_monsters` needs nothing, because `updateMobSpawningFlags()` already computes each level from its own rules.
   - `advance_time` is already routed per clock by `MinecraftServerTimeSyncMixin.java:28-38`.
5. **`ServerClockInstanceMixin`** (existing): make `packNetworkState` use the owner world's rules.
   - Implement `WorldClockOwner` on the instance.
   - In `PerWorldClocks.create`, after `init()`, call `getInstance(holder)` for every `Registries.WORLD_CLOCK` holder and set the owner on it.
   - The wrap returns `owner != null ? owner.getGameRules() : server.getGameRules()`. It still never touches `overworld()`, which preserves the Worldthreader fix from b347022.
6. **Optional `mixin/GameRuleCommandMixin`**: `@ModifyArg` on the `Component.translatable("commands.gamerule.set", …)` call in `setRule` (`GameRuleCommand.java:51`). It swaps in a key that names the world when the source is a `CustomServerLevel` and the rule is not global-only.

Register 2, 4 and 6 in `worlds.mixins.json`.

### i18n (`src/main/resources/assets/mc-worlds/lang/en_us.json`)

```json
"dev.wroud.mc.worlds.command.settings.gamerule.list": "World %s overrides these game rules:",
"dev.wroud.mc.worlds.command.settings.gamerule.list.entry": "%s = %s (server: %s)",
"dev.wroud.mc.worlds.command.settings.gamerule.list.none": "World %s uses the server value of every game rule",
"dev.wroud.mc.worlds.command.settings.gamerule.query.override": "In world %s, %s is overridden to:",
"dev.wroud.mc.worlds.command.settings.gamerule.query.inherit": "World %s uses the server value of %s:",
"dev.wroud.mc.worlds.command.settings.gamerule.inherit.success": "World %s now uses the server value of %s:",
"dev.wroud.mc.worlds.command.settings.gamerule.inherit.not_overridden": "World %s does not override %s",
"dev.wroud.mc.worlds.command.settings.gamerule.global": "%s is a server-wide game rule and cannot be overridden per world",
"dev.wroud.mc.worlds.gamerule.set.world": "Gamerule %s is now set to: %s in world %s"
```

The last key is only needed if the optional mixin 6 is adopted.

### Datagen

- Add `data/tags/GameRuleTagsProvider extends FabricTagsProvider<GameRule<?>>` for `Registries.GAME_RULE`, and register it in `DataGenerator.java` next to `DimensionTypeTagsProvider`.
- `builder(PerWorldGameRules.GLOBAL)` adds the keys of `send_command_feedback`, `log_admin_commands`, `max_command_sequence_length`, `max_command_forks` and `max_block_modifications`.
- The output is `versions/latest/src/main/generated/data/mc-worlds/tags/game_rule/global.json`.
- Modpacks and mods can extend the tag; for example, a mod can mark its own rule as global.

### Docs / API

- README: add a `/worlds settings gamerule` section and a note that `/gamerule` inside a created world now changes only that world.
- API.md: providers can pre-seed overrides with `level.getGameRules().set(rule, value, null)` once the level is constructed.

### Rejected alternatives

- **A full separate rule set per world** (Paper, Multiverse `has-custom-gamerules`, WorldGameRules). Admins must re-apply every rule per world, which is the SPIGOT-169/4971 failure. It also needs a policy for which values to copy at creation (Multiverse uses vanilla defaults, `MultiverseCommand.kt:171`).
- **Leaving vanilla `/gamerule` global and adding `/worlds gamerule set`.** This would need a mixin to keep `/gamerule` global, contradicts the `/time`/`/weather` precedent, and leaves the client Game Rules screen and Fabric enum rules inconsistent.
- **Re-implementing `onGameRuleChanged` for per-world changes.** The filter approach keeps vanilla as the single source of side effects.

## Edge cases & risks

- **Behaviour change.** Today `/gamerule` run inside a created world changes the server value. Afterwards it changes only that world. This needs a changelog entry. See open question 1.
- **Hot path.** `get` adds one `Reference2ObjectOpenHashMap` lookup. The tag is checked only in `set`. If a rule becomes `#global` after an override already exists (for example after `/reload`), the stale override keeps applying until it is cleared. `/worlds settings gamerule` should flag such entries.
- **Modded rules (Fabric `GameRuleBuilder`).**
  - They are overridable by default.
  - A mod that reads `level.getGameRules()` becomes per-world. One that reads `server.getGameRules()` silently ignores overrides.
  - Fabric's `GameRuleEvents` listeners and the JSON-RPC management notification (`MinecraftServer.java:2075`) also fire for per-world changes and cannot tell which world changed.
  - Datapacks cannot register rules (the registry is built in). They can only *set* rules, and `execute in <world> run gamerule …` works for that.
- **Rules that act server-wide by design.**
  - `show_death_messages`/`show_advancement_messages` are decided per world but broadcast to everyone (`ServerPlayer.java:900`, `PlayerAdvancements.java:178-179`).
  - `global_sound_events` from world W reaches players in all worlds (`ServerLevel.java:1088-1101`).

  These follow vanilla semantics; the docs should state them.
- **show_advancement_messages override vs `server.properties`.** `announce-player-achievements` only sets the server value (`DedicatedServer.java:247-249`), so an override wins in its world.
- **Lazy worlds.** Overrides load with the level, and spawn flags are recomputed on activation (`ActivationLevelState.java:32`). A player who logs in to an unloaded world gets the correct login flags, because `placeNewPlayer` reads the loaded level (`PlayerList.java:164`).
- **Cross-world projectiles and pets.**
  - An ender pearl reads its owner's current world (`ThrownEnderpearl.java:158-161`).
  - `forgive_dead_players` reads the death world for the player and the mob's world for mobs.

  These are accepted vanilla quirks.
- **Concurrency (Worldthreader).** Reads of the override map can race with server-thread writes. This is the same exposure vanilla already has with the global `GameRuleMap`.
- **Vanilla API drift.** `LayeredGameRules` overrides public `GameRules` methods. A signature change or `final` breaks compilation, which is loud, not a silent mixin miss. On MC bumps, re-check:
  - `GameRules.java:116-161` (methods that touch the private map);
  - `MinecraftServer.onGameRuleChanged` still iterating `getPlayers()`/`getAllLevels()`;
  - `ServerPlayer.restoreFrom` still having exactly one `getGameRules()` call.
- **Memory note conflict.** `reference_clock_api_26_3` says "Game rules are NOT per-level… Don't add one". That note describes vanilla and must be updated when this ships, because `ServerClockInstanceMixin` changes meaning.

## Open questions for the maintainer

1. **Opt-in or always-on?** Should `/gamerule` in a created world write an override immediately (consistent with `/time`/`/weather`; the recommended option, plus the optional message mixin 6)? Or only after a per-world opt-in such as `/worlds settings gamerule own`, so that existing admins' habits do not silently change?
2. **What is global-only?**
   - Is the 5-rule `#mc-worlds:global` set right?
   - Should modded rules default to global (safer for mods that read `server.getGameRules()`) instead of overridable?
   - Should the tag mechanism be replaced by a hard-coded `Set` (less flexible, no datagen)?
3. **Pinning and inherit semantics.** Should there be a way to pin a value equal to the server value, for example `/worlds settings gamerule <rule> pin`? And should setting a world's value back to the server value clear the override automatically, instead of only via `inherit`?
4. Should per-world changes skip the JSON-RPC management notification (one `@WrapWithCondition` on `MinecraftServer.java:2075`)?
5. `/gamerule` needs `LEVEL_GAMEMASTERS` but `/worlds settings gamerule … inherit` needs `LEVEL_ADMINS`. Should the subcommand use the lower permission?
6. Should this ship together with per-world difficulty, to give one "inherit unless overridden" changelog story (see `per-world-difficulty.md`)?

## Manual test plan

Use `./gradlew runServer` for server checks and `./gradlew runClient` (op, LAN or a joined dev server) for client-flag checks. Setup: `/worlds create gr_test`, then `/worlds create gr_nether from-dimension minecraft:the_nether`.

1. **Back-compat.**
   - `/execute in mc-worlds:gr_test run gamerule keep_inventory` returns the server value.
   - Change the rule in the Overworld; the custom world follows.
   - `/worlds settings gamerule` in gr_test reports no overrides.
   - No `game_rules.dat` exists yet.
2. **Override and persistence.**
   - `/execute in mc-worlds:gr_test run gamerule keep_inventory true` while the server value is `false`. The query is `true` in gr_test and `false` in the Overworld and the Nether.
   - Restart; the override survives. `world/dimensions/mc-worlds/gr_test/data/mc-worlds/game_rules.dat` exists.
3. **The server value does not clobber overrides.**
   - `/execute in minecraft:overworld run gamerule keep_inventory false`; gr_test is still `true`.
   - Run `/gamerule keep_inventory true` in the vanilla Nether. The server value changes, because vanilla dimensions never get overrides.
4. **Keep inventory across worlds.**
   - Set the spawn point in the Overworld (server value `false`). Die in gr_test (override `true`). The respawn keeps the inventory and XP.
   - Reverse it: gr_test override `false`, server `true`. Items drop in gr_test, and the respawn has an empty inventory with **no** duplicated XP.
5. **Client flags** (client).
   - gr_test: `immediate_respawn true`, server `false`. Dying in gr_test gives no death screen. `/worlds tp` to the Overworld, then die: the death screen appears.
   - Repeat after relogging while standing in gr_test.
   - `reduced_debug_info true` in gr_test: F3 coordinates are hidden there and visible again after teleporting out.
   - Toggle the server value while standing in gr_test with an override: the state does not flicker or change.
6. **Time and weather.**
   - `advance_time false` in gr_test: its sky freezes on the client while the Overworld keeps cycling, and `/time query` returns the same value in gr_test twice.
   - `advance_weather false` in gr_test plus `/weather rain` there: it stays rainy, while the Overworld's weather keeps changing.
   - Sleep in gr_test with `players_sleeping_percentage 0`: only gr_test's clock skips.
7. **Spawning.** `spawn_monsters false` in gr_test: no hostiles there at night, hostiles still spawn in the Overworld. Let the world auto-unload (60 s empty), then return: still no hostiles.
8. **Locator bar** (2 clients). `locator_bar false` in gr_test: no bar while both players are in gr_test, and the bar is back when both are in the Overworld.
9. **Global-only.** `/execute in mc-worlds:gr_test run gamerule send_command_feedback false` changes the server value (check from the Overworld) and creates no override. Restore it afterwards.
10. **Inherit.** `/worlds settings gamerule keep_inventory inherit` in gr_test: the value follows the server value again and the list is empty.
11. **Game Rules screen** (client, op, inside gr_test). It shows the effective values. Changing one rule creates exactly one override.
12. **Delete.** `/worlds delete gr_test`, then recreate `gr_test`: there are no stale overrides.

## Sources

- SPIGOT-169 (keepInventory lost in Nether/End; "Bukkit's gamerules have always been per world"): https://hub.spigotmc.org/jira/browse/SPIGOT-169
- SPIGOT-4971 ("gamerule only applied to overworld", closed Invalid): https://hub.spigotmc.org/jira/browse/SPIGOT-4971
- GlobalGamerule (Paper): https://hangar.papermc.io/mja00/GlobalGamerule
- Global Gamerule (Bukkit): https://dev.bukkit.org/projects/global-gamerule
- Multiverse-Core `/mv gamerule`: https://github.com/Multiverse/Multiverse-Core/blob/main/src/main/java/org/mvplugins/multiverse/core/commands/GameruleCommand.java
- senseiwells' Multiverse (26.3): https://github.com/senseiwells/Multiverse/blob/26.3/README.md, https://github.com/senseiwells/Multiverse/blob/26.3/src/main/kotlin/me/senseiwells/multiverse/commands/MultiverseCommand.kt
- Arcade `CustomLevel` (Multiverse's dimension library): https://github.com/CasualChampionships/arcade/blob/26.3/arcade-dimensions/src/main/kotlin/net/casual/arcade/dimensions/level/CustomLevel.kt
- Multiworld: https://modrinth.com/mod/multiworld, https://github.com/IsaiahMC/multiworld/blob/master/Multiworld-Common/src/main/java/multiworld/mixin/MixinGameruleCommand.java, https://github.com/IsaiahMC/multiworld/blob/master/fabric/Multiworld-Fabric-1.21.11/src/main/java/me/isaiah/multiworld/command/GameruleCommand2.java
- WorldGameRules: https://modrinth.com/mod/worldgamerules, https://github.com/DrexHD/WorldGameRules (`src/main/java/me/drex/world_gamerules/mixin/ServerLevelMixin.java`, `mixin/gamerules/keep_inventory/ServerPlayerMixin.java`, `CHANGELOG.md`)
- Fantasy `DelegatingGameRules` / `RuntimeLevel`: https://github.com/NucleoidMC/fantasy/blob/main/src/main/java/xyz/nucleoid/fantasy/DelegatingGameRules.java, https://github.com/NucleoidMC/fantasy/blob/main/src/main/java/xyz/nucleoid/fantasy/RuntimeLevel.java
- Custom Worlds (`/cw config gamerule … reset`): https://modrinth.com/mod/customworlds
- mc-worlds issue tracker: https://github.com/Wroud/mc-worlds/issues
