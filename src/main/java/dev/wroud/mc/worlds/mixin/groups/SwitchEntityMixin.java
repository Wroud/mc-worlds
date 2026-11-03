package dev.wroud.mc.worlds.mixin.groups;

import dev.wroud.mc.worlds.manager.group.WorldGroups;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SwitchEntityMixin {

    @Inject(method = "canTeleport", at = @At("HEAD"), cancellable = true)
    private void mcworlds$keepEntitiesInGroup(Level from, Level to, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayer) && !WorldGroups.sameGroup(from, to)) {
            cir.setReturnValue(false);
        }
    }
}
