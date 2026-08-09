package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端 → 客户端：打开对话 NPC 界面。
 * <p>
 * 携带 NPC 实体 id、显示名、皮肤名以及完整对话 JSON 原文，
 * 客户端解析 JSON 后渲染分支对话界面。
 */
public record OpenDialogNpcScreenS2CPacket(int entityId, String npcName, String skin,
        String dialogJson) implements CustomPacketPayload {

    public static final Type<OpenDialogNpcScreenS2CPacket> ID =
            new Type<>(Noellesroles.id("open_dialog_npc_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialogNpcScreenS2CPacket> CODEC =
            StreamCodec.ofMember(OpenDialogNpcScreenS2CPacket::encode, OpenDialogNpcScreenS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeUtf(this.npcName, 256);
        buf.writeUtf(this.skin, 256);
        buf.writeUtf(this.dialogJson, 262144);
    }

    public static OpenDialogNpcScreenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenDialogNpcScreenS2CPacket(
                buf.readVarInt(),
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readUtf(262144));
    }
}
