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

public record GameRuleOriginsPayload(Optional<Origin> origin, Map<Identifier, String> serverValues)
    implements CustomPacketPayload {
  public static final Type<GameRuleOriginsPayload> TYPE = new Type<>(McWorldMod.id("game_rule_origins/2"));
  public static final StreamCodec<ByteBuf, GameRuleOriginsPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.optional(Origin.STREAM_CODEC), GameRuleOriginsPayload::origin,
      ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.STRING_UTF8),
      GameRuleOriginsPayload::serverValues,
      GameRuleOriginsPayload::new);

  public record Origin(boolean group, Identifier id) {
    public static final StreamCodec<ByteBuf, Origin> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, Origin::group,
        Identifier.STREAM_CODEC, Origin::id,
        Origin::new);
  }

  @Override
  public Type<GameRuleOriginsPayload> type() {
    return TYPE;
  }
}
