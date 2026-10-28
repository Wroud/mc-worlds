package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChunkMap.class)
public abstract class ChunkDataFixContextMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @ModifyArg(
            method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;getChunkDataFixContextTag(Lnet/minecraft/resources/ResourceKey;Ljava/util/Optional;)Lnet/minecraft/nbt/CompoundTag;"
            ),
            index = 0
    )
    private ResourceKey<Level> chunkFixContextDimension(ResourceKey<Level> dimension) {
        return DimensionDetectionUtil.chunkFixContextDimension(this.level, dimension);
    }
}
