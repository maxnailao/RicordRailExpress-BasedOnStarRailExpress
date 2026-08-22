package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 木乃伊技能1「木乃伊的诅咒」打开背包选人包（S2C）
 * 服务端通知客户端打开背包界面，木乃伊在背包中点击玩家头像选择诅咒目标
 */
public record MunaiyiOpenInventoryS2CPacket() implements CustomPacketPayload {
    public static final ResourceLocation MUNAIYI_OPEN_INV_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "munaiyi_open_inventory");
    public static final CustomPacketPayload.Type<MunaiyiOpenInventoryS2CPacket> ID = new CustomPacketPayload.Type<>(
            MUNAIYI_OPEN_INV_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MunaiyiOpenInventoryS2CPacket> CODEC = StreamCodec
            .unit(new MunaiyiOpenInventoryS2CPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
