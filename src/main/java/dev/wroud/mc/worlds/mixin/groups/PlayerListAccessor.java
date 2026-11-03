package dev.wroud.mc.worlds.mixin.groups;

import java.util.Map;
import java.util.UUID;

import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerList.class)
public interface PlayerListAccessor {

    @Accessor("playersByUUID")
    Map<UUID, ServerPlayer> mcworlds$playersByUUID();

    @Accessor("stats")
    Map<UUID, ServerStatsCounter> mcworlds$stats();

    @Accessor("advancements")
    Map<UUID, PlayerAdvancements> mcworlds$advancements();

    @Invoker("save")
    void mcworlds$save(ServerPlayer player);
}
