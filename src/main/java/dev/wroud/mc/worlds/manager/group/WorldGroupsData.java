package dev.wroud.mc.worlds.manager.group;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class WorldGroupsData extends SavedData {
  private static final Codec<WorldGroupsData> CODEC = Codec
      .unboundedMap(Identifier.CODEC, WorldGroup.CODEC)
      .xmap(WorldGroupsData::new, data -> Maps.filterKeys(data.groups, id -> !TemporaryIds.isTemporary(id)));

  public static final SavedDataType<WorldGroupsData> TYPE = new SavedDataType<>(McWorldMod.id("world_groups"),
      WorldGroupsData::new, CODEC, null);

  private final Map<Identifier, WorldGroup> groups;

  private WorldGroupsData() {
    this.groups = new HashMap<>();
  }

  private WorldGroupsData(Map<Identifier, WorldGroup> groups) {
    this.groups = new HashMap<>(groups);
  }

  public Map<Identifier, WorldGroup> groups() {
    return Collections.unmodifiableMap(this.groups);
  }

  public @Nullable WorldGroup get(Identifier id) {
    return this.groups.get(id);
  }

  public @Nullable WorldGroup put(Identifier id, WorldGroup group) {
    this.setDirty();
    return this.groups.put(id, group);
  }

  public @Nullable WorldGroup remove(Identifier id) {
    this.setDirty();
    return this.groups.remove(id);
  }
}
