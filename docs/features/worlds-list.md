# /worlds list

**Status: shipped in 1.9.0.** Differences from this design: unloaded worlds are gray, not red (lazy is not an error); the hover is a formatted card (bold id, status line, gray labels with white values, yes/no instead of true/false); the click still fills in `/worlds tp <id>`, with a tooltip that says so; permission stayed at `LEVEL_ADMINS`.

**Verdict.** Build it. Ship one subcommand, `/worlds list`, with `loaded` and `unloaded` filters. It copies vanilla `/datapack list` closely: two `There are N … world(s): [a], [b]` lines, `[id]` chat links coloured green for loaded and gray for unloaded, a hover with the details, and a shift-click that inserts the id. Each entry gets a `SuggestCommand` of `/worlds tp <id>`, the same click pattern as `/locate`. The command reads only the loaded-levels map, `WorldsManager` handles, the `WorldsData` snapshot and `PlayerList`. It never calls `MinecraftServer.getLevel`, so it cannot lazy-load a world. Don't add a separate `info`, `who` or `whoall`: put dimension type, provider, load-on-startup, the world-spawn marker and player names in the per-entry hover, the way `/datapack list` puts a pack's description in its hover. The command returns the number of worlds listed. No datagen change, because translations are hand-written. No public API change. The one real policy decision is permission. Vanilla read-only lists use `LEVEL_GAMEMASTERS`, and command blocks and functions run at that level. So `list` can only serve command blocks if the `/worlds` root drops from `LEVEL_ADMINS` to `LEVEL_GAMEMASTERS`, with every existing subcommand keeping its own `LEVEL_ADMINS` gate.

## Summary

| Item | Decision |
|---|---|
| Command | `/worlds list` · `/worlds list loaded` · `/worlds list unloaded` |
| Output | Vanilla `/datapack list` shape: one `sendSuccess(…, false)` line per group, `.none` line when a group is empty |
| Which worlds | Same set `/worlds tp` already accepts: every loaded dimension plus every saved mc-worlds world |
| Sorting | `Identifier` natural order (path, then namespace) |
| Entry | `[namespace:path]`, GREEN loaded / RED unloaded, insertion = id, click = suggest `/worlds tp <id>`, hover = details |
| Hover | dimension type, provider, load on startup (mc-worlds worlds only), "holds the world spawn", players, "Click to teleport" |
| Return value | Number of entries printed (sum for bare `list`), like `DataPackCommand.listPacks` |
| Permission | Recommended `LEVEL_GAMEMASTERS` (needs root lowered); fallback keep `LEVEL_ADMINS` |
| Console / RCON / command block | Works: no player required; console gets the plain text, without hover |
| `info` / `who` | Not in v1; details and players are in the hover |
| Pagination | None (vanilla list commands never paginate); `loaded`/`unloaded` filters are the volume control |
| Datagen / API | None / none |

## Use cases & demand

- **Inventory of lazy worlds.** A lazy world that is not loaded is invisible today, except through `/worlds tp` tab completion. `/worlds delete` completion lists only *loaded* custom worlds (`WorldsCommands.java:54-56` → `WorldsManager.getWorldIds()`, `WorldsManager.java:43-45`), so you cannot find an unloaded world's id that way.
- **What is loaded right now.** This helps with memory and tick diagnosis. The 1.8.26 load/unload-loop investigation (memory `project_getlevel_lazy_load_loops.md`) had to rely on log greps.
- **Where players are.** This is what Multiverse-Core's `/mv who` and `whoall` do. Here it goes in the hover instead of separate commands.
- **Console, RCON and command blocks.** For example, `execute store result … run worlds list`. Multiverse (Fabric) issue #3 shows that console users get stuck when a feature depends on chat clicks, so the clicks here are only a convenience.

