package dev.wroud.mc.worlds.mixin.groups;

import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.debug.DebugSubscription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {

    @Accessor("requestedDebugSubscriptions")
    Set<DebugSubscription<?>> mcworlds$getRequestedDebugSubscriptions();

    @Invoker("triggerDimensionChangeTriggers")
    void mcworlds$triggerDimensionChangeTriggers(ServerLevel oldLevel);
}
