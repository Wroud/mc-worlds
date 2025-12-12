package dev.wroud.mc.worlds.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.server.level.IScheduledTasksLevel;
import net.minecraft.server.level.ServerLevel;

@Mixin(ServerLevel.class)
public class ServerLevelExtraMixin implements IScheduledTasksLevel {
    @Unique
    private final Queue<Runnable> worlds$scheduledTasks = new ConcurrentLinkedQueue<>();

    @Inject(method = "tick", at = @At("HEAD"))
    @SuppressWarnings("resource")
    private void onTickHead(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        List<Runnable> tasksToExecute = new ArrayList<>();
        Runnable task;
        while ((task = this.worlds$scheduledTasks.poll()) != null) {
            tasksToExecute.add(task);
        }

        ServerLevel level = (ServerLevel) (Object) this;
        for (Runnable taskToRun : tasksToExecute) {
            try {
                taskToRun.run();
            } catch (Exception e) {
                McWorldMod.LOGGER
                    .error("Error executing scheduled task in " + level.dimension().identifier() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void schedule(Runnable task) {
        this.worlds$scheduledTasks.offer(task);
    }
}
