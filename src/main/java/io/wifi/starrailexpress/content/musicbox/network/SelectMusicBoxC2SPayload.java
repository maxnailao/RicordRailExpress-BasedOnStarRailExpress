package io.wifi.starrailexpress.content.musicbox.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端→服务端：选择/装备音乐盒。
 *
 * @param musicBoxId 音乐盒 ID（空字符串表示取消装备）
 */
public record SelectMusicBoxC2SPayload(String musicBoxId) implements CustomPacketPayload {

    public static final Type<SelectMusicBoxC2SPayload> ID = new Type<>(SRE.id("select_music_box"));
    public static final StreamCodec<FriendlyByteBuf, SelectMusicBoxC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.musicBoxId),
            buf -> new SelectMusicBoxC2SPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /**
     * 服务端注册接收器。在 SREReceiverRegister 中调用。
     */
    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                MusicBoxPlayerComponent comp = MusicBoxPlayerComponent.KEY.get(player);
                String id = payload.musicBoxId;
                if (id.isEmpty()) {
                    // 取消装备
                    comp.setEquippedBox(null);
                    player.sendSystemMessage(Component.literal("§7已取消装备音乐盒"));
                } else {
                    if (!comp.hasMusicBox(id)) {
                        player.sendSystemMessage(Component.literal("§c你尚未拥有该音乐盒"));
                        return;
                    }
                    if (!MusicBoxRegistry.contains(id)) {
                        player.sendSystemMessage(Component.literal("§c无效的音乐盒 ID"));
                        return;
                    }
                    comp.setEquippedBox(id);
                    var box = MusicBoxRegistry.get(id);
                    player.sendSystemMessage(Component.literal("§a已装备音乐盒: ").append(box.displayName()));
                }
            });
        });
    }
}
