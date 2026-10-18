package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 抽奖失败通知包（服务端 -> 客户端）
 * @param reasonCode 0 = 抽数不足
 */
public record LootFailNoticeS2CPacket(int poolID, int reasonCode) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_FAIL_NOTICE_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_fail_notice");
    public static final Type<LootFailNoticeS2CPacket> ID = new Type<>(LOOT_FAIL_NOTICE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootFailNoticeS2CPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(poolID);
        buf.writeInt(reasonCode);
    }

    public static LootFailNoticeS2CPacket read(FriendlyByteBuf buf) {
        return new LootFailNoticeS2CPacket(buf.readInt(), buf.readInt());
    }

    static {
        CODEC = StreamCodec.ofMember(LootFailNoticeS2CPacket::write, LootFailNoticeS2CPacket::read);
    }
}