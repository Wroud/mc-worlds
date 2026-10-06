package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import java.util.Optional;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerParentVehicleMixin {

  @ModifyExpressionValue(
      method = "loadAndSpawnParentVehicle",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/ValueInput;child(Ljava/lang/String;)Ljava/util/Optional;"))
  private Optional<ValueInput> mcworlds$dropVehicleFromOtherDimension(Optional<ValueInput> rootVehicle,
      ValueInput playerInput) {
    if (rootVehicle.isEmpty()) {
      return rootVehicle;
    }

    var player = (ServerPlayer) (Object) this;
    var savedDimension = playerInput.read(ServerPlayer.TAG_DIMENSION, Level.RESOURCE_KEY_CODEC);

    if (savedDimension.isEmpty() || savedDimension.get().equals(player.level().dimension())) {
      return rootVehicle;
    }

    McWorldMod.LOGGER.info("Dropping vehicle of {} left in dimension {}", player.getPlainTextName(),
        savedDimension.get().identifier());
    return Optional.empty();
  }
}
