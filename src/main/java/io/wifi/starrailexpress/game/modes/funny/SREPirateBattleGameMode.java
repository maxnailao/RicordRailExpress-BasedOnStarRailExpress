package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

/**
 * 海盗大作战
 * <p>
 * 同亡命徒大战模式，但开局清空亡命徒原本物品，改为给予：
 * 海盗弯刀×3、海盗燧发枪×1、可直接使用护盾×1、耐久橡木船×1。
 * 游戏开始后：
 * - 若玩家背包内海盗弯刀数量 < 3，每隔5秒补给一个海盗弯刀
 * - 若玩家背包内海盗燧发枪数量 < 1，每隔10秒补给一个海盗燧发枪
 * - 若玩家背包内耐久橡木船 < 1，每隔30秒补给一个耐久橡木船
 * 最终存活者获胜。
 * </p>
 *
 * @author jiale
 */
public class SREPirateBattleGameMode extends WTLooseEndsGameMode {

    /** 海盗弯刀补给间隔：5秒 = 100 ticks */
    private static final int CUTLASS_REFILL_INTERVAL = 5 * 20;
    /** 海盗燧发枪补给间隔：10秒 = 200 ticks */
    private static final int FLINTLOCK_REFILL_INTERVAL = 10 * 20;
    /** 耐久橡木船补给间隔：30秒 = 600 ticks */
    private static final int BOAT_REFILL_INTERVAL = 30 * 20;

    /** 海盗弯刀最大持有数 */
    private static final int MAX_CUTLASS = 3;
    /** 海盗燧发枪最大持有数 */
    private static final int MAX_FLINTLOCK = 1;
    /** 耐久橡木船最大持有数 */
    private static final int MAX_BOAT = 1;

    /** 各补给 tick 计数器 */
    private int cutlassRefillTick = 0;
    private int flintlockRefillTick = 0;
    private int boatRefillTick = 0;

    public SREPirateBattleGameMode(ResourceLocation identifier) {
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

    // ──────────────── 初始化：替换物品为海盗套装 ────────────────

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        // 重置补给计时器
        cutlassRefillTick = 0;
        flintlockRefillTick = 0;
        boatRefillTick = 0;
    }

    /**
     * 替换亡命徒默认物品为海盗套装：
     * 海盗弯刀×3 + 海盗燧发枪×1 + 防御药剂×1 + 耐久橡木船×1
     */
    @Override
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            // 海盗弯刀 ×3
            ItemStack cutlasses = new ItemStack(ModItems.PIRATE_CUTLASS);
            cutlasses.setCount(MAX_CUTLASS);
            player.addItem(cutlasses);
            // 海盗燧发枪 ×1
            player.addItem(new ItemStack(ModItems.PIRATE_FLINTLOCK));
            // 可直接使用护盾（防御药剂）×1
            player.addItem(new ItemStack(TMMItems.DEFENSE_VIAL));
            // 耐久橡木船 ×1
            player.addItem(new ItemStack(ModItems.DURABILITY_BOAT));
        }
    }

    // ──────────────── 服务端主循环 ────────────────

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // ① 物品补给逻辑
        refillItems(serverWorld);

        // ② 常规胜利条件检查（最后存活者获胜）
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

        // 游戏结束
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    /**
     * 定时为存活玩家补给物品
     */
    private void refillItems(ServerLevel serverWorld) {
        cutlassRefillTick++;
        flintlockRefillTick++;
        boatRefillTick++;

        boolean doCutlass = cutlassRefillTick >= CUTLASS_REFILL_INTERVAL;
        boolean doFlintlock = flintlockRefillTick >= FLINTLOCK_REFILL_INTERVAL;
        boolean doBoat = boatRefillTick >= BOAT_REFILL_INTERVAL;

        if (!doCutlass && !doFlintlock && !doBoat)
            return;

        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerEliminated(player))
                continue;

            // 海盗弯刀补给
            if (doCutlass) {
                int cutlassCount = countItem(player, ModItems.PIRATE_CUTLASS);
                if (cutlassCount < MAX_CUTLASS) {
                    player.addItem(new ItemStack(ModItems.PIRATE_CUTLASS));
                }
            }

            // 海盗燧发枪补给
            if (doFlintlock) {
                int flintlockCount = countItem(player, ModItems.PIRATE_FLINTLOCK);
                if (flintlockCount < MAX_FLINTLOCK) {
                    player.addItem(new ItemStack(ModItems.PIRATE_FLINTLOCK));
                }
            }

            // 耐久橡木船补给
            if (doBoat) {
                int boatCount = countItem(player, ModItems.DURABILITY_BOAT);
                if (boatCount < MAX_BOAT) {
                    player.addItem(new ItemStack(ModItems.DURABILITY_BOAT));
                }
            }
        }

        if (doCutlass) cutlassRefillTick = 0;
        if (doFlintlock) flintlockRefillTick = 0;
        if (doBoat) boatRefillTick = 0;
    }

    /**
     * 统计玩家背包中某物品的数量
     */
    private int countItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
