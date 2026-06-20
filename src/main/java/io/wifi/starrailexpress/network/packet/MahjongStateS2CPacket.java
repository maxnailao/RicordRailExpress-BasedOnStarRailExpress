package io.wifi.starrailexpress.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 麻将状态同步 S2C 网络包
 * phase: 0=waiting, 1=dealing, 2=playing, 3=action_window, 4=ended
 */
public record MahjongStateS2CPacket(
        byte phase,
        byte currentTurn,
        byte dealerIndex,
        byte playerIndex,
        byte[] myHand,
        byte[] oppCounts,
        byte[] myMeldsData,
        byte[] oppMeldsData,
        byte wallRemaining,
        byte[][] allDiscards,
        byte lastDiscard,
        byte lastDiscardBy,
        byte[] availableActions,
        String[] names,
        byte winnerIndex,
        byte winType
) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mahjong_state");
    public static final Type<MahjongStateS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MahjongStateS2CPacket> CODEC =
            StreamCodec.ofMember(MahjongStateS2CPacket::write, MahjongStateS2CPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ══════════════════════════════════════════════
    // 序列化辅助
    // ══════════════════════════════════════════════

    private static void writeBytes(FriendlyByteBuf buf, byte[] arr) {
        buf.writeByte(arr.length);
        buf.writeBytes(arr);
    }

    private static byte[] readBytes(FriendlyByteBuf buf) {
        int len = buf.readByte() & 0xFF;
        byte[] arr = new byte[len];
        buf.readBytes(arr);
        return arr;
    }

    // ══════════════════════════════════════════════
    // write
    // ══════════════════════════════════════════════

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(phase);
        buf.writeByte(currentTurn);
        buf.writeByte(dealerIndex);
        buf.writeByte(playerIndex);

        // 我的手牌
        writeBytes(buf, myHand);

        // 对手手牌数量 [3]
        buf.writeByte(oppCounts[0]);
        buf.writeByte(oppCounts[1]);
        buf.writeByte(oppCounts[2]);

        // 我的副露 (encoded: count, then per meld: type_byte + tileCount_byte + tiles)
        writeBytes(buf, myMeldsData);

        // 对手副露 (encoded: for each of 3 opponents: count, then per meld: type_byte + tileCount_byte + tiles)
        writeBytes(buf, oppMeldsData);

        // 牌墙剩余
        buf.writeByte(wallRemaining);

        // 4个玩家的弃牌
        for (int i = 0; i < 4; i++) {
            writeBytes(buf, allDiscards[i]);
        }

        buf.writeByte(lastDiscard);
        buf.writeByte(lastDiscardBy);

        // 可用动作
        writeBytes(buf, availableActions);

        // 玩家名称
        for (int i = 0; i < 4; i++) {
            buf.writeUtf(names[i] != null ? names[i] : "", 64);
        }

        buf.writeByte(winnerIndex);
        buf.writeByte(winType);
    }

    // ══════════════════════════════════════════════
    // read
    // ══════════════════════════════════════════════

    public static MahjongStateS2CPacket read(FriendlyByteBuf buf) {
        byte phase = buf.readByte();
        byte currentTurn = buf.readByte();
        byte dealerIndex = buf.readByte();
        byte playerIndex = buf.readByte();

        byte[] myHand = readBytes(buf);

        byte[] oppCounts = new byte[3];
        oppCounts[0] = buf.readByte();
        oppCounts[1] = buf.readByte();
        oppCounts[2] = buf.readByte();

        byte[] myMeldsData = readBytes(buf);
        byte[] oppMeldsData = readBytes(buf);

        byte wallRemaining = buf.readByte();

        byte[][] allDiscards = new byte[4][];
        for (int i = 0; i < 4; i++) allDiscards[i] = readBytes(buf);

        byte lastDiscard = buf.readByte();
        byte lastDiscardBy = buf.readByte();

        byte[] availableActions = readBytes(buf);

        String[] names = new String[4];
        for (int i = 0; i < 4; i++) names[i] = buf.readUtf(64);

        byte winnerIndex = buf.readByte();
        byte winType = buf.readByte();

        return new MahjongStateS2CPacket(
                phase, currentTurn, dealerIndex, playerIndex,
                myHand, oppCounts, myMeldsData, oppMeldsData,
                wallRemaining, allDiscards, lastDiscard, lastDiscardBy,
                availableActions, names, winnerIndex, winType
        );
    }

    // ══════════════════════════════════════════════
    // 静态工厂
    // ══════════════════════════════════════════════

    public static MahjongStateS2CPacket waiting(int playerIndex, String[] names, int waitCount) {
        return new MahjongStateS2CPacket(
                (byte) 0, (byte) -1, (byte) -1, (byte) playerIndex,
                new byte[0], new byte[3],
                new byte[0], new byte[0],
                (byte) 0, new byte[][]{new byte[0], new byte[0], new byte[0], new byte[0]},
                (byte) -1, (byte) -1,
                new byte[0], names, (byte) -1, (byte) 0
        );
    }

    // ══════════════════════════════════════════════
    // 编码/解码辅助（供 MahjongSession 使用）
    // ══════════════════════════════════════════════

    /** 编码单个玩家的副露为字节数组 */
    public static byte[] encodeMelds(int[][] melds) {
        if (melds == null || melds.length == 0) return new byte[]{0};
        byte[] buf = new byte[2 + melds.length * 20]; // 足够大
        int pos = 0;
        buf[pos++] = (byte) melds.length;
        for (int[] meld : melds) {
            buf[pos++] = (byte) meld.length;
            for (int tile : meld) buf[pos++] = (byte) tile;
        }
        byte[] result = new byte[pos];
        System.arraycopy(buf, 0, result, 0, pos);
        return result;
    }

    /** 解码副露数据 */
    public static int[][] decodeMelds(byte[] data) {
        if (data == null || data.length == 0 || data[0] == 0) return new int[0][];
        int pos = 0;
        int count = data[pos++] & 0xFF;
        int[][] melds = new int[count][];
        for (int i = 0; i < count; i++) {
            int tileCount = data[pos++] & 0xFF;
            melds[i] = new int[tileCount];
            for (int j = 0; j < tileCount; j++) {
                melds[i][j] = data[pos++] & 0xFF;
            }
        }
        return melds;
    }

    /** 编码3个对手的副露 */
    public static byte[] encodeOppMelds(int[][][] oppMelds) {
        byte[] buf = new byte[100]; // 足够大
        int pos = 0;
        for (int o = 0; o < 3; o++) {
            int[][] melds = oppMelds[o];
            if (melds == null || melds.length == 0) {
                buf[pos++] = 0;
                continue;
            }
            buf[pos++] = (byte) melds.length;
            for (int[] meld : melds) {
                buf[pos++] = (byte) meld.length;
                for (int tile : meld) buf[pos++] = (byte) tile;
            }
        }
        byte[] result = new byte[pos];
        System.arraycopy(buf, 0, result, 0, pos);
        return result;
    }

    /** 解码3个对手的副露 */
    public static int[][][] decodeOppMelds(byte[] data) {
        int[][][] result = new int[3][][];
        if (data == null || data.length == 0) {
            for (int i = 0; i < 3; i++) result[i] = new int[0][];
            return result;
        }
        int pos = 0;
        for (int o = 0; o < 3; o++) {
            if (pos >= data.length) { result[o] = new int[0][]; continue; }
            int count = data[pos++] & 0xFF;
            if (count == 0) { result[o] = new int[0][]; continue; }
            result[o] = new int[count][];
            for (int i = 0; i < count; i++) {
                int tileCount = data[pos++] & 0xFF;
                result[o][i] = new int[tileCount];
                for (int j = 0; j < tileCount; j++) {
                    result[o][i][j] = data[pos++] & 0xFF;
                }
            }
        }
        return result;
    }
}
