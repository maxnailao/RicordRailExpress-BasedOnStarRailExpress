package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 西部牛仔·决斗邀请包。
 *
 * <p>客户端在背包 {@code LimitedInventoryScreen} 点选一名存活玩家，请求花费金币对其发起决斗。
 * 服务端在 {@code ModPacketsReciever} 里按角色校验后调用
 * {@code CowboyDuelManager.start(...)}。
 */
public record CowboyDuelC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation COWBOY_DUEL_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "cowboy_duel");
    public static final CustomPacketPayload.Type<CowboyDuelC2SPacket> ID = new CustomPacketPayload.Type<>(
            COWBOY_DUEL_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CowboyDuelC2SPacket> CODEC;

    public CowboyDuelC2SPacket(UUID target) {
        this.target = target;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static CowboyDuelC2SPacket read(FriendlyByteBuf buf) {
        return new CowboyDuelC2SPacket(buf.readUUID());
    }

    public UUID target() {
        return this.target;
    }

    static {
        CODEC = StreamCodec.ofMember(CowboyDuelC2SPacket::write, CowboyDuelC2SPacket::read);
    }
}
