package dev.wroud.mc.worlds.mixin;

import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DerivedLevelData.class)
public interface DerivedLevelDataAccessor {

    @Mutable
    @Accessor("wrapped")
    void mcworlds$setWrapped(ServerLevelData wrapped);
}
