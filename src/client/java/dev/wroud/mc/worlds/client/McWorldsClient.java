package dev.wroud.mc.worlds.client;

import dev.wroud.mc.worlds.network.GameRuleOriginsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class McWorldsClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ClientPlayNetworking.registerGlobalReceiver(GameRuleOriginsPayload.TYPE,
        (payload, context) -> GameRuleOriginsTooltip.origins = payload);
  }
}
