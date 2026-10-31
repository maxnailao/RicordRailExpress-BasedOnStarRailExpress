package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 机动者体力数值同步（S2C，只发给本人）。
 *
 * <p>本仓库的体力（vanilla {@code sprintingTicks}）在服务端和客户端**各自独立模拟**，
 * 服务端改动不会自动反映到玩家自己的体力条上。所以滑铲扣体力、超载回体力这类
 * 由服务端发起的数值变化必须显式下发一次，否则玩家看到的体力条不会动。</p>
 */
public record JidongzheStaminaS2CPacket(float stamina) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Noellesroles.id("jidongzhe_stamina");
    public static final Type<JidongzheStaminaS2CPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, JidongzheStaminaS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeFloat(packet.stamina()),
            buf -> new JidongzheStaminaS2CPacket(buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
