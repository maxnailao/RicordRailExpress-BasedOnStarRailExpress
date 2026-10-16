package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 神父咏诵界面开启包（S2C）。
 *
 * <p>上游通过通用 {@code OpenScreenManager / OpenScreenPayload} 打开
 * {@link org.agmas.noellesroles.client.screen.PriestChantScreen}；本仓库没有该通用管理器，
 * 因此按本仓库「一屏一包」的既有惯例（如重刑犯抉择界面）单独下发一个开界面包。</p>
 */
public record PriestChantOpenS2CPacket() implements CustomPacketPayload {

    public static final Type<PriestChantOpenS2CPacket> ID = new Type<>(Noellesroles.id("priest_chant_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PriestChantOpenS2CPacket> CODEC = StreamCodec
            .ofMember(PriestChantOpenS2CPacket::encode, PriestChantOpenS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        // 无负载
    }

    public static PriestChantOpenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new PriestChantOpenS2CPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
