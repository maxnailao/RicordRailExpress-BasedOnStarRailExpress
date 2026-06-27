package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 全服「阿蒙时刻」终幕状态广播。
 * active=true 开启终幕表现（偏灰滤镜、小丑音乐、全局状态栏倒计时）；false 关闭。
 */
public record AmonFinaleS2CPacket(boolean active) implements CustomPacketPayload {
    public static final Type<AmonFinaleS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "amon_finale"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AmonFinaleS2CPacket> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.active),
            buf -> new AmonFinaleS2CPacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
