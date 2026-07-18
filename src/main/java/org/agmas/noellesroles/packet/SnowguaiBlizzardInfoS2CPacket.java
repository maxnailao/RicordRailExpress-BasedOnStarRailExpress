package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 雪怪暴风雪信息同步包：服务端 → 客户端。
 * <p>
 * 仅发送给雪怪玩家，用于 HUD 显示暴风雪倒计时。
 *
 * @param nextBlizzardInTicks 距离下一次普通暴风雪的 tick（无暴风雪活跃时有效）
 * @param activeType         0=无暴风雪, 1=普通/强制暴风雪, 2=最终暴风雪
 * @param activeRemainingTicks 当前暴风雪剩余 tick（activeType &gt; 0 时有效）
 */
public record SnowguaiBlizzardInfoS2CPacket(int nextBlizzardInTicks, byte activeType,
                                             int activeRemainingTicks)
        implements CustomPacketPayload {

    public static final byte TYPE_NONE = 0;
    public static final byte TYPE_NORMAL = 1;
    public static final byte TYPE_FINAL = 2;

    public static final Type<SnowguaiBlizzardInfoS2CPacket> ID =
            new Type<>(Noellesroles.id("snowguai_blizzard_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnowguaiBlizzardInfoS2CPacket> CODEC =
            StreamCodec.ofMember(SnowguaiBlizzardInfoS2CPacket::encode, SnowguaiBlizzardInfoS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(nextBlizzardInTicks);
        buf.writeByte(activeType);
        buf.writeInt(activeRemainingTicks);
    }

    public static SnowguaiBlizzardInfoS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new SnowguaiBlizzardInfoS2CPacket(buf.readInt(), buf.readByte(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
