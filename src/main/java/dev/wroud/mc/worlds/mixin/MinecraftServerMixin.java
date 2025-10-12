package dev.wroud.mc.worlds.mixin;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.util.LevelActivationUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

  @Shadow
  @Final
  private Map<ResourceKey<Level>, ServerLevel> levels;

  /**
   * @author Wroud
   * @reason Prevent ConcurrentModificationException by returning a copy of the
   *         levels collection
   */
  @Overwrite
  public Iterable<ServerLevel> getAllLevels() {
    return new ArrayList<>(this.levels.values());
  }

  @Inject(method = "getLevel", at = @At("RETURN"), cancellable = true)
  private void onGetLevelReturn(ResourceKey<Level> resourceKey, CallbackInfoReturnable<ServerLevel> cir) {
    ServerLevel level = cir.getReturnValue();

    if (level == null) {
      var worlds = McWorldMod.getMcWorld(((MinecraftServer) (Object) this));

      if (worlds != null) {
        var worldData = worlds.getManager().getWorldsData().getLevelData(resourceKey.location());

        if (worldData != null) {
          var handle = worlds.loadOrCreate(resourceKey.location(), worldData);
          // LevelActivationUtil.forceLoadLevel(handle.getServerLevel());
          cir.setReturnValue(handle.getServerLevel());
        }
      }
    }
  }

  @Inject(method = "isAllowedToEnterPortal", at = @At("RETURN"), cancellable = true)
  private void onIsAllowedToEnterPortal(Level level, CallbackInfoReturnable<Boolean> cir) {
    if (level instanceof CustomServerLevel customServerLevel) {
      if (!customServerLevel.canTeleport()) {
        cir.setReturnValue(false);
      }
    }
  }
}
