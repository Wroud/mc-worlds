package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @ModifyExpressionValue(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;"
            )
    )
    private ResourceKey<Level> modifyEndDimensionCheck(ResourceKey<Level> original) {
        ResourceKey<Level> vanillaDimension = DimensionDetectionUtil.getVanillaDimensionMapping((Level) (Object) this);
        return vanillaDimension != null && vanillaDimension == Level.END ? ((Level) (Object) this).dimension() : original;
    }
    
    @Redirect(
            method = "advanceWeatherCycle",
            at = @At(value = "INVOKE", 
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V")
    )
    private void redirectBroadcastAll(PlayerList playerList, net.minecraft.network.protocol.Packet<?> packet) {
        playerList.broadcastAll(packet, ((ServerLevel) (Object) this).dimension());
    }
    
}
