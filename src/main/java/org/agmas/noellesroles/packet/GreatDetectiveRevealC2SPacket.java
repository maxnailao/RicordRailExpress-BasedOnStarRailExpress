package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 大侦探「目标情况」请求包：客户端在推理之书上选择查明「方位」或「生死」时发送。
 *
 * <p>{@code mode} 取 {@link #MODE_DISTANCE} 或 {@link #MODE_VITAL}。
 */
public record GreatDetectiveRevealC2SPacket(UUID killer, byte mode) implements CustomPacketPayload {

    /** 查明「方位」：记录触发时的距离快照。 */
    public static final byte MODE_DISTANCE = 0;
    /** 查明「生死」：记录凶手当前是否存活。 */
    public static final byte MODE_VITAL = 1;

    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "great_detective_reveal");
    public static final Type<GreatDetectiveRevealC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GreatDetectiveRevealC2SPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> {
                        buf.writeUUID(packet.killer());
                        buf.writeByte(packet.mode());
                    },
                    buf -> new GreatDetectiveRevealC2SPacket(buf.readUUID(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
