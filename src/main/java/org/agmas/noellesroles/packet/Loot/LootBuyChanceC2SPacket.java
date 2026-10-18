package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 购买抽数请求包：客户端请求用硬币购买抽奖次数（服务端按 LOTTERY_CHANCE_COIN_COST 单价结算）
 */
public record LootBuyChanceC2SPacket(int count) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_BUY_CHANCE_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_buy_chance");
    public static final Type<LootBuyChanceC2SPacket> ID = new Type<>(LOOT_BUY_CHANCE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootBuyChanceC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.count);
    }

    public static LootBuyChanceC2SPacket read(FriendlyByteBuf buf) {
        return new LootBuyChanceC2SPacket(buf.readInt());
    }

    static {
        CODEC = StreamCodec.ofMember(LootBuyChanceC2SPacket::write, LootBuyChanceC2SPacket::read);
    }
}