Demand evidence:
- senseiwells/Multiverse #4, "list command". Opened 2026-07-16, closed, 0 comments: *"it is not easily possible to view all worlds created and managed by this mod in game. I'd suggest a /multiverse list command."* It is that tracker's only feature request. #3 is "console support".
- Multiverse-Core #1960, "Order mv list output alphabetically". Opened 2019-01-26, labelled Accepted, milestone 5.0.0. **The GitHub API shows 4 👍, not 8.**
- Multiverse-Core 5's `/mv list` lists loaded worlds sorted with their environment, then unloaded worlds sorted in gray with `- UNLOADED` (`ListCommand.java:80-92` @ `396ece4`). It adds `--page`, `--filter` and `--raw` (`:57`), and has separate `/mv info` and `/mv who` (`InfoCommand.java`, `WhoCommand.java` in the same package).
- Multiworld (Fabric) documents `/mw list`, "List all worlds", under its base permission `multiworld.cmd`.

## Vanilla alignment

- **Shape.** `/datapack list` is the closest analog: two groups (enabled/available ≈ loaded/unloaded), filter literals, and a summed return value (`DataPackCommand.java:134-137, 219-260`). `/team list` and `/bossbar list` use the same `There are %s …: %s` plus `.none` pattern (`TeamCommand.java:377-388`, `BossBarCommands.java:287-297`).
- **Entry styling.** Pack links are `[id]` in square brackets, GREEN or RED, with insertion and a hover of title + `"\n"` + description (`PackLocationInfo.java:13-20`). Boss bars use a hover id plus insertion (`CustomBossEvent.java:84-91`).
- **Click.** `/locate` uses `ClickEvent.SuggestCommand` plus a `chat.coordinates.tooltip` hover ("Click to teleport") (`LocateCommand.java:175-181`). `RunCommand` would show the client's "permissions required" confirmation screen for any op-gated command, which `/worlds tp` is (see Vanilla internals).
- **Sorting.** `ComponentUtils.formatAndSortList` sorts by natural order (`ComponentUtils.java:88-100`). `Identifier` sorts by path, then namespace (`Identifier.java:143-149`).
- **Permission.** Every vanilla read-only list or query is `LEVEL_GAMEMASTERS`: datapack, bossbar, team, locate, forceload, and seed on a dedicated server. `/list` is open to all, but it shows names only, never dimensions.
- **Not invented.** No hidden worlds, aliases, per-world permissions, regex filters or paging. Those are Multiverse policies that vanilla does not have.

## Current state in mc-worlds

- **Command root.** `WorldsCommands.register` registers `literal("worlds").requires(LEVEL_ADMINS)` with delete, tp, create and settings (`WorldsCommands.java:61-70`). The second `register(literal("worlds")…redirect(root))` at `:71-74` merges into the existing node. Brigadier `CommandNode.addChild` copies only `command` and children (`CommandNode.java:73-81`), so the second node's `requires` and `redirect` are discarded. The effective root gate is `:65`.
- **Per-subcommand gates already exist.** Create `CreateCommand.java:38`, delete `DeleteCommand.java:23`, tp `TeleportCommand.java:30` and settings `SettingsCommand.java:31` each have their own `requires(Commands.hasPermission(Commands.LEVEL_ADMINS))`. Lowering the root therefore exposes nothing else. CLAUDE.md says `ServerLevel.LEVEL_ADMINS`, but the code uses `Commands.LEVEL_ADMINS`.
- **Suggestions.** `WORLD_SUGGESTIONS` is the union of `server.levelKeys()` and `WorldsData.getLevelsData().keySet()` (`WorldsCommands.java:40-52`). That is exactly the set the list should show. `UNKNOWN_WORLD_EXCEPTION` is at `:58-59`, and is duplicated in `SettingsCommand.java:26-27`.
- **Output style today.** Messages build from translatable fragments with GOLD literals (`CreateCommand.java:143-147`, `SettingsCommand.java:65-69`, which prints `true`/`false` raw). Create's "Teleport" link uses `ClickEvent.RunCommand("/worlds tp …")` (`CreateCommand.java:157-161`). Returns are `Command.SINGLE_SUCCESS` or `1` (`CreateCommand.java:166`, `DeleteCommand.java:51`, `TeleportCommand.java:56`).
- **Translations.** `src/main/resources/assets/mc-worlds/lang/en_us.json:1-19` is hand-written, with keys under `dev.wroud.mc.worlds.command.<sub>.*`. Datagen registers only `DimensionTypeTagsProvider` (`DataGenerator.java:14`) and has no language provider. CLAUDE.md's "add datagen entry" step for new commands does not apply here.
- **Data available without side effects:**
  - Loaded custom worlds come from `WorldsManager.worlds` (`WorldsManager.java:33`), via `getWorld(id)` (`:47-49`) → `WorldHandle` (`WorldHandle.java:18-28`).
  - All saved worlds come from `WorldsData.getLevelsData()`, which returns a **copy** (`WorldsData.java:52-54`).
  - Per-world data: `WorldsLevelData.getProvider()` (`:46-48`), `getLevelStem()` (nullable, `:50-52`), `getSeed()` (`:54-56`) and `isLazy()` (`:58-60`). Load-on-startup is `!isLazy()`, as in `SettingsCommand.java:63`.
  - `getLevelStem()` is `null` when the stored generator could not be decoded (`WorldGeneratorData.java:55-57`) or was never stored. mc-stargate creates every world with a `null` stem and its own provider (`mc-stargate/.../StargateWorldsManager.java:38-44`).
  - Every loaded level, including vanilla and other mods' dimensions, is in `MinecraftServerAccessor.getLevels()` (`MinecraftServerAccessor.java:28-29`).
  - `DimensionDetectionUtil.getDimensionType` (`DimensionDetectionUtil.java:51-62`) is the established side-effect-free pattern: read the loaded map first, then the stored stem. The list should do the same.
