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
import org.agmas.noellesroles.role.ModRoles;
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

    /**
     * 黑警时刻进度条的 id（客户端 {@code StatusInit} 里注册的同名条目）。
     *
     * <p>替代原来屏幕正中 + 左下角的两行倒计时文字，改成和亡命徒一样的读条。
     */
    public static final String PROGRESS_BAR_ID = "corrupt_cop_blackout";

    /** 效果刷新间隔（tick）- 每10秒刷新一次 */
    private static final int EFFECT_REFRESH_INTERVAL = 200;

    /**
     * 被亡命徒时刻打断、回溯后恢复时，至少保留的剩余时间（tick）。
     *
     * <p>亡命徒时刻最长 150 秒，而处决时刻只有 129 秒 —— 如果打断时已经快到点，
     * 直接按剩余时间恢复会等于"0 秒时重新触发"，等于没触发。这里给个下限保证
     * 回溯之后处决时刻还剩一段能打的时间。
     */
    private static final int MIN_RESUME_TICKS = 20 * 30;

    private final Player player;

    /** 是否已激活 */
    private boolean active = false;

    /** 开始时间（游戏tick） */
    private long startTime = 0;

    /** 效果刷新计时器 */
    private int effectRefreshTimer = 0;

    /** 是否已被手动触发过（防止重复触发） */
    private boolean hasBeenTriggered = false;

    /**
     * 因亡命徒时刻而挂起时，挂起那一刻的剩余 tick。
     *
     * <p>{@code >= 0} 表示当前处于"被亡命徒时刻打断"的挂起状态；
     * {@code -1} 表示没有挂起。挂起不是结束：回溯后要按这个剩余时间恢复。
     */
    private int suspendedRemainingTicks = -1;

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

        // 给予装备
        giveEquitment();

        // 给予透视效果
        giveWallHackEffects();

        // 播放激活效果
        playActivationEffects();

        // 发送激活消息
        sendActivationMessage();

        // 顶上挂进度条（代替原来的两行倒计时文字）
        broadcastProgressBar(true);

        return true;
    }


    /**
     * 给予巡警手枪和一颗手榴弹
     */
    private void giveEquitment() {
        ItemStack patrollerRevolver = new ItemStack(ModItems.PATROLLER_REVOLVER);
        player.getInventory().add(patrollerRevolver);
        ItemStack grenade = new ItemStack(TMMItems.GRENADE);
        player.getInventory().add(grenade);

        if (player instanceof ServerPlayer sp) {
            sp.containerMenu.broadcastChanges();
            sp.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * 收回处决装备（巡警手枪 + 手雷），亡命徒时刻打断时用。
     *
     * <p>为什么要收：亡命徒时刻是"时间回溯"级别的全场事件，处决时刻挂起期间
     * 黑警不能保留处决用的武力 —— 否则他照样能崩掉亡命徒，等于没退出。
     *
     * <p>这里把背包里**所有**该物品都清掉（不只是一个），回溯后由
     * {@link #giveEquitment()} 重新发一套，避免出现两把手枪/两颗雷。
     */
    private void withholdEquitment() {
        if (!(player instanceof ServerPlayer sp)) return;
        io.wifi.starrailexpress.util.SREItemUtils.clearItem(sp, ModItems.PATROLLER_REVOLVER);
        io.wifi.starrailexpress.util.SREItemUtils.clearItem(sp, TMMItems.GRENADE);
        sp.containerMenu.broadcastChanges();
        sp.inventoryMenu.slotsChanged(sp.getInventory());
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

        // 收掉进度条
        broadcastProgressBar(false);

        // 检查是否击杀所有玩家
        checkAndHandleEndGame();

        // 发送结束消息
        sendEndMessage();
    }

    /**
     * 黑警**死亡**时立刻收场。
     *
     * <p>为什么不直接复用 {@link #end()}：
     * {@code end()} 里会调 {@link #checkAndHandleEndGame()}，那是"时刻自然结束、
     * 黑警还活着"的分支 —— 会在还剩其他人时把黑警判负击杀。死亡时人已经死了，
     * 再走一遍等于重复处理（甚至对尸体再发一次击杀）。所以这里只做收尾：
     * 清全局状态（客户端据此隐藏 HUD、背景音乐随谓词变假而淡出停止）、收进度条。
     *
     * <p>调用点：{@code CorruptCopPlayerComponent#serverTick} 在玩家死亡的那一帧。
     * 以前那里直接 return，导致时刻永远不结束 —— 音乐和倒计时一直挂在屏幕上。
     *
     * @return 是否真的执行了收尾（本来就没激活则返回 false）
     */
    public boolean endOnDeath() {
        if (!active && suspendedRemainingTicks < 0) return false;

        active = false;
        suspendedRemainingTicks = -1;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            // setter 内部会 sync()，客户端收到后 HUD 立刻消失
            gameWorld.setCorruptCopBlackoutActive(false);
        }

        removeWallHackEffects();
        broadcastProgressBar(false);
        // 全场标题：黑警死亡 / 黑警未能完成处决
        broadcastDeathTitle();
        return true;
    }

    // ==================== 与亡命徒时刻的互斥 ====================

    /** 是否正处于"被亡命徒时刻打断"的挂起状态（不是结束）。 */
    public boolean isSuspendedForLooseEnd() {
        return suspendedRemainingTicks >= 0;
    }

    /**
     * 亡命徒时刻开启 → 强制黑警退出处决时刻（挂起）。
     *
     * <p>亡命徒时刻是更高优先级的全场事件（技能全禁、时间回溯），
     * 处决时刻不能和它并行：否则黑警会在"亡命徒时间线"里继续倒计时、
     * 音乐和读条跟亡命徒的读条叠在一起，回溯后时间也已经被吃掉了。
     *
     * <p>这里只做挂起：记下剩余 tick，清掉全局状态（客户端 HUD / 读条 / 音乐
     * 一起收），移除透视。{@link #resumeAfterLooseEnd(long)} 负责回溯后接续。
     *
     * @return 是否真的挂起了（本来没在激活则返回 false）
     */
    public boolean suspendForLooseEnd() {
        if (!active) return false;

        suspendedRemainingTicks = Math.max(0, getRemainingTicks(player.level().getGameTime()));
        active = false;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            gameWorld.setCorruptCopBlackoutActive(false);
        }
        removeWallHackEffects();
        // 收走处决装备：挂起期间黑警不能留着枪和雷去干预亡命徒时刻
        withholdEquitment();
        broadcastProgressBar(false);

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.corrupt_cop.blackout_suspended")
                            .withStyle(ChatFormatting.DARK_RED),
                    true);
        }
        return true;
    }

    /**
     * 亡命徒时刻失败回溯 → 重新触发处决时刻。
     *
     * <p>按挂起时记下的剩余时间接续（重算 {@code startTime}，让
     * {@link #getRemainingTicks(long)} 回到挂起那一刻的值）；剩余不足
     * {@link #MIN_RESUME_TICKS} 时抬到这个下限，避免"刚恢复就结束"。
     *
     * @param currentTime 当前游戏时间
     * @return 是否真的恢复了（没挂起则返回 false）
     */
    public boolean resumeAfterLooseEnd(long currentTime) {
        if (suspendedRemainingTicks < 0) return false;

        int remaining = Math.max(suspendedRemainingTicks, MIN_RESUME_TICKS);
        suspendedRemainingTicks = -1;

        active = true;
        // 反推 startTime：保证 currentTime 时刻的剩余正好是 remaining
        startTime = currentTime - (DURATION_TICKS - remaining);
        effectRefreshTimer = 0;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            gameWorld.setCorruptCopBlackoutActive(true);
            gameWorld.setCorruptCopBlackoutRemainingSeconds(remaining / 20);
        }
        giveWallHackEffects();
        // 回溯 → 重新发一套处决装备（挂起时收走的枪和雷）
        giveEquitment();
        broadcastProgressBar(true);

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.corrupt_cop.blackout_resumed")
                            .withStyle(ChatFormatting.DARK_RED),
                    true);
        }
        return true;
    }

    /**
     * 静默丢弃挂起状态（不重新触发）。
     *
     * <p>用于"挂起期间本局已经结束"的情况：{@code RefugeeComponent} 在清场时也会把
     * {@code isAnyRevivals} 置回 false，此时不能把它误判成"亡命徒失败回溯"再触发一次。
     */
    public void dropSuspended() {
        if (suspendedRemainingTicks < 0) return;
        suspendedRemainingTicks = -1;
        active = false;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            gameWorld.setCorruptCopBlackoutActive(false);
        }
        broadcastProgressBar(false);
    }

    /**
     * 给所有在线玩家播放「黑警死亡」标题。
     *
     * <p>顺序按原版 /title 的语义来：先 times，再 subtitle，最后 title ——
     * 副标题必须先于主标题设置，否则主标题弹出来时副标题还是空的。
     */
    private void broadcastDeathTitle() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var server = serverPlayer.server;
        if (server == null) return;

        Component title = Component.translatable("title.noellesroles.corrupt_cop.died")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("title.noellesroles.corrupt_cop.died.subtitle")
                .withStyle(ChatFormatting.GRAY);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            io.wifi.starrailexpress.util.SRENetworkMessageUtils.sendTitleTime(p, 10, 60, 20);
            io.wifi.starrailexpress.util.SRENetworkMessageUtils.sendSubtitle(p, subtitle);
            io.wifi.starrailexpress.util.SRENetworkMessageUtils.sendTitle(p, title);
        }
    }

    /**
     * 通知所有客户端显示 / 隐藏黑警时刻进度条。
     *
     * <p>进度值本身不需要在这里同步：客户端从
     * {@code SREClient.gameComponent.getCorruptCopBlackoutRemainingSeconds()} 自己算，
     * 服务端每秒刷新一次剩余秒数，所以只需要开 / 关两个包。
     */
    private void broadcastProgressBar(boolean show) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var server = serverPlayer.server;
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (show) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                        new io.wifi.starrailexpress.network.TriggerStatusBarPayload(PROGRESS_BAR_ID));
            } else {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                        new io.wifi.starrailexpress.network.RemoveStatusBarPayload(PROGRESS_BAR_ID));
            }
        }
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

        // 统计存活的除黑警以外的所有玩家数量（坠木/皮革嘎的不计入击杀目标，同亡命徒）
        int aliveOthersCount = 0;
        for (Player p : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            if (p == player) continue; // 跳过黑警自己
            if (gameWorld.isRole(p, ModRoles.ZHUIMU) || gameWorld.isRole(p, ModRoles.PIGE)) continue;
            aliveOthersCount++;
        }

        if (aliveOthersCount == 0) {
            // 击杀了所有其他玩家，黑警胜利
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.corrupt_cop.blackout_victory")
                            .withStyle(style -> style.withColor(0x2D2D2D).withBold(true)),
                    false);
        } else {
            // 还有其他玩家存活，黑警死亡
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

        // 每 5 秒补发一次进度条。
        // 原因：显示用的是一个"一次性触发"的包，中途重连/后加入的观战者收不到，
        // 就看不到读条了。补发同 id 只是刷新，不会产生第二条。
        if (currentTime % 100 == 0) {
            broadcastProgressBar(true);
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
        boolean wasActive = active || suspendedRemainingTicks >= 0;
        if (active) {
            removeWallHackEffects();
        }
        active = false;
        startTime = 0;
        effectRefreshTimer = 0;
        hasBeenTriggered = false;
        suspendedRemainingTicks = -1;

        // 兜底：局结束时如果时刻还挂着（例如被强制清场），把进度条和全局状态一起收掉，
        // 否则读条会残留到下一局。只在服务端做，避免客户端误发同步包。
        if (wasActive && !player.level().isClientSide()) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld != null) {
                gameWorld.setCorruptCopBlackoutActive(false);
            }
            broadcastProgressBar(false);
        }
    }
}