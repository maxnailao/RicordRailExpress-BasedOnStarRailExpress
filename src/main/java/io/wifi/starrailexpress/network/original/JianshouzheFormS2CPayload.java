package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 坚守者之怒皮肤形态同步包（服务器 → 客户端）
 * @param form 当前形态（1 或 2）
 */
public record JianshouzheFormS2CPayload(int form) implements CustomPacketPayload {
    public static final Type<JianshouzheFormS2CPayload> ID = new Type<>(SRE.id("jianshouzhe_form_s2c"));
    public static final StreamCodec<FriendlyByteBuf, JianshouzheFormS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JianshouzheFormS2CPayload::form, JianshouzheFormS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}