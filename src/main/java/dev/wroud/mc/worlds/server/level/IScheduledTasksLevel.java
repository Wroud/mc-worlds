package dev.wroud.mc.worlds.server.level;

/**
 * Interface for server levels that support scheduled task execution.
 * Tasks scheduled via this interface will be executed on the next tick.
 */
public interface IScheduledTasksLevel {
    /**
     * Schedules a task to be executed on the next tick of this level.
     * The task will be executed at the beginning of the tick cycle.
     * This is thread-safe and can be called from any thread.
     * 
     * @param task The task to execute on the next tick
     */
    void schedule(Runnable task);
}
