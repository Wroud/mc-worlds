package dev.wroud.mc.worlds.manager.group;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.WorldsException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
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

  static final String KEY = "dev.wroud.mc.worlds.command.group.";

  static Codec<WorldGroup> codec(WorldGroups groups, Identifier id) {
    var ops = groups.server().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    return RecordCodecBuilder.create(builder -> builder.group(
        Codec.unboundedMap(WorldKind.CODEC, Identifier.CODEC).optionalFieldOf("mains", Map.of())
            .forGetter(group -> group.mains),
        Identifier.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(group -> List.copyOf(group.members)),
        RespawnData.CODEC.optionalFieldOf("spawn").forGetter(group -> Optional.ofNullable(group.spawn)),
        Codec.BOOL.optionalFieldOf("frozen", false).forGetter(group -> group.frozen),
        Identifier.CODEC.optionalFieldOf("provider").forGetter(group -> Optional.ofNullable(group.provider)),
        Codec.PASSTHROUGH.optionalFieldOf("data")
            .forGetter(group -> Optional.ofNullable(group.instance).map(instance -> new Dynamic<>(ops, instance.save(ops)))))
        .apply(builder, (mains, members, spawn, frozen, provider, data) -> {
          var group = new WorldGroup(groups, id);
          group.mains.putAll(mains);
          group.members.addAll(members);
          group.spawn = spawn.orElse(null);
          group.frozen = frozen;
          provider.ifPresent(key -> group.load(key, data.orElse(null)));
          return group;
        }));
  }

  private final WorldGroups groups;
  private final Identifier id;
  private final Map<WorldKind, Identifier> mains = new EnumMap<>(WorldKind.class);
  private final SequencedSet<Identifier> members = new LinkedHashSet<>();
  private @Nullable RespawnData spawn;
  private volatile boolean frozen;
  private @Nullable Identifier provider;
  private @Nullable GroupInstance instance;

  WorldGroup(WorldGroups groups, Identifier id) {
    this.groups = groups;
    this.id = id;
  }

  void attach(Identifier provider, GroupInstance instance) {
    this.provider = provider;
    this.instance = instance;
  }

  private void load(Identifier provider, @Nullable Dynamic<?> saved) {
    var factory = WorldGroups.provider(provider);
    if (factory == null) {
      McWorldMod.LOGGER.warn("Group {} has no instance: provider {} is not registered", this.id, provider);
      return;
    }
    try {
      this.attach(provider, factory.create(this, saved));
    } catch (RuntimeException e) {
      McWorldMod.LOGGER.error("Group {} has no instance: provider {} failed to load it", this.id, provider, e);
    }
  }

  public Identifier id() {
    return this.id;
  }

  public MinecraftServer server() {
    return this.groups.server();
  }

  public @Nullable GroupInstance instance() {
    return this.instance;
  }

  public boolean isDeleted() {
    return this.groups.get(this.id) != this;
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

  public Join join(Identifier world, boolean asMain) {
    this.requireLive();
    this.groups.requireGroupable(world);
    if (!WorldGroups.accepts(this.id, world)) {
      throw new WorldsException(KEY + "join.exception.not_temporary", world, this.id);
    }
    if (asMain && !WorldKind.of(this.server(), world).isMain()) {
      throw new WorldsException(KEY + "join.exception.other", world);
    }
    return this.groups.join(this, world, asMain);
  }

  public void leave(Identifier world) {
    this.requireLive();
    if (!this.contains(world)) {
      throw new WorldsException(KEY + "leave.exception.not_member", world, this.id);
    }
    this.groups.leave(world);
  }

  public Spawn spawn() {
    return this.groups.spawnOf(this.id);
  }

  @Nullable RespawnData spawnData() {
    return this.spawn;
  }

  public void setSpawn(@Nullable RespawnData spawn) {
    this.requireLive();
    if (spawn != null && !this.contains(spawn.dimension().identifier())) {
      throw new WorldsException(KEY + "spawn.exception.outside", this.id);
    }
    this.spawn = spawn;
    this.groups.spawnChanged(this.id);
  }

  public boolean isFrozen() {
    return this.frozen;
  }

  public void setFrozen(boolean frozen) {
    this.requireLive();
    this.frozen = frozen;
    this.markDirty();
  }

  void markDirty() {
    this.groups.markDirty();
  }

  public CompletableFuture<Boolean> returnTo(ServerPlayer player) {
    this.requireLive();
    return this.groups.transitions().returnTo(this, player);
  }

  public GameRules rules() {
    this.requireLive();
    return this.groups.gameRules(this.id);
  }

  public void delete() {
    this.requireLive();
    this.groups.delete(this);
  }

  private void requireLive() {
    if (this.isDeleted()) {
      throw WorldGroups.unknownGroup(this.id);
    }
  }
}
