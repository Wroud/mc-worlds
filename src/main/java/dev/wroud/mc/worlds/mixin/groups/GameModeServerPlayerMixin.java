package dev.wroud.mc.worlds.mixin.groups;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.wroud.mc.worlds.server.level.GroupGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayer.class)
public abstract class GameModeServerPlayerMixin {

    @WrapMethod(method = "calculateGameModeForNewPlayer")
    private GameType mcworlds$groupGameMode(@Nullable GameType loadedGameType, Operation<GameType> original) {
        GameType mode = GroupGameRules.newPlayerMode((ServerPlayer) (Object) this, loadedGameType);
        return mode != null ? mode : original.call(loadedGameType);
    }
}
