package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.server.level.ServerLevel;

@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {

  @Accessor("emptyTime")
  int getEmptyTime();
}
