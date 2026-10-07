package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.command.WorldGameRuleCommand;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerGameRulesMixin {

    @Shadow
    public ServerPlayer player;

    @WrapOperation(
        method = "broadcastGameRuleChangeToOperators",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent mcworlds$scopedMessage(String key, Object[] args, Operation<MutableComponent> original,
            GameRule<?> rule, Object value) {
        return this.player.level() instanceof CustomServerLevel level
            ? WorldGameRuleCommand.scoped("set", level, rule)
            : original.call(key, args);
    }

    @Inject(
        method = "sendGameRuleValues",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void mcworlds$sendOrigins(CallbackInfo ci) {
        PerWorldGameRules.sendOrigins(this.player);
    }
}
