package org.agmas.noellesroles.cs2;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.cs2.network.BoxDropS2CPayload;

import java.util.*;

/**
 * CS2 风格箱子掉落管理器
 * <p>
 * 监听游戏结束事件，按概率掉落箱子和货币。
 * </p>
 */
public class CS2BoxDropManager {

    /** 未掉落时累加概率 */
    private static final int MISS_INCREMENT = 5;
    /** MVP 额外概率加成 */
    private static final int MVP_BONUS = 25;
    /** 货币掉落范围 */
    private static final int CURRENCY_MIN = 5;
    private static final int CURRENCY_MAX = 10;
    /** MVP 额外货币 */
    private static final int MVP_CURRENCY_BONUS = 10;

    private CS2BoxDropManager() {}

    /**
     * 注册游戏结束事件监听
     */
    public static void register() {
        OnGameEnd.EVENT.register(CS2BoxDropManager::onGameEnd);
        CS2MvpScoreManager.register();
    }

    /**
     * 游戏结束处理
     */
    private static void onGameEnd(ServerLevel world, SREGameWorldComponent gameComponent) {
        try {
            processBoxDrops(world, gameComponent);
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[CS2Drop] Error processing box drops", e);
        }
    }

    private static void processBoxDrops(ServerLevel world, SREGameWorldComponent gameComponent) {
        CS2BoxManager boxManager = CS2BoxManager.getInstance();
        Collection<CS2BoxConfig> allBoxes = boxManager.getAllBoxes();
        if (allBoxes.isEmpty()) {
            Noellesroles.LOGGER.info("[CS2Drop] No box configs loaded, skipping drops");
            return;
        }

        // 构建可用箱子列表
        List<String> availableBoxIds = new ArrayList<>();
        for (CS2BoxConfig config : allBoxes) {
            availableBoxIds.add(config.getBoxId());
        }

        // 确定 MVP（使用积分制）
        UUID mvpUuid = CS2MvpScoreManager.getMvp(world, gameComponent);

        // 向所有玩家广播 MVP 信息
        if (mvpUuid != null) {
            ServerPlayer mvpPlayer = (world.getPlayerByUUID(mvpUuid) instanceof ServerPlayer sp) ? sp : null;
            if (mvpPlayer != null) {
                int mvpScore = CS2MvpScoreManager.getScore(mvpUuid);
                for (ServerPlayer p : world.players()) {
                    p.displayClientMessage(
                            Component.literal("⭐ MVP: " + mvpPlayer.getName().getString()
                                    + " (" + mvpScore + " 分)")
                                    .withStyle(ChatFormatting.GOLD), true);
                }
            }
        }

        RandomSource random = RandomSource.create();

        // 使用当前世界的玩家而非全局玩家列表（避免多世界重复发放）
        for (ServerPlayer player : world.players()) {
            boolean isMvp = player.getUUID().equals(mvpUuid);
            CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);

            // 箱子掉落判定
            int dropChance = inv.getBoxDropChance();
            if (isMvp) {
                dropChance += MVP_BONUS;
            }

            String droppedBoxId = null;
            if (random.nextInt(100) < dropChance) {
                // 掉落成功
                droppedBoxId = availableBoxIds.get(random.nextInt(availableBoxIds.size()));
                inv.addBox(droppedBoxId, 1);
                inv.resetBoxDropChance();

                player.displayClientMessage(
                        Component.literal("🎁 你获得了一个箱子: " + boxManager.getBox(droppedBoxId).getBoxName())
                                .withStyle(ChatFormatting.GOLD), true);
            } else {
                // 未掉落，概率累加
                inv.addBoxDropChance(MISS_INCREMENT);
            }
            inv.sync();

            // 货币掉落
            int currency = random.nextInt(CURRENCY_MIN, CURRENCY_MAX + 1);
            if (isMvp) {
                currency += MVP_CURRENCY_BONUS;
            }
            PlayerEconomyManager.addCoinNum(player, currency);

            // 发送掉落通知到客户端
            ServerPlayNetworking.send(player,
                    new BoxDropS2CPayload(
                            droppedBoxId != null ? droppedBoxId : "",
                            currency,
                            isMvp));

            // MVP 额外提示
            if (isMvp) {
                player.displayClientMessage(
                        Component.literal("⭐ MVP 奖励: +" + currency + " 货币")
                                .withStyle(ChatFormatting.YELLOW), true);
            }

            Noellesroles.LOGGER.info("[CS2Drop] Player={}, MVP={}, box={}, currency={}",
                    player.getName().getString(), isMvp, droppedBoxId, currency);
        }
    }

    /**
     * 确定 MVP 玩家（已迁移至 CS2MvpScoreManager.getMvp）
     * @deprecated 使用 {@link CS2MvpScoreManager#getMvp} 替代
     */
    @Deprecated
    private static UUID determineMVP(ServerLevel world, SREGameWorldComponent gameComponent) {
        return CS2MvpScoreManager.getMvp(world, gameComponent);
    }
}