- **What loads worlds (must not be called).**
  - `MinecraftServerMixin.onGetLevelReturn` turns a `null` from `getLevel` into `loadOrCreate` for any saved world (`MinecraftServerMixin.java:38-64`).
  - `loadOrCreateWorld` builds the level and logs `Loading world:` (`WorldsManager.java:64-127`).
  - Vanilla `findRespawnDimension()` and `DimensionArgument.getDimension()` both call `getLevel` (see below). Existing commands call it on purpose: `TeleportCommand.java:47,61` and `DeleteCommand.java:33`.
- **Lifecycle details that affect "loaded".**
  - A new `CustomServerLevel` is put into the server's `levels` map via a deferred `server.execute` (`CustomServerLevel.java:65-71`), while its handle is in `WorldsManager.worlds` right away (`WorldsManager.java:123-124`).
  - Stopped levels leave `levels` at `END_SERVER_TICK` (`McWorldMod.java:51-75`), and the handle is dropped on `UNLOAD` (`McWorldMod.java:77-81` → `WorldsManager.java:129-153`).
  - A deleted world stays in `WorldsData` until that unload (`WorldsManager.java:134-151`). `isDeleteOnClose()` is at `CustomServerLevel.java:115-117`.
  - The world-spawn world never auto-stops (`ActiveLevelState.java:21-25`, `CustomServerLevel.java:119-121`).
  - States are Init → Activation → Active → Stopping → Stopped (`CustomServerLevel.java:62-63, 111-133`).
- **Worlds that can never load.** `unreadableGenerators` is private (`WorldsManager.java:36, 70-72, 88-94`).
- **Public API.** `API.md:1-77` covers only `ServerLevelProvider` registration. mc-stargate already reads `getWorldsData().getLevelData(id)` directly (`mc-stargate/.../CreateWorldCommand.java:70`).

## Vanilla internals (MC 26.3-rc-1)

- `ListPlayersCommand.java:17` has no `requires`. `:29-35` uses `ComponentUtils.formatList(players, …)`, `sendSuccess(…, false)` and `return players.size()`.
- `DataPackCommand.java`:
  - `:100` puts the root at `LEVEL_GAMEMASTERS`, while `:141` puts `create` at `LEVEL_OWNERS`. That is precedent for a lower root with stricter subcommands.
  - `:134-137` define `list`, `list available` and `list enabled`.
  - `:219-221` return enabled + available.
  - `:230-238` and `:248-256` print `.none` or `.success` with `formatList(…, p -> p.getChatLink(…))`.
