package org.agmas.noellesroles.client.blindness;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 失明症客户端状态中枢
 * <p>
 * 负责揭示数据的生命周期管理：每客户端 tick 清理到期的轮廓揭示与声纹标记；
 * 效果被移除（治愈/过期）或断线/换世界时立即清空全部本地状态，
 * 防止"失明治愈后轮廓仍在飘"的跨局残留。
 */
public final class BlindnessClientState {

    /** 上一 tick 本地玩家是否处于失明状态，用于检测"效果结束"边沿 */
    private static boolean wasBlind;

    private BlindnessClientState() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BlindnessClientState::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearAll());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
    }

    private static void tick(Minecraft client) {
        long now = System.nanoTime();
        if (client.player == null) {
            if (wasBlind) {
                clearAll();
            }
            return;
        }
        boolean blind = client.player.hasEffect(ModEffects.BLINDNESS_SICKNESS);
        if (wasBlind && !blind) {
            // 效果刚结束：立即清掉所有残留揭示
            clearAll();
            return;
        }
        wasBlind = blind;
        if (!blind) {
            return;
        }
        ContactRevealManager.tick(now);
        SoundEchoHudRenderer.tick(now);
    }

    private static void clearAll() {
        wasBlind = false;
        ContactRevealManager.clear();
        SoundEchoHudRenderer.clear();
    }
}
