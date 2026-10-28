package dev.wroud.mc.worlds.manager.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.WorldsData;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

public record SourceSave(Path root, String folder, @Nullable String nested, List<SourceDimension> dimensions) {
  private static final String ZIP_EXTENSION = ".zip";
  private static final List<Map<String, String>> ZIP_OPTIONS = List.of(Map.of("accessMode", "readOnly"),
      Map.of("accessMode", "readOnly", "encoding", "Cp437"));
  private static final List<String> ARCHIVE_EXTENSIONS = List.of(".tar.gz", ".tgz", ".tar", ".rar", ".7z");
  private static final List<Identifier> VANILLA_ORDER = List.of(Level.OVERWORLD.identifier(),
      Level.NETHER.identifier(), Level.END.identifier());
  private static final Map<String, Identifier> LEGACY_FOLDERS = Map.of("region", Level.OVERWORLD.identifier(),
      "DIM-1", Level.NETHER.identifier(), "DIM1", Level.END.identifier());

  public List<Identifier> dimensionIds() {
    return dimensions.stream().map(SourceDimension::id).toList();
  }

  public @Nullable SourceDimension dimension(Identifier id) {
    return dimensions.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
  }

  public List<Identifier> emptyDimensionIds() {
    return dimensions.stream().filter(SourceDimension::empty).map(SourceDimension::id).toList();
  }

  public Map<Identifier, Identifier> targets(Identifier baseId, @Nullable Identifier dimension, boolean all)
      throws ImportException {
    List<Identifier> nonEmpty = dimensions.stream().filter(d -> !d.empty()).map(SourceDimension::id).toList();
    if (all) {
      if (nonEmpty.isEmpty()) {
        throw new ImportException("exception.empty_dimension", allDimensionsLabel(), folder);
      }
      Map<Identifier, Identifier> targets = new LinkedHashMap<>();
      for (Identifier source : nonEmpty) {
        Identifier target = derivedId(baseId, source);
        if (targets.containsValue(target)) {
          List<Identifier> colliding = nonEmpty.stream().filter(id -> derivedId(baseId, id).equals(target)).toList();
          throw new ImportException("exception.id_collision", join(colliding), folder, target);
        }
        targets.put(source, target);
      }
      return targets;
    }
    if (dimension != null) {
      SourceDimension source = dimension(dimension);
      if (source == null) {
        throw new ImportException("exception.no_dimension", folder, dimension);
      }
      if (source.empty()) {
        throw new ImportException("exception.empty_dimension", dimension, folder);
      }
      return Map.of(dimension, baseId);
    }
    Identifier overworld = Level.OVERWORLD.identifier();
    if (nonEmpty.contains(overworld)) {
      return Map.of(overworld, baseId);
    }
    if (nonEmpty.size() == 1) {
      return Map.of(nonEmpty.getFirst(), baseId);
    }
    if (nonEmpty.isEmpty()) {
      throw new ImportException("exception.empty_dimension", allDimensionsLabel(), folder);
    }
    throw new ImportException("exception.choose_dimension", folder, join(nonEmpty));
  }

  static Identifier derivedId(Identifier baseId, Identifier source) {
    if (source.equals(Level.OVERWORLD.identifier())) {
      return baseId;
    }
    String suffix;
    if (source.equals(Level.NETHER.identifier())) {
      suffix = "nether";
    } else if (source.equals(Level.END.identifier())) {
      suffix = "the_end";
    } else if (source.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
      suffix = source.getPath();
    } else {
      suffix = source.getNamespace() + "_" + source.getPath();
    }
    return baseId.withPath(baseId.getPath() + "_" + suffix.replace('/', '_'));
  }

  private String allDimensionsLabel() {
    return dimensions.isEmpty() ? Level.OVERWORLD.identifier().toString() : join(dimensionIds());
  }

  private static String join(List<Identifier> ids) {
    return ids.stream().map(Identifier::toString).collect(Collectors.joining(", "));
  }

  static SourceSave resolve(ImportJob job, String folder) throws ImportException {
    LevelStorageSource.LevelStorageAccess access = job.storage();
    LevelStorageSource storage = access.parent();
    Path baseDir = storage.getBaseDir();
    Path folderPath = folderPath(baseDir, folder);
    if (folderPath == null) {
      throw new ImportException("exception.invalid_folder", folder);
    }
    if (folder.equals(access.getLevelId())) {
      throw new ImportException("exception.own_world", folder);
    }
    if (Files.isRegularFile(folderPath)) {
      return openArchive(job, folderPath);
    }
    Path root = folderPath;
    String nested = null;
    if (!hasLevelData(folderPath)) {
      Path inner = singleNestedSave(folderPath);
      if (inner == null) {
        Path zip = baseDir.resolve(folder + ZIP_EXTENSION);
        if (Files.isRegularFile(zip)) {
          return openArchive(job, zip);
        }
        for (String extension : ARCHIVE_EXTENSIONS) {
          String archive = folder + extension;
          if (Files.isRegularFile(baseDir.resolve(archive))) {
            throw new ImportException("exception.archive", archive);
          }
        }
        throw new ImportException("exception.not_found", folder);
      }
      root = inner;
      nested = inner.getFileName().toString();
    }
    try {
      if (!storage.getWorldDirValidator().validateDirectory(folderPath, true).isEmpty()) {
        throw new ImportException("exception.symlink", folder);
      }
      if (DirectoryLock.isLocked(root)) {
        throw new ImportException("exception.locked", folder);
      }
      return new SourceSave(root, folder, nested, discoverDimensions(root));
    } catch (OverlappingFileLockException e) {
      throw new ImportException("exception.own_world", folder);
    } catch (IOException e) {
      throw job.failure(e);
    }
  }

