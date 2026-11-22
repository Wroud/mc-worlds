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

`McWorld` creates and finds groups; a `WorldGroup` manages itself:

```java
Identifier DEFAULT_GROUP;
WorldGroup createGroup(Identifier group);
WorldGroup createGroup(Identifier group, @Nullable Identifier provider);
@Nullable WorldGroup group(Identifier group);
Map<Identifier, WorldGroup> groups();
Identifier groupOf(Identifier world);
static Identifier groupOf(Level level);
static Identifier groupOf(ServerPlayer player);
static void registerGroupProvider(Identifier id, GroupProvider provider);
```

```java
public final class WorldGroup {
  Identifier id();
  MinecraftServer server();
  @Nullable GroupInstance instance();
  WorldGroup.Join join(Identifier world, boolean asMain);
  void leave(Identifier world);
  SequencedSet<Identifier> members();
  boolean contains(Identifier world);
  @Nullable Identifier main(WorldKind kind);
  boolean isMain(Identifier world);
  WorldGroup.Spawn spawn();
  void setSpawn(@Nullable RespawnData spawn);
  GameRules rules();
  CompletableFuture<Boolean> returnTo(ServerPlayer player);
  void delete();
  boolean isDeleted();
}
```

- `DEFAULT_GROUP` has no `WorldGroup`: `group(DEFAULT_GROUP)` is null.
- `join` takes a world id, so the world doesn't have to be loaded. A world in another group moves.
- `rules` returns the group's rules, the same ones `getGameRules()` returns in each of its worlds, even before any world joins. Setting a rule there works as `/gamerule` does in one of those worlds.
- `delete` removes the group. Players inside move out at the end of the tick. `createGroup` refuses that id until the deletion has finished; `GROUP_DELETED` is where a mod creates it again. Every other change to a deleted group throws, and `isDeleted` tells.
- `returnTo` is the counterpart of `returnHome`: it sends the player back to where they last left the group, or to the group spawn when that world is gone or they were never inside, and loads the world first. It completes true when the teleport was sent, false when the player is already inside, dead, offline by then or refused, and exceptionally when the world can't load.
- A level from another mod counts as `DEFAULT_GROUP` and can't join a group.
- `groupOf(Level)`, `groupOf(ServerPlayer)` and `isTemporary` are safe to call every tick. `worlds()` and `groupOf(Identifier)` do more work per call, so don't.

### Temporary worlds and groups

A temporary world or group uses the normal calls with a generated id: the name plus 8 random hex characters, such as `mc-worlds-temp:match-3f9c1a7e`. Its files are deleted when it closes and at startup.

```java
static Identifier temporaryId(String name);
static boolean isTemporary(Identifier id);
static String temporarySuffix(Identifier id);
boolean returnHome(ServerPlayer player);
```

A minigame:

```java
var group = worlds.createGroup(McWorld.temporaryId("match"));
var arena = McWorld.temporaryId("arena");
worlds.createWorld(arena, worlds.levelStem(WorldPresets.FLAT, null), null).thenAccept(level -> {
  player.teleport(new TeleportTransition(level, position, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
});
group.join(arena, false);
// later
worlds.returnHome(player);
group.delete();
```

- Only temporary worlds can join a temporary group.
- A player enters blank, so give starting items after entry.
- `returnHome` is true when the player will go home or a queued entry into a temporary group was cancelled. A mod that owes a reward checks `isTemporary(groupOf(player))` first.
- `importWorld` refuses `all` with a temporary id.
- `temporarySuffix` returns the random part (`3f9c1a7e`), a short id for logs and exported results.

### Moving players

Use vanilla `ServerPlayer.teleport(TeleportTransition)`. A teleport into another group becomes a group switch at the end of the tick, and `postTeleportTransition` runs after it with the new player. `level.getRespawnData()` is the group spawn.

```java
boolean teleport(ServerPlayer player, Identifier world);
static TeleportTransition spawnTransition(ServerLevel level, Entity entity);
```

