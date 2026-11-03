package dev.wroud.mc.worlds.manager.group.transition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;

final class GroupProfiles {
  static final AttachmentType<Identifier> MARKER = AttachmentRegistry.create(McWorldMod.id("profile_group"),
      builder -> builder.copyOnDeath());
  private static final Set<String> TRANSITION_KEYS = Set.of("Pos", "Motion", "Rotation", "Dimension", "fall_distance",
      "OnGround", "PortalCooldown", "RootVehicle");
  private static final String NAME = "Name";
  private static final String GROUP = "Group";
  private static final String HOME = "Home";

  private final MinecraftServer server;
  private final WorldGroups worldGroups;
  private final Path groups;
  private final Path records;

  GroupProfiles(MinecraftServer server, WorldGroups worldGroups) {
    this.server = server;
    this.worldGroups = worldGroups;
    var players = ((MinecraftServerAccessor) server).getStorageSource().getLevelPath(LevelResource.PLAYER_DATA_DIR)
        .getParent();
    this.groups = players.resolve("groups");
    this.records = players.resolve(McWorldMod.MOD_ID);
  }

  static @Nullable Identifier markerOf(ServerPlayer player) {
    return player.getAttached(MARKER);
  }

  static Identifier groupOf(ServerPlayer player) {
    var marker = markerOf(player);
    return marker != null ? marker : WorldGroups.groupOrDefault(player.level());
  }

  static ServerPlayer.SavedPosition positionOf(CompoundTag tag) {
    return ServerPlayer.SavedPosition.MAP_CODEC.codec().parse(NbtOps.INSTANCE, tag).result()
        .orElse(ServerPlayer.SavedPosition.EMPTY);
  }

  static @Nullable ResourceKey<Level> dimensionOf(CompoundTag tag) {
    return positionOf(tag).dimension().orElse(null);
  }

  static @Nullable Identifier groupOf(CompoundTag record) {
    return record.getString(GROUP).map(Identifier::tryParse).orElse(null);
  }

  static CompoundTag withPosition(CompoundTag profile, CompoundTag where) {
    for (var key : TRANSITION_KEYS) {
      var value = where.get(key);
      if (value != null) {
        profile.put(key, value.copy());
      } else {
        profile.remove(key);
      }
    }
    return profile;
  }

  static void stripPosition(CompoundTag tag) {
    TRANSITION_KEYS.forEach(tag::remove);
  }

  UUID storageId(NameAndId nameAndId) {
    var owner = this.server.getWorldData().getSinglePlayerUUID();
    return owner != null && this.server.isSingleplayerOwner(nameAndId) ? owner : nameAndId.id();
  }

  CompoundTag tagOf(ServerPlayer player) {
    try (var reporter = new ProblemReporter.ScopedCollector(player.problemPath(), McWorldMod.LOGGER)) {
      var output = TagValueOutput.createWithContext(reporter, player.registryAccess());
      player.saveWithoutId(output);
      return output.buildResult();
    }
  }

  void writeRecord(ServerPlayer player, Identifier group, CompoundTag tag) {
    var record = new CompoundTag();
    record.putString(NAME, player.getPlainTextName());
    record.putString(GROUP, group.toString());
    withPosition(record, tag);
    if (TemporaryIds.isTemporary(group)) {
      this.record(player.getUUID()).flatMap(this::homeFor)
          .ifPresent(home -> record.put(HOME, home));
    }
    this.write(this.recordFile(player.getUUID()), record);
  }

  private Optional<CompoundTag> homeFor(CompoundTag old) {
    var kept = this.validHome(old);
    if (kept.isPresent()) {
      return kept;
    }
    var oldGroup = groupOf(old);
    if (dimensionOf(old) == null || oldGroup != null && TemporaryIds.isTemporary(oldGroup)
        || this.inTemporaryGroup(old)) {
      return Optional.empty();
    }
    return Optional.of(withPosition(new CompoundTag(), old));
  }

  Optional<CompoundTag> validHome(CompoundTag record) {
    return record.getCompound(HOME).filter(home -> !this.inTemporaryGroup(home));
  }

  private boolean inTemporaryGroup(CompoundTag position) {
    var dimension = dimensionOf(position);
    return dimension != null && TemporaryIds.isTemporary(this.worldGroups.groupOrDefault(dimension.identifier()));
  }

  void deleteRecord(UUID uuid) {
    var file = this.recordFile(uuid);
    try {
      Files.deleteIfExists(file);
      Files.deleteIfExists(oldFile(file));
    } catch (IOException e) {
      McWorldMod.LOGGER.warn("Could not delete {}", file, e);
    }
  }

  Optional<CompoundTag> record(UUID uuid) {
    return this.read(this.recordFile(uuid));
  }

  void write(Identifier group, UUID uuid, CompoundTag tag) {
    this.write(this.profileFile(group, uuid), tag);
  }

  Optional<CompoundTag> load(Identifier group, NameAndId nameAndId) {
    if (WorldGroups.isDefault(group)) {
      return this.server.getPlayerList().loadPlayerData(nameAndId);
    }
    return this.read(this.profileFile(group, this.storageId(nameAndId)));
  }

  private void write(Path file, CompoundTag tag) {
    try {
      Files.createDirectories(file.getParent());
      var tmp = Files.createTempFile(file.getParent(), file.getFileName() + "-", ".tmp");
      NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(tag), tmp);
      Util.safeReplaceFile(file, tmp, oldFile(file));
    } catch (IOException e) {
      McWorldMod.LOGGER.warn("Failed to write {}", file, e);
    }
  }

  private Optional<CompoundTag> read(Path file) {
    return this.readOne(file).or(() -> this.readOne(oldFile(file)))
        .map(tag -> DataFixTypes.PLAYER.updateToCurrentVersion(this.server.getFixerUpper(), tag,
            NbtUtils.getDataVersion(tag)));
  }

  private Optional<CompoundTag> readOne(Path file) {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
    } catch (IOException e) {
      McWorldMod.LOGGER.warn("Failed to read {}", file, e);
      return Optional.empty();
    }
  }

  Path statsFile(Identifier group, UUID uuid) {
    return this.groupFolder(group).resolve("stats").resolve(uuid + ".json");
  }

  Path advancementsFile(Identifier group, UUID uuid) {
    return this.groupFolder(group).resolve("advancements").resolve(uuid + ".json");
  }

  void deleteGroup(Identifier group) {
    deleteFolder(this.groupFolder(group));
  }

  void deleteTemporaryGroups() {
    deleteFolder(this.groups.resolve(TemporaryIds.NAMESPACE));
  }

  private static void deleteFolder(Path folder) {
    try {
      FileUtils.deleteDirectory(folder.toFile());
    } catch (IOException e) {
      McWorldMod.LOGGER.warn("Could not delete {}", folder, e);
    }
  }

  private Path profileFile(Identifier group, UUID uuid) {
    return this.groupFolder(group).resolve("data").resolve(uuid + ".dat");
  }

  private Path groupFolder(Identifier group) {
    return this.groups.resolve(group.getNamespace()).resolve(group.getPath());
  }

  private Path recordFile(UUID uuid) {
    return this.records.resolve(uuid + ".dat");
  }

  private static Path oldFile(Path file) {
    return file.resolveSibling(file.getFileName() + "_old");
  }
}
