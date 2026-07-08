package io.wifi.starrailexpress.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 象棋状态同步 S2C 网络包
 * action: 0=waiting, 1=game_start, 2=move, 3=win, 4=opponent_left
 */
public record XiangqiStateS2CPacket(
        byte action,
        byte[] boardData,   // 90 bytes (10x9), piece encoding
        UUID currentTurn,
        UUID winner,
        boolean isRed,
        String redName,
        String blackName
) implements CustomPacketPayload {

    public static final byte WAITING = 0;
    public static final byte GAME_START = 1;
    public static final byte MOVE = 2;
    public static final byte WIN = 3;
    public static final byte OPPONENT_LEFT = 4;

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "xiangqi_state");
    public static final Type<XiangqiStateS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, XiangqiStateS2CPacket> CODEC =
            StreamCodec.ofMember(XiangqiStateS2CPacket::write, XiangqiStateS2CPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeByteArray(boardData);
        buf.writeUUID(currentTurn != null ? currentTurn : new UUID(0, 0));
        buf.writeUUID(winner != null ? winner : new UUID(0, 0));
        buf.writeBoolean(isRed);
        buf.writeUtf(redName != null ? redName : "", 64);
        buf.writeUtf(blackName != null ? blackName : "", 64);
    }

    public static XiangqiStateS2CPacket read(FriendlyByteBuf buf) {
        byte action = buf.readByte();
        byte[] boardData = buf.readByteArray(90);
        UUID currentTurn = buf.readUUID();
        UUID winner = buf.readUUID();
        boolean isRed = buf.readBoolean();
        String redName = buf.readUtf(64);
        String blackName = buf.readUtf(64);
        return new XiangqiStateS2CPacket(action, boardData, currentTurn, winner, isRed, redName, blackName);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ── 静态工厂方法 ──

    public static XiangqiStateS2CPacket waiting() {
        return new XiangqiStateS2CPacket(WAITING, new byte[90], new UUID(0, 0), null, false, "", "");
    }

    public static XiangqiStateS2CPacket gameStart(byte[] board, UUID turn, boolean isRed, String rName, String bName) {
        return new XiangqiStateS2CPacket(GAME_START, board, turn, null, isRed, rName, bName);
    }

    public static XiangqiStateS2CPacket move(byte[] board, UUID turn, boolean isRed, String rName, String bName) {
        return new XiangqiStateS2CPacket(MOVE, board, turn, null, isRed, rName, bName);
    }

    public static XiangqiStateS2CPacket win(byte[] board, UUID winner, boolean isRed, String rName, String bName) {
        return new XiangqiStateS2CPacket(WIN, board, null, winner, isRed, rName, bName);
    }

    public static XiangqiStateS2CPacket opponentLeft(byte[] board, boolean isRed, String rName, String bName) {
        return new XiangqiStateS2CPacket(OPPONENT_LEFT, board, null, null, isRed, rName, bName);
    }
}
