# MC Worlds API

Other mods can do everything the `/worlds` commands do through `McWorld`, and can register their own world types through server level providers.

## Getting started

Worlds is published to GitHub Packages. Add the repository and the dependency to your `build.gradle.kts`:

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

dependencies {
    modImplementation("dev.wroud.mc:worlds:${mc_worlds_version}")
}
```

Authenticate with `gpr.user` and `gpr.key` in your `gradle.properties`, or the `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables. The token needs the `packages:read` permission.

## McWorld

`McWorld.of(server)` is the entry point. Everything public on `McWorld` is the API; other classes in `dev.wroud.mc.worlds` can change between versions. The commands call these same methods, so a mod gets the same checks and the same messages.

```java
McWorld worlds = McWorld.of(server);
```

- Call it on the server thread, once the overworld has loaded. `SERVER_STARTED` is a safe place to start. It throws `IllegalStateException` before that.
- A request that can't be done throws `WorldsException`, an unchecked exception. `toComponent()` is the translated message the command prints, and `getMessage()` is its text.
- A future fails with `WorldsException` for what goes wrong later, and completes on the server thread.

### Worlds

```java
CompletableFuture<ServerLevel> createWorld(Identifier id, LevelStem stem, @Nullable Long seed);
CompletableFuture<ServerLevel> createWorld(Identifier id, ResourceKey<ServerLevelProvider<?>> provider, @Nullable Long seed);
LevelStem levelStem(@Nullable ResourceKey<WorldPreset> preset, @Nullable Identifier dimension);
LevelStem levelStem(ResourceKey<LevelStem> key);
CompletableFuture<ImportResult> importWorld(Identifier id, String source, @Nullable Identifier dimension, boolean all, @Nullable Identifier group);
CompletableFuture<ImportResult> importWorld(Identifier id, String source, @Nullable Identifier dimension, boolean all, @Nullable Identifier group, Consumer<Collection<Identifier>> started);
void deleteWorld(Identifier id);
Set<Identifier> worlds();
boolean isLoaded(Identifier world);
boolean loadsOnStartup(Identifier world);
void setLoadOnStartup(Identifier world, boolean load);
@Nullable RespawnData worldSpawn(Identifier world);
void setWorldSpawn(Identifier world, RespawnData spawn);
static CompletableFuture<ServerLevel> whenReady(ServerLevel level);
```

- `createWorld` loads the level inside the call, which takes a few milliseconds of that tick; on a busy server, four overworld-type worlds in one tick can use up its budget, so spread many creates across ticks. It throws on an invalid or taken id, on a dimension type that isn't from the registry, and when the world can't load. Its future completes when the world is ready and fails if the world stops first. Called from another thread, it moves to the server thread and reports refusals through the future.
- `levelStem` turns a preset and one of its dimensions, or a `LevelStem` key, into the stem `createWorld` takes. A null preset means the normal one, and a null dimension means the preset's first.
- `importWorld` takes what `/worlds import` takes: a save folder beside the world, a zip, or a URL. `ImportResult` holds the ids of the worlds that loaded and the report lines the command prints. `started` receives the target ids once they are checked.
- `whenReady` gives each caller its own future. It completes at once for a level that is ready, and fails for a stopping level.

### Groups

```java
Identifier DEFAULT_GROUP;
void createGroup(Identifier group);
WorldGroup deleteGroup(Identifier group);
WorldGroup.Join joinGroup(Identifier world, Identifier group, boolean asMain);
Identifier leaveGroup(Identifier world);
void setGroupSpawn(Identifier group, @Nullable RespawnData spawn);
WorldGroup.Spawn groupSpawn(Identifier group);
Identifier groupOf(Identifier world);
@Nullable WorldGroup group(Identifier group);
SequencedCollection<Identifier> members(Identifier group);
Map<Identifier, WorldGroup> groups();
static Identifier groupOf(Level level);
static Identifier groupOf(ServerPlayer player);
```

- `joinGroup` takes a world id, so the world doesn't have to be loaded.
- `deleteGroup` returns the removed group. Players inside move out at the end of the tick. `createGroup` refuses that id until the deletion has finished; `GROUP_DELETED` is where a mod creates it again.
- A level from another mod counts as `DEFAULT_GROUP` and can't join a group.
- `groupOf(Level)`, `groupOf(ServerPlayer)` and `isTemporary` are safe to call every tick. `worlds()`, `members(DEFAULT_GROUP)` and `groupOf(Identifier)` do more work per call, so don't.

### Temporary worlds and groups

A temporary world or group uses the normal calls with a generated id. Its files are deleted when it closes and at startup.

```java
static Identifier temporaryId(String name);
static boolean isTemporary(Identifier id);
boolean returnHome(ServerPlayer player);
```

A minigame:

```java
var group = McWorld.temporaryId("match");
worlds.createGroup(group);
worlds.createWorld(McWorld.temporaryId("arena"), worlds.levelStem(WorldPresets.FLAT, null), null).thenAccept(level -> {
  worlds.joinGroup(level.dimension().identifier(), group, false);
  player.teleport(new TeleportTransition(level, position, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
});
// later
worlds.returnHome(player);
worlds.deleteGroup(group);
```

- Only temporary worlds can join a temporary group.
- A player enters blank, so give starting items after entry.
- `returnHome` is true when the player will go home or a queued entry into a temporary group was cancelled. A mod that owes a reward checks `isTemporary(groupOf(player))` first.
- `importWorld` refuses `all` with a temporary id.

