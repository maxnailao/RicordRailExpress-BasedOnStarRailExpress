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

public record DrawMusicBoxLotteryC2SPayload() implements CustomPacketPayload {

    public static final Type<DrawMusicBoxLotteryC2SPayload> ID = new Type<>(SRE.id("draw_music_box_lottery"));
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
                String wonId = comp.drawLottery();

                boolean won = wonId != null;
                String musicBoxName = "";
                if (won) {
                    MusicBox box = MusicBoxRegistry.get(wonId);
                    if (box != null) {
                        musicBoxName = box.displayName().getString();
                    }
                }

                MusicBoxLotteryResultS2CPayload result = new MusicBoxLotteryResultS2CPayload(
                        won, musicBoxName, comp.getLotteryTickets());
                ServerPlayNetworking.send(player, result);

                SyncLotteryTicketsS2CPayload sync = new SyncLotteryTicketsS2CPayload(comp.getLotteryTickets());
                ServerPlayNetworking.send(player, sync);
            });
        });
    }
}
