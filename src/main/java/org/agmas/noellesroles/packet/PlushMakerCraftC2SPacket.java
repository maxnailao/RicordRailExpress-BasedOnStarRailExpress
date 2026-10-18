package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 玩偶制作器提交包（C2S）：客户端把玩家输入的「玩家名或 UUID」发到服务端。
 *
 * @param playerId 玩家名或 UUID 字符串（已由客户端 {@code trim}，可能为空则由服务端忽略）
 */
public record PlushMakerCraftC2SPacket(String playerId) implements CustomPacketPayload {

    /** 输入长度上限：防止恶意超长字符串（玩家名最长 16，UUID 36，这里留足余量）。 */
    public static final int MAX_INPUT_LENGTH = 64;

    public static final ResourceLocation PAYLOAD_ID = Noellesroles.id("plush_maker_craft");
    public static final Type<PlushMakerCraftC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PlushMakerCraftC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeUtf(packet.playerId(), MAX_INPUT_LENGTH),
            buf -> new PlushMakerCraftC2SPacket(buf.readUtf(MAX_INPUT_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