- `PackLocationInfo.java:13-20` builds the link: `wrapInSquareBrackets`, GREEN or RED, `withInsertion`, and `HoverEvent.ShowText(title + "\n" + description)`.
- `LocateCommand.java`: `:59` is `LEVEL_GAMEMASTERS`, and `:175-181` builds the GREEN bracketed link with `ClickEvent.SuggestCommand("/tp @s …")` and a hover of `chat.coordinates.tooltip`.
- `ComponentUtils.java`: `:19` defines `DEFAULT_SEPARATOR` (gray `", "`), `:84-100` define `formatList`/`formatAndSortList`, `:112-114` is `formatList(components, separator)`, and `:140-142` is `wrapInSquareBrackets`.
- `Identifier.java:18` is `Comparable`, and `:143-149` compares path first, then namespace.
- `Commands.java:165-169` defines `LEVEL_ALL`, `MODERATORS`, `GAMEMASTERS`, `ADMINS` and `OWNERS`. `:543-545` is `hasPermission`, which returns a `PermissionProviderCheck`, a `Predicate<CommandSourceStack>` (`PermissionProviderCheck.java:5`).
- Source permission levels:
  - Command blocks: `GAMEMASTER` (`CommandBlockEntity.java:50`).
  - Functions: `GAMEMASTER` (`ServerFunctionManager.java:89`), set by `function-permission-level`, default `GAMEMASTER` (`DedicatedServerProperties.java:94-97`).
  - Console: `OWNER` (`MinecraftServer.java:1672`).
  - RCON: `OWNER` (`RconConsoleSource.java:32`).
  - **Consequence:** the whole `/worlds` tree is unavailable to command blocks and functions today.
- `MinecraftServer.java`:
  - `:242` `levels` is a `LinkedHashMap`.
  - `:1175-1177` `getLevel` is a plain map get, which the mod's mixin wraps.
  - `:1179-1181` `levelKeys()` is a *live* `keySet()` view.
  - `:1239-1242` console `sendSystemMessage` logs `message.getString()`, so console output has no hover or click.
  - `:1369-1371` is `getPlayerList`.
  - `:1676-1681` `findRespawnDimension()` calls `getLevel`. **Don't use it.**
  - `:1693-1695` `getRespawnData()` is a side-effect-free field read.
- `PlayerList.java:818` is `getPlayers()`. `ServerPlayer.java:1771` is `level()`.
- `DimensionArgument.java:36-40` suggests only `levelKeys()`. `:51-60` `getDimension` calls `getLevel`, which **lazy-loads**, so `/worlds info <id>` must never use `DimensionArgument`.
- `CommandSourceStack.java:474-487` `sendSuccess` respects `acceptsSuccess` and silent sources, which covers command block `commandBlockOutput`.
- Client side (`26.3-rc-1` clientOnly sources):
  - `Screen.java:266-277,374-376`: `RunCommand` → `sendUnattendedCommand`.
  - `ClientPacketListener.java:2586-2600` re-parses with `restrictedSuggestionsProvider` (`PermissionSet.NO_PERMISSIONS`, `:469`). Any op-gated command → `PERMISSIONS_REQUIRED` confirmation screen (`:2581-2582`).
  - `SuggestCommand` just inserts the text (`Screen.java:313-325`).
- Vanilla `en_us.json`: `:2589` `"chat.coordinates.tooltip": "Click to teleport"`, `:2612` `"chat.square_brackets": "[%s]"`, `:2783-2786` `commands.datapack.list.*` (`"There are %s data pack(s) enabled: %s"`, `"There are no data packs enabled"`).
- `CompressionDecoder.java:14` sets an 8 MiB uncompressed packet cap, which is irrelevant for realistic world counts.

## Proposed design

### Command tree

```
/worlds list                → loaded line + unloaded line, returns loaded + unloaded
/worlds list loaded         → loaded line only,           returns loaded count
/worlds list unloaded       → unloaded line only,         returns unloaded count
```

Registered as `.then(ListCommand.build())` in `WorldsCommands.register`. `list` carries `requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`, and the root `requires` at `WorldsCommands.java:65` drops to `LEVEL_GAMEMASTERS`. Keep `:73` in sync; it is a no-op merge. If the maintainer keeps admin-only, both stay `LEVEL_ADMINS` and nothing else changes. No arguments, so nothing to resolve and no `getLevel`.

### Example output

Chat, with a player at op level 4:

```
There are 4 loaded world(s): [minecraft:creative], [minecraft:overworld], [minecraft:the_end], [minecraft:the_nether]
There are 2 unloaded world(s): [minecraft:mining], [minecraft:skyblock]
```

Hover on `[minecraft:mining]`:

