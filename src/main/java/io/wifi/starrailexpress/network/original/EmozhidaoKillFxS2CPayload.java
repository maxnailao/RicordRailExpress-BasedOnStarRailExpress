package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 恶魔之刃击杀特效同步包（服务器 → 仅击杀者客户端）。
 * 击杀者装备恶魔之刃用刀击杀玩家后，服务器只向击杀者本人发送本包，
 * 客户端收到后在手持刀位置生成紫粉色粒子，其他玩家不可见。
 */
public record EmozhidaoKillFxS2CPayload() implements CustomPacketPayload {
    public static final Type<EmozhidaoKillFxS2CPayload> ID = new Type<>(SRE.id("emozhidao_kill_fx_s2c"));
    public static final StreamCodec<FriendlyByteBuf, EmozhidaoKillFxS2CPayload> CODEC =
            StreamCodec.unit(new EmozhidaoKillFxS2CPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}