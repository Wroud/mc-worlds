package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PrepareSpawnTask.class)
public class PrepareSpawnTaskMixin {

  @Shadow
  @Final
  private MinecraftServer server;

  @Shadow
  @Final
  private NameAndId nameAndId;

  @ModifyVariable(
      method = "start",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer$SavedPosition;dimension()Ljava/util/Optional;"))
  private ServerPlayer.SavedPosition mcworlds$spawnAtWorldSpawnIfDimensionMissing(
      ServerPlayer.SavedPosition loadedPosition) {
    var dimension = loadedPosition.dimension().orElse(null);

    if (dimension == null || this.server.getLevel(dimension) != null) {
      return loadedPosition;
    }

    McWorldMod.LOGGER.info("{} logged out in missing dimension {}, moving to world spawn",
        this.nameAndId.name(), dimension.identifier());
    return ServerPlayer.SavedPosition.EMPTY;
  }
}
