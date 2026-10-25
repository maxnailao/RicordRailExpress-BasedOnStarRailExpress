package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 鹈鹕肚内炸弹倒计时广播（服务器 → 全体客户端）：
 * 被鹈鹕吞下的玩家携带炸弹时，服务端每秒广播剩余秒数，客户端屏幕中央显示；
 * secondsLeft < 0 表示清除显示（爆炸、被吐出或状态失效）。
 */
public record PelicanBombCountdownS2CPacket(int secondsLeft) implements CustomPacketPayload {
    public static final Type<PelicanBombCountdownS2CPacket> ID =
            new Type<>(Noellesroles.id("pelican_bomb_countdown_s2c"));
    public static final StreamCodec<FriendlyByteBuf, PelicanBombCountdownS2CPacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PelicanBombCountdownS2CPacket::secondsLeft,
                    PelicanBombCountdownS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}