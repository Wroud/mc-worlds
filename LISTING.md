---
name: "Worlds"
summary: "Multiverse for Fabric: create unlimited worlds with their own weather, time and game rules, a working End dragon and every vanilla mechanic."
icon: src/main/resources/assets/mc-worlds/icon.png
---

# Worlds

Create and manage as many worlds as you like on one server, all with in-game commands.

<a href="https://pixly.gg/?utm_source=modrinth&utm_medium=referral&utm_campaign=mc-worlds&utm_content=mods-banner">
  <picture>
  <source media="(max-width: 640px)" srcset="https://pixly.gg/assets/promo/pixly-banner-mods-compact-2400x1200.jpg">
  <img src="https://pixly.gg/assets/promo/pixly-banner-mods-2400x600.jpg" alt="Pixly: Minecraft servers you pay for only while they're online" width="100%">
  </picture>
</a>

## New in 1.10: game rules for every world

Each world you create can now have its own game rules, set with the vanilla `/gamerule` command.

- **Your rules, per world.** Run `/gamerule` inside a world and the change applies there only. Every rule you leave alone keeps following the server.
- **Always clear where a rule comes from.** `/gamerule` names the world it changed, `/gamerule` on its own lists a world's own rules, and `/gamerule <rule> inherit` hands a rule back to the server.
- **The Game Rules screen knows too.** With Worlds on your game, each rule's tooltip says whether the world set it, uses the server's value, or is server-wide.
- **Keep inventory where you died.** Keep inventory follows the world you died in, and the death screen, debug info, time and weather follow the world you are in.
- **Steadier world generation.** A rare crash when several worlds generated villages at once is fixed.

## New in 1.9: see all your worlds

- **World list.** `/worlds list` shows every world, loaded ones in green and sleeping ones in gray, with details on hover and a click to teleport.

## Features

- Create new worlds with custom IDs and optional seeds
- Create worlds from presets (flat world, amplified, large biomes, etc.)
- Default world creation uses normal overworld dimensions
- Delete worlds safely (removes all players first)
- Teleport between worlds easily
- List every world, and see at a glance which ones are loaded
- Lazy worlds loading (support for infinite worlds)
- Every world has its own weather and time of day
- Every world can have its own game rules, set with vanilla `/gamerule`
- Create fully functional Overworld, End and Nether worlds (mod includes fixes to enable End Dragon and other vanilla mechanics in the custom worlds)
- **API for other mods to register custom level providers**

## Commands

All commands use the base `/worlds` command:

### Create Command

The `/worlds create` command allows you to create new worlds with various options:

**Basic usage:**
- `/worlds create <id>` - Creates a new normal overworld with the specified ID and a random seed
- `/worlds create <id> <seed>` - Creates a new normal overworld with the specified ID and seed

**Create from preset:**
- `/worlds create <id> from-preset <preset>` - Creates a world using a specific world preset (e.g., `minecraft:flat`, `minecraft:amplified`, `minecraft:large_biomes`)
- `/worlds create <id> from-preset <preset> <seed>` - Creates a world from a preset with a specific seed
- `/worlds create <id> from-preset <preset> <dimension>` - Creates a world from a preset using a specific dimension from that preset
- `/worlds create <id> from-preset <preset> <dimension> <seed>` - Combines preset, dimension, and seed options

**Create from dimension:**
- `/worlds create <id> from-dimension <dimension>` - Creates a world using an existing dimension type (e.g., `minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end`)
- `/worlds create <id> from-dimension <dimension> <seed>` - Creates a world from a dimension with a specific seed

**Examples:**
```
/worlds create myworld
/worlds create myworld 12345
/worlds create flatworld from-preset minecraft:flat
/worlds create amplified from-preset minecraft:amplified 67890
/worlds create nether from-dimension minecraft:the_nether
/worlds create custom_end from-dimension minecraft:the_end 11111
```

### Delete Command

- `/worlds delete <id>` - Deletes the specified world and kicks all players currently in it

### Teleport Command

- `/worlds tp <id> [targets]` - Teleports you (or specified players) to the world

### List Command

