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

1. Download the mod file from the releases page
2. Place it in your `mods` folder
3. Start your Minecraft server or client

## Requirements

- Fabric Loader
- Minecraft 1.21.8+

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed release notes and version history.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
