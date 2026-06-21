package io.wifi.starrailexpress.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 斗地主状态同步 S2C 网络包
 * <p>
 * action: 0=waiting, 1=bidding, 2=play, 3=pass, 4=win, 5=opponent_left
 * </p>
 */
public record DoudizhuStateS2CPacket(
        byte action,
        int playerIndex,
        int currentTurn,
        int landlordIndex,
        int[] myHand,
        int oppCount1,
        int oppCount2,
        int[] bottomCards,
        int[] lastPlayed,
        int lastPlayedBy,
        int consecutivePasses,
        int[] bids,
        String[] playerNames,
        byte winnerSide
) implements CustomPacketPayload {

    public static final byte WAITING = 0;
    public static final byte BIDDING = 1;
    public static final byte PLAY = 2;
    public static final byte PASS = 3;
    public static final byte WIN = 4;
    public static final byte OPPONENT_LEFT = 5;

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "doudizhu_state");
    public static final Type<DoudizhuStateS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DoudizhuStateS2CPacket> CODEC =
            StreamCodec.ofMember(DoudizhuStateS2CPacket::write, DoudizhuStateS2CPacket::read);

    private static void writeInts(FriendlyByteBuf buf, int[] arr) {
        buf.writeByte(arr.length);
        for (int v : arr) buf.writeByte(v);
    }
    private static int[] readInts(FriendlyByteBuf buf) {
        int len = buf.readByte() & 0xFF; // 无符号读取，防止负数导致NegativeArraySizeException
        if (len > 64) len = 64; // 安全上限
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = buf.readByte();
        return arr;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeByte(playerIndex);
        buf.writeByte(currentTurn);
        buf.writeByte(landlordIndex);
        writeInts(buf, myHand);
        buf.writeByte(oppCount1);
        buf.writeByte(oppCount2);
        writeInts(buf, bottomCards);
        writeInts(buf, lastPlayed);
        buf.writeByte(lastPlayedBy);
        buf.writeByte(consecutivePasses);
        writeInts(buf, bids);
        for (int i = 0; i < 3; i++)
            buf.writeUtf(playerNames[i] != null ? playerNames[i] : "");
        buf.writeByte(winnerSide);
    }

    public static DoudizhuStateS2CPacket read(FriendlyByteBuf buf) {
        byte action = buf.readByte();
        int playerIndex = buf.readByte();
        int currentTurn = buf.readByte();
        int landlordIndex = buf.readByte();
        int[] myHand = readInts(buf);
        int oppCount1 = buf.readByte();
        int oppCount2 = buf.readByte();
        int[] bottomCards = readInts(buf);
        int[] lastPlayed = readInts(buf);
        int lastPlayedBy = buf.readByte();
        int consecutivePasses = buf.readByte();
        int[] bids = readInts(buf);
        String[] names = new String[3];
        for (int i = 0; i < 3; i++) names[i] = buf.readUtf(64);
        byte winnerSide = buf.readByte();
        return new DoudizhuStateS2CPacket(action, playerIndex, currentTurn, landlordIndex,
                myHand, oppCount1, oppCount2, bottomCards, lastPlayed, lastPlayedBy,
                consecutivePasses, bids, names, winnerSide);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ── 静态工厂方法 ──

    public static DoudizhuStateS2CPacket waiting(int playerIndex, String[] names, int waitCount) {
        return new DoudizhuStateS2CPacket(
                WAITING, playerIndex, -1, -1,
                new int[0], 0, 0,
                new int[0], new int[0], -1, 0,
                new int[]{0, 0, 0}, names, (byte) -1);
    }

    public static DoudizhuStateS2CPacket create(
            int phase, int playerIndex, int currentTurn, int landlordIndex,
            int[] myHand, int oppCount1, int oppCount2,
            int[] bottomCards, int[] lastPlayed, int lastPlayedBy,
            int consecutivePasses, int[] bids, String[] names, byte winnerSide) {
        byte act = switch (phase) {
            case 1 -> BIDDING;   // Phase.BIDDING
            case 2 -> PLAY;      // Phase.PLAYING (used for both play and pass)
            case 3 -> WIN;       // Phase.ENDED
            default -> BIDDING;
        };
        return new DoudizhuStateS2CPacket(act, playerIndex, currentTurn, landlordIndex,
                myHand, oppCount1, oppCount2, bottomCards, lastPlayed, lastPlayedBy,
                consecutivePasses, bids, names, winnerSide);
    }

    public static DoudizhuStateS2CPacket opponentLeft(int playerIndex, String[] names) {
        return new DoudizhuStateS2CPacket(
                OPPONENT_LEFT, playerIndex, -1, -1,
                new int[0], 0, 0,
                new int[0], new int[0], -1, 0,
                new int[]{0, 0, 0}, names, (byte) -1);
    }
}
