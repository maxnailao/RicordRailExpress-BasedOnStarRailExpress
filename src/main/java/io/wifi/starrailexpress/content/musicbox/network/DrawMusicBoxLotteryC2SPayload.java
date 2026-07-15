package io.wifi.starrailexpress.content.musicbox.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.musicbox.MusicBox;
import io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端→服务端：请求音乐盒抽奖。
 */
public record DrawMusicBoxLotteryC2SPayload() implements CustomPacketPayload {

    public static final Type<DrawMusicBoxLotteryC2SPayload> ID = new Type<>(SRE.id("draw_musicbox_lottery"));
    public static final StreamCodec<FriendlyByteBuf, DrawMusicBoxLotteryC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {},
            buf -> new DrawMusicBoxLotteryC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                MusicBoxPlayerComponent comp = MusicBoxPlayerComponent.KEY.get(player);

                // 检查是否已拥有全部
                boolean allOwned = true;
                for (MusicBox box : MusicBoxRegistry.getAll()) {
                    if (!comp.hasMusicBox(box.id())) {
                        allOwned = false;
                        break;
                    }
                }
                if (allOwned) {
                    player.displayClientMessage(
                            Component.translatable("screen.sre.musicbox.lottery.all_owned")
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                    return;
                }

                String wonId = comp.drawLottery();
                String result;
                if (wonId != null) {
                    MusicBox box = MusicBoxRegistry.get(wonId);
                    result = box != null ? box.displayName().getString() : wonId;
                } else {
                    result = "";
                }
                // 发送结果给客户端
                ServerPlayNetworking.send(player,
                        new MusicBoxLotteryResultS2CPayload(wonId != null, result, comp.getLotteryTickets()));
            });
        });
    }
}
