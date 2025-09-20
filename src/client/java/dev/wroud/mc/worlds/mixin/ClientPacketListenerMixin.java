package dev.wroud.mc.worlds.mixin;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl {

    public ClientPacketListenerMixin(Minecraft minecraft, net.minecraft.network.Connection connection,
            net.minecraft.client.multiplayer.CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    private Holder<DimensionType> currentDimensionHolder;

    @ModifyVariable(method = "handleRespawn", at = @At("STORE"), ordinal = 0)
    private Holder<DimensionType> captureDimensionHolder(Holder<DimensionType> holder) {
        this.currentDimensionHolder = holder;
        return holder;
    }

    @ModifyArg(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;determineLevelLoadingReason(ZLnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;"), index = 1)
    private ResourceKey<Level> modifyToDimension(ResourceKey<Level> toDimension) {
        if (currentDimensionHolder != null) {
            ResourceKey<Level> vanillaMapping = DimensionDetectionUtil
                    .getVanillaDimensionMapping(currentDimensionHolder);
            if (vanillaMapping != null) {
                return vanillaMapping;
            }
        }
        return toDimension;
    }

    @ModifyArg(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;determineLevelLoadingReason(ZLnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;"), index = 2)
    private ResourceKey<Level> modifyFromDimension(ResourceKey<Level> fromDimension) {
        var localPlayer = this.minecraft.player;
        if (localPlayer != null && localPlayer.level() != null) {
            return DimensionDetectionUtil.getVanillaDimensionMapping(localPlayer.level());
        }
        return fromDimension;
    }
}
