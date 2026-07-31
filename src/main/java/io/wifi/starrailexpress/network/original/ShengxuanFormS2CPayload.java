package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 圣宣皮肤形态同步包（服务器 → 客户端）
 * <p>
 * 用于将服务器端的形态切换结果同步到客户端，
 * 使客户端渲染能够显示正确的形态（黑枪/白枪）。
 * </p>
 *
 * @param form 当前形态（1 或 2）
 */
public record ShengxuanFormS2CPayload(int form) implements CustomPacketPayload {
    public static final Type<ShengxuanFormS2CPayload> ID = new Type<>(SRE.id("shengxuan_form_s2c"));
    public static final StreamCodec<FriendlyByteBuf, ShengxuanFormS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShengxuanFormS2CPayload::form, ShengxuanFormS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
