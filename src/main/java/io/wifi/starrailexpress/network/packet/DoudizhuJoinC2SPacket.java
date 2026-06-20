package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.content.minigame.doudizhu.DoudizhuSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 斗地主加入/离开/AI补位 C2S 网络包
 * action: 0=join, 1=leave, 2=fillAI
 */
public record DoudizhuJoinC2SPacket(byte action) implements CustomPacketPayload {

    public static final byte ACTION_JOIN = 0;
    public static final byte ACTION_LEAVE = 1;
    public static final byte ACTION_FILL_AI = 2;

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "doudizhu_join");
    public static final Type<DoudizhuJoinC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DoudizhuJoinC2SPacket> CODEC =
            StreamCodec.ofMember(DoudizhuJoinC2SPacket::write, DoudizhuJoinC2SPacket::read);

    public void write(FriendlyByteBuf buf) { buf.writeByte(action); }

    public static DoudizhuJoinC2SPacket read(FriendlyByteBuf buf) {
        return new DoudizhuJoinC2SPacket(buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DoudizhuJoinC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (packet.action() == ACTION_JOIN)
                DoudizhuSessionManager.INSTANCE.handleJoin(player);
            else if (packet.action() == ACTION_LEAVE)
                DoudizhuSessionManager.INSTANCE.handleLeave(player);
            else if (packet.action() == ACTION_FILL_AI)
                DoudizhuSessionManager.INSTANCE.handleFillAI(player);
        });
    }
}
