package dev.wroud.mc.worlds.manager.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.command.TeleportCommand;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.manager.WorldsCreator;
import dev.wroud.mc.worlds.manager.WorldsException;
import dev.wroud.mc.worlds.manager.importer.ImportResult.Line;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.manager.group.WorldKind;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LevelSettings;

public final class WorldImporter {
  private WorldImporter() {
  }

  private record Plan(SourceSave save, Map<Identifier, Identifier> targets, boolean all, @Nullable Identifier group) {
  }

  private record Prepared(Plan plan, StagedImport staged, List<ResolvedDimension> dims, IntList mapIds,
      @Nullable Difficulty difficulty) {
  }

  private record Remapped(Prepared prepared, Int2IntMap table) {
  }

  public static CompletableFuture<ImportResult> run(MinecraftServer server, Identifier id, String folder,
      @Nullable Identifier dimension, boolean all, @Nullable Identifier group,
      Consumer<Collection<Identifier>> started) {
    if (all && TemporaryIds.isTemporary(id)) {
      throw new ImportException("exception.temporary_all");
    }
    WorldsCreator.validLevelIdFormat(id);
    var manager = McWorldMod.getManager(server).orElseThrow();
    var label = ArchiveDownload.label(folder);
    var description = id + " from " + label;
    if (!manager.tryBeginImport(description)) {
      throw new ImportException("exception.busy", manager.currentImport().orElse(""));
    }
    manager.getTemporaryStorage().beginWorld(id);
    var job = new ImportJob(server, label, phase -> {
      McWorldMod.LOGGER.info("Import of {}: {}", description, phase);
      manager.setImportPhase(phase);
    });

    var result = new CompletableFuture<ImportResult>();
    CompletableFuture
        .supplyAsync(() -> plan(job, folder, id, dimension, all, group), Util.nonCriticalIoPool())
        .thenApplyAsync(plan -> validate(job, plan, started), server)
        .thenApplyAsync(plan -> prepare(job, plan), Util.ioPool())
        .thenCompose(prepared -> prepared.mapIds().isEmpty()
            ? CompletableFuture.completedFuture(new Remapped(prepared, Int2IntMaps.EMPTY_MAP))
            : CompletableFuture.supplyAsync(() -> allocateMaps(job, prepared), server)
                .thenApplyAsync(remapped -> remapMaps(job, remapped), Util.ioPool()))
        .thenApplyAsync(remapped -> install(job, remapped.prepared()), server)
        .handle((imported, error) -> finish(job, imported, error))
        .thenCompose(future -> future)
        .whenCompleteAsync((imported, error) -> {
          manager.endImport();
          if (error == null) {
            result.complete(imported);
          } else {
            result.completeExceptionally(toWorldsException(job, error));
          }
        }, server);
    return result;
  }

  private static WorldsException toWorldsException(ImportJob job, Throwable error) {
    var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (cause instanceof WorldsException worldsException) {
      return worldsException;
    }
    McWorldMod.LOGGER.error("Import from {} failed", job.folder(), cause);
    return ImportException.failed(job.folder(), cause);
  }

  private static Plan plan(ImportJob job, String folder, Identifier id, @Nullable Identifier dimension, boolean all,
      @Nullable Identifier group) {
    SourceSave save;
    try {
      StagingStage.clearStaging(job.worldDir());
      if (ArchiveDownload.isUrl(folder)) {
        var file = StagingStage.downloadFile(job.worldDir());
        ArchiveDownload.download(job, folder, file);
        save = SourceSave.openArchive(job, file);
      } else {
        save = SourceSave.resolve(job, folder);
      }
    } catch (IOException e) {
      throw job.failure(e);
    }
    var targets = save.targets(id, dimension, all);
    for (var source : targets.keySet()) {
      if (!LevelDataResolver.generatorAvailable(save.dimension(source), job.server().registryAccess())) {
        throw new ImportException("exception.datapack_dimension", source, save.folder());
      }
    }
    return new Plan(save, targets, all, group);
  }

  private static Plan validate(ImportJob job, Plan plan, Consumer<Collection<Identifier>> started) {
    checkTargets(job, plan);
    checkGroup(job, plan);
    started.accept(plan.targets().values());
    return plan;
  }

  private static Prepared prepare(ImportJob job, Plan plan) {
    var staged = StagingStage.stage(job, plan.save(), plan.targets().keySet());

    job.phase().accept("level data");
    var reg = job.server().registryAccess();
    var dims = new ArrayList<ResolvedDimension>();
    for (var target : plan.targets().entrySet()) {
      dims.add(LevelDataResolver.resolve(staged, plan.save(), plan.save().dimension(target.getKey()),
          ResourceKey.create(Registries.DIMENSION, target.getValue()), reg));
      LevelDataResolver.copyRootState(staged, target.getKey());
    }
    job.checkStopped();

    var difficulty = staged.levelTag().get("difficulty_settings").read(LevelSettings.DifficultySettings.CODEC)
        .result().map(LevelSettings.DifficultySettings::difficulty).orElse(null);
    return new Prepared(plan, staged, List.copyOf(dims), MapRemapper.sourceMapIds(staged), difficulty);
  }

  private static Remapped allocateMaps(ImportJob job, Prepared prepared) {
    job.checkStopped();
    job.phase().accept("maps");
    return new Remapped(prepared, MapRemapper.allocate(job.server(), prepared.mapIds()));
  }

  private static Remapped remapMaps(ImportJob job, Remapped remapped) {
    try {
      MapRemapper.remap(job, remapped.prepared().staged(), remapped.table(), remapped.prepared().dims());
    } catch (RuntimeException e) {
      throw job.failure(e);
    }
    job.checkStopped();
    return remapped;
  }

