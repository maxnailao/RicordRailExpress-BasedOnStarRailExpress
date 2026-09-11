package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 重刑犯「做出你的抉择」GUI 开启包（S2C）。
 *
 * <p>游戏正式开始时由服务端发给重刑犯，携带剩余抉择秒数，客户端据此打开
 * {@link org.agmas.noellesroles.client.screen.ConvictChoiceScreen} 并绘制倒计时。</p>
 *
 * @param secondsLeft 剩余抉择时间（秒）
 */
public record ConvictChoiceOpenS2CPacket(int secondsLeft) implements CustomPacketPayload {

    public static final Type<ConvictChoiceOpenS2CPacket> ID = new Type<>(Noellesroles.id("convict_choice_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConvictChoiceOpenS2CPacket> CODEC = StreamCodec
            .ofMember(ConvictChoiceOpenS2CPacket::encode, ConvictChoiceOpenS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(secondsLeft);
    }

    public static ConvictChoiceOpenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new ConvictChoiceOpenS2CPacket(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
