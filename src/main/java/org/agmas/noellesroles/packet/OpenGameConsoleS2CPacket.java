package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 打开游戏掌机面板包：服务端 → 客户端。
 * <p>
 * 由 /spectorlittlegames 指令在确认玩家为旁观者后发送，
 * 客户端收到后打开 {@code GameConsoleScreen} 游戏选择界面。
 */
public record OpenGameConsoleS2CPacket() implements CustomPacketPayload {

    public static final Type<OpenGameConsoleS2CPacket> ID =
            new Type<>(Noellesroles.id("open_game_console"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGameConsoleS2CPacket> CODEC =
            StreamCodec.unit(new OpenGameConsoleS2CPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
