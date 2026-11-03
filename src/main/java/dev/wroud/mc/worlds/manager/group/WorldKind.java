package dev.wroud.mc.worlds.manager.group;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public enum WorldKind implements StringRepresentable {
  OVERWORLD,
  NETHER,
  END,
  OTHER;

  public static final Codec<WorldKind> CODEC = StringRepresentable.fromEnum(WorldKind::values);

  private final String key = this.name().toLowerCase(Locale.ROOT);

  public static WorldKind of(MinecraftServer server, Identifier world) {
    return of(DimensionDetectionUtil.getVanillaDimensionMapping(server, ResourceKey.create(Registries.DIMENSION, world)));
  }

  public static WorldKind of(Holder<DimensionType> type) {
    return of(DimensionDetectionUtil.getVanillaDimensionMapping(type));
  }

  private static WorldKind of(@Nullable ResourceKey<Level> vanilla) {
    if (vanilla == Level.OVERWORLD) {
      return OVERWORLD;
    }
    if (vanilla == Level.NETHER) {
      return NETHER;
    }
    if (vanilla == Level.END) {
      return END;
    }
    return OTHER;
  }

  public boolean isMain() {
    return this != OTHER;
  }

  public String key() {
    return this.key;
  }

  @Override
  public String getSerializedName() {
    return this.key;
  }
}
