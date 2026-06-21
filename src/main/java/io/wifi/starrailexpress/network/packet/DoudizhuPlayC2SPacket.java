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
 * 斗地主出牌 C2S 网络包
 * cardIds: 出的牌ID数组，空数组=不出/pass
 */
public record DoudizhuPlayC2SPacket(int[] cardIds) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "doudizhu_play");
    public static final Type<DoudizhuPlayC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DoudizhuPlayC2SPacket> CODEC =
            StreamCodec.ofMember(DoudizhuPlayC2SPacket::write, DoudizhuPlayC2SPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(cardIds.length);
        for (int v : cardIds) buf.writeByte(v);
    }

    public static DoudizhuPlayC2SPacket read(FriendlyByteBuf buf) {
        int len = buf.readByte() & 0xFF; // 无符号读取，防止负数导致崩溃
        if (len > 20) len = 20; // 最大出牌数限制
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = buf.readByte();
        return new DoudizhuPlayC2SPacket(arr);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DoudizhuPlayC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() ->
                DoudizhuSessionManager.INSTANCE.handlePlay(player, packet.cardIds()));
    }
}
