package org.agmas.noellesroles.game.roles.neutral.corruptcop;

import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.ModItems;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.network.chat.Component;
/**
 * 黑警时刻管理类
 *
 * 管理"黑警时刻"能力：
 * - 触发条件：场上没有杀手阵营存活时
 * - 持续时间：150秒
 * - 效果：透视能力 + 巡警手枪
 */
public class CorruptCopTime {

    /** 黑警时刻持续时间（tick）- 129s */
    public static final int DURATION_TICKS = 129*20;

    /** 效果刷新间隔（tick）- 每10秒刷新一次 */
    private static final int EFFECT_REFRESH_INTERVAL = 200;

    private final Player player;

    /** 是否已激活 */
    private boolean active = false;

    /** 开始时间（游戏tick） */
    private long startTime = 0;

    /** 效果刷新计时器 */
    private int effectRefreshTimer = 0;

    /** 是否已被手动触发过（防止重复触发） */
    private boolean hasBeenTriggered = false;

    public CorruptCopTime(Player player) {
        this.player = player;
    }

    /**
     * 激活黑警时刻（服务端调用）
     * @param currentTime 当前游戏时间
     * @return 是否成功激活
     */
    public boolean activate(long currentTime) {
        if (active) return false;
        if (hasBeenTriggered) return false; // 每局只能触发一次
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        active = true;
        startTime = currentTime;
        effectRefreshTimer = 0;
        hasBeenTriggered = true;

        // 设置全局黑警时刻状态
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            gameWorld.setCorruptCopBlackoutActive(true);
            // 立即同步初始剩余时间
            int initialRemainingSeconds = DURATION_TICKS / 20;
            gameWorld.setCorruptCopBlackoutRemainingSeconds(initialRemainingSeconds);
        }

        // 触发真正的熄灯事件（5秒 = 100 ticks）
        triggerBlackoutEvent();

        // 给予巡警手枪
        giveEquitment();

        // 给予透视效果
        giveWallHackEffects();

        // 播放激活效果
        playActivationEffects();

        // 发送激活消息
        sendActivationMessage();


