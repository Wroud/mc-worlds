---
name: "Worlds"
summary: "A mod to support infinite dimensions (Multiverse for fabric). With own weather, Time, End Dragon and all vanilla mechanics."
icon: src/main/resources/assets/mc-worlds/icon.png
---

# Worlds

Run as many worlds as you want on one Fabric server, the way Multiverse does on Paper. Create, list, delete and teleport between worlds with in-game commands. Every world plays like real vanilla Minecraft, and Worlds is also a base other mods build their own dimensions on.

<a href="https://pixly.gg/?utm_source=modrinth&utm_medium=referral&utm_campaign=mc-worlds&utm_content=mods-banner">
  <picture>
  <source media="(max-width: 640px)" srcset="https://pixly.gg/assets/promo/pixly-banner-mods-compact-2400x1200.jpg">
  <img src="https://pixly.gg/assets/promo/pixly-banner-mods-2400x600.jpg" alt="Pixly: Minecraft servers you pay for only while they're online" width="100%">
  </picture>
</a>

## Features

- **Any kind of world.** Overworld, Nether, End, flat, amplified, large biomes, or any world preset and dimension type from a data pack, with your own seed or a random one.
- **Vanilla mechanics that just work.** The End dragon fight, portals, maps, ender pearls and other vanilla mechanics work in every world you create, not only in the original three.
- **Each world keeps its own weather and time.** Vanilla `/weather` and `/time` run inside a world change only that world.
- **Each world can have its own game rules.** Keep inventory, PvP, mob spawning, daylight and more, set with vanilla `/gamerule` inside the world. Rules you don't change follow the server.
- **Lightweight.** Worlds load when a player enters them and unload a minute after the last player leaves, so you can keep many worlds without paying for idle ones.
- **Close to vanilla.** No new commands to learn for weather, time or game rules, and the Overworld, Nether and End behave exactly as in vanilla.
- **A base for other mods.** Mods register their own world types through the Worlds API. [Stargate](https://modrinth.com/mod/mc-stargate) is built on it.

## Commands

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

**A safe lobby that loads with the server**
```
/worlds create lobby
/worlds tp lobby
/worlds settings spawn here
/worlds settings loadOnStartup true
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

**Moving from World Manager:** replace World Manager with Worlds, then run `/worlds create <id> from-dimension <type> <seed>` with the id, type and seed each world had. Classic Overworld, Nether and End worlds are supported.

## For mod developers

Worlds provides an API for registering your own server level providers, so your mod can add worlds with their own generation, rules or mechanics while Worlds handles creating, loading, saving and teleporting. See [API.md](https://github.com/Wroud/mc-worlds/blob/main/API.md) for the guide and the Gradle setup.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.18.4+ and Fabric API
- Java 25

You can support this project by hosting your server on [Pixly Hosting](https://pixly.gg).
