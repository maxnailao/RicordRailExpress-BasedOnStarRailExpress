package org.agmas.noellesroles.content.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.Noellesroles;

/**
 * RPG-7 网络包：射击(SHOOT) / 装填(RELOAD)。
 * 服务端收到 SHOOT 后消耗弹药、设置冷却并生成火箭弹抛射物；
 * 收到 RELOAD 后消耗背包中的火箭弹并装填。
 */
public record Rpg7ShootPayload(Action action) implements CustomPacketPayload {

    public enum Action {
        SHOOT, RELOAD
    }

    public static final Type<Rpg7ShootPayload> ID = new Type<>(Noellesroles.id("rpg7_shoot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Rpg7ShootPayload> CODEC = StreamCodec.ofMember(
            (Rpg7ShootPayload p, RegistryFriendlyByteBuf buf) -> buf.writeVarInt(p.action().ordinal()),
            (RegistryFriendlyByteBuf buf) -> new Rpg7ShootPayload(Action.values()[buf.readVarInt()]));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(Rpg7ShootPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            org.agmas.noellesroles.content.entity.Rpg7RocketEntity.handleAction(player, payload.action());
        });
    }
}