### Moving players

Use vanilla `ServerPlayer.teleport(TeleportTransition)`. A teleport into another group becomes a group switch at the end of the tick, and `postTeleportTransition` runs after it with the new player. `level.getRespawnData()` is the group spawn.

```java
boolean teleport(ServerPlayer player, Identifier world);
static TeleportTransition spawnTransition(ServerLevel level, Entity entity);
```

`teleport` moves a player to the world's spawn, as `/worlds tp` does. It throws for an unknown world, and returns false when the player is already there or the world is stopping. `spawnTransition` is that same spawn search for a mod that moves the entity itself. A group switch builds a new `ServerPlayer`, so don't keep the old reference.

Non-player entities never cross groups.

### Events

```java
McWorld.AFTER_SWITCH.register((oldPlayer, newPlayer, from, to) -> { });
McWorld.GROUP_DELETED.register((server, group) -> { });
```

- `AFTER_SWITCH` fires on every group switch, once the new player is in the level and saved. `oldPlayer` is still readable, and `isDeadOrDying()` on it tells a death from a teleport. To move the new player here, use a cross-group teleport or wait a tick.
- `GROUP_DELETED` fires when a group's player folder goes: at the end of the tick, or when the last player holding it leaves. It doesn't fire for the startup sweep, so a mod that keeps data per temporary group removes it at `SERVER_STARTED` with `isTemporary`, as those ids are never reused. A deletion still pending when the server stops fires nothing.

On a group switch, Fabric's `AFTER_PLAYER_CHANGE_LEVEL` fires if the level changed through a teleport. `AFTER_RESPAWN` doesn't fire, because with `alive=true` Fabric would copy every attachment over the target group's data. `COPY_FROM` fires only on a death that respawns in another group, for a throwaway player. `JOIN` and `LEAVE` don't fire.

### Per-group mod data

Anything in the player file is per group with no code: persistent Fabric attachments, Cardinal Components, inventory, ender chest, effects and data written through `addAdditionalSaveData`. An attachment that should survive death still needs `copyOnDeath`, as in vanilla.

A mod with its own `SavedData` keys it by `(McWorld.groupOf(player), uuid)`. Data keyed by player alone stays shared across groups, as vanilla scoreboards do. [docs/compatibility.md](docs/compatibility.md) lists what has been tested.

## Migrating from 1.x

Worlds 2.0.0 removes the internal calls older mods used:

| Before | Now |
|---|---|
| `McWorldMod.getMcWorld(server).orElseThrow()` | `McWorld.of(server)` |
| `loadOrCreate(id, levelData)` with your provider | `createWorld(id, provider, seed)`; its future gives the ready level |
| `getManager().getWorldsData().getLevelData(id) != null` | `worlds().contains(id)`, which also holds vanilla dimensions |
| `LevelActivationUtil.executeWhenLevelReady(level, action)` | `McWorld.whenReady(level).thenRun(action)`; it runs at once for a ready level |
| `WorldLocation.findSpawn(level, entity)` | `McWorld.spawnTransition(level, entity)`, which returns a `TeleportTransition` |
| `new WorldLocation(level, transition)`, `.teleport(entity)` | `entity.setPortalCooldown()`, `entity.teleport(transition)` |

## Server Level Providers

A provider creates worlds with your own `CustomServerLevel` subclass, generation or mechanics.

```java
public class MyLevelProvider implements ServerLevelProvider<MyServerLevel> {
    public static final ResourceKey<ServerLevelProvider<?>> KEY = ResourceKey.create(WorldsRegistries.LEVEL_PROVIDER,
        Identifier.fromNamespaceAndPath("mymod", "my_provider"));

    @Override
    public LevelStem createLevelStem(MinecraftServer server, ResourceKey<Level> dimension, long seed) {
        return server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.OVERWORLD).value();
    }

    @Override
    public MyServerLevel create(MinecraftServer server, Executor executor,
        LevelStorageSource.LevelStorageAccess storage, WorldsLevelData levelData, ResourceKey<Level> dimension,
        LevelStem stem, List<CustomSpawner> spawners) {
        return new MyServerLevel(server, executor, storage, levelData, dimension, stem, spawners);
    }
}
```

Register it during mod initialization. The registry is ready as soon as `WorldsRegistries` loads:

```java
Registry.register(WorldsRegistries.LEVEL_PROVIDER_REGISTRY, MyLevelProvider.KEY.identifier(), new MyLevelProvider());
```

Then create a world with it. `createLevelStem` runs at each load, so the provider can change generation between versions:

```java
McWorld.of(server).createWorld(id, MyLevelProvider.KEY, seed);
```

### Game Rules

Every `CustomServerLevel` returns its own game rules from `getGameRules()`: values the world overrides, with every other rule read from the server, or the group's rules while it is in a group. A provider can pre-seed overrides once the level is constructed:

```java
level.getGameRules().set(GameRules.KEEP_INVENTORY, true, null);
```

Rules in the `#mc-worlds:global` game rule tag are always written to the server's rules. A mod that reads `level.getGameRules()` sees the world's value; one that reads `server.getGameRules()` sees only the server's value.

## Dependencies

Add mc-worlds as a dependency in your `fabric.mod.json`:

```json
{
  "depends": {
    "mc-worlds": "*"
  }
}
```
