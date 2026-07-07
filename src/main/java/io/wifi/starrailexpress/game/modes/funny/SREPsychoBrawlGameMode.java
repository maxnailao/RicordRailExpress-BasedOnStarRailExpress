package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 疯魔乱斗场
 * <p>
 * 类似亡命徒模式，所有玩家为亡命徒。
 * 安全时间结束后，全员进入5分钟疯魔模式，每20秒护盾增加1层（上限2层）。
 * 疯魔结束后30秒（疯魔开始5分半），所有存活玩家强制死亡，无人胜利。
 * 最终存活者获胜。
 * </p>
 *
 * @author jiale
 */
public class SREPsychoBrawlGameMode extends WTLooseEndsGameMode {

    /** 疯魔持续时间：5分钟 = 6000 ticks */
    private static final int PSYCHO_DURATION_TICKS = 5 * 60 * 20;
    /** 护盾刷新间隔：20秒 = 400 ticks */
    private static final int SHIELD_REFRESH_INTERVAL = 20 * 20;
    /** 最大护盾层数 */
    private static final int MAX_SHIELD = 2;
    /** 疯魔开始后强制结束时间：5分半 = 6600 ticks（疯魔5分钟 + 30秒缓冲） */
    private static final int FORCED_END_DELAY_TICKS = 5 * 60 * 20 + 30 * 20;

    /** 疯魔阶段开始时的游戏时间（tick） */
    private long psychoStartTime = 0;
    /** 护盾刷新 tick 计数器 */
    private int shieldRefreshTick = 0;

    public SREPsychoBrawlGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public boolean hasMood() {
        return false;
    }

    // ──────────────── 安全时间结束 → 触发疯魔 ────────────────

    @Override
    public void tickServerSafeTimeChecker(ServerLevel serverWorld, SREGameWorldComponent gameCCA) {
        if (safeTimeStarted > 10) {
            if (serverWorld.getGameTime() - safeTimeStarted >= SREConfig.instance().safeTimeCooldown * 20) {
                safeTimeStarted = 0;
                // 安全时间结束，启动全员疯魔
                activatePsychoForAll(serverWorld, gameCCA);
                psychoStartTime = serverWorld.getGameTime();
                shieldRefreshTick = 0;
                SRE.LOGGER.info("[PsychoBrawl] Safe time ended - Psycho mode activated for all players!");
            }
        }
    }

    /**
     * 为所有存活玩家启动疯魔模式
     */
    private void activatePsychoForAll(ServerLevel serverWorld, SREGameWorldComponent gameCCA) {
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerEliminated(player))
                continue;
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);
            if (psycho != null) {
                psycho.startPsycho_time(PSYCHO_DURATION_TICKS, MAX_SHIELD);
            }
        }
    }

    // ──────────────── 服务端主循环 ────────────────

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // ① 安全时间检查（父类中处理正常安全时间倒计时）
        tickServerSafeTimeChecker(serverWorld, gameWorldComponent);

        // 疯魔尚未启动时不执行后续逻辑
        if (psychoStartTime == 0)
            return;

        // ② 每 20 秒为所有存活疯魔玩家增加 1 层护盾（上限 MAX_SHIELD）
        if (++shieldRefreshTick >= SHIELD_REFRESH_INTERVAL) {
            for (ServerPlayer player : serverWorld.players()) {
                if (GameUtils.isPlayerEliminated(player))
                    continue;
                SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);
                if (psycho != null && psycho.getPsychoTicks() > 0) {
                    int currentArmour = psycho.armour;
                    if (currentArmour < MAX_SHIELD) {
                        psycho.setArmour(currentArmour + 1);
                    }
                }
            }
            shieldRefreshTick = 0;
        }

        // ②.5 实时显示护盾数量（actionbar）
        updateShieldDisplay(serverWorld);

        // ③ 常规胜利条件检查（最后存活者获胜，委托给父类亡命徒逻辑）
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        int playersLeft = 0;
        Player lastPlayer = null;
        for (Player player : serverWorld.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                playersLeft++;
                lastPlayer = player;
            }
        }

        if (playersLeft <= 0) {
            var modifiedWinStatus = AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld,
                    GameUtils.WinStatus.NO_PLAYER, true);
            if (!modifiedWinStatus.equals(GameUtils.WinStatus.NONE)) {
                GameUtils.stopGame(serverWorld);
                return;
            }
        }

        if (playersLeft == 1) {
            gameWorldComponent.setLooseEndWinner(lastPlayer.getUUID());
            winStatus = GameUtils.WinStatus.LOOSE_END;
        }

        // ④ 强制结束检查：疯魔开始 5 分半后，所有玩家死亡 → 无人胜利
        if (psychoStartTime > 0
                && serverWorld.getGameTime() - psychoStartTime >= FORCED_END_DELAY_TICKS) {
            forceEndAllPlayers(serverWorld);
            winStatus = GameUtils.WinStatus.NO_PLAYER;
        }

        // 游戏结束
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    /**
     * 实时向疯魔玩家发送护盾数量（显示在 actionbar）
     */
    private void updateShieldDisplay(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerEliminated(player))
                continue;
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);
            if (psycho != null && psycho.getPsychoTicks() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < MAX_SHIELD; i++) {
                    sb.append(i < psycho.armour ? "§a\u2588" : "§7\u2588");
                }
                player.displayClientMessage(
                        Component.literal("\u26E8 \u62A4\u76FE: ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(sb.toString())),
                        true);
            }
        }
    }

    /**
     * 强制清除所有存活玩家的护盾并击杀
     */
    private void forceEndAllPlayers(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerEliminated(player))
                continue;
            // 清除所有护盾以确保强制死亡生效
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (armor != null) {
                armor.clear();
            }
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);
            if (psycho != null && psycho.getPsychoTicks() > 0) {
                psycho.stopPsycho();
            }
            GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, true);
        }
        SRE.LOGGER.info("[PsychoBrawl] Forced end - all remaining players eliminated, no winner.");
    }
}
