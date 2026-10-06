package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.EmozhidaoSkinHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sounds.SoundSource;

/** 恶魔之刃举刀音效：本地玩家开始蓄力举刀的那一 tick，仅自己播放一次专属音效。 */
public final class EmozhidaoDrawSoundHandler {
    private static boolean lastDrawing = false;

    private EmozhidaoDrawSoundHandler() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var player = client.player;
            if (player == null || client.level == null) {
                lastDrawing = false;
                return;
            }
            var useItem = player.getUseItem();
            boolean drawing = player.isUsingItem()
                    && useItem.is(TMMItems.KNIFE)
                    && EmozhidaoSkinHandler.hasEmozhidaoSkinEquipped(player, useItem);
            if (drawing && !lastDrawing) {
                // 客户端本地播放：仅本地玩家可听见，不经过服务端
                client.level.playLocalSound(
                        player.getX(), player.getY(), player.getZ(),
                        TMMSounds.ITEM_KNIFE_EMOZHIDAO_DRAW,
                        SoundSource.PLAYERS, 1.0f, 1.0f, false);
            }
            lastDrawing = drawing;
        });
    }
}