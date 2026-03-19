package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;

/**
 * Makes weather state-change broadcasts in advanceWeatherCycle dimension-aware.
 *
 * Vanilla's unfiltered broadcastAll() calls in the "wasRaining != isRaining()" block push
 * STOP_RAINING / START_RAINING / RAIN_LEVEL_CHANGE / THUNDER_LEVEL_CHANGE to every player
 * on the server regardless of which world they are in. The vanilla assumption is that only
 * the overworld has weather (Nether/End ignore these packets), but per-world weather in
 * custom dimensions breaks that assumption.
 *
 * Fix: always scope these broadcasts to the level that produced them.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {

    @Redirect(
        method = "advanceWeatherCycle",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void redirectWeatherStateBroadcast(PlayerList playerList, Packet<?> packet) {
        ServerLevel self = (ServerLevel) (Object) this;
        playerList.broadcastAll(packet, self.dimension());
    }
}
