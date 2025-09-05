package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LegacyStructureDataHandler.class)
public class LegacyStructureDataHandlerMixin {

    @ModifyVariable(
        method = "getLegacyStructureHandler(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/storage/DimensionDataStorage;)Lnet/minecraft/world/level/levelgen/structure/LegacyStructureDataHandler;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static ResourceKey<Level> replaceToDimension(ResourceKey<Level> resourceKey) {
        MinecraftServer server = McWorldMod.getServer();
        if (server != null) {
            ServerLevel level = server.getLevel(resourceKey);
            if (level != null) {
                ResourceKey<Level> mappedDimension = DimensionDetectionUtil.getVanillaDimensionMapping(level);
                if (mappedDimension != null) {
                    return mappedDimension;
                }
            }
        }
        
        return resourceKey;
    }
}
