package org.agmas.noellesroles.game.modes.werewolf.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 狼人杀私有信息 S2C 包（仅发给特定玩家，避免信息泄露）
 * infoType: 0=炼药师看到昨夜被狼杀的玩家座位
 *           1=预言家查验结果为狼人
 *           2=预言家查验结果为好人
 * Author: jiale
 */
public record WerewolfPrivateInfoS2CPacket(
        byte infoType,
        int seat
) implements CustomPacketPayload {

    public static final Type<WerewolfPrivateInfoS2CPacket> TYPE =
            new Type<>(SRE.jialeId("werewolf_private_info"));

    public static final StreamCodec<FriendlyByteBuf, WerewolfPrivateInfoS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, WerewolfPrivateInfoS2CPacket::infoType,
            ByteBufCodecs.INT, WerewolfPrivateInfoS2CPacket::seat,
            WerewolfPrivateInfoS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
