package dev.wroud.mc.worlds.mixin.groups;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.manager.group.transition.GroupTransitions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.LookAt;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeleportCommand.class)
public abstract class SwitchTeleportCommandMixin {

    @WrapOperation(
            method = "performTeleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/LookAt;perform(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/world/entity/Entity;)V"))
    private static void mcworlds$lookAtAfterSwitch(LookAt lookAt, CommandSourceStack source, Entity victim,
            Operation<Void> original) {
        var transitions = victim instanceof ServerPlayer player ? GroupTransitions.of(player.level().getServer()) : null;
        if (transitions == null
                || !transitions.afterSwitch((ServerPlayer) victim, entity -> original.call(lookAt, source, entity))) {
            original.call(lookAt, source, victim);
        }
    }
}
