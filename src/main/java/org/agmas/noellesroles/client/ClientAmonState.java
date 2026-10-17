package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.StatusBarHUD;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent;
import org.agmas.noellesroles.packet.AmonFinaleS2CPacket;
import org.agmas.noellesroles.packet.AmonSkinS2CPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿蒙夺舍后的客户端皮肤顶替状态：对所有玩家可见，将阿蒙渲染为被夺舍宿主的完整皮肤。
 */
public class ClientAmonState {
    private static final Map<UUID, UUID> skins = new ConcurrentHashMap<>();

    /** 终幕「阿蒙时刻」全局表现：偏灰滤镜、小丑音乐与状态栏倒计时是否激活。 */
    public static volatile boolean finaleActive = false;
    private static long finaleStartMs = 0L;
    /** 终幕总时长（毫秒）：直接由服务端 {@link AmonPlayerComponent#FINALE_TICKS} 换算，避免两处硬编码不同步。 */
    private static final long FINALE_DURATION_MS = AmonPlayerComponent.FINALE_TICKS * 50L;

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player) -> {
            UUID targetId = disguiseTargetFor(player.getUUID());
            if (targetId == null || targetId.equals(player.getUUID())) {
                return PlayerSkinResult.SKIP;
            }
            PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(targetId);
            Minecraft client = Minecraft.getInstance();
            if (info == null && client.getConnection() != null) {
                info = client.getConnection().getPlayerInfo(targetId);
            }
            if (info != null && info.getSkin() != null) {
                return PlayerSkinResult.playerSkin(info.getSkin());
            }
            return PlayerSkinResult.SKIP;
        });
        ClientPlayNetworking.registerGlobalReceiver(AmonSkinS2CPacket.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (payload.amonId() == null) {
                        skins.clear();
                    } else if (payload.hostId() != null) {
                        skins.put(payload.amonId(), payload.hostId());
                    } else {
                        skins.remove(payload.amonId());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(AmonFinaleS2CPacket.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    finaleActive = payload.active();
                    finaleStartMs = System.currentTimeMillis();
                    if (payload.active()) {
                        // 「阿蒙时刻」开始：播放专属音源 a_meng.ogg
                        startFinaleSound(ctx.client());
                    } else {
                        // 终幕结束：仅把进度归零不会移除状态条（HUD 默认保留 500s），需显式移除。
                        StatusBarHUD.getInstance().removeStatusBar("AmonFinale");
                        // 同时停掉终幕音乐 —— 音源长达 3 分半，且终幕只有 80 秒，
                        // 不停的话会盖过后续所有音效。
                        stopFinaleSound(ctx.client());
                    }
                }));
    }

    /**
     * 播放「阿蒙时刻」专属音效。
     *
     * <p>用 MASTER 声道与本模组其它职业音乐保持一致（MUSIC 声道会被玩家的音乐音量滑块静音）。
     */
    private static void startFinaleSound(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        mc.player.playNotifySound(TMMSounds.ROLE_AMON_FINALE, SoundSource.MASTER, 1.0F, 1.0F);
    }

    /** 停止「阿蒙时刻」专属音效（按音效 id + 音源类别匹配）。 */
    private static void stopFinaleSound(Minecraft mc) {
        mc.getSoundManager().stop(TMMSounds.ROLE_AMON_FINALE.getLocation(), SoundSource.MASTER);
    }

    /** 终幕进度（1→0），供全局状态栏显示倒计时。 */
    public static float finaleProgress() {
        if (!finaleActive) return 0f;
        long elapsed = System.currentTimeMillis() - finaleStartMs;
        return Mth.clamp(1f - (float) elapsed / FINALE_DURATION_MS, 0f, 1f);
    }

    public static UUID disguiseTargetFor(UUID amonId) {
        return amonId == null ? null : skins.get(amonId);
    }

    /** 游戏重置时清空所有阿蒙伪装映射与终幕状态。 */
    public static void clearAll() {
        skins.clear();
        finaleActive = false;
        StatusBarHUD.getInstance().removeStatusBar("AmonFinale");
    }
}
