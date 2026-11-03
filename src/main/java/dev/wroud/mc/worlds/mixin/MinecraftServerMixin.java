package dev.wroud.mc.worlds.mixin;

import java.util.ArrayList;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.wroud.mc.worlds.manager.LazyLevelMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

  @Shadow
  @Final
  @Mutable
  private Map<ResourceKey<Level>, ServerLevel> levels;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void mcworlds$lazyLevels(CallbackInfo ci) {
    this.levels = new LazyLevelMap((MinecraftServer) (Object) this, this.levels);
  }

  /**
   * @author Wroud
   * @reason Prevent ConcurrentModificationException by returning a copy of the
   *         levels collection
   */
  @Overwrite
  public Iterable<ServerLevel> getAllLevels() {
    return new ArrayList<>(this.levels.values());
  }
}
