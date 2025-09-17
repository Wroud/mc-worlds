# MC Worlds

A Minecraft mod that allows you to create and manage worlds in-game using simple commands.

## Features

- Create new worlds with custom IDs and optional seeds
- Delete worlds safely (removes all players first)
- Teleport between worlds easily
- **API for other mods to register custom level providers**

## Commands

All commands use the base `/worlds` command:

- `/worlds create <id> [seed]` - Creates a new overworld with the specified ID and optional seed
- `/worlds delete <id>` - Deletes the specified world and kicks all players currently in it
- `/worlds tp <id> [targets]` - Teleports you (or specified players) to the world

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
    modImplementation("dev.wroud.mc.worlds:mc-worlds:${mc_worlds_version}")
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
