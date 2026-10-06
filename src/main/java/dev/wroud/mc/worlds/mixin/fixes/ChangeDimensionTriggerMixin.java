package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.abstractions.ServerPlayerAbstraction;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.advancements.triggers.ChangeDimensionTrigger;
import net.minecraft.resources.ResourceKey;
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
        return DimensionDetectionUtil.getVanillaDimensionMapping(ServerPlayerAbstraction.getServer(serverPlayer), fromDimension);
    }

    @ModifyVariable(
        method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private ResourceKey<Level> replaceToDimension(ResourceKey<Level> toDimension, ServerPlayer serverPlayer) {
        return DimensionDetectionUtil.getVanillaDimensionMapping(ServerPlayerAbstraction.getServer(serverPlayer), toDimension);
    }
}
