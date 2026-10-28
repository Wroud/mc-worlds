package dev.wroud.mc.worlds;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.manager.WorldHandle;
import dev.wroud.mc.worlds.manager.WorldsManager;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

public class McWorld {
  private WorldsManager worldsManager;
  private final AtomicReference<@Nullable RunningImport> runningImport = new AtomicReference<>();

  public McWorld(MinecraftServer server) {
    this.worldsManager = new WorldsManager(server);
  }

  public WorldsManager getManager() {
    return worldsManager;
  }

  public void loadSavedWorlds() {
    worldsManager.loadSavedWorlds();
  }

  public @Nullable WorldHandle loadOrCreate(Identifier location, WorldsLevelData levelData) {
    return worldsManager.loadOrCreateWorld(location, levelData);
  }

  public void handleWorldUnload(Identifier level) {
    worldsManager.unloadWorld(level);
  }

  public boolean tryBeginImport(String description) {
    return runningImport.compareAndSet(null, new RunningImport(description, "starting"));
  }

  public void setImportPhase(String phase) {
    runningImport.updateAndGet(current -> current == null ? null : new RunningImport(current.description(), phase));
  }

  public void endImport() {
    runningImport.set(null);
  }

  public Optional<String> currentImport() {
    return Optional.ofNullable(runningImport.get()).map(current -> current.description() + " (" + current.phase() + ")");
  }

  private record RunningImport(String description, String phase) {
  }
}
