package dev.wroud.mc.worlds.mixin;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl {

    public ClientPacketListenerMixin(Minecraft minecraft, net.minecraft.network.Connection connection, net.minecraft.client.multiplayer.CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    @ModifyVariable(
        method = "determineLevelLoadingReason(ZLnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private ResourceKey<Level> replaceToDimension(ResourceKey<Level> toDimension) {
        // For the destination dimension, we cannot reliably map it on the client side
        // since we don't have access to the actual ServerLevel object.
        // The server-side mixins will handle the mapping there.
        return toDimension;
    }

    @ModifyVariable(
        method = "determineLevelLoadingReason(ZLnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private ResourceKey<Level> replaceFromDimension(ResourceKey<Level> fromDimension) {
		LocalPlayer localPlayer = this.minecraft.player;
		Level fromLevel = localPlayer.level();
        if (fromLevel != null && fromLevel.dimension().equals(fromDimension)) {
            ResourceKey<Level> vanillaMapping = DimensionDetectionUtil.getVanillaDimensionMapping(fromLevel);
            if (vanillaMapping != null) {
                return vanillaMapping;
            }
        }
        return fromDimension;
    }
}
