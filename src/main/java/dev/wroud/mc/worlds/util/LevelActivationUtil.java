package dev.wroud.mc.worlds.util;

import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for scheduling actions to be executed when a level is ready.
 * For CustomServerLevel instances, waits until the level is active.
 * For regular ServerLevel instances, executes the action immediately.
 */
public class LevelActivationUtil {
  private static final long TICK_TIME_NANOS = 50_000_000L; // 50ms per tick (20 TPS)

  public static void forceLoadLevel(ServerLevel level) {
    if (level instanceof CustomServerLevel customLevel) {
      while (!customLevel.isActive() && !customLevel.isStopped()) {
        long nextTickTime = System.nanoTime() + TICK_TIME_NANOS;
        customLevel.tick(() -> false);
        
        long waitTime = nextTickTime - System.nanoTime();
        if (waitTime > 0) {
          try {
            Thread.sleep(waitTime / 1_000_000L, (int)(waitTime % 1_000_000L));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
  }

  /**
   * Schedules an action to be executed when the level is ready.
   * If the level is a CustomServerLevel, waits until it's active.
   * If it's a regular ServerLevel, executes the action immediately.
   * 
   * @param level  The ServerLevel to check
   * @param action The action to execute when the level is ready
   */
  public static void executeWhenLevelReady(ServerLevel level, Runnable action) {
    if (level instanceof CustomServerLevel customLevel) {
      scheduleCustomLevelActivationCheck(customLevel, action);
    } else {
      // For regular ServerLevel, execute immediately
      action.run();
    }
  }

  /**
   * Schedules a periodic check for CustomServerLevel activation and executes the
   * action when ready.
   * 
   * @param level  The CustomServerLevel to monitor
   * @param action The action to execute when the level becomes active
   */
  private static void scheduleCustomLevelActivationCheck(CustomServerLevel level, Runnable action) {
    Executor executor = ((MinecraftServerAccessor) level.getServer()).getExecutor();

    Runnable checkTask = new Runnable() {
      @Override
      public void run() {
        if (level.isActive()) {
          action.run();
        } else {
          // Schedule another check after 100ms
          CompletableFuture
              .delayedExecutor(100, TimeUnit.MILLISECONDS)
              .execute(() -> level.getServer().execute(this));
        }
      }
    };

    executor.execute(checkTask);
  }
}
