# Worlds

A Minecraft mod that allows you to create and manage worlds in-game using simple commands.

## Features

- Create new worlds with custom IDs and optional seeds
- Create worlds from presets (flat world, amplified, large biomes, etc.)
- Default world creation uses normal overworld dimensions
- Delete worlds safely (removes all players first)
- Teleport between worlds easily
- Lazy worlds loading (support for infinite worlds)
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

For detailed documentation and examples, see [API.md](API.md).

## Installation

### For Players
1. Download the mod file from the releases page
2. Place it in your `mods` folder
3. Start your Minecraft server or client

### For Mod Developers (Using as Dependency)

MC Worlds is published to GitHub Packages. To use it as a dependency in your Gradle project:

1. Add the GitHub Packages repository to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/wroud/mc-worlds")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
            password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String?
        }
    }
}
```

2. Add the dependency:

```kotlin
dependencies {
    modImplementation("dev.wroud.mc:worlds:${mc_worlds_version}")
}
```

3. Set up authentication by adding to your `gradle.properties`:

```properties
gpr.user=your_github_username
gpr.key=your_github_token
```

Or set environment variables `GITHUB_ACTOR` and `GITHUB_TOKEN`.

Note: You need a GitHub personal access token with `packages:read` permission to download from GitHub Packages.

## Requirements

- Fabric Loader
- Minecraft 1.21.8+

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed release notes and version history.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
