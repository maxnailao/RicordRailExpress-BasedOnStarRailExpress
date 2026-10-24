package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 星光（传承）击杀通知包（服务器 → 仅击杀者客户端）：
 * 收到后刀刃环绕粒子永久追加一颗深蓝黑影（本局有效）。
 */
/** clear=false：击杀者累加一颗深蓝黑影；clear=true：对局结束，立即清除所有深蓝黑影。
 *  claw=false：星光皮肤击杀（黑影+黑灰滤镜）；claw=true：猩红之爪击杀（暗红滤镜） */
public record XingguangKillFxS2CPayload(boolean clear, boolean claw) implements CustomPacketPayload {
    public static final Type<XingguangKillFxS2CPayload> ID = new Type<>(SRE.id("xingguang_kill_fx_s2c"));
    public static final StreamCodec<FriendlyByteBuf, XingguangKillFxS2CPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, XingguangKillFxS2CPayload::clear,
                    ByteBufCodecs.BOOL, XingguangKillFxS2CPayload::claw,
                    XingguangKillFxS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}