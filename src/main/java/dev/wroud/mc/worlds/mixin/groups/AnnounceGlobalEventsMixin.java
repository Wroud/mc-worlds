package dev.wroud.mc.worlds.mixin.groups;

import java.util.List;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class AnnounceGlobalEventsMixin {
    @ModifyExpressionValue(
            method = "globalLevelEvent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;")
    )
    private List<ServerPlayer> mcworlds$groupPlayers(List<ServerPlayer> players) {
        var self = (ServerLevel) (Object) this;
        return players.stream().filter(player -> WorldGroups.sameGroup(player.level(), self)).toList();
    }
}
