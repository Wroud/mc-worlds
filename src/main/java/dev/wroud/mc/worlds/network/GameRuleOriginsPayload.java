package dev.wroud.mc.worlds.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.wroud.mc.worlds.McWorldMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GameRuleOriginsPayload(Optional<Identifier> world, Map<Identifier, String> serverValues)
    implements CustomPacketPayload {
  public static final Type<GameRuleOriginsPayload> TYPE = new Type<>(McWorldMod.id("game_rule_origins"));
  public static final StreamCodec<ByteBuf, GameRuleOriginsPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.optional(Identifier.STREAM_CODEC), GameRuleOriginsPayload::world,
      ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.STRING_UTF8),
      GameRuleOriginsPayload::serverValues,
      GameRuleOriginsPayload::new);

  @Override
  public Type<GameRuleOriginsPayload> type() {
    return TYPE;
  }
}
