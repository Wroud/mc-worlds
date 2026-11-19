package dev.wroud.mc.worlds.manager.group;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class WorldGroupsData extends SavedData {
  static SavedDataType<WorldGroupsData> type(WorldGroups groups) {
    Codec<WorldGroupsData> codec = Codec.<Identifier, WorldGroup>dispatchedMap(Identifier.CODEC,
        id -> WorldGroup.codec(groups, id))
        .xmap(WorldGroupsData::new, WorldGroupsData::persistent);
    return new SavedDataType<>(McWorldMod.id("world_groups"), WorldGroupsData::new, codec, null);
  }

  private final Map<Identifier, WorldGroup> groups;

  private WorldGroupsData() {
    this.groups = new ConcurrentHashMap<>();
  }

  private WorldGroupsData(Map<Identifier, WorldGroup> groups) {
    this.groups = new ConcurrentHashMap<>(groups);
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

  private Map<Identifier, WorldGroup> persistent() {
    return Maps.filterKeys(this.groups, id -> !TemporaryIds.isTemporary(id));
  }

  public @Nullable WorldGroup remove(Identifier id) {
    this.setDirty();
    return this.groups.remove(id);
  }
}