```
Dimension type: minecraft:overworld
Provider: mc-worlds:default
Load on startup: false
No players
Click to teleport
```

Hover on `[minecraft:overworld]`:

```
Dimension type: minecraft:overworld
Holds the world spawn
Players (2): Alice, Bob
Click to teleport
```

Console or RCON get the same two lines as plain text. On a server without lazy worlds, the second line is `There are no unloaded worlds`.

### Data access (no side effects)

Build one snapshot per invocation, on the server thread:

1. `levels = ((MinecraftServerAccessor) server).getLevels()`. Read it and don't hold on to it, the same as `DimensionDetectionUtil.java:52`.
2. `saved = manager.getWorldsData().getLevelsData()`, which is already a copy.
3. `ids = saved.keySet() ∪ levels.keySet().identifier()`. This is the `WORLD_SUGGESTIONS` set.
4. For each id:
   - `handle = manager.getWorld(id)` and `level = levels.get(key)`, falling back to `handle.getServerLevel()`. The fallback covers the deferred put at `CustomServerLevel.java:65-71`.
   - Skip the id if `level instanceof CustomServerLevel c && c.isDeleteOnClose()`.
   - `loaded = level != null`.
   - Dimension type is `level.dimensionTypeRegistration()` when loaded, otherwise `saved.get(id).getLevelStem().type()` when the stem is non-null. Display `unwrapKey()`, and omit the line when it is unknown.
   - Provider and load-on-startup come from `saved.get(id)`. Show them only when the id is an mc-worlds world.
   - Players: one pass over `server.getPlayerList().getPlayers()`, grouped by `player.level().dimension()`.
   - World spawn: `server.getRespawnData().dimension()` (`MinecraftServer.java:1693`), read once.

Never call `server.getLevel`, `findRespawnDimension`, `DimensionArgument.getDimension`, `McWorld.loadOrCreate` or `WorldsManager.loadOrCreateWorld`.

### Entry component

```java
ComponentUtils.wrapInSquareBrackets(Component.literal(id.toString()))
    .withStyle(s -> s.withColor(loaded ? ChatFormatting.GREEN : ChatFormatting.RED)
        .withInsertion(id.toString())
        .withHoverEvent(new HoverEvent.ShowText(details))
        .withClickEvent(canTeleport ? new ClickEvent.SuggestCommand("/worlds tp " + id) : null));
```

- `details = ComponentUtils.formatList(lines, Component.literal("\n"))`, using the `"\n"` join from `PackLocationInfo.java:18`.
- Player names use `ComponentUtils.formatList(players, Player::getName)`.
- `canTeleport = Commands.hasPermission(Commands.LEVEL_ADMINS).test(source)`, so a level-2 viewer doesn't get a click that would only fail. The trailing "Click to teleport" line uses the same condition.
- Entries are sorted by `Identifier` before `ComponentUtils.formatList(sorted, formatter)`.

### i18n keys (`src/main/resources/assets/mc-worlds/lang/en_us.json`)

| Key | en_us |
|---|---|
| `dev.wroud.mc.worlds.command.list.loaded.success` | `There are %s loaded world(s): %s` |
| `dev.wroud.mc.worlds.command.list.loaded.none` | `There are no loaded worlds` (unreachable while vanilla dimensions are listed; add only if scope becomes "mc-worlds worlds only") |
| `dev.wroud.mc.worlds.command.list.unloaded.success` | `There are %s unloaded world(s): %s` |
| `dev.wroud.mc.worlds.command.list.unloaded.none` | `There are no unloaded worlds` |
| `dev.wroud.mc.worlds.command.list.dimension_type` | `Dimension type: %s` |
| `dev.wroud.mc.worlds.command.list.provider` | `Provider: %s` |
| `dev.wroud.mc.worlds.command.list.load_on_startup` | `Load on startup: %s` (arg is raw `true`/`false`, matching `/worlds settings loadOnStartup` output at `SettingsCommand.java:68`) |
| `dev.wroud.mc.worlds.command.list.world_spawn` | `Holds the world spawn` |
| `dev.wroud.mc.worlds.command.list.players` | `Players (%s): %s` |
| `dev.wroud.mc.worlds.command.list.players.none` | `No players` |
| vanilla `chat.coordinates.tooltip` (reused) | `Click to teleport`. Vanilla keys are already reused at `CreateCommand.java:34` |

