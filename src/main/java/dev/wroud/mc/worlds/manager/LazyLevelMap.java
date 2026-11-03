package dev.wroud.mc.worlds.manager;

import java.util.Collections;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.ForwardingMap;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class LazyLevelMap extends ForwardingMap<ResourceKey<Level>, ServerLevel> {
  private final MinecraftServer server;
  private final Map<ResourceKey<Level>, ServerLevel> loaded;
  private final Map<ResourceKey<Level>, ServerLevel> view;

  public LazyLevelMap(MinecraftServer server, Map<ResourceKey<Level>, ServerLevel> loaded) {
    this.server = server;
    this.loaded = loaded;
    this.view = Collections.unmodifiableMap(loaded);
  }

  public static Map<ResourceKey<Level>, ServerLevel> loaded(MinecraftServer server) {
    var levels = ((MinecraftServerAccessor) server).getLevels();
    return levels instanceof LazyLevelMap lazy ? lazy.view : levels;
  }

  @Override
  protected Map<ResourceKey<Level>, ServerLevel> delegate() {
    return this.loaded;
  }

  @Override
  public @Nullable ServerLevel get(@Nullable Object key) {
    var level = this.loaded.get(key);
    if (level != null || !(key instanceof ResourceKey<?> dimension)) {
      return level;
    }
    var manager = McWorldMod.getManager(this.server).orElse(null);
    if (manager == null) {
      return null;
    }
    var levelData = manager.getWorldsData().getLevelData(dimension.identifier());
    if (levelData == null) {
      return null;
    }
    var handle = manager.loadOrCreateWorld(dimension.identifier(), levelData);
    return handle != null ? handle.getServerLevel() : null;
  }
}
