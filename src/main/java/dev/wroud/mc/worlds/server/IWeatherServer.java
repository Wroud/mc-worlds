package dev.wroud.mc.worlds.server;

import net.minecraft.server.level.ServerLevel;

/**
 * Interface to add level-specific weather methods to MinecraftServer.
 */
public interface IWeatherServer {
    /**
     * Sets weather parameters for a specific level.
     *
     * @param level       the level to set weather for
     * @param clearTime   the clear weather time
     * @param rainTime    the rain time
     * @param raining     whether it should be raining
     * @param thundering  whether it should be thundering
     */
    void setWeatherParameters(ServerLevel level, int clearTime, int rainTime, boolean raining, boolean thundering);
}
