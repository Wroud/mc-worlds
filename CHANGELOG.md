# Changelog

## Unreleased

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## 2.0.1 - 2026-11-11

### Fixed

- Loot in created worlds follows each world's own seed instead of one roll shared by the whole server.

## 2.0.0 - 2026-11-03

### Added

- World groups. Create a group with `/worlds group create <group>`, then run `/worlds group join <group>` in each world that belongs to it. The worlds of a group act as one vanilla world: a nether portal leads to the group's own Nether-like world and back, an end portal leads to the group's End-like world, leaving the End or dying without a bed brings you back to the group's spawn, and the compass points at it. A bed in another group does not count there. Worlds you never put in a group behave exactly as before.
- Each group has its own spawn, set with `/worlds group spawn here` or a position, separate from the server spawn of `/setworldspawn`. `/worlds group` shows the group of the world you are in, and `/worlds group list` shows all of them.
- The worlds of a group share one set of game rules: a rule set in any of them, with `/gamerule` or the Game Rules screen, holds in all of them. A world's own overrides pause while it is in a group and return when it leaves.
- Two game rules for groups, `mc-worlds:default_game_mode` and `mc-worlds:force_game_mode`, give a group a default or a forced game mode, like the `gamemode` and `force-gamemode` server properties do for the whole server.
- `/worlds import … group <group>` puts imported worlds straight into a group, so an imported Overworld, Nether and End pair their portals. `/worlds list` shows each world's group.
- Each group keeps its own copy of every player: inventory, ender chest, experience, health, effects, game mode, respawn point, statistics, advancements and the data other mods store on the player. Moving into a world of another group, by teleport, respawn or another mod's portal, swaps to that group's copy and leaves the one you came from behind, untouched, until you return. A first visit to a group starts you fresh. Boats, minecarts, thrown ender pearls and mobs stay in their group. The copies are ordinary player files under `players/groups/` in the save.
- Deleting a group also deletes the player data kept for it.
- Temporary worlds and groups for minigames and events. `/worlds create temporary <name>`, `/worlds import temporary <name>` and `/worlds group create temporary <name>` make one with a generated id, and `/worlds list` marks the temporary worlds. A temporary group holds only temporary worlds and deleting it deletes them with the player data kept for it. Nothing temporary survives a restart.
- Entering a temporary group remembers where you came from. `/worlds group temp leave <players>` sends players back there with everything they had, and so does deleting the group while they are inside, on the death screen or offline. Nothing from inside comes home with them.
- Data pack functions can create, enter and delete all of this. Generated ids are in the `mc-worlds:temporary` command storage, ready for function macros.
- Other mods can now do everything the commands do: create, import and delete worlds, manage groups and run temporary worlds for minigames. They can also react when a player moves between groups or a group is deleted. The [API guide](https://github.com/Wroud/mc-worlds/blob/main/API.md) has the details.

### Changed

- Every world command now needs operator level 2 instead of 3, so data pack functions and command blocks can use them. Level 2 operators and command blocks can now delete any world or group and make the server download a world.
- The word `temporary` is now part of `/worlds create`, `/worlds import` and `/worlds group create`. A world or group named `temporary` must be written `minecraft:temporary`, and `/worlds create temporary 123` no longer means a world named `temporary` with seed 123. The `mc-worlds-temp` namespace is reserved.
- `allow_entering_nether_using_portals` applies to every Nether-like world a portal leads to, not only the vanilla Nether.
- The API for mods changed, so mods built on an older version of Worlds need an update. That includes Stargate.

### Fixed

- Nether portals in the vanilla Overworld respect `allow_entering_nether_using_portals` again.
- Entering a created world no longer crashes the server when the full Cardinal Components library is installed.
- `/worlds create` now tells you when the world is deleted before it is ready, or can't load.

## 1.11.0 - 2026-10-29

### Added

- Bring existing worlds onto your server with `/worlds import`: a singleplayer world, a world from another server, the worlds of a Paper server with Multiverse, or the worlds of another server running Worlds. Put the save next to the server world, or give a zip file or a direct download link to one. Worlds from older Minecraft versions are upgraded the same way the server upgrades its own world.
- An imported world keeps its terrain generator and seed, spawn, weather, time of day, filled maps and the game rules that differ from the server's. You can import just the Overworld, one dimension, or the Overworld, Nether and End together. The original save is never changed.

### Fixed

- Old chunks in created Overworld-like worlds now blend with newly generated terrain after a Minecraft update, and chunks from before the caves and cliffs update get the new deep terrain instead of air. Chunks that were already upgraded stay as they are.
- `/worlds create` no longer hangs on "please wait" when the id belongs to a saved world that failed to load.

## 1.10.0 - 2026-10-07

### Added

- Each created world can now have its own game rules. Change a rule with `/gamerule` inside the world, and every rule you leave alone keeps following the server. In a created world, `/gamerule` on its own lists the rules the world changed, and `/gamerule <rule> inherit` makes a rule follow the server again.
- Keep inventory follows the world where you died, and the death screen, reduced debug info, time and weather follow the world you are in.
- With Worlds installed on your game, the Game Rules screen opened in a created world shows in each rule's tooltip whether the world set it, uses the server's value, or is server-wide.

### Changed

- In a created world, `/gamerule` now changes the rule only for that world, and its message names the world. The Overworld, Nether and End keep sharing the server's rules, and the rules for command feedback, admin command logging and command limits stay server-wide.

### Fixed

- Fixed a rare crash, "Exception generating new chunk", when several worlds generate villages or other structures at the same time, for example right after creating new worlds or starting the server.

## 1.9.0 - 2026-10-07

### Added

- A new `/worlds list` command shows every world on the server, loaded worlds in green and unloaded ones in gray. Hover over a world to see its type, whether it loads on startup, whether it holds the world spawn and who is in it, and click it to get its teleport command ready in chat. Listing never loads an unloaded world.

## 1.8.27 - 2026-10-07

### Fixed

- Teleporting to the Overworld while the world spawn is in another world now lands at the Overworld's own spawn, inside its world border, instead of at the other world's coordinates.

## 1.8.26 - 2026-10-07

### Fixed

- A world that holds the world spawn (set with `/execute in <world> run setworldspawn`) no longer unloads and reloads every minute while it is empty. It now stays loaded.
- Deleting the world that holds the world spawn moves the spawn back to the Overworld, to the spot the game originally picked, instead of leaving it pointing at a world that no longer exists.
- Players in a world that is being deleted are sent to the world spawn, even when it is in another extra world. Before, they landed in the Overworld at that world's coordinates.
- Deleting a world while it is unloading now deletes it, instead of reporting success and keeping it.
- Map markers spin only on maps of Nether-like worlds, as in the vanilla Nether. Before, they spun on Overworld and End maps instead.
- Maps and dimension-change advancements no longer load an unloaded extra world just to check what kind of world it is.

## 1.8.25 - 2026-10-06

### Fixed

- Players who were offline when their world was deleted now return at world spawn instead of their old coordinates in the Overworld.

## 1.8.24 - 2026-10-06

### Fixed

- The sky no longer flickers to the Overworld's time in worlds that keep their own time.
- Works with Worldthreader: opening or creating a world no longer freezes the server, and worlds that keep their own time tick in parallel again.
- Scheduled functions no longer run a second time on an extra world's clock.

## 1.8.23 - 2026-10-05

### Fixed

- With Iris shader packs such as Complementary, worlds with a Nether-like dimension type now use the pack's Nether shaders. Before, they got its Overworld shaders, with a sky, clouds and daylight. This covers any extra world that uses the vanilla Nether dimension type and Nether-like dimensions from other mods, such as Stargate's Melted.

## 1.8.22 - 2026-09-15

### Added

- Support for Minecraft 26.3 (Fabric API 0.160.5+26.3).

## 1.8.21 - 2026-09-14

### Added

- Support for Minecraft 26.3 Release Candidate 3 (Fabric API 0.160.4+26.3).

## 1.8.20 - 2026-09-11

### Added

- Support for Minecraft 26.3 Release Candidate 2 (Fabric API 0.160.3+26.3).

## 1.8.19 - 2026-09-11

### Added

- Support for Minecraft 26.3 Release Candidate 1 (Fabric API 0.160.3+26.3).

## 1.8.18 - 2026-09-09

### Added

- Support for Minecraft 26.3 Pre-Release 3 (Fabric API 0.160.1+26.3).
- `ServerLevelProvider.createLevelStem`, a recovery hook called only when a world's stored generator could not be read. A provider whose generation is determined by the world id and seed can rebuild one there instead of the world being skipped. `mc-worlds:default` does not implement it.

### Fixed

- A world whose stored generator cannot be decoded no longer loses it. The undecodable `level_stem` is kept as-is and written back unchanged, so the world recovers once whatever supplied that generator is present again. Such a world is skipped with an error in the log if its provider cannot rebuild a generator, instead of failing server startup and `MinecraftServer.getLevel`.

## 1.8.17 - 2026-09-08

### Added

- Support for Minecraft 26.3 Pre-Release 2

### Fixed

- Weather no longer occurs in End-like custom dimensions
- The Nether travel advancement now triggers when leaving Nether-like custom dimensions

## 1.8.16 - 2026-06-17

### Added

- Support for Minecraft 26.2

## 1.8.15 - 2026-06-15

### Added

- Support for Minecraft 26.2 Release Candidate 2

## 1.8.14 - 2026-06-05

### Added

- Support for Minecraft 26.2 Pre-Release 4

## 1.8.13 - 2026-06-02

### Added

- Support for Minecraft 26.2 Pre-Release 3

## 1.8.12 - 2026-05-29

### Added

- Support for Minecraft 26.2 Pre-Release 2

## 1.8.11 - 2026-05-27

### Added

- Support for Minecraft 26.2 Pre-Release 1

## 1.8.10 - 2026-04-11

### Added

- Support for Minecraft 26.1.2

## 1.8.9 - 2026-04-02

### Added

- Support for Minecraft 26.1.1

## 1.8.8 - 2026-03-25

### Added

- Support for Minecraft 26.1

## 1.8.7 - 2026-03-24

### Added

- Support for Minecraft 26.1 Release candidate 3

## 1.8.6 - 2026-03-21

### Added

- Support for Minecraft 26.1 Release candidate 2

## 1.8.5 - 2026-03-20

### Added

- Support for Minecraft 26.1 Release candidate 1

## 1.8.4 - 2026-03-20

### Fixed

- Rain in custom worlds no longer stops when a player enters or when the weather changes in another world.
- Setting time speed (`/time rate`) in a custom world now correctly applies only to that world and no longer reverts.

## 1.8.3 - 2026-03-18

### Added

- Support for Minecraft 26.1 Pre-Release 3

## 1.8.2 - 2026-03-17

### Added

- Support for Minecraft 26.1 Pre-Release 2

## 1.8.1 - 2026-03-11

### Added

- Support for Minecraft 26.1 Pre-Release 1

## 1.8.0 - 2025-12-12

### Added

- Support for Minecraft 1.21.11

### Changed

- Some mixins updated and fixed

## 1.7.4 - 2025-10-29

### Added

- `/worlds settings` command to configure world loading behavior and spawn point for the current world

## 1.7.3 - 2025-10-29

### Fixed

- respect world spawn when teleporting with `/worlds tp` command

## 1.7.2 - 2025-10-25

### Added

- Compatibility with [worldthreader](https://github.com/2No2Name/worldthreader)

## 1.7.1 - 2025-10-22

### Changed

- Hide Nether and The End dimensions in the preset suggestion
- Prepare level spawn on the same thread

## 1.7.0 - 2025-10-14

### Added

- Creating worlds from presets (you can create a flat world from a normal world and vice versa now)
- By default `/worlds create name` will create a normal overworld

## 1.6.13 - 2025-10-13

### Changed

- Better stability in unexpected cases

## 1.6.12 - 2025-10-13

### Fixed

- Null pointer exception when accessing server levels before overworld initialization [#3](https://github.com/Wroud/mc-worlds/issues/3)

## 1.6.11 - 2025-10-11

### Fixed

- Include datagen in jar

## 1.6.10 - 2025-10-11

### Changed

- Consistent logging

## 1.6.9 - 2025-10-10

### Fixed

- Watchdog killing server if level initialization is too long

## 1.6.8 - 2025-10-09

### Changed

- Update fabric version

## 1.6.7 - 2025-10-08

### Fixed

- Prevent world unloading during save

## 1.6.6 - 2025-10-07

### Added

- Support for 1.21.10

## 1.6.5 - 2025-10-03

### Added

- Support for 1.21.10-rc1

## 1.6.4 - 2025-10-03

### Fixed

- Worlds unloading in sprinting mode
- Concurrency exception

## 1.6.3 - 2025-10-02

### Fixed

- Bee, End Portal, Falling block, Frosted Ice block fixes

## 1.6.2 - 2025-10-02

### Fixed

- Level migration from 1.21.8

## 1.6.1 - 2025-09-30

### Added

- Support for 1.21.9

## 1.6.0 - 2025-09-30

### Added

- Non-blocking spawn location detection for overworld-like worlds to prevent server thread blocking

## 1.5.0 - 2025-09-29

### Added

- Prevent players and entities from teleporting to unloading worlds
- Automatically unload worlds after 1 minute of being empty

### Fixed

- World deletion [#1](https://github.com/Wroud/mc-worlds/issues/1)

## 1.4.3 - 2025-09-26

### Added

- List Quilt as supported loader (not tested)
- Support for 1.21.9-rc1

## 1.4.2 - 2025-09-24

### Added

- Support for 1.21.9-pre4

## 1.4.1 - 2025-09-23

### Added

- Support for 1.21.9-pre3

## 1.4.0 - 2025-09-21

### Added

- Support for 1.21.9-pre2

## 1.3.1 - 2025-09-18

### Fixed

- Display unloaded worlds in the teleport comand list

## 1.3.0 - 2025-09-17

### Added

- Level provider API
- Worlds lazy-loaded on demand by default
- Dimensions type tags for custom dimension types

### Changed

- Worlds activated in tick instead of request

## 1.2.0 - 2025-09-05

### Fixed

- Custom spawners in overworld like worlds (cats, phantoms, patrol, wandering trader)
- Server crash during worlds deletion
- Localization

## 1.1.0 - 2025-09-04

### Added

- Support for Nether and End dimensions creation
- Proper Overworld initialization and spawn setting

### Fixed

- End world teleportation

## 1.0.1 - 2025-09-04

### Added

- Proper dimension deletion

## 1.0.0 - 2025-09-03

### Added

- Initial plugin version with simple overworld manadgement
