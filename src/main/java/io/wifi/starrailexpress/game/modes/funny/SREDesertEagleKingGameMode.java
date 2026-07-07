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
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

/**
 * 沙鹰之王
 * <p>
 * 类似亡命徒模式，所有玩家为亡命徒。
 * 开局清除原有物品，替换为1把沙漠之鹰 + 64个沙鹰弹夹 + 撬棍。
 * 安全时间结束后全员进入疯魔护盾（5分半），每20秒护盾增加1层（上限2层）。
 * 游戏过去5分半时，所有存活玩家强制死亡，无人胜利。
 * 最终存活者获胜。
 * </p>
 *
 * @author jiale
 */
public class SREDesertEagleKingGameMode extends WTLooseEndsGameMode {

    /** 护盾刷新间隔：20秒 = 400 ticks */
    private static final int SHIELD_REFRESH_INTERVAL = 20 * 20;
    /** 最大护盾层数 */
    private static final int MAX_SHIELD = 2;
    /** 疯魔护盾持续时间：与强制结束时间同步 = 6600 ticks（5分半） */
    private static final int PSYCHO_DURATION_TICKS = 5 * 60 * 20 + 30 * 20;
    /** 安全时间结束后强制结束时间：5分半 = 6600 ticks */
    private static final int FORCED_END_DELAY_TICKS = 5 * 60 * 20 + 30 * 20;

    /** 计时开始时的游戏时间（tick） */
    private long timerStartTime = 0;
    /** 护盾刷新 tick 计数器 */
    private int shieldRefreshTick = 0;

    public SREDesertEagleKingGameMode(ResourceLocation identifier) {
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

    // ──────────────── 初始化：替换物品为沙鹰套装 ────────────────

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        // 初始化时清零所有玩家护盾（通用护盾 + 疯魔护盾）
        for (ServerPlayer player : players) {
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (armor != null) {
                armor.armor = 0;
                armor.sync();
            }
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);
            if (psycho != null && psycho.getPsychoTicks() > 0) {
                psycho.stopPsycho();
            }
        }
    }

    /**
     * 替换亡命徒默认物品为沙鹰套装：1把沙漠之鹰 + 64个弹夹 + 撬棍
     */
    @Override
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            // 1 把沙漠之鹰
            player.addItem(new ItemStack(ModItems.DESERT_EAGLE));
            // 64 个沙鹰弹夹
            ItemStack magazines = new ItemStack(ModItems.DESERT_EAGLE_MAGAZINE);
            magazines.setCount(64);
            player.addItem(magazines);
            // 1 把撬棍
            player.addItem(new ItemStack(TMMItems.CROWBAR));
        }
    }

    // ──────────────── 安全时间结束 → 开始计时 ────────────────

    @Override
    public void tickServerSafeTimeChecker(ServerLevel serverWorld, SREGameWorldComponent gameCCA) {
        if (safeTimeStarted > 10) {
            if (serverWorld.getGameTime() - safeTimeStarted >= SREConfig.instance().safeTimeCooldown * 20) {
                safeTimeStarted = 0;
                timerStartTime = serverWorld.getGameTime();
                shieldRefreshTick = 0;
                // 为所有存活玩家启动疯魔护盾
                activatePsychoShields(serverWorld);
                SRE.LOGGER.info("[DesertEagleKing] Safe time ended - psycho shields activated!");
            }
        }
    }

    // ──────────────── 服务端主循环 ────────────────

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // ① 安全时间检查
        tickServerSafeTimeChecker(serverWorld, gameWorldComponent);

        // 计时尚未开始时不执行后续逻辑
        if (timerStartTime == 0)
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

        // ③ 常规胜利条件检查（最后存活者获胜）
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

        // ④ 强制结束检查：计时开始 5 分半后，所有玩家死亡 → 无人胜利
        if (timerStartTime > 0
                && serverWorld.getGameTime() - timerStartTime >= FORCED_END_DELAY_TICKS) {
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
     * 实时向存活玩家发送护盾数量（显示在 actionbar）
     */
    private void updateShieldDisplay(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerEliminated(player))
                continue;
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);
            if (psycho != null && psycho.getPsychoTicks() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < MAX_SHIELD; i++) {
                    sb.append(i < psycho.armour ? "\u00A7a\u2588" : "\u00A77\u2588");
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
            // 停止疯魔护盾
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);
            if (psycho != null && psycho.getPsychoTicks() > 0) {
                psycho.stopPsycho();
            }
            // 清除通用护盾
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (armor != null) {
                armor.clear();
            }
            GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN, true);
        }
        SRE.LOGGER.info("[DesertEagleKing] Forced end - all remaining players eliminated, no winner.");
    }

    /**
     * 为所有存活玩家启动疯魔护盾
     */
    private void activatePsychoShields(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerEliminated(player))
                continue;
            SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);
            if (psycho != null) {
                psycho.startPsycho_time(PSYCHO_DURATION_TICKS, MAX_SHIELD);
            }
        }
    }
}
