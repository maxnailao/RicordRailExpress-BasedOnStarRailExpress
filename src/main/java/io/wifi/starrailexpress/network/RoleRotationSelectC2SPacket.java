package io.wifi.starrailexpress.network;

import io.netty.buffer.ByteBuf;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoleRotationSelectC2SPacket(int choiceIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RoleRotationSelectC2SPacket> TYPE =
        new Type<>(ResourceLocation.tryBuild(SRE.MOD_ID, "role_rotation_select"));

    public static final StreamCodec<ByteBuf, RoleRotationSelectC2SPacket> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RoleRotationSelectC2SPacket::choiceIndex,
            RoleRotationSelectC2SPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}