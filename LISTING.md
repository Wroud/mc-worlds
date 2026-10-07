---
name: "Worlds: Multiverse for Fabric"
summary: "Run unlimited worlds on one Fabric server, like Multiverse on Paper, World Manager or Multiworld: create, list and teleport with commands. Every world keeps its own time, weather and game rules, and the End dragon, portals and maps work in all of them."
icon: src/main/resources/assets/mc-worlds/icon.png
curseforge_slugs:
  mc-stargate: mc-stargate
---

<center>
<a href="https://pixly.gg/?utm_source=modrinth&utm_medium=referral&utm_campaign=mc-worlds&utm_content=mods-banner"><img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/banner.png" alt="Worlds: Multiverse for Fabric servers. Create, list and teleport between unlimited worlds, each one real vanilla." width="100%"><img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/promo-pixly.png" alt="Pixly hosting: no subscription, crossplay in one switch, mods, modpacks, from 8¢ per hour. Try it free." width="100%"></a>
</center>

<center>
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/badge-performance.png" alt="Performance: worlds load on demand, nothing idles" height="44">
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/badge-vanilla.png" alt="Vanilla-like: vanilla commands, vanilla mechanics" height="44">
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/badge-latest.png" alt="Latest Minecraft: updated within a week of each release" height="44">
</center>

<center>
<img src="https://img.shields.io/modrinth/v/rDdf0tz6?style=for-the-badge&label=Latest&color=2E8BC0&labelColor=143C5C" alt="Latest version, with the Minecraft version it runs on">
<img src="https://img.shields.io/modrinth/dt/rDdf0tz6?style=for-the-badge&label=Downloads&color=2E8BC0&labelColor=143C5C" alt="Download count">
<img src="https://img.shields.io/badge/Loader-Fabric-2E8BC0?style=for-the-badge&labelColor=143C5C" alt="Fabric loader">
<img src="https://img.shields.io/badge/Side-Server-2E8BC0?style=for-the-badge&labelColor=143C5C" alt="Server side, optional on the client">
</center>

**Worlds** lets a Fabric server run as many worlds as it wants, the way Multiverse does on Paper. Create, list, delete and teleport between worlds with in-game commands, and give each one its own time, weather and game rules. Every world plays like real vanilla Minecraft, down to the End dragon, which also makes Worlds a base other mods build their dimensions on. Coming from World Manager or Multiworld? Your worlds move over with one command each.

<center>
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/ribbon-create.png" alt="One command, any world" width="100%">
</center>

<center>
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/create-end.webp" alt="Typing /worlds create event_end from-dimension minecraft:the_end, then /worlds tp event_end, and arriving in a new End under the circling Ender Dragon" width="100%">
</center>

`/worlds create <id>` makes an Overworld. Add `from-preset` for flat, amplified, large biomes or any world preset from a data pack, or `from-dimension` for a Nether, an End or any dimension type, with your own seed or a random one. `/worlds tp` takes you there, `/worlds list` shows every world and who is in it, and `/worlds delete` removes one after moving everyone out.

<center>
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/ribbon-vanilla.png" alt="Real vanilla everywhere" width="100%">
</center>

- **The End dragon fight, portals, maps and ender pearls work in every world** you create, not only in the original three.
- **Own weather and time.** Vanilla `/weather` and `/time` run inside a world change only that world.
- **Own game rules.** Keep inventory, PvP, mob spawning, daylight and more, set with vanilla `/gamerule` inside the world. Rules you leave alone follow the server, and `/gamerule <rule> inherit` hands one back. With Worlds installed on the client, the Game Rules screen shows which rules the world changed.
- **Nothing new to learn.** No extra commands for weather, time or rules, and the Overworld, Nether and End behave exactly as in vanilla.

<center>
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/ribbon-servers.png" alt="Built for servers" width="100%">
</center>

- **Load on demand.** A world loads when a player enters it and unloads a minute after the last player leaves, so you can keep hundreds of worlds without paying for idle ones.
- **Updated within a week of each Minecraft release.** Worlds follows the newest Minecraft version; older versions stay on the Versions tab.
- **A base for other mods.** Mods register their own world types through the Worlds API and let Worlds handle creating, loading, saving and teleporting. [Stargate](https://modrinth.com/mod/mc-stargate) is built on it; [API.md](https://github.com/Wroud/mc-worlds/blob/main/API.md) has the guide and the Gradle setup.

<center>
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/ribbon-commands.png" alt="Commands" width="100%">
</center>

| Command | What it does |
|---|---|
| `/worlds create <id> [seed]` | Creates an overworld |
| `/worlds create <id> from-preset <preset> [dimension] [seed]` | Creates a world from a world preset, for example `minecraft:flat` or `minecraft:amplified` |
| `/worlds create <id> from-dimension <type> [seed]` | Creates a world of a dimension type, for example `minecraft:the_nether` or `minecraft:the_end` |
| `/worlds tp <id> [players]` | Teleports you or other players to a world's spawn |
| `/worlds list [loaded\|unloaded]` | Lists worlds; hover for details, click to teleport |
| `/worlds delete <id>` | Deletes a world after moving everyone out of it |
| `/worlds settings spawn [here\|<x> <y> <z>]` | Shows or sets the spawn of the world you are in |
| `/worlds settings loadOnStartup [true\|false]` | Shows or sets whether the world you are in loads when the server starts |
| `/gamerule <rule> <value>` | In a created world, sets a rule for that world only |
| `/gamerule` | In a created world, lists the rules the world changed |
| `/gamerule <rule> inherit` | In a created world, makes a rule follow the server again |

A world id without a namespace gets `minecraft:`, so `/worlds create mining` creates `minecraft:mining`. Run any command for another world with `/execute in <world> run ...`. `/worlds` commands need operator level 3; `/gamerule` needs level 2, as in vanilla.

<center>
<img src="https://raw.githubusercontent.com/Wroud/mc-worlds/main/docs/listing/ribbon-examples.png" alt="Examples" width="100%">
</center>

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

**Keep inventory only in an arena**
```
/execute in minecraft:arena run gamerule keep_inventory true
```

**Moving from World Manager or Multiworld:** replace it with Worlds, then run `/worlds create <id> from-dimension <type> <seed>` with the id, type and seed each world had. Classic Overworld, Nether and End worlds are supported.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.18.4+ and Fabric API
- Java 25

## Recent updates

- **1.10.0** Per-world game rules through vanilla `/gamerule`, and a fix for a rare "Exception generating new chunk" crash when several worlds generate structures at once.
- **1.9.0** `/worlds list` with loaded and unloaded worlds, hover details and click-to-teleport.
- **1.8.26** A world that holds the world spawn stays loaded, and deleting it moves the spawn back to the Overworld.

The full history is in the [changelog](https://github.com/Wroud/mc-worlds/blob/main/CHANGELOG.md).

You can support this project by hosting your server on [Pixly Hosting](https://pixly.gg).
