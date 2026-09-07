package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

@Mixin(MinecraftServer.class)
public class MinecraftServerTimeSyncMixin {

    @Inject(method = "forceGameTimeSynchronization", at = @At("RETURN"))
    private void onForceGameTimeSynchronization(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        for (ServerLevel level : server.getAllLevels()) {
            if (level instanceof CustomServerLevel csl && !csl.players().isEmpty()) {
                server.getPlayerList().broadcastAll(csl.clockManager().createFullSyncPacket(), csl.dimension());
            }
        }
    }
}