  static SourceSave openArchive(ImportJob job, Path file) throws ImportException {
    FileSystem fileSystem = openZip(file, job.folder());
    job.archive().set(fileSystem);
    Path root = saveRoot(fileSystem.getPath("/"));
    if (root == null) {
      throw new ImportException("exception.no_save", job.folder());
    }
    String nested = root.getFileName() == null ? null : root.getFileName().toString();
    try {
      return new SourceSave(root, job.folder(), nested, discoverDimensions(root));
    } catch (IOException e) {
      throw job.failure(e);
    }
  }

  private static boolean isZip(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      byte[] magic = in.readNBytes(4);
      return magic.length == 4 && magic[0] == 'P' && magic[1] == 'K'
          && ((magic[2] == 3 && magic[3] == 4) || (magic[2] == 5 && magic[3] == 6));
    } catch (IOException e) {
      return false;
    }
  }

  private static FileSystem openZip(Path file, String label) throws ImportException {
    if (!isZip(file)) {
      throw new ImportException("exception.archive", label);
    }
    IOException failure = null;
    for (Map<String, String> options : ZIP_OPTIONS) {
      try {
        return Util.ZIP_FILE_SYSTEM_PROVIDER.newFileSystem(file, options);
      } catch (IOException e) {
        failure = failure == null ? e : failure;
      }
    }
    throw new ImportException(failure, "exception.bad_archive", label, failure);
  }

  private static @Nullable Path saveRoot(Path dir) {
    return hasLevelData(dir) ? dir : singleNestedSave(dir);
  }

  public static List<String> suggestFolders(LevelStorageSource.LevelStorageAccess access) {
    Path baseDir = access.parent().getBaseDir();
    try (Stream<Path> entries = Files.list(baseDir)) {
      Stream<String> saves = access.parent().findLevelCandidates().levels().stream()
          .map(LevelStorageSource.LevelDirectory::directoryName);
      Stream<String> others = entries
          .filter(path -> isZipName(path) ? Files.isRegularFile(path) : singleNestedSave(path) != null)
          .map(path -> path.getFileName().toString());
      return Stream.concat(saves, others)
          .filter(name -> !name.equals(access.getLevelId()) && !name.equals(McWorldMod.MOD_ID))
          .sorted()
          .toList();
    } catch (IOException | RuntimeException e) {
      return List.of();
    }
  }

  public static List<Identifier> suggestDimensions(LevelStorageSource.LevelStorageAccess access, String folder) {
    Path folderPath = folderPath(access.parent().getBaseDir(), folder);
    if (folderPath == null) {
      return List.of();
    }
    if (!Files.isRegularFile(folderPath)) {
      return dimensionIds(saveRoot(folderPath));
    }
    try (FileSystem fileSystem = openZip(folderPath, folder)) {
      return dimensionIds(saveRoot(fileSystem.getPath("/")));
    } catch (IOException | ImportException e) {
      return List.of();
    }
  }

  private static List<Identifier> dimensionIds(@Nullable Path root) {
    if (root == null) {
      return List.of();
    }
    try {
      List<Identifier> ids = new ArrayList<>(modernDimensions(root).keySet());
      LEGACY_FOLDERS.forEach((name, id) -> {
        if (Files.isDirectory(root.resolve(name))) {
          ids.add(id);
        }
      });
      return ids;
    } catch (IOException e) {
      return List.of();
    }
  }

  private static boolean isZipName(Path path) {
    return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION);
  }

  private static @Nullable Path folderPath(Path baseDir, String folder) {
    if (folder.isEmpty() || folder.equals(".") || folder.equals("..") || !FileUtil.isPathPartPortable(folder)) {
      return null;
    }
    for (char illegal : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
      if (folder.indexOf(illegal) >= 0) {
        return null;
      }
    }
    try {
      Path path = baseDir.resolve(folder);
      return Objects.equals(path.getParent(), baseDir) && path.getFileName().toString().equals(folder) ? path : null;
    } catch (InvalidPathException e) {
      return null;
    }
  }

  private static boolean hasLevelData(Path dir) {
    LevelStorageSource.LevelDirectory directory = new LevelStorageSource.LevelDirectory(dir);
    return Files.isRegularFile(directory.dataFile()) || Files.isRegularFile(directory.oldDataFile());
  }

  private static @Nullable Path singleNestedSave(Path dir) {
    if (!Files.isDirectory(dir) || hasLevelData(dir) || Files.isDirectory(dir.resolve("region"))) {
      return null;
    }
    try (Stream<Path> children = Files.list(dir)) {
      List<Path> saves = children.filter(Files::isDirectory).filter(SourceSave::hasLevelData).limit(2).toList();
      return saves.size() == 1 ? saves.getFirst() : null;
    } catch (IOException e) {
      return null;
    }
  }

  private static List<SourceDimension> discoverDimensions(Path root) throws IOException {
    Predicate<Identifier> storedGenerator = storedGenerators(root);
    Map<Identifier, SourceDimension> found = new LinkedHashMap<>();
    modernDimensions(root).forEach((id, dir) -> found.put(id, new SourceDimension(id, List.of(dir),
        !RegionHeaders.hasStoredChunk(dir.resolve("region")),
        storedGenerator.test(id) || hasOwnGenerator(dir))));
    Path legacyRegion = root.resolve("region");
    if (Files.isDirectory(legacyRegion)) {
      List<Path> parts = Stream.of("region", "entities", "poi").map(root::resolve).filter(Files::isDirectory)
          .toList();
      addLegacy(found, Level.OVERWORLD.identifier(), parts, legacyRegion);
    }
    for (String name : List.of("DIM-1", "DIM1")) {
      Path dir = root.resolve(name);
      if (Files.isDirectory(dir)) {
        addLegacy(found, LEGACY_FOLDERS.get(name), List.of(dir), dir.resolve("region"));
      }
    }
    return found.values().stream().sorted(Comparator.comparingInt((SourceDimension d) -> vanillaRank(d.id()))
        .thenComparing(d -> d.id().toString())).toList();
  }

  private static Predicate<Identifier> storedGenerators(Path root) {
    Path data = root.resolve(LevelResource.DATA.id());
    if (Files.exists(WorldsData.TYPE.id().withSuffix(".dat").resolveAgainst(data))
        || Files.exists(data.resolve(WorldsData.LEGACY_FILE))) {
      return id -> true;
    }
    Set<String> keys = new HashSet<>();
    keys.addAll(compoundKeys(WorldGenSettings.TYPE.id().withSuffix(".dat").resolveAgainst(data), "data",
        "dimensions"));
    keys.addAll(compoundKeys(root.resolve(LevelResource.LEVEL_DATA_FILE.id()), "Data", "WorldGenSettings",
        "dimensions"));
    return id -> keys.contains(id.toString());
  }

  private static Set<String> compoundKeys(Path file, String... path) {
    if (!Files.isRegularFile(file)) {
      return Set.of();
    }
    try {
      CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
      for (String key : path) {
        tag = tag.getCompoundOrEmpty(key);
      }
      return tag.keySet();
    } catch (IOException | RuntimeException e) {
      return Set.of();
    }
  }

  private static boolean hasOwnGenerator(Path dimensionDir) {
    return Files.exists(WorldGenSettings.TYPE.id().withSuffix(".dat")
        .resolveAgainst(dimensionDir.resolve(LevelResource.DATA.id())));
  }

  private static Map<Identifier, Path> modernDimensions(Path root) throws IOException {
    Map<Identifier, Path> found = new LinkedHashMap<>();
    Path dimensionsDir = root.resolve("dimensions");
    if (Files.isDirectory(dimensionsDir)) {
      for (Path namespaceDir : subdirectories(dimensionsDir)) {
        String namespace = namespaceDir.getFileName().toString();
        if (Identifier.isValidNamespace(namespace)) {
          collectModern(namespace, namespaceDir, "", found);
        }
      }
    }
    return found;
  }

  private static void collectModern(String namespace, Path dir, String path, Map<Identifier, Path> found)
      throws IOException {
    if (!path.isEmpty() && isDimensionFolder(dir)) {
      Identifier id = Identifier.tryBuild(namespace, path);
      if (id != null) {
        found.put(id, dir);
      }
      return;
    }
    for (Path child : subdirectories(dir)) {
      String name = child.getFileName().toString();
      if (Identifier.isValidPath(name) && !name.contains("/")) {
        collectModern(namespace, child, path.isEmpty() ? name : path + "/" + name, found);
      }
    }
  }

  private static void addLegacy(Map<Identifier, SourceDimension> found, Identifier id, List<Path> parts,
      Path regionDir) {
    found.putIfAbsent(id, new SourceDimension(id, parts, !RegionHeaders.hasStoredChunk(regionDir), true));
  }

  private static boolean isDimensionFolder(Path dir) {
    return Files.isDirectory(dir.resolve("region")) || Files.isDirectory(dir.resolve("entities"))
        || Files.isDirectory(dir.resolve("data"));
  }

  private static List<Path> subdirectories(Path dir) throws IOException {
    try (Stream<Path> children = Files.list(dir)) {
      return children.filter(Files::isDirectory).sorted().toList();
    }
  }

  private static int vanillaRank(Identifier id) {
    int index = VANILLA_ORDER.indexOf(id);
    return index < 0 ? VANILLA_ORDER.size() : index;
  }
}
