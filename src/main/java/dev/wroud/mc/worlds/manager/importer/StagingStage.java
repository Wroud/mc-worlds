package dev.wroud.mc.worlds.manager.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

import com.mojang.serialization.Dynamic;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.nbt.NbtFormatException;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.LevelVersion;
import net.minecraft.world.level.storage.SavedDataStorage;

public final class StagingStage {
  private static final String STAGING_DIR = "staging";
  private static final String DOWNLOAD_FILE = "download.zip";
  private static final List<String> ROOT_PARTS = List.of(LevelResource.LEVEL_DATA_FILE.id(),
      LevelResource.OLD_LEVEL_DATA_FILE.id(), LevelResource.DATA.id());

  private StagingStage() {
  }

  private static Path stagingRoot(Path worldDir) {
    return worldDir.resolve(McWorldMod.MOD_ID).resolve(STAGING_DIR);
  }

  static void clearStaging(Path worldDir) throws IOException {
    FileUtils.deleteDirectory(stagingRoot(worldDir).toFile());
  }

  static Path downloadFile(Path worldDir) throws IOException {
    Path stagingRoot = stagingRoot(worldDir);
    Files.createDirectories(stagingRoot);
    return stagingRoot.resolve(DOWNLOAD_FILE);
  }

  public static void deleteStaging(Path worldDir) throws IOException {
    clearStaging(worldDir);
    Path parent = stagingRoot(worldDir).getParent();
    if (Files.isDirectory(parent)) {
      try (var entries = Files.list(parent)) {
        if (entries.findAny().isEmpty()) {
          Files.delete(parent);
        }
      }
    }
  }

  static StagedImport stage(ImportJob job, SourceSave save, Set<Identifier> sources) throws ImportException {
    Path stagingRoot = stagingRoot(job.worldDir());
    try {
      job.phase().accept("copying");
      String levelId = UUID.randomUUID().toString();
      Path stagedDir = stagingRoot.resolve(levelId);
      Files.createDirectories(stagedDir);

      Map<Path, Copy> files = filesToCopy(save, partsToCopy(save, sources), stagedDir);
      checkDiskSpace(job, files.values());
      copy(job, files);
      job.closeArchive();

      job.phase().accept("checking");
      LevelStorageSource.LevelStorageAccess access = LevelStorageSource.createDefault(stagingRoot)
          .validateAndCreateAccess(levelId);
      job.staging().set(access);
      Dynamic<?> unfixedTag = access.getUnfixedDataTagWithFallback();
      checkSummary(access, unfixedTag, save.folder());
      job.checkStopped();

      job.phase().accept("upgrading");
      Dynamic<?> levelTag = DataFixers.getFileFixer().fix(access, unfixedTag, job.progress());
      job.checkStopped();
      return new StagedImport(access, levelTag, new SavedDataStorage(access.getLevelPath(LevelResource.DATA),
          DataFixers.getDataFixer(), job.server().registryAccess()));
    } catch (ImportException e) {
      throw e;
    } catch (Exception e) {
      throw job.failure(e);
    }
  }

  private static List<String> partsToCopy(SourceSave save, Set<Identifier> sources) {
    List<String> parts = new ArrayList<>(ROOT_PARTS);
    for (SourceDimension dimension : save.dimensions()) {
      if (sources.contains(dimension.id())) {
        for (Path part : dimension.parts()) {
          parts.add(save.root().relativize(part).toString());
        }
      }
    }
    return parts;
  }

  private static Map<Path, Copy> filesToCopy(SourceSave save, List<String> parts, Path stagedDir)
      throws IOException {
    Path base = stagedDir.normalize();
    Map<Path, Copy> files = new LinkedHashMap<>();
    for (String part : parts) {
      Path source = save.root().resolve(part);
      if (!Files.exists(source)) {
        continue;
      }
      Path targetPart = base.resolve(part);
      Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
              Files.createDirectories(target(dir));
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              if (!file.getFileName().toString().equals(LevelResource.LOCK_FILE.id())) {
                files.put(file, new Copy(target(file), attrs.size()));
              }
              return FileVisitResult.CONTINUE;
            }

            private Path target(Path path) throws IOException {
              Path target = targetPart.resolve(source.relativize(path).toString()).normalize();
              if (!target.startsWith(base)) {
                throw new IOException(path + " points outside the world");
              }
              return target;
            }
          });
    }
    return files;
  }

  private static void checkDiskSpace(ImportJob job, Collection<Copy> files) throws ImportException {
    long required = 0;
    for (Copy file : files) {
      required += file.size();
    }
    if (required > job.diskBudget()) {
      throw new ImportException("exception.disk_space", job.folder());
    }
  }

  private static void copy(ImportJob job, Map<Path, Copy> files) throws IOException, ImportException {
    for (Map.Entry<Path, Copy> entry : files.entrySet()) {
      Path source = entry.getKey();
      try (InputStream in = Files.newInputStream(source);
          OutputStream out = Files.newOutputStream(entry.getValue().target())) {
        job.copy(in, out, entry.getValue().size(),
            () -> ImportException.failed(job.folder(), new IOException(source + " is larger than its declared size")),
            copied -> {
            });
      }
    }
    job.checkStopped();
  }

  private record Copy(Path target, long size) {
  }

  private static void checkSummary(LevelStorageSource.LevelStorageAccess access, Dynamic<?> unfixedTag,
      String folder) throws ImportException {
    LevelSummary summary;
    try {
      summary = access.fixAndGetSummaryFromTag(unfixedTag);
    } catch (NbtFormatException e) {
      throw new ImportException(e, "exception.incompatible", folder, LevelVersion.parse(unfixedTag).minecraftVersionName());
    }
    String version = summary.levelVersion().minecraftVersionName();
    if (summary.requiresManualConversion() || !summary.isCompatible()) {
      throw new ImportException("exception.incompatible", folder, version);
    }
    if (summary.isDowngrade()) {
      throw new ImportException("exception.newer_version", folder, version);
    }
  }
}
