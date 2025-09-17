package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "getLevel", at = @At("RETURN"), cancellable = true)
    private void onGetLevelReturn(ResourceKey<Level> resourceKey, CallbackInfoReturnable<ServerLevel> cir) {
        ServerLevel level = cir.getReturnValue();

        if (level == null) {
            var worlds = McWorldMod.getMcWorld(((MinecraftServer) (Object) this));
            var worldData = worlds.getManadger().getWorldsData().getLevelData(resourceKey.location());

            if (worldData != null) {
                var handle = worlds.loadOrCreate(resourceKey.location(), worldData);
                cir.setReturnValue(handle.getServerLevel());
            }
        }
    }
}
