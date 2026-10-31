package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 机动者滑铲请求（C2S）。
 *
 * <p>「蹲键 / 跳键刚被按下」这一上升沿只有客户端能可靠判定，位移本身仍由服务端权威结算：
 * {@code slideJump} 为 false 表示疾跑中按下蹲键（起滑），为 true 表示滑铲期间按下跳键（滑铲跳）。</p>
 */
public record JidongzheSlideC2SPacket(boolean slideJump) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Noellesroles.id("jidongzhe_slide");
    public static final Type<JidongzheSlideC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, JidongzheSlideC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeBoolean(packet.slideJump()),
            buf -> new JidongzheSlideC2SPacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