        return true;
    }


    /**
     * 给予巡警手枪、德林加和一颗手榴弹
     */
    private void giveEquitment() {
        ItemStack patrollerRevolver = new ItemStack(ModItems.PATROLLER_REVOLVER);
        player.getInventory().add(patrollerRevolver);
        player.getInventory().add(new ItemStack(TMMItems.DERRINGER));
        ItemStack grenade = new ItemStack(TMMItems.GRENADE);
        player.getInventory().add(grenade);

    }

    /**
     * 给予透视效果（夜视）
     */
    private void giveWallHackEffects() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // 只给自己夜视效果，不再给其他人发光效果
        serverPlayer.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                DURATION_TICKS,
                0,
                false,
                false
        ));
    }

    /**
     * 刷新透视效果（防止效果提前消失）
     */
    private void refreshWallHackEffects() {
        if (!active) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ServerLevel serverWorld = (ServerLevel) player.level();
        int remainingTicks = DURATION_TICKS - (int)(serverPlayer.level().getGameTime() - startTime);
        if (remainingTicks <= 0) return;

        // 只刷新自己的夜视效果
        MobEffectInstance nightVision = serverPlayer.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision == null || nightVision.getDuration() < 100) {
            serverPlayer.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION,
                    remainingTicks,
                    0,
                    false,
                    false
            ));
        }
    }


    /**
     * 播放激活效果（音效 + 闪电）
     */
    private void playActivationEffects() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ServerLevel serverWorld = (ServerLevel) player.level();

        // 播放激活音效
        serverWorld.playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_NEARBY_CLOSE,
                SoundSource.MASTER, 2.0F, 0.6F);

        serverWorld.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.WITHER_SPAWN,
                SoundSource.PLAYERS, 1.0F, 1.0F);

        // 生成闪电特效
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverWorld);
        if (lightning != null) {
            lightning.setPos(player.getX(), player.getY(), player.getZ());
            lightning.setVisualOnly(true);  // 仅视觉效果，不伤害
            serverWorld.addFreshEntity(lightning);
        }
    }

    /**
     * 触发真正的熄灯事件（关闭列车灯光）
     */
    private void triggerBlackoutEvent() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ServerLevel serverWorld = (ServerLevel) player.level();
        SREWorldBlackoutComponent blackoutComponent = SREWorldBlackoutComponent.KEY.get(serverWorld);

        // 触发熄灯事件，持续 100 ticks（5秒）
        blackoutComponent.triggerBlackout(true, 100);
    }

    /**
     * 发送激活消息
     */
    private void sendActivationMessage() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        serverPlayer.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.noellesroles.corrupt_cop.blackout_activated")
                        .withStyle(style -> style.withColor(0x2D2D2D).withBold(true)),
                true);
        // 发送全局广播
        var broadcastMessage = Component
                .translatable("message.noellesroles.corrupt_cop.blackout_activated.tip")
                .withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD);
        serverPlayer.server.getPlayerList().getPlayers().forEach((p) -> {
            BroadcastCommand.BroadcastMessage(p, broadcastMessage);
        });
    }

    /**
     * 结束黑警时刻
     */
    public void end() {
        if (!active) return;

        active = false;

        // 清除全局黑警时刻状态
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            gameWorld.setCorruptCopBlackoutActive(false);
        }

        // 移除透视效果
        removeWallHackEffects();

        // 检查是否击杀所有玩家
        checkAndHandleEndGame();

        // 发送结束消息
        sendEndMessage();
    }

    /**
     * 检查黑警时刻结束后的游戏状态并处理
     */
    /**
     * 检查黑警时刻结束后的游戏状态并处理
     */
    private void checkAndHandleEndGame() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        ServerLevel serverWorld = (ServerLevel) player.level();

        // 统计存活平民数量（平民阵营，且不是黑警自己）
        int aliveCivilianCount = 0;
        for (Player p : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            if (p == player) continue; // 跳过黑警自己

            io.wifi.starrailexpress.api.SRERole role = gameWorld.getRole(p);
            if (role != null && role.isInnocent()) {
                aliveCivilianCount++;
            }
        }

        if (aliveCivilianCount == 0) {
            // 击杀所有人（平民全灭），黑警胜利

            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.corrupt_cop.blackout_victory")
                            .withStyle(style -> style.withColor(0x2D2D2D).withBold(true)),
                    false);
        } else {
            // 还有平民存活，黑警死亡
            GameUtils.killPlayer(serverPlayer, true, null, GameConstants.DeathReasons.BLACKOUT_TIMEOUT);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.corrupt_cop.blackout_failed")
                            .withStyle(style -> style.withColor(0x72D67E).withBold(true)),
                    false);
        }
    }

    /**
     * 强制结束（不清除效果，用于重置）
     */
    public void forceEnd() {
        active = false;
        startTime = 0;
        effectRefreshTimer = 0;
        removeWallHackEffects();
    }

    /**
     * 移除透视效果
     */
    private void removeWallHackEffects() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // 只移除自己的夜视效果
        serverPlayer.removeEffect(MobEffects.NIGHT_VISION);
    }


    /**
     * 发送结束消息
     */
    private void sendEndMessage() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        serverPlayer.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.noellesroles.corrupt_cop.blackout_ended")
                        .withStyle(style -> style.withColor(0x666666)),
                true);
    }

    /**
     * 每 tick 更新（由组件调用）
     *
     * @param currentTime 当前游戏时间
     * @return true 表示还在进行中，false 表示已结束
     */
    public boolean tick(long currentTime) {
        if (!active) return false;

        // 检查是否超时
        if (isExpired(currentTime)) {
            end();
            return false;
        }

        // 定期刷新效果
        effectRefreshTimer++;
        if (effectRefreshTimer >= EFFECT_REFRESH_INTERVAL) {
            effectRefreshTimer = 0;
            refreshWallHackEffects();
        }

        // 每秒同步剩余时间到全局组件（20 ticks = 1 秒）
        if (effectRefreshTimer % 20 == 0) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld != null) {
                int remainingSeconds = getRemainingSeconds(currentTime);
                gameWorld.setCorruptCopBlackoutRemainingSeconds(remainingSeconds);
            }
        }

        return true;
    }



    /**
     * 检查黑警时刻是否已结束
     */
    public boolean isExpired(long currentTime) {
        if (!active) return true;
        return (currentTime - startTime) >= DURATION_TICKS;
    }

    /**
     * 获取黑警时刻进度 (0~1)
     */
    public float getProgress(long currentTime) {
        if (!active) return 0f;
        long elapsed = currentTime - startTime;
        return Math.min(1f, (float) elapsed / DURATION_TICKS);
    }

    /**
     * 获取剩余时间（秒）
     */
    public int getRemainingSeconds(long currentTime) {
        if (!active) return 0;
        long elapsed = currentTime - startTime;
        long remaining = DURATION_TICKS - elapsed;
        return Math.max(0, (int) (remaining / 20));
    }

    /**
     * 获取剩余时间（tick）
     */
    public int getRemainingTicks(long currentTime) {
        if (!active) return 0;
        long elapsed = currentTime - startTime;
        long remaining = DURATION_TICKS - elapsed;
        return Math.max(0, (int) remaining);
    }

    // ==================== Getter / Setter ====================

    public boolean isActive() {
        return active;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public boolean isHasBeenTriggered() {
        return hasBeenTriggered;
    }

    public void setHasBeenTriggered(boolean hasBeenTriggered) {
        this.hasBeenTriggered = hasBeenTriggered;
    }

    /**
     * 重置所有状态（用于清除/初始化）
     */
    public void reset() {
        if (active) {
            removeWallHackEffects();
        }
        active = false;
        startTime = 0;
        effectRefreshTimer = 0;
        hasBeenTriggered = false;
    }
}