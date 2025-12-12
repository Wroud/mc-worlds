package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.abstractions.ServerPlayerAbstraction;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.advancements.criterion.ChangeDimensionTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChangeDimensionTrigger.class)
public class ChangeDimensionTriggerMixin {

    @ModifyVariable(
        method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private ResourceKey<Level> replaceFromDimension(ResourceKey<Level> fromDimension, ServerPlayer serverPlayer) {
        ServerLevel fromLevel = ServerPlayerAbstraction.getServer(serverPlayer).getLevel(fromDimension);
        if (fromLevel != null) {
            ResourceKey<Level> vanillaFromMapping = DimensionDetectionUtil.getVanillaDimensionMapping(fromLevel);
            if (vanillaFromMapping != null) {
                return vanillaFromMapping;
            }
        }
        return fromDimension;
    }

    @ModifyVariable(
        method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private ResourceKey<Level> replaceToDimension(ResourceKey<Level> toDimension, ServerPlayer serverPlayer) {
        ServerLevel toLevel = ServerPlayerAbstraction.getServer(serverPlayer).getLevel(toDimension);
        if (toLevel != null) {
            ResourceKey<Level> vanillaToMapping = DimensionDetectionUtil.getVanillaDimensionMapping(toLevel);
            if (vanillaToMapping != null) {
                return vanillaToMapping;
            }
        }
        return toDimension;
    }
}
