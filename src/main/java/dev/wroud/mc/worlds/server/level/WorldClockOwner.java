package dev.wroud.mc.worlds.server.level;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public interface WorldClockOwner {
  void mcworlds$setOwner(ServerLevel level);

  @Nullable
  ServerLevel mcworlds$getOwner();
}
