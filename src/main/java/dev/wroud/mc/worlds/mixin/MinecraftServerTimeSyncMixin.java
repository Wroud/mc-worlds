package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.server.level.PerWorldClockManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * After the global time sync packet is broadcast every 20 ticks, send
 * per-world clock states to players currently in a CustomServerLevel so
 * their client sky/moon rendering stays correct.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerTimeSyncMixin {

    @Inject(method = "forceGameTimeSynchronization", at = @At("RETURN"))
    private void onForceGameTimeSynchronization(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        for (ServerLevel level : server.getAllLevels()) {
            if (level instanceof CustomServerLevel csl) {
                PerWorldClockManager mgr = csl.getPerWorldClockManager();
                if (mgr != null) {
                    server.getPlayerList().broadcastAll(mgr.createFullSyncPacket(), csl.dimension());
                }
            }
        }
    }
}