- `/worlds list` - Lists all worlds, loaded ones in green and unloaded (lazy) ones in gray
- `/worlds list loaded` - Lists only the worlds that are loaded right now
- `/worlds list unloaded` - Lists only the worlds that are not loaded (listing never loads them)

Hover over a world to see its dimension type, provider, whether it loads on startup, whether it holds the world spawn and which players are in it. Click it to fill in `/worlds tp <id>`.

### Settings Command

The `/worlds settings` command allows you to view and modify settings for the current world you're in. Similar to Minecraft's `/gamerule` command, you can query the current value by omitting the new value parameter.

**Load on Startup:**
- `/worlds settings loadOnStartup` - Displays whether the world loads automatically on server start
- `/worlds settings loadOnStartup <true|false>` - Enable or disable automatic loading on server start
  - When enabled (`true`), the world will load automatically when the server starts
  - When disabled (`false`), the world will only load when a player enters it (lazy loading)

**Spawn Point:**
- `/worlds settings spawn` - Displays the current spawn point and rotation
- `/worlds settings spawn here` - Sets the spawn point to your current position and rotation
- `/worlds settings spawn <x> <y> <z>` - Sets the spawn point to specific coordinates (rotation defaults to 0, 0)

**Examples:**
```
/worlds settings loadOnStartup
/worlds settings loadOnStartup true
/worlds settings loadOnStartup false
/worlds settings spawn
/worlds settings spawn here
/worlds settings spawn 100 64 200
```

**Note:** These commands affect the world you're currently in. Navigate to the world you want to configure before running the command.

### Game Rules

Every created world follows the server's game rules until you change one for it. Vanilla `/gamerule` run inside a created world, or through `/execute in <world> run gamerule ...`, changes the rule only for that world, and the message names the world. With the mod installed on the client, the Game Rules screen's tooltips also show where each value comes from. The Overworld, Nether and End always share the server's rules, so run `/gamerule` there (for example `/execute in minecraft:overworld run gamerule ...`) to change a rule for the whole server.

In a created world, vanilla `/gamerule` also does the following. In the Overworld, Nether and End it works exactly as in vanilla.

- `/gamerule` - Lists the game rules this world has changed, with the server's value next to each
- `/gamerule <rule>` - Displays the rule's value and says whether it is this world's own value, the server value, or server-wide
- `/gamerule <rule> inherit` - Removes the world's own value so the rule follows the server again

A few rules always stay server-wide: `send_command_feedback`, `log_admin_commands`, `max_command_sequence_length`, `max_command_forks` and `max_block_modifications`. Setting one of them in a created world changes the server's value. Mods and data packs can add more rules to the `#mc-worlds:global` game rule tag.

Keep inventory follows the world where the player died. Death messages, advancement messages and global sounds are decided by the world where they happen but are still shown to every player, as in vanilla.

**Examples:**
```
/execute in mc-worlds:arena run gamerule keep_inventory true
/execute in mc-worlds:arena run gamerule
/execute in mc-worlds:arena run gamerule keep_inventory
/execute in mc-worlds:arena run gamerule keep_inventory inherit
```

## Importing worlds from World Manager

1. Replace World Manager mod with Worlds
2. use `/worlds create {world_id_to_import} {world_type} {world_seed}`
   You need to specify same world id, type and seed that world had when was created with "World Manager", only classic Overworld, End and Nether worlds supported

## For Mod Developers

MC Worlds provides an API that allows other mods to register custom server level providers. This enables you to create worlds with custom behavior, special rules, or unique mechanics.

### Quick Start

1. Add MC Worlds as a dependency in your `fabric.mod.json`
2. Implement the `ServerLevelProvider` interface
3. Register your provider using `WorldsRegistries.LEVEL_PROVIDER_REGISTRY`

For detailed documentation and examples, see [API.md](https://github.com/Wroud/mc-worlds/blob/main/API.md).

## Requirements

- Minecraft 26.3
- Fabric Loader 0.18.4+ and Fabric API
- Java 25

You can support this project by hosting your server on [Pixly Hosting](https://pixly.gg)
