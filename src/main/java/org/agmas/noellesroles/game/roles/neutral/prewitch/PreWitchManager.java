package org.agmas.noellesroles.game.roles.neutral.prewitch;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnDeathWithBody;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.List;

/**
 * 预备魔女 / 魔女的全局系统：
 * <ul>
 * <li>记录「杀人现场」（死亡位置），供预备魔女目击后扣心情</li>
 * <li>死亡回溯的死亡入口</li>
 * <li>转化为魔女时的报幕与钟声音效</li>
 * </ul>
 */
public final class PreWitchManager {

    /** 一处杀人现场 */
    public record KillScene(long id, ResourceLocation dimension, Vec3 pos, long recordedAtTick) {
    }

    /** 现场最多保留数量（超出后丢弃最早的记录） */
    private static final int MAX_SCENES = 96;
    /** 杀人现场的有效时长（tick） */
    private static final int SCENE_LIFETIME_TICKS = 20 * 60 * 20;

    private static final List<KillScene> SCENES = new ArrayList<>();
    private static long nextSceneId = 0L;

    private PreWitchManager() {
    }

    public static void register() {
        // 每局开局 / 结束时清空现场记录
        OnGameTrueStarted.EVENT.register(level -> clearScenes());
        OnGameEnd.EVENT.register((level, gameWorldComponent) -> clearScenes());

        // 记录死亡现场 + 死亡回溯
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            recordScene(serverPlayer);
            PreWitchPlayerComponent.KEY.maybeGet(serverPlayer)
                    .ifPresent(PreWitchPlayerComponent::onDeathForRewind);
        });

        // 有尸体生成时同样记录现场（位置以尸体为准，更贴近"杀人现场"）
        OnDeathWithBody.EVENT.register((victim, killer, deathReason, body) -> {
            if (!(victim instanceof ServerPlayer)) {
                return;
            }
            recordScene((ServerLevel) victim.level(), body.position());
        });
    }

    public static boolean isPreWitch(SRERole role) {
        return role != null && role.identifier().equals(ModRoles.PRE_WITCH_ID);
    }

    public static boolean isMajo(SRERole role) {
        return role != null && role.identifier().equals(ModRoles.MAJO_ID);
    }

    // ==================== 杀人现场 ====================

    private static void recordScene(ServerPlayer victim) {
        recordScene(victim.serverLevel(), victim.position());
    }

    private static void recordScene(ServerLevel level, Vec3 pos) {
        if (level == null || pos == null) {
            return;
        }
        long now = level.getGameTime();
        synchronized (SCENES) {
            SCENES.add(new KillScene(nextSceneId++, level.dimension().location(), pos, now));
            while (SCENES.size() > MAX_SCENES) {
                SCENES.remove(0);
            }
        }
    }

    /** 该世界中仍然"新鲜"的杀人现场 */
    public static List<KillScene> getScenes(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        ResourceLocation dimension = level.dimension().location();
        long now = level.getGameTime();
        List<KillScene> result = new ArrayList<>();
        synchronized (SCENES) {
            for (KillScene scene : SCENES) {
                if (!scene.dimension().equals(dimension)) {
                    continue;
                }
                // 现场在记录时刻之后的一段时间内有效
                if (now - scene.recordedAtTick() > SCENE_LIFETIME_TICKS) {
                    continue;
                }
                result.add(scene);
            }
        }
        return result;
    }

    public static void clearScenes() {
        synchronized (SCENES) {
            SCENES.clear();
            nextSceneId = 0L;
        }
    }

    // ==================== 转化报幕 ====================

    /**
     * 【审判庭的钟声敲响了】—— 钟声音效 + 全服聊天播报。
     *
     * <p>
     * 按需求不再弹出 title / subtitle 类报幕。
     */
    public static void announceTransformation(ServerPlayer majo, ServerLevel level) {
        // 钟声（服务端会广播给该世界的所有玩家）
        level.playSound(null, majo.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 4.0F, 0.7F);

        Component broadcast = Component
                .translatable("message.noellesroles.prewitch.transform_broadcast")
                .withStyle(ChatFormatting.RED);
        for (ServerPlayer target : level.players()) {
            ServerPlayNetworking.send(target, new BroadcastMessageS2CPacket(broadcast));
        }
    }

    /** 供其他系统使用：把玩家直接变成魔女（保留其已有的技能） */
    public static void forceTransform(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRunning() || !isPreWitch(gameWorld.getRole(player))) {
            return;
        }
        PreWitchPlayerComponent.KEY.maybeGet(player)
                .ifPresent(component -> component.transform(player, level, gameWorld));
    }
}
