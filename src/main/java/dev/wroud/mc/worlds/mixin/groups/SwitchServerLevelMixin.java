package dev.wroud.mc.worlds.mixin.groups;

import dev.wroud.mc.worlds.manager.group.transition.GroupTransitions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class SwitchServerLevelMixin {

    @Inject(method = "addPlayer", at = @At("HEAD"))
    private void mcworlds$verifyProfileGroup(ServerPlayer player, CallbackInfo ci) {
        var level = (ServerLevel) (Object) this;
        var transitions = GroupTransitions.of(level.getServer());
        if (transitions != null) {
            transitions.verify(player, level);
        }
    }
}
