package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖结果快速出售请求包
 * @param results 本次抽奖结果列表，每项为 {quality, ansID}；服务端自行重算价格并校验发放记录
 */
public record LootSellC2SPacket(int poolID, List<int[]> results) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_SELL_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_sell");
    public static final Type<LootSellC2SPacket> ID = new Type<>(LOOT_SELL_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootSellC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(poolID);
        buf.writeInt(results.size());
        for (int[] result : results) {
            buf.writeInt(result[0]);
            buf.writeInt(result[1]);
        }
    }

    public static LootSellC2SPacket read(FriendlyByteBuf buf) {
        int poolID = buf.readInt();
        int count = buf.readInt();
        if (count < 0 || count > 20)
            count = 0;
        List<int[]> results = new ArrayList<>();
        for (int i = 0; i < count; i++)
            results.add(new int[]{buf.readInt(), buf.readInt()});
        return new LootSellC2SPacket(poolID, results);
    }

    static {
        CODEC = StreamCodec.ofMember(LootSellC2SPacket::write, LootSellC2SPacket::read);
    }
}