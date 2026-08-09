package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端 → 服务端：玩家在对话界面选择了某个选项。
 * <p>
 * 服务端校验节点与选项合法性后，执行该选项配置的命令（如有）。
 */
public record DialogSelectC2SPacket(int entityId, String nodeId, int optionIndex)
        implements CustomPacketPayload {

    public static final Type<DialogSelectC2SPacket> ID =
            new Type<>(Noellesroles.id("dialog_npc_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogSelectC2SPacket> CODEC =
            StreamCodec.ofMember(DialogSelectC2SPacket::encode, DialogSelectC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeUtf(this.nodeId, 256);
        buf.writeVarInt(this.optionIndex);
    }

    public static DialogSelectC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new DialogSelectC2SPacket(
                buf.readVarInt(),
                buf.readUtf(256),
                buf.readVarInt());
    }
}
