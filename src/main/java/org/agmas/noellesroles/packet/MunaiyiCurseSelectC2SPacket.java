package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 木乃伊技能1「木乃伊的诅咒」选人包
 * 客户端在背包界面点击玩家头像后发送，请求对目标施加一层诅咒
 */
public record MunaiyiCurseSelectC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation MUNAIYI_CURSE_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "munaiyi_curse_select");
    public static final CustomPacketPayload.Type<MunaiyiCurseSelectC2SPacket> ID = new CustomPacketPayload.Type<>(
            MUNAIYI_CURSE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MunaiyiCurseSelectC2SPacket> CODEC;

    public MunaiyiCurseSelectC2SPacket(UUID target) {
        this.target = target;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static MunaiyiCurseSelectC2SPacket read(FriendlyByteBuf buf) {
        return new MunaiyiCurseSelectC2SPacket(buf.readUUID());
    }

    public UUID target() {
        return this.target;
    }

    static {
        CODEC = StreamCodec.ofMember(MunaiyiCurseSelectC2SPacket::write, MunaiyiCurseSelectC2SPacket::read);
    }
}
