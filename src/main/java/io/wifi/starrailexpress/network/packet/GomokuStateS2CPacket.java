package io.wifi.starrailexpress.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 五子棋状态同步 S2C 网络包
 * action: 0=waiting, 1=game_start, 2=move, 3=win, 4=draw, 5=opponent_left
 */
public record GomokuStateS2CPacket(
        byte action,
        byte[] boardData,   // 361 bytes (19x19), 0=空, 1=黑, 2=白
        UUID currentTurn,   // 当前回合玩家 UUID
        UUID winner,        // 胜利者 UUID (仅 action=WIN 时有效)
        boolean isBlack,    // 客户端玩家是否为黑棋
        String blackName,   // 黑棋玩家名
        String whiteName    // 白棋玩家名
) implements CustomPacketPayload {

    public static final byte WAITING = 0;
    public static final byte GAME_START = 1;
    public static final byte MOVE = 2;
    public static final byte WIN = 3;
    public static final byte DRAW = 4;
    public static final byte OPPONENT_LEFT = 5;

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "gomoku_state");
    public static final Type<GomokuStateS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GomokuStateS2CPacket> CODEC =
            StreamCodec.ofMember(GomokuStateS2CPacket::write, GomokuStateS2CPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeByteArray(boardData);
        buf.writeUUID(currentTurn != null ? currentTurn : new UUID(0, 0));
        buf.writeUUID(winner != null ? winner : new UUID(0, 0));
        buf.writeBoolean(isBlack);
        buf.writeUtf(blackName != null ? blackName : "");
        buf.writeUtf(whiteName != null ? whiteName : "");
    }

    public static GomokuStateS2CPacket read(FriendlyByteBuf buf) {
        byte action = buf.readByte();
        byte[] boardData = buf.readByteArray(361);
        UUID currentTurn = buf.readUUID();
        UUID winner = buf.readUUID();
        boolean isBlack = buf.readBoolean();
        String blackName = buf.readUtf(64);
        String whiteName = buf.readUtf(64);
        return new GomokuStateS2CPacket(action, boardData, currentTurn, winner, isBlack, blackName, whiteName);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 创建等待状态包 */
    public static GomokuStateS2CPacket waiting() {
        return new GomokuStateS2CPacket(WAITING, new byte[361], new UUID(0, 0), null, false, "", "");
    }

    /** 创建游戏开始包 */
    public static GomokuStateS2CPacket gameStart(byte[] board, UUID currentTurn, boolean clientIsBlack,
                                                  String blackName, String whiteName) {
        return new GomokuStateS2CPacket(GAME_START, board, currentTurn, null, clientIsBlack, blackName, whiteName);
    }

    /** 创建落子状态包 */
    public static GomokuStateS2CPacket move(byte[] board, UUID currentTurn, boolean clientIsBlack,
                                             String blackName, String whiteName) {
        return new GomokuStateS2CPacket(MOVE, board, currentTurn, null, clientIsBlack, blackName, whiteName);
    }

    /** 创建胜利包 */
    public static GomokuStateS2CPacket win(byte[] board, UUID winner, boolean clientIsBlack,
                                            String blackName, String whiteName) {
        return new GomokuStateS2CPacket(WIN, board, new UUID(0, 0), winner, clientIsBlack, blackName, whiteName);
    }

    /** 创建平局包 */
    public static GomokuStateS2CPacket draw(byte[] board, boolean clientIsBlack,
                                             String blackName, String whiteName) {
        return new GomokuStateS2CPacket(DRAW, board, new UUID(0, 0), null, clientIsBlack, blackName, whiteName);
    }

    /** 创建对手离开包 */
    public static GomokuStateS2CPacket opponentLeft(byte[] board, boolean clientIsBlack,
                                                     String blackName, String whiteName) {
        return new GomokuStateS2CPacket(OPPONENT_LEFT, board, new UUID(0, 0), null, clientIsBlack, blackName, whiteName);
    }
}
