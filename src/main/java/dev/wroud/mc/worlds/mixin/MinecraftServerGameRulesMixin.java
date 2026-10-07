package dev.wroud.mc.worlds.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public class MinecraftServerGameRulesMixin {

    @WrapWithCondition(
        method = "onGameRuleChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/notifications/NotificationManager;onGameRuleChanged(Lnet/minecraft/world/level/gamerules/GameRule;Ljava/lang/Object;)V"))
    private boolean mcworlds$notifyServerWideOnly(NotificationManager notifications, GameRule<?> rule, Object value) {
        return !PerWorldGameRules.WORLD_CHANGE.isBound();
    }

    @ModifyExpressionValue(
        method = "onGameRuleChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    private List<ServerPlayer> mcworlds$playersWithValue(List<ServerPlayer> original, GameRule<?> rule, Object value) {
        return PerWorldGameRules.affectedPlayers(original, rule);
    }

    @ModifyExpressionValue(
        method = "onGameRuleChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getAllLevels()Ljava/lang/Iterable;"))
    private Iterable<ServerLevel> mcworlds$levelsWithValue(Iterable<ServerLevel> original, GameRule<?> rule, Object value) {
        return PerWorldGameRules.affectedLevels(original, rule);
    }
}
