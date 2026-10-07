# MC Worlds API

This mod provides an API for other mods to register custom server level providers.

## Server Level Providers

Server level providers allow you to create custom implementations of server levels with custom behavior.

### Registering a Custom Level Provider

To register your own level provider, follow these steps:

1. Implement the `ServerLevelProvider` interface:

```java
public class MyCustomLevelProvider implements ServerLevelProvider<MyCustomServerLevel> {
    public static final ResourceKey<ServerLevelProvider<?>> MY_PROVIDER = 
        ResourceKey.create(WorldsRegistries.LEVEL_PROVIDER, 
            Identifier.fromNamespaceAndPath("mymod", "my_provider"));

    @Override
    public MyCustomServerLevel create(
        MinecraftServer minecraftServer,
        Executor executor,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        LevelData serverLevelData,
        ResourceKey<Level> resourceKey,
        LevelStem levelStem,
        ChunkProgressListener chunkProgressListener,
        boolean bl,
        List<CustomSpawner> customSpawners,
        RandomSequences randomSequences) {
        
        return new MyCustomServerLevel(
            minecraftServer,
            executor,
            levelStorageAccess,
            serverLevelData,
            resourceKey,
            levelStem,
            chunkProgressListener,
            bl,
            customSpawners,
            randomSequences);
    }
}
```

2. Register your provider during mod initialization:

```java
@Override
public void onInitialize() {
    Registry.register(WorldsRegistries.LEVEL_PROVIDER_REGISTRY, 
        MyCustomLevelProvider.MY_PROVIDER.identifier(), 
        new MyCustomLevelProvider());
}
```

### Game Rules

Every `CustomServerLevel` returns its own game rules from `getGameRules()`: values the world overrides, with every other rule read from the server. A provider can pre-seed overrides once the level is constructed:

```java
level.getGameRules().set(GameRules.KEEP_INVENTORY, true, null);
```

Rules in the `#mc-worlds:global` game rule tag are always written to the server's rules. A mod that reads `level.getGameRules()` sees the world's value; one that reads `server.getGameRules()` sees only the server's value.

### Dependencies

Make sure to add mc-worlds as a dependency in your `fabric.mod.json`:

```json
{
  "depends": {
    "mc-worlds": "*"
  }
}
```

## Registry Access

The registry is available as soon as the `WorldsRegistries` class is loaded, making it safe for other mods to register their providers during their initialization phase.

- **Registry Key**: `WorldsRegistries.LEVEL_PROVIDER`
- **Registry Instance**: `WorldsRegistries.LEVEL_PROVIDER_REGISTRY`
