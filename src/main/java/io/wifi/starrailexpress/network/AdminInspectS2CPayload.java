package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 管理员客户端：某个在线玩家的数据快照，供“资产管理”界面展示与选择删除。
 *
 * <p>这些数据大多是“仅本人可见”的组件（如余额），管理员客户端平时读不到，
 * 因此统一由服务端聚合后下发一份。
 */
public record AdminInspectS2CPayload(
        String target, boolean found, int balance, int selfSelect,
        List<String> cardKeys, List<Integer> cardCounts,
        List<String> whTypes, List<String> whIds, List<Integer> whCounts,
        List<String> titleIds) implements CustomPacketPayload {

    private static final int MAX = 1024; // 防御性上限

    public static final Type<AdminInspectS2CPayload> ID =
            new Type<>(SRE.id("admin_inspect_data"));
    public static final StreamCodec<FriendlyByteBuf, AdminInspectS2CPayload> CODEC =
            StreamCodec.ofMember(AdminInspectS2CPayload::encode, AdminInspectS2CPayload::decode);

    private void encode(FriendlyByteBuf buf) {
        buf.writeUtf(target, 64);
        buf.writeBoolean(found);
        buf.writeVarInt(balance);
        buf.writeVarInt(selfSelect);
        writePairs(buf, cardKeys, cardCounts);
        writeTriples(buf);
        writeStrings(buf, titleIds);
    }

    private static AdminInspectS2CPayload decode(FriendlyByteBuf buf) {
        String target = buf.readUtf(64);
        boolean found = buf.readBoolean();
        int balance = buf.readVarInt();
        int selfSelect = buf.readVarInt();
        List<String> cardKeys = new ArrayList<>();
        List<Integer> cardCounts = new ArrayList<>();
        readPairs(buf, cardKeys, cardCounts);
        List<String> whTypes = new ArrayList<>();
        List<String> whIds = new ArrayList<>();
        List<Integer> whCounts = new ArrayList<>();
        readTriples(buf, whTypes, whIds, whCounts);
        List<String> titleIds = readStrings(buf);
        return new AdminInspectS2CPayload(target, found, balance, selfSelect,
                cardKeys, cardCounts, whTypes, whIds, whCounts, titleIds);
    }

    private static void writePairs(FriendlyByteBuf buf, List<String> keys, List<Integer> counts) {
        int n = Math.min(keys.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(keys.get(i), 64);
            buf.writeVarInt(counts.get(i));
        }
    }

    private static void readPairs(FriendlyByteBuf buf, List<String> keys, List<Integer> counts) {
        int n = Math.min(buf.readVarInt(), MAX);
        for (int i = 0; i < n; i++) {
            keys.add(buf.readUtf(64));
            counts.add(buf.readVarInt());
        }
    }

    private void writeTriples(FriendlyByteBuf buf) {
        int n = Math.min(whIds.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(whTypes.get(i), 16);
            buf.writeUtf(whIds.get(i), 128);
            buf.writeVarInt(whCounts.get(i));
        }
    }

    private static void readTriples(FriendlyByteBuf buf, List<String> types, List<String> ids, List<Integer> counts) {
        int n = Math.min(buf.readVarInt(), MAX);
        for (int i = 0; i < n; i++) {
            types.add(buf.readUtf(16));
            ids.add(buf.readUtf(128));
            counts.add(buf.readVarInt());
        }
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> list) {
        int n = Math.min(list.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(list.get(i), 64);
        }
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readUtf(64));
        }
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}