Ids are passed through `Component.translationArg(Identifier)` (`Component.java:216`), as `/forceload` does.

### Datagen

None. The datagen pack has no language provider (`DataGenerator.java:14`), and translations stay hand-written. Introducing a `FabricLanguageProvider` only for these keys would split one file across two mechanisms.

### Public API

None. The command uses only existing public methods (`getWorldsData`, `getWorld`). mc-stargate already reads `WorldsData` directly. Nothing needs adding to `API.md`.

### Files to add/change

- **Add** `src/main/java/dev/wroud/mc/worlds/command/ListCommand.java`, about 100 lines.
- **Change** `src/main/java/dev/wroud/mc/worlds/command/WorldsCommands.java`: add `.then(ListCommand.build())`. If `GAMEMASTERS` is chosen, also change the root `requires` at `:65` and `:73`.
- **Change** `src/main/resources/assets/mc-worlds/lang/en_us.json`: add the keys above.
- **Change** `README.md`: add a `### List Command` section under `## Commands`, after Teleport (`README.md:52`).
- **Change** `CHANGELOG.md`: add an `### Added` entry under `## Unreleased`.

## Edge cases & risks

- **Lazy-load regression.** This is the only serious risk. Any future refactor that swaps the map read for `getLevel` would load every lazy world, and calling it repeatedly would recreate the 60 s load/unload loop. The test plan checks the log for this explicitly.
- **Transient states.**
  - Init and Activation (a new world preparing spawn) and Stopping all count as loaded.
  - A Stopped level is removed in the same tick (`McWorldMod.java:51-75`), so commands never see it.
  - A world mid-creation has a handle before its deferred `levels` put, which is why the handle is used as a fallback.
- **World being deleted.** It stays in `WorldsData` and `levels` until unload. Skip it via `isDeleteOnClose()`, or it would appear as a teleport target that `canTeleport()` rejects (`CustomServerLevel.java:107-109`).
- **Unknown dimension type.** Unloaded worlds with a `null` stem, such as all mc-stargate worlds, have no dimension-type line. The provider line identifies them instead.
- **Unreadable generator.** Such a world shows as unloaded, but `/worlds tp` reports "Unknown world id!", because `loadOrCreate` returns `null` (`MinecraftServerMixin.java:58-63`, `TeleportCommand.java:47-50`). Marking it would need a new getter on the private `unreadableGenerators`. Out of scope for v1.
- **Volume.** A server with many mc-stargate addresses gets a long single line. Vanilla list commands never paginate, and `list loaded` is the mitigation. Payload size is far below the 8 MiB cap.
- **Console loses hover detail.** This is inherent to `getString()` (`MinecraftServer.java:1239-1242`), the same as `/datapack list`. The loaded/unloaded split is in the text, so the key state survives.
- **Permission change blast radius.** Lowering the root changes only what level-2 clients see in their command tree (`/worlds list`). Every other subcommand keeps `LEVEL_ADMINS`. Exposing which world a player is in at level 2 adds nothing new, because level 2 already has `/tp`, `@a[…]` selectors and `/execute`. Don't go below level 2: vanilla never shows other players' dimensions to non-ops.
- **`levelKeys()` is a live view** (`MinecraftServer.java:1179-1181`). Iterate over a copy, the same reason `getAllLevels` was overwritten (`MinecraftServerMixin.java:28-36`).
- **Namespace default.** `/worlds create foo` makes `minecraft:foo`, because `IdentifierArgument` defaults the namespace. Path-first sorting keeps user worlds alphabetical among the vanilla dimensions.

## Open questions for the maintainer

