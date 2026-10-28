# Worlds

Run as many worlds as you want on one Fabric server, the way Multiverse does on Paper. Create, list, delete and teleport between worlds with in-game commands. Every world plays like real vanilla Minecraft, and Worlds is also a base other mods build their own dimensions on.

## Features

- **Any kind of world.** Overworld, Nether, End, flat, amplified, large biomes, or any world preset and dimension type from a data pack, with your own seed or a random one.
- **Vanilla mechanics that just work.** The End dragon fight, portals, maps, ender pearls and other vanilla mechanics work in every world you create, not only in the original three.
- **Each world keeps its own weather and time.** Vanilla `/weather` and `/time` run inside a world change only that world.
- **Each world can have its own game rules.** Keep inventory, PvP, mob spawning, daylight and more, set with vanilla `/gamerule` inside the world. Rules you don't change follow the server.
- **Import existing worlds.** Bring in a singleplayer save, a world from another server, or a Paper or Multiverse server's worlds with `/worlds import`.
- **Lightweight.** Worlds load when a player enters them and unload a minute after the last player leaves, so you can keep many worlds without paying for idle ones.
- **Close to vanilla.** No new commands to learn for weather, time or game rules, and the Overworld, Nether and End behave exactly as in vanilla.
- **A base for other mods.** Mods register their own world types through the Worlds API. [Stargate](https://modrinth.com/mod/mc-stargate) is built on it.

## Commands

| Command | What it does |
|---|---|
| `/worlds create <id> [seed]` | Creates an overworld |
| `/worlds create <id> from-preset <preset> [dimension] [seed]` | Creates a world from a world preset, for example `minecraft:flat` or `minecraft:amplified` |
| `/worlds create <id> from-dimension <type> [seed]` | Creates a world of a dimension type, for example `minecraft:the_nether` or `minecraft:the_end` |
| `/worlds import <id> <folder> [<dimension>\|all]` | Imports a world save, a zip placed next to the server world or a zip URL, see [Import command](#import-command) |
| `/worlds tp <id> [players]` | Teleports you or other players to a world's spawn |
| `/worlds list [loaded\|unloaded]` | Lists worlds; hover for details, click to teleport |
| `/worlds delete <id>` | Deletes a world after moving everyone out of it |
| `/worlds settings spawn [here\|<x> <y> <z>]` | Shows or sets the spawn of the world you are in |
| `/worlds settings loadOnStartup [true\|false]` | Shows or sets whether the world you are in loads when the server starts |
| `/gamerule <rule> <value>` | In a created world, sets a rule for that world only |
| `/gamerule` | In a created world, lists the rules the world changed |
| `/gamerule <rule> inherit` | In a created world, makes a rule follow the server again |

A world id without a namespace gets `minecraft:`, so `/worlds create mining` creates `minecraft:mining`. Run any command for another world with `/execute in <world> run ...`. `/worlds` commands need operator level 3; `/gamerule` needs level 2, as in vanilla.

## Examples

**A resource world you can reset**
```
/worlds create mining
/worlds tp mining
/worlds delete mining
/worlds create mining
```

**A creative build world with no mobs and endless daylight**
```
/worlds create build from-preset minecraft:flat
/worlds tp build
/gamerule spawn_mobs false
/gamerule advance_time false
/gamerule advance_weather false
```

**A safe lobby where new players spawn**
```
/worlds create lobby
/worlds tp lobby
/setworldspawn
/gamerule pvp false
/gamerule spawn_monsters false
```

**A fresh End with its own dragon fight**
```
/worlds create event_end from-dimension minecraft:the_end
/worlds tp event_end @a
```

**Keep inventory only in an arena**
```
/execute in minecraft:arena run gamerule keep_inventory true
```

**Moving from World Manager:** replace World Manager with Worlds, then run `/worlds create <id> from-dimension <type> <seed>` with the id, type and seed each world had; Worlds picks up the world's existing folder. Classic Overworld, Nether and End worlds are supported. A world kept as its own save folder, such as a singleplayer world or a Paper or Multiverse server's world, is brought in with [`/worlds import`](#import-command) instead.

## Import command

`/worlds import <id> <folder> [<dimension>|all]` copies an existing save into the server as a new world.

**Where the save goes.** Put the save folder next to the server world, in the folder that holds `world/`, and give its name as `<folder>` (in quotes if it has spaces). A folder that only wraps the save in one more folder works too. The save folder is never changed; the import works on a copy, and a server running the import stays playable.

**Zip files and links.** `<folder>` can also be a `.zip` next to the server world, or a direct download link to a zip in quotes. A zip is read in place and only the imported dimensions are unpacked. A link is downloaded into the server world's `mc-worlds/staging/` folder first and deleted when the import ends. Other archives (`.tar.gz`, `.rar`, `.7z`) have to be extracted first. Share pages from Google Drive, MediaFire or OneDrive are web pages, not files, and are refused; use a direct link. Chat input is limited to 256 characters, so run long links from the server console.

**Which dimensions.**

- Without a dimension: the save's Overworld, or its only dimension with chunks.
- `<dimension>`: one dimension, for example `minecraft:the_nether`, `minecraft:the_end` or any other dimension in the save.
- `all`: every dimension with chunks, as `<id>` (Overworld), `<id>_nether`, `<id>_the_end` and `<id>_<path>` for other dimensions (`<id>_<namespace>_<path>` outside `minecraft`).
- Dimensions without chunks are not imported. Naming one is refused; `all` skips them and lists them.

**Detected automatically.**

- The save's layout and version. Saves from older Minecraft versions, including the old `region/`, `DIM-1/`, `DIM1/` layout, are upgraded the same way the server upgrades its own world at startup, and their chunks upgrade when they first load. Saves from a newer Minecraft version are refused.
- Paper and Multiverse saves: the 26.1+ layout, where every world is a dimension inside one save, and the older one with a folder per world (`world_nether`, `world_the_end` and so on).
- Saves from another server running Worlds.
- The generator and seed, so new chunks continue the original terrain. A vanilla dimension whose generator this server cannot read uses the server's own generator for that dimension, with a warning.
- The spawn point.
- Game rules that differ from the server's, kept as the new world's own rules (`/gamerule <rule> inherit` makes one follow the server again).
- Weather and time of day.
- Filled maps, under new map ids, so they do not replace the server's own maps.

**Not imported:** player data and inventories, advancements, stats, the scoreboard, difficulty and datapacks. These are server-wide in Minecraft.

**Datapack dimensions.** If the server has the same datapack installed, a datapack dimension is imported with the server's generator for it. Otherwise the import is refused before anything is copied; install the same datapack on the server first.

**Portals.** Nether and End portals in imported worlds lead to the server's own Nether and End.

One import runs at a time. Progress is written to the server log.

**Examples**
```
/worlds import old_survival "My Old World"
/worlds import old_survival_nether "My Old World" minecraft:the_nether
/worlds import hub paperworld all
/worlds import adventure "Adventure Map.zip"
/worlds import adventure "https://example.com/maps/adventure.zip"
```

## For mod developers

Worlds provides an API for registering your own server level providers, so your mod can add worlds with their own generation, rules or mechanics while Worlds handles creating, loading, saving and teleporting. See [API.md](API.md) for the guide and the Gradle setup.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.18.4+ and Fabric API
- Java 25

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT. See [LICENSE](LICENSE).
