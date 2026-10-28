package dev.wroud.mc.worlds.manager.importer;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.worldupdate.RegionStorageUpgrader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.storage.LevelResource;

public final class MapRemapper {
  private static final String MAP_ID_KEY = "minecraft:map_id";
  private static final List<String> ITEM_HOLDERS = List.of("block_entities", "entities", "Entities");

  private MapRemapper() {
  }

  public static IntList sourceMapIds(StagedImport s) {
    IntList ids = new IntArrayList();
    Path folder = mapFile(sourceData(s), new MapId(0)).getParent();
    if (!Files.isDirectory(folder)) {
      return ids;
    }
    try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.dat")) {
      for (Path file : files) {
        String name = file.getFileName().toString();
        try {
          int id = Integer.parseInt(name.substring(0, name.length() - ".dat".length()));
          if (id >= 0 && Files.isRegularFile(file)) {
            ids.add(id);
          }
        } catch (NumberFormatException ignored) {
        }
      }
    } catch (IOException e) {
      McWorldMod.LOGGER.warn("Failed to list the maps in {}", folder, e);
    }
    ids.sort(null);
    return ids;
  }

  public static Int2IntMap allocate(MinecraftServer server, IntList ids) {
    Int2IntMap table = new Int2IntOpenHashMap(ids.size());
    for (int i = 0; i < ids.size(); i++) {
      table.put(ids.getInt(i), server.overworld().getFreeMapId().id());
    }
    return table;
  }

  static void remap(ImportJob job, StagedImport s, Int2IntMap table, List<ResolvedDimension> dims) {
    Map<String, String> dimensions = new HashMap<>();
    for (ResolvedDimension dim : dims) {
      dimensions.put(dim.sourceDim().toString(), dim.newKey().identifier().toString());
    }
    Path serverData = job.storage().getLevelPath(LevelResource.DATA);
    for (Int2IntMap.Entry entry : table.int2IntEntrySet()) {
      writeMap(s, mapFile(sourceData(s), new MapId(entry.getIntKey())),
          mapFile(serverData, new MapId(entry.getIntValue())), dimensions);
    }

    var builder = new RegionStorageUpgrader.Builder(DataFixers.getDataFixer())
        .trackProgress(job.progress())
        .addTagModifier(LevelDataResolver.currentVersion(), (pos, tag) -> replaceMapIds(tag, table));
    for (ResolvedDimension dim : dims) {
      CompoundTag context = ChunkMap.getChunkDataFixContextTag(
          DimensionDetectionUtil.chunkFixContextDimension(dim.newKey(), dim.stem().type()),
          dim.stem().generator().getTypeNameForDataFixer());
      ResourceKey<Level> sourceKey = ResourceKey.create(Registries.DIMENSION,
          dim.sourceDim());
      for (var upgrader : List.of(
          builder.copy().setDataFixType(DataFixTypes.CHUNK).setType("chunk").setFolderName("region")
              .setDataFixContextTag(context),
          builder.copy().setDataFixType(DataFixTypes.ENTITY_CHUNK).setTypeAndFolderName("entities"))) {
        var region = upgrader.build(0);
        region.init(sourceKey, s.access());
        region.upgrade();
      }
    }
  }

  private static Path sourceData(StagedImport s) {
    return s.access().getLevelPath(LevelResource.DATA);
  }

  private static Path mapFile(Path dataDir, MapId id) {
    return Identifier.withDefaultNamespace(id.key()).withSuffix(".dat").resolveAgainst(dataDir);
  }

  private static void writeMap(StagedImport s, Path source, Path target, Map<String, String> dimensions) {
    try {
      CompoundTag tag = s.rootData().readTagFromDisk(source, DataFixTypes.SAVED_DATA_MAP_DATA,
          LevelDataResolver.currentVersion());
      CompoundTag data = tag.getCompoundOrEmpty("data");
      String newDimension = data.getString("dimension").map(dimensions::get).orElse(null);
      if (newDimension != null) {
        data.putString("dimension", newDimension);
      } else {
        data.putBoolean("locked", true);
      }
      NbtUtils.addCurrentDataVersion(tag);
      Files.createDirectories(target.getParent());
      NbtIo.writeCompressed(tag, target);
    } catch (IOException | RuntimeException e) {
      McWorldMod.LOGGER.error("Failed to copy map {} to {}", source, target, e);
    }
  }

  private static boolean replaceMapIds(CompoundTag chunk, Int2IntMap table) {
    boolean changed = false;
    for (String key : ITEM_HOLDERS) {
      Tag holder = chunk.get(key);
      if (holder != null) {
        changed |= replaceIn(holder, table);
      }
    }
    return changed;
  }

  private static boolean replaceIn(Tag tag, Int2IntMap table) {
    boolean changed = false;
    if (tag instanceof CompoundTag compound) {
      for (String key : compound.keySet()) {
        Tag value = compound.get(key);
        if (MAP_ID_KEY.equals(key) && value instanceof IntTag(int id)) {
          if (table.containsKey(id)) {
            compound.putInt(key, table.get(id));
            changed = true;
          }
        } else {
          changed |= replaceIn(value, table);
        }
      }
    } else if (tag instanceof ListTag list) {
      for (Tag element : list) {
        changed |= replaceIn(element, table);
      }
    }
    return changed;
  }
}
