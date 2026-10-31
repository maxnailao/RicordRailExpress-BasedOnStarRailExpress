package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 机动者跪滑姿态同步（S2C），服务端在滑铲起 / 止时各发一次，广播给同维度所有玩家。
 *
 * <p>滑铲只有 2~6 tick，表情系统会在水平位移超过 0.03 格时打断动作，因此姿态不走表情，
 * 改由本包驱动 {@code JidongzheClient} 的客户端姿态状态。</p>
 *
 * <p>{@code durationTicks} 只在 {@code start} 为 true 时有意义：客户端据此知道本次滑铲有多长，
 * 可以对姿态做「淡入 -> 保持 -> 淡出」的插值，而不是瞬间摆到位再瞬间弹回。</p>
 */
public record JidongzheSlidePoseS2CPacket(int entityId, boolean start, int durationTicks)
        implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Noellesroles.id("jidongzhe_slide_pose");
    public static final Type<JidongzheSlidePoseS2CPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, JidongzheSlidePoseS2CPacket> CODEC = StreamCodec
            .ofMember(
                    (packet, buf) -> {
                        buf.writeVarInt(packet.entityId());
                        buf.writeBoolean(packet.start());
                        buf.writeVarInt(packet.durationTicks());
                    },
                    buf -> new JidongzheSlidePoseS2CPacket(buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
