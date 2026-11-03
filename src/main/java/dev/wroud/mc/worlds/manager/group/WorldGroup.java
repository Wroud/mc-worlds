package dev.wroud.mc.worlds.manager.group;

import java.util.Locale;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelData.RespawnData;

public final class WorldGroup {
  public record Join(Identifier group, WorldKind kind, boolean main, @Nullable Identifier previousGroup,
      @Nullable Identifier replacedMain) {
  }

  public enum SpawnSource {
    SET,
    MAIN_OVERWORLD,
    FIRST_MEMBER,
    SERVER;

    private final String key = this.name().toLowerCase(Locale.ROOT);

    public String key() {
      return this.key;
    }
  }

  public record Spawn(RespawnData spawn, SpawnSource source) {
  }

  public static final Codec<WorldGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.unboundedMap(WorldKind.CODEC, Identifier.CODEC).optionalFieldOf("mains", Map.of())
          .forGetter(group -> group.mains),
      Identifier.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(group -> List.copyOf(group.members)),
      RespawnData.CODEC.optionalFieldOf("spawn").forGetter(group -> Optional.ofNullable(group.spawn)))
      .apply(instance, WorldGroup::new));

  private final Map<WorldKind, Identifier> mains = new EnumMap<>(WorldKind.class);
  private final SequencedSet<Identifier> members = new LinkedHashSet<>();
  private @Nullable RespawnData spawn;

  public WorldGroup() {
  }

  private WorldGroup(Map<WorldKind, Identifier> mains, List<Identifier> members, Optional<RespawnData> spawn) {
    this.mains.putAll(mains);
    this.members.addAll(members);
    this.spawn = spawn.orElse(null);
  }

  public @Nullable Identifier main(WorldKind kind) {
    return this.mains.get(kind);
  }

  @Nullable Identifier setMain(WorldKind kind, Identifier world) {
    return this.mains.put(kind, world);
  }

  public boolean isMain(Identifier world) {
    return this.mains.containsValue(world);
  }

  public SequencedSet<Identifier> members() {
    return Collections.unmodifiableSequencedSet(this.members);
  }

  public boolean contains(Identifier world) {
    return this.members.contains(world);
  }

  boolean add(Identifier world) {
    return this.members.add(world);
  }

  boolean remove(Identifier world) {
    this.mains.values().remove(world);
    if (this.spawn != null && this.spawn.dimension().identifier().equals(world)) {
      this.spawn = null;
    }
    return this.members.remove(world);
  }

  public @Nullable RespawnData spawn() {
    return this.spawn;
  }

  void setSpawn(@Nullable RespawnData spawn) {
    this.spawn = spawn;
  }
}
