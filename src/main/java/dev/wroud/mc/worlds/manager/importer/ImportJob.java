package dev.wroud.mc.worlds.manager.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.worldupdate.UpgradeProgress;
import net.minecraft.world.level.storage.LevelStorageSource;

record ImportJob(MinecraftServer server, String folder, UpgradeProgress progress, Consumer<String> phase,
    AtomicReference<LevelStorageSource.LevelStorageAccess> staging, AtomicReference<FileSystem> archive) {
  private static final long LOW_DISK_SPACE_BYTES = 64L * 1024 * 1024;
  private static final int BUFFER_BYTES = 64 * 1024;
  private static final long PROGRESS_INTERVAL_MS = 5_000;

  ImportJob(MinecraftServer server, String folder, Consumer<String> phase) {
    this(server, folder, new UpgradeProgress() {
      @Override
      public boolean isCanceled() {
        return super.isCanceled() || !server.isRunning();
      }
    }, phase, new AtomicReference<>(), new AtomicReference<>());
  }

  LevelStorageSource.LevelStorageAccess storage() {
    return ((MinecraftServerAccessor) server).getStorageSource();
  }

  Path worldDir() {
    return storage().getLevelDirectory().path();
  }

  long diskBudget() {
    return Math.max(0, storage().estimateDiskSpace() - LOW_DISK_SPACE_BYTES);
  }

  void checkStopped() throws ImportException {
    if (progress.isCanceled()) {
      throw ImportException.stopped(folder);
    }
  }

  ImportException failure(Exception e) {
    return progress.isCanceled() ? ImportException.stopped(folder) : ImportException.failed(folder, e);
  }

  long copy(InputStream in, OutputStream out, long limit, Supplier<ImportException> overLimit,
      LongConsumer onProgress) throws IOException, ImportException {
    byte[] buffer = new byte[BUFFER_BYTES];
    long copied = 0;
    long lastReport = Util.getMillis();
    int read;
    while ((read = in.read(buffer)) >= 0) {
      checkStopped();
      copied += read;
      if (copied > limit) {
        throw overLimit.get();
      }
      out.write(buffer, 0, read);
      if (Util.getMillis() - lastReport >= PROGRESS_INTERVAL_MS) {
        lastReport = Util.getMillis();
        onProgress.accept(copied);
      }
    }
    return copied;
  }

  void closeArchive() {
    FileSystem fileSystem = archive.getAndSet(null);
    if (fileSystem != null) {
      try {
        fileSystem.close();
      } catch (IOException e) {
        McWorldMod.LOGGER.warn("Could not close the archive of {}", folder, e);
      }
    }
  }
}
