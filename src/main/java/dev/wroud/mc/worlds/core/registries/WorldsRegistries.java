package dev.wroud.mc.worlds.core.registries;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.DefaultServerLevelProvider;
import dev.wroud.mc.worlds.manager.ServerLevelProvider;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Registry for custom server level providers.
 * Other mods can register their own level providers using:
 * Registry.register(WorldsRegistries.LEVEL_PROVIDER_REGISTRY, resourceLocation, levelProvider);
 */
public class WorldsRegistries {
  /**
   * Registry key for server level providers
   */
  public static final ResourceKey<Registry<ServerLevelProvider<?>>> LEVEL_PROVIDER = createRegistryKey("level_provider");
  
  /**
   * Registry for server level providers. Other mods can use this to register their own providers.
   * The registry is created during class loading to ensure it's available for other mods.
   */
  public static final Registry<ServerLevelProvider<?>> LEVEL_PROVIDER_REGISTRY;
  
  static {
    // Create the registry during class loading so it's available for other mods
    LEVEL_PROVIDER_REGISTRY = FabricRegistryBuilder.createSimple(LEVEL_PROVIDER).buildAndRegister();
  }

  private static <T> ResourceKey<Registry<T>> createRegistryKey(String string) {
    return ResourceKey.createRegistryKey(McWorldMod.id(string));
  }
  
  /**
   * Bootstrap method to register default providers.
   * This should be called during mod initialization, after the registry is available.
   */
  public static void bootstrap() {
    // Register the default level provider
    Registry.register(LEVEL_PROVIDER_REGISTRY, DefaultServerLevelProvider.DEFAULT.location(), new DefaultServerLevelProvider());
  }
}
