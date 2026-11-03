package dev.wroud.mc.worlds.mixin.groups;

import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.manager.group.transition.GroupTransitions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Ready")
public abstract class LoginSpawnReadyMixin {

  @WrapOperation(
      method = "spawn",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;loadPlayerData(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;"))
  private Optional<CompoundTag> mcworlds$resolvedProfile(PlayerList playerList, NameAndId nameAndId,
      Operation<Optional<CompoundTag>> original) {
    var live = original.call(playerList, nameAndId);
    var transitions = GroupTransitions.of(playerList.getServer());
    return transitions != null ? transitions.resolveLogin(nameAndId, live) : live;
  }
}
