package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 暴风雪状态同步包：服务端 → 客户端。
 * <p>
 * 告知客户端当前暴风雪所处阶段及剩余时间，客户端据此本地倒计时。
 * <ul>
 *   <li>{@link #PHASE_IDLE} = 0：冷却等待中</li>
 *   <li>{@link #PHASE_WARNING} = 1：预警倒计时（暴风雪即将到来）</li>
 *   <li>{@link #PHASE_ACTIVE} = 2：暴风雪活跃中</li>
 * </ul>
 */
public record BlizzardStateS2CPacket(byte phase, int remainingTicks)
        implements CustomPacketPayload {

    /** 冷却等待中 */
    public static final byte PHASE_IDLE = 0;
    /** 预警倒计时 */
    public static final byte PHASE_WARNING = 1;
    /** 暴风雪活跃中 */
    public static final byte PHASE_ACTIVE = 2;

    public static final Type<BlizzardStateS2CPacket> ID = new Type<>(Noellesroles.id("blizzard_state_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlizzardStateS2CPacket> CODEC = StreamCodec
            .ofMember(BlizzardStateS2CPacket::encode, BlizzardStateS2CPacket::decode);

    /**
     * 创建"空闲"状态包。
     */
    public static BlizzardStateS2CPacket idle() {
        return new BlizzardStateS2CPacket(PHASE_IDLE, 0);
    }

    /**
     * 创建"预警"状态包。
     */
    public static BlizzardStateS2CPacket warning(int remainingTicks) {
        return new BlizzardStateS2CPacket(PHASE_WARNING, remainingTicks);
    }

    /**
     * 创建"活跃"状态包。
     */
    public static BlizzardStateS2CPacket active(int remainingTicks) {
        return new BlizzardStateS2CPacket(PHASE_ACTIVE, remainingTicks);
    }

    /**
     * 当前是否处于暴风雪活跃阶段。
     */
    public boolean isActive() {
        return phase == PHASE_ACTIVE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeByte(phase);
        buf.writeInt(remainingTicks);
    }

    public static BlizzardStateS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new BlizzardStateS2CPacket(buf.readByte(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
