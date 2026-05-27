# MC-Worlds Agent Instructions

Fabric Minecraft mod for managing multiple per-server worlds via in-game commands. Mod ID `mc-worlds`. Published to Modrinth and CurseForge (see `build.gradle.kts` `publishMods`). Versions in `gradle.properties` (mod) and `versions/latest/gradle.properties` (MC/Java).

## Architecture

```
McWorldMod (ModInitializer) → McWorld (per-server facade) → WorldsManager
  → WorldHandle (id+level+data) → CustomServerLevel  → WorldsLevelData
```

| Class | Role |
|-------|------|
| `McWorldMod` | Entry point, event registration |
| `McWorld` | Per-server facade |
| `WorldsManager` | Load/create/delete/unload |
| `WorldHandle` | id + level + levelData |
| `CustomServerLevel` | Extends `ServerLevel`; lifecycle Init→Active→Stopping→Stopped; per-world weather/clock |
| `WorldsLevelData` | World config (seed, provider, spawn, lazy), Codec-persisted |
| `WorldsCreator` | Factory (preset/dimension/seed resolution) |
| `WorldsData` | `SavedData` for all worlds, Codec-serialized |
| `DefaultServerLevelProvider` | Registered as `mc-worlds:default` |
| `WorldsRegistries` | Fabric registry for `ServerLevelProvider` |

## Key Files
- Entry: `src/main/java/dev/wroud/mc/worlds/McWorldMod.java`
- Manager: `manager/WorldsManager.java`
- Level: `server/level/CustomServerLevel.java`
- Data: `manager/level/data/WorldsLevelData.java`
- Commands: `command/` (Create/Delete/Teleport/Settings, registered in `WorldsCommands.java`)
- Mixin config: `src/main/resources/worlds.mixins.json`, `src/client/resources/worlds.client.mixins.json`
- Build: `build.gradle.kts`, `gradle.properties`, `versions/latest/gradle.properties`
- Metadata: `src/main/resources/fabric.mod.json`

## Build & Run
```bash
./gradlew build         # build JAR → build/libs/worlds-<version>.jar
./gradlew runClient     # dev client
./gradlew runServer     # dev server (also: manual QA — no test suite)
./gradlew runDatagen    # data generation
./gradlew genSources    # regenerate decompiled MC sources after MC upgrade
```

## Mixins
ASM bytecode injection patching closed-source MC classes.

| Package | Purpose |
|---------|---------|
| `mixin/` (root) | Core: server init, weather, wandering trader spawner |
| `mixin/fixes/` | Vanilla mechanics fixes for custom dimensions (portals, maps, entities) |
| `mixin/filefix/` | Data format migration (DataFixers) |
| `client/mixin/` | Client packet handling, dimension context |

Add a mixin: create class under `src/main/java/dev/wroud/mc/worlds/mixin/`, register in `worlds.mixins.json` (or client equivalent), annotate with `@Mixin` + `@Inject`/`@Redirect`/`@ModifyVariable`.

## Patterns
- **Codec:** `Codec<T>` via `RecordCodecBuilder` for persisted data.
- **State machine:** `CustomServerLevel` auto-unloads after 1200 ticks (60s) without players.
- **Registry:** `WorldsRegistries.SERVER_LEVEL_PROVIDER.register(id, provider)`.
- **Commands:** Brigadier; require `ServerLevel.LEVEL_ADMINS`; one class per command, registered in `WorldsCommands.java`.
- **i18n:** `Component.translatable("...")`; keys in `src/main/resources/assets/mc-worlds/lang/en_us.json`.

## Common Tasks
- **New command:** create `command/MyCommand.java` → register in `WorldsCommands` → add translation keys → add datagen entry under `src/datagen/`.
- **New persistent field:** add to `WorldsLevelData` + its `CODEC` + getter/setter; access via `CustomServerLevel.getWorldData()`.
- **MC version bump:** update `versions/latest/gradle.properties` (minecraft_version, java_version) and `gradle.properties` (mod_version, loader_version, fabric_version); fix broken mixin targets; `./gradlew build`.

## Minecraft Source (Mojmap)
Fabric Loom decompiles MC into source JARs in:
```
.gradle/loom-cache/minecraftMaven/net/minecraft/
  minecraft-common-<hash>/<version>/...-sources.jar     # server + shared
  minecraft-clientOnly-<hash>/<version>/...-sources.jar # client-only
```
IDE attaches these automatically (Cmd/Ctrl+Click any MC class). Manual extract: `unzip .../minecraft-common-*-sources.jar -d /tmp/mc-src`.

Relevant packages: `net.minecraft.server.level` (`ServerLevel`, `ServerPlayer`), `net.minecraft.server` (`MinecraftServer`), `net.minecraft.commands` (Brigadier), `net.minecraft.world.level` (`LevelData`, `WeatherData`, `SavedData`), `net.minecraft.world.level.storage`.