  private static ImportResult install(ImportJob job, Prepared prepared) {
    var server = job.server();
    job.phase().accept("installing");
    checkTargets(job, prepared.plan());
    checkMainSlots(job, prepared);

    var moved = new LinkedHashMap<Path, Path>();
    try {
      for (var d : prepared.dims()) {
        var from = prepared.staged().access()
            .getDimensionPath(ResourceKey.create(Registries.DIMENSION, d.sourceDim()));
        var to = job.storage().getDimensionPath(d.newKey());
        Files.createDirectories(to.getParent());
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        moved.put(from, to);
      }
    } catch (IOException e) {
      moved.forEach((from, to) -> {
        try {
          Files.move(to, from, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException rollback) {
          McWorldMod.LOGGER.error("Could not move {} back to {}", to, from, rollback);
        }
      });
      throw ImportException.failed(job.folder(), e);
    }

    var save = prepared.plan().save();
    var group = prepared.plan().group();
    var worlds = new ArrayList<Identifier>();
    var lines = new ArrayList<Line>();
    var notes = new ArrayList<Component>();
    for (var d : prepared.dims()) {
      var line = load(job, d, group);
      lines.add(line);
      if (!line.failure()) {
        worlds.add(d.newKey().identifier());
      }
      notes.addAll(d.warnings());
    }
    if (prepared.difficulty() != null && prepared.difficulty() != server.getWorldData().getDifficulty()) {
      notes.add(Component.translatable(ImportException.KEY_PREFIX + "warning.global"));
    }
    if (save.nested() != null) {
      notes.add(Component.translatable(ImportException.KEY_PREFIX + "success.nested", save.nested(), job.folder()));
    }
    if (prepared.plan().all()) {
      notes.add(group == null
          ? Component.translatable(ImportException.KEY_PREFIX + "success.portals")
          : Component.translatable(ImportException.KEY_PREFIX + "success.portals_group", group.toString()));
      var skipped = save.emptyDimensionIds();
      if (!skipped.isEmpty()) {
        notes.add(Component.translatable(ImportException.KEY_PREFIX + "success.skipped_empty",
            skipped.stream().map(Identifier::toString).collect(Collectors.joining(", "))));
      }
    }
    notes.forEach(note -> lines.add(new Line(note, false)));
    return new ImportResult(worlds, lines);
  }

  private static Line load(ImportJob job, ResolvedDimension d, @Nullable Identifier group) {
    var id = d.newKey().identifier();
    Object reason;
    try {
      if (WorldsCreator.registerAndLoad(job.server(), id, d.data()) != null) {
        var message = Component.translatable(ImportException.KEY_PREFIX + "success", id.toString(), job.folder(),
            d.sourceDim().toString());
        var join = group != null ? groups(job).join(id, group, false) : null;
        if (join != null) {
          message.append(join.main()
              ? Component.translatable(ImportException.KEY_PREFIX + "success.group_main", join.kind().key(),
                  group.toString())
              : Component.translatable(ImportException.KEY_PREFIX + "success.group_member", group.toString()));
        }
        return new Line(message.append(TeleportCommand.link(id)), false);
      }
      reason = Component.translatable(ImportException.KEY_PREFIX + "reason.see_log");
    } catch (Exception e) {
      McWorldMod.LOGGER.error("Imported world {} could not be loaded", id, e);
      reason = String.valueOf(e.getMessage());
    }
    return new Line(Component.translatable(ImportException.KEY_PREFIX + "result.not_loaded", id.toString(), reason),
        true);
  }

  private static void checkTargets(ImportJob job, Plan plan) {
    job.checkStopped();
    for (var target : plan.targets().values()) {
      WorldsCreator.validImportTarget(target, job.server());
    }
  }

  private static WorldGroups groups(ImportJob job) {
    return Objects.requireNonNull(WorldGroups.of(job.server()));
  }

  private static void checkGroup(ImportJob job, Plan plan) {
    var group = plan.group();
    if (group == null) {
      return;
    }
    if (groups(job).get(group) == null) {
      throw new ImportException("exception.unknown_group", group.toString());
    }
    for (var world : plan.targets().values()) {
      if (!groups(job).accepts(group, world)) {
        throw new ImportException("exception.not_temporary", world.toString(), group.toString());
      }
    }
  }

  private static void checkMainSlots(ImportJob job, Prepared prepared) {
    var group = prepared.plan().group();
    if (group == null) {
      return;
    }
    checkGroup(job, prepared.plan());
    var target = groups(job).get(group);
    for (var d : prepared.dims()) {
      var kind = WorldKind.of(d.stem().type());
      var existing = target.main(kind);
      if (existing != null) {
        throw new ImportException("exception.main_taken", group.toString(), kind.key(), existing.toString());
      }
    }
  }

  private static CompletableFuture<ImportResult> finish(ImportJob job, @Nullable ImportResult imported,
      @Nullable Throwable error) {
    job.closeArchive();
    var access = job.staging().getAndSet(null);
    if (access != null) {
      access.safeClose();
    }
    CompletableFuture<ImportResult> result = error == null
        ? CompletableFuture.completedFuture(imported)
        : CompletableFuture.failedFuture(error);
    if (!job.server().isRunning()) {
      return result;
    }
    return CompletableFuture.runAsync(() -> deleteStaging(job.worldDir()), Util.ioPool()).thenCompose(v -> result);
  }

  private static void deleteStaging(Path worldDir) {
    try {
      StagingStage.deleteStaging(worldDir);
    } catch (IOException e) {
      McWorldMod.LOGGER.warn("Could not delete the import staging folder", e);
    }
  }
}
