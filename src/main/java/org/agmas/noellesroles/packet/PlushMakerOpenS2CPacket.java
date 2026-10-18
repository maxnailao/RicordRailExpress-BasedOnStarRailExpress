package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 玩偶制作器界面开启包（S2C）。
 *
 * <p>玩家右键 {@code noellesroles:plush_maker} 方块时，服务端下发本包，客户端据此打开
 * {@link org.agmas.noellesroles.client.screen.PlushMakerScreen}。</p>
 */
public record PlushMakerOpenS2CPacket() implements CustomPacketPayload {

    public static final Type<PlushMakerOpenS2CPacket> ID = new Type<>(Noellesroles.id("plush_maker_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlushMakerOpenS2CPacket> CODEC = StreamCodec
            .ofMember(PlushMakerOpenS2CPacket::encode, PlushMakerOpenS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        // 无负载
    }

    public static PlushMakerOpenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new PlushMakerOpenS2CPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