1. **Permission.** Should the `/worlds` root drop to `LEVEL_GAMEMASTERS` so `list` matches vanilla read-only lists and works from command blocks and functions? This is the `/datapack` pattern (`DataPackCommand.java:100,141`). Or does the whole tree stay `LEVEL_ADMINS`, with no command-block support?
2. **Scope.** Should the list cover every dimension `/worlds tp` accepts (recommended: complete picture, player counts add up to the online total), or only mc-worlds-managed worlds?
3. **Console detail.** Is hover-only detail enough, or is `/worlds info <id>` wanted for console and RCON? If it is wanted, it takes `IdentifierArgument` + `WORLD_SUGGESTIONS`, never `DimensionArgument`, and prints the same lines as the hover.
4. **Minor points:**
   - RED for unloaded follows the `/datapack` precedent; Multiverse uses gray.
   - Keep the `loaded`/`unloaded` literals, or ship bare `list` only?
   - Should `CreateCommand`'s `RunCommand` teleport link (`CreateCommand.java:160`) later move to `SuggestCommand` to avoid the client confirmation screen?

## Manual test plan

Run `./gradlew runServer` and connect `./gradlew runClient`. The `mc-server-probe` skill can script the console part.

1. **Fresh world, from the console.** Run `worlds list`. Expect `There are 3 loaded world(s): [minecraft:overworld], [minecraft:the_end], [minecraft:the_nether]` and then `There are no unloaded worlds`.
2. **Return value.**
   - Run `scoreboard objectives add n dummy`.
   - Run `execute store result score #w n run worlds list`.
   - Run `scoreboard players get #w n`. Expect 3.
   - Repeat with `list loaded` and `list unloaded`.
3. **Lazy world.**
   - Run `worlds create mining`. It is loaded right away, and `list` shows it under loaded.
   - Leave it empty for more than 60 s and wait for the unload in the log. It then shows under unloaded.
   - Run `worlds list` every ~5 s for 3 minutes.
   - Expect `logs/latest.log` to contain **no** new `Loading world: minecraft:mining` lines, and no recurring load/unload pairs.
4. **Hover and click, in the client.**
   - Hovering `[minecraft:mining]` shows dimension type, provider `mc-worlds:default`, `Load on startup: false`, `No players` and `Click to teleport`.
   - Clicking it fills the chat box with `/worlds tp minecraft:mining`, with no confirmation screen.
   - Pressing Enter loads the world and teleports.
   - Shift-clicking inserts the id.
5. **Players and settings.**
   - While you are inside `mining`, its hover shows `Players (1): <you>`, and the overworld no longer lists you.
   - `worlds settings loadOnStartup true` is reflected in the hover.
6. **World spawn.** Run `execute in minecraft:mining run setworldspawn`. The hover shows "Holds the world spawn", and the world stays under loaded while empty.
7. **Unknown stem.** Create a world from `from-preset minecraft:flat` and let it unload. Its hover still shows a dimension type, from the stored stem. With mc-stargate installed, a dialed and then unloaded address shows its provider and no dimension-type line.
8. **Delete.**
   - Run `worlds delete minecraft:mining`. It disappears from both lines.
   - While it is still stopping, it is not listed.
9. **Permission.**
   - Put `worlds list` in a command block. With `GAMEMASTERS` its output appears; with `LEVEL_ADMINS` you get "Unknown or incomplete command".
   - With `op-permission-level=2` in `run/server.properties`, a re-opped player sees `/worlds list` but no other `/worlds` subcommands, and the entries have no click.
10. **Sorting.** Create `zeta` and `alpha` and run `list`. They appear in path order among the vanilla dimensions.

## Sources

- Multiverse (Fabric) #4 "list command": https://github.com/senseiwells/Multiverse/issues/4
- Multiverse (Fabric) #3 "console support": https://github.com/senseiwells/Multiverse/issues/3
- Multiverse-Core #1960 "Order mv list output alphabetically" (4 👍 via GitHub API): https://github.com/Multiverse/Multiverse-Core/issues/1960
- Multiverse-Core 5 `ListCommand`: https://github.com/Multiverse/Multiverse-Core/blob/396ece4d274a468d0b87c0060422cdbff92601af/src/main/java/org/mvplugins/multiverse/core/commands/ListCommand.java
- Multiverse-Core command sources (`InfoCommand`, `WhoCommand`): https://github.com/Multiverse/Multiverse-Core/tree/main/src/main/java/org/mvplugins/multiverse/core/commands
- Multiverse-Core 5 changelog (`/mv list` paging, filter, `--raw`): https://mvplugins.org/mv5/whats-new/multiverse-core
- Multiworld (`/mw list`, `multiworld.cmd`): https://modrinth.com/mod/multiworld
