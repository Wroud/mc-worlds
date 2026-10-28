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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.command.TeleportCommand;
import dev.wroud.mc.worlds.manager.WorldsCreator;
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

  public record Line(Component message, boolean failure) {
  }

  private record Plan(SourceSave save, Map<Identifier, Identifier> targets, boolean all) {
  }

  private record Prepared(Plan plan, StagedImport staged, List<ResolvedDimension> dims, IntList mapIds,
      @Nullable Difficulty difficulty) {
  }

  private record Remapped(Prepared prepared, Int2IntMap table) {
  }

  public static CompletableFuture<List<Line>> run(MinecraftServer server, Identifier id, String folder, String label,
      @Nullable Identifier dimension, boolean all, Consumer<Collection<Identifier>> started) {
    var mcWorld = McWorldMod.getMcWorld(server).orElseThrow();
    var description = id + " from " + label;
    if (!mcWorld.tryBeginImport(description)) {
      return CompletableFuture.failedFuture(
          new ImportException("exception.busy", mcWorld.currentImport().orElse("")));
    }
    var job = new ImportJob(server, label, phase -> {
      McWorldMod.LOGGER.info("Import of {}: {}", description, phase);
      mcWorld.setImportPhase(phase);
    });

    return CompletableFuture
        .supplyAsync(() -> call(() -> plan(job, folder, id, dimension, all)), Util.nonCriticalIoPool())
        .thenApplyAsync(plan -> call(() -> validate(job, plan, started)), server)
        .thenApplyAsync(plan -> call(() -> prepare(job, plan)), Util.ioPool())
        .thenCompose(prepared -> prepared.mapIds().isEmpty()
            ? CompletableFuture.completedFuture(new Remapped(prepared, Int2IntMaps.EMPTY_MAP))
            : CompletableFuture.supplyAsync(() -> call(() -> allocateMaps(job, prepared)), server)
                .thenApplyAsync(remapped -> call(() -> remapMaps(job, remapped)), Util.ioPool()))
        .thenApplyAsync(remapped -> call(() -> install(job, remapped.prepared())), server)
        .handle((lines, error) -> finish(job, lines, error))
        .thenCompose(future -> future)
        .whenComplete((lines, error) -> mcWorld.endImport());
  }

  private static Plan plan(ImportJob job, String folder, Identifier id, @Nullable Identifier dimension, boolean all)
      throws ImportException {
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
    return new Plan(save, targets, all);
  }

  private static Plan validate(ImportJob job, Plan plan, Consumer<Collection<Identifier>> started)
      throws ImportException {
    checkTargets(job, plan);
    started.accept(plan.targets().values());
    return plan;
  }

  private static Prepared prepare(ImportJob job, Plan plan) throws ImportException {
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

  private static Remapped allocateMaps(ImportJob job, Prepared prepared) throws ImportException {
    job.checkStopped();
    job.phase().accept("maps");
    return new Remapped(prepared, MapRemapper.allocate(job.server(), prepared.mapIds()));
  }

  private static Remapped remapMaps(ImportJob job, Remapped remapped) throws ImportException {
    try {
      MapRemapper.remap(job, remapped.prepared().staged(), remapped.table(), remapped.prepared().dims());
    } catch (RuntimeException e) {
      throw job.failure(e);
    }
    job.checkStopped();
    return remapped;
  }

  private static List<Line> install(ImportJob job, Prepared prepared) throws ImportException {
    var server = job.server();
    job.phase().accept("installing");
    checkTargets(job, prepared.plan());

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
    var lines = new ArrayList<Line>();
    var notes = new ArrayList<Component>();
    for (var d : prepared.dims()) {
      lines.add(load(job, d));
      notes.addAll(d.warnings());
    }
    if (prepared.difficulty() != null && prepared.difficulty() != server.getWorldData().getDifficulty()) {
      notes.add(Component.translatable(ImportException.KEY_PREFIX + "warning.global"));
    }
    if (save.nested() != null) {
      notes.add(Component.translatable(ImportException.KEY_PREFIX + "success.nested", save.nested(), job.folder()));
    }
    if (prepared.plan().all()) {
      notes.add(Component.translatable(ImportException.KEY_PREFIX + "success.portals"));
      var skipped = save.emptyDimensionIds();
      if (!skipped.isEmpty()) {
        notes.add(Component.translatable(ImportException.KEY_PREFIX + "success.skipped_empty",
            skipped.stream().map(Identifier::toString).collect(Collectors.joining(", "))));
      }
    }
    notes.forEach(note -> lines.add(new Line(note, false)));
    return lines;
  }

  private static Line load(ImportJob job, ResolvedDimension d) {
    var id = d.newKey().identifier();
    Object reason;
    try {
      if (WorldsCreator.registerAndLoad(job.server(), id, d.data()) != null) {
        return new Line(Component.translatable(ImportException.KEY_PREFIX + "success", id.toString(), job.folder(),
            d.sourceDim().toString()).append(TeleportCommand.link(id)), false);
      }
      reason = Component.translatable(ImportException.KEY_PREFIX + "reason.see_log");
    } catch (Exception e) {
      McWorldMod.LOGGER.error("Imported world {} could not be loaded", id, e);
      reason = String.valueOf(e.getMessage());
    }
    return new Line(Component.translatable(ImportException.KEY_PREFIX + "result.not_loaded", id.toString(), reason),
        true);
  }

  private static void checkTargets(ImportJob job, Plan plan) throws ImportException {
    job.checkStopped();
    for (var target : plan.targets().values()) {
      try {
        WorldsCreator.validImportTarget(target, job.server());
      } catch (WorldsCreator.InvalidLevelIdException e) {
        throw new ImportException(e.getTranslationKey(), target.toString());
      }
    }
  }

  private static CompletableFuture<List<Line>> finish(ImportJob job, @Nullable List<Line> lines,
      @Nullable Throwable error) {
    job.closeArchive();
    var access = job.staging().getAndSet(null);
    if (access != null) {
      access.safeClose();
    }
    CompletableFuture<List<Line>> result = error == null
        ? CompletableFuture.completedFuture(lines)
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

  private static <T> T call(Step<T> step) {
    try {
      return step.run();
    } catch (ImportException e) {
      throw new CompletionException(e);
    }
  }

  private interface Step<T> {
    T run() throws ImportException;
  }
}
