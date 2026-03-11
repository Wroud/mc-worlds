package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.saveddata.WanderingTraderData;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTraderSpawner.class)
public class WanderingTraderSpawnerMixin {

    @Shadow
    private WanderingTraderData traderData;

    /**
     * Allows the WanderingTraderSpawner to be instantiated with a null savedDataStorage.
     * The tick method will use level.getDataStorage() instead.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(SavedDataStorage savedDataStorage, CallbackInfo ci) {
        // No-op: just allows the constructor to be called with null
    }

    /**
     * Gets trader data from the ServerLevel's data storage instead of the instance field.
     * This allows the spawner to work correctly with custom worlds that have their own data storage.
     * Uses the same caching approach as the original implementation.
     *
     * @param level The server level to get the data storage from
     * @return The WanderingTraderData for the given level
     */
    @Unique
    private WanderingTraderData getTraderData(ServerLevel level) {
        if (this.traderData == null) {
            this.traderData = level.getDataStorage().computeIfAbsent(WanderingTraderData.TYPE);
        }
        return this.traderData;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/npc/wanderingtrader/WanderingTraderSpawner;getTraderData()Lnet/minecraft/world/level/saveddata/WanderingTraderData;"
            )
    )
    private WanderingTraderData redirectGetTraderData(WanderingTraderSpawner instance, Operation<WanderingTraderData> original, ServerLevel level, boolean spawnEnemies) {
        return this.getTraderData(level);
    }
}
