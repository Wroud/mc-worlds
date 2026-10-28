package dev.wroud.mc.worlds.manager.importer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.ChunkPos;

public final class RegionHeaders {
  private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
  private static final int SECTOR_BYTES = 4096;
  private static final int ENTRIES = 1024;

  private RegionHeaders() {
  }

  public static boolean hasStoredChunk(Path regionDir) {
    if (!Files.isDirectory(regionDir)) {
      return false;
    }
    try (Stream<Path> files = Files.list(regionDir)) {
      return files.anyMatch(file -> !storedChunks(file).isEmpty());
    } catch (IOException | UncheckedIOException e) {
      return false;
    }
  }

  public static List<ChunkPos> storedChunks(Path regionFile) {
    Matcher matcher = REGION_FILE.matcher(regionFile.getFileName().toString());
    if (!matcher.matches() || !Files.isRegularFile(regionFile)) {
      return List.of();
    }
    ChunkPos min;
    IntBuffer locations;
    try {
      min = ChunkPos.minFromRegion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
      locations = readLocations(regionFile);
    } catch (IOException | NumberFormatException e) {
      return List.of();
    }
    List<ChunkPos> chunks = new ArrayList<>();
    for (int index = 0; locations != null && index < ENTRIES; index++) {
      if (locations.get(index) != 0) {
        chunks.add(new ChunkPos(min.x() + (index & 31), min.z() + (index >> 5)));
      }
    }
    return chunks;
  }

  private static @Nullable IntBuffer readLocations(Path regionFile) throws IOException {
    if (Files.size(regionFile) < SECTOR_BYTES * 2) {
      return null;
    }
    ByteBuffer header = ByteBuffer.allocate(SECTOR_BYTES);
    try (SeekableByteChannel channel = Files.newByteChannel(regionFile, StandardOpenOption.READ)) {
      while (header.hasRemaining()) {
        if (channel.read(header) < 0) {
          return null;
        }
      }
    }
    return header.flip().asIntBuffer();
  }
}