`teleport` moves a player to the world's spawn, as `/worlds tp` does. It throws for an unknown world, and returns false when the player is already there, the world is stopping or it refuses the player. `spawnTransition` is that same spawn search for a mod that moves the entity itself. A group switch builds a new `ServerPlayer`, so don't keep the old reference.

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

`WorldsGameRules` holds the mod's own rules: `DEFAULT_GAME_MODE` and `FORCE_GAME_MODE` for a group's game mode, and `SPAWN_PROTECTION`, the spawn protection radius in blocks or `-1` (the default) for the server's `spawn-protection` property. `group.rules().set(WorldsGameRules.SPAWN_PROTECTION, 0, server)` turns it off in a group.

Rules in the `#mc-worlds:global` game rule tag are always written to the server's rules. A mod that reads `level.getGameRules()` sees the world's value; one that reads `server.getGameRules()` sees only the server's value.

### Entry

Override `canEnter` to choose who may enter the level from another level:

```java
@Override
public boolean canEnter(Entity entity) {
    return super.canEnter(entity) && entity instanceof ServerPlayer;
}
```

It is asked for `/tp`, `/worlds tp`, portals, ender pearls and group switches. By default it asks the group's instance (see Group Instances). A refused move leaves the entity where it was. Login, respawn and the End exit before the credits don't ask it, so a player who logs out inside the level comes back into it. It can run off the server thread, and more than once for one move, so keep it free of side effects. `isOpen()` is false once the level is stopping.

## Group Instances

A provider attaches a `GroupInstance` to each group created with it, the way a level provider builds a `CustomServerLevel`:

```java
@FunctionalInterface
public interface GroupProvider {
  GroupInstance create(WorldGroup group, @Nullable Dynamic<?> saved);
}

public abstract class GroupInstance {
  protected final MinecraftServer server;
  protected final WorldGroup group;

  protected GroupInstance(WorldGroup group) { ... }
  public final WorldGroup group() { ... }
  public final void setDirty() { ... }
  public <T> T save(DynamicOps<T> ops) { return ops.emptyMap(); }
  public void tick() {}
  public boolean canEnter(Entity entity) { return true; }
  public void removed() {}
}
```

Register the provider during mod initialization, then create groups with it. `instance()` on the group returns the instance, and `group()` on the instance returns its group:

```java
McWorld.registerGroupProvider(MyGame.ID, MyGame::new);
var game = (MyGame) McWorld.of(server).createGroup(id, MyGame.ID).instance();
```

- `saved` is null for a new group; the instance sets up its own initial state.
- A `CustomServerLevel` returns its group's instance from `groupInstance()`, null outside such a group and once the group is deleted. It is safe to read in `canEnter`.
- `canEnter` decides who may enter the group from outside it, under the same rules as the level's `canEnter` in Entry. Travel between the group's own worlds doesn't ask it. The level asks it unless the level overrides `canEnter` without calling `super`.
- A permanent group stores what `save` returns with the group at the next save after `setDirty`, and passes it back at startup. `create` then runs while the server loads, before `McWorld.of(server)` is available, so it must not call it.
- `create` runs before the group is registered, at startup and on `createGroup`, so the constructor uses only `id()` and `server()` of its group. Set the group up after `createGroup` returns or in the first `tick`. A temporary group is never saved.
- `tick` runs at the end of every server tick, before group switches run, whether or not the group's worlds are loaded. An instance may delete its own group from it.
- `removed` runs once when the group is deleted. Its players are queued to leave at the end of that tick. When the server stops, every temporary group is removed once its players are saved and `removed` runs for its instance; a permanent group's instance gets no call.
- If a saved group's provider is no longer registered, or `create` throws at startup, the group loads without an instance, and its data is dropped the next time the groups are saved.

## Dependencies

Add mc-worlds as a dependency in your `fabric.mod.json`:

```json
{
  "depends": {
    "mc-worlds": "*"
  }
}
```
