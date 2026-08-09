package org.agmas.noellesroles.cs2;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.Pair;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * CS2 箱子管理器
 * <p>
 * 管理所有箱子配置，提供开箱抽奖功能。
 * </p>
 */
public class CS2BoxManager {

    private static CS2BoxManager instance;

    /** 所有已加载的箱子配置 {boxId: config} */
    private final Map<String, CS2BoxConfig> boxConfigs = new LinkedHashMap<>();

    /** 箱子配置目录 */
    private final Path configDir;

    private CS2BoxManager() {
        this.configDir = Paths.get("CS2_box");
        reload();
    }

    public static CS2BoxManager getInstance() {
        if (instance == null) {
            instance = new CS2BoxManager();
        }
        return instance;
    }

    /**
     * 重新加载所有箱子配置
     */
    public void reload() {
        boxConfigs.clear();
        List<CS2BoxConfig> configs = CS2BoxConfigParser.loadAll(configDir);
        for (CS2BoxConfig config : configs) {
            boxConfigs.put(config.getBoxId(), config);
        }
        Noellesroles.LOGGER.info("[CS2Box] Loaded {} box configs.", boxConfigs.size());
    }

    /**
     * 获取所有箱子配置
     */
    public Collection<CS2BoxConfig> getAllBoxes() {
        return Collections.unmodifiableCollection(boxConfigs.values());
    }

    /**
     * 获取指定箱子配置
     */
    public CS2BoxConfig getBox(String boxId) {
        return boxConfigs.get(boxId);
    }

    /**
     * 获取所有箱子 ID 列表
     */
    public Set<String> getBoxIds() {
        return Collections.unmodifiableSet(boxConfigs.keySet());
    }

    /**
     * 开箱结果
     */
    public static class BoxRollResult {
        /** 结果品质等级（0=common, 1=uncommon, ..., 5=unbelievable） */
        public final int quality;
        /** 皮肤 ID（格式：itemType/skinName） */
        public final String skinId;
        /** 是否为重复皮肤 */
        public final boolean isDuplicate;

        public BoxRollResult(int quality, String skinId, boolean isDuplicate) {
            this.quality = quality;
            this.skinId = skinId;
            this.isDuplicate = isDuplicate;
        }
    }

    /**
     * 执行开箱
     *
     * @param boxId  箱子 ID
     * @param player 开箱玩家
     * @return 开箱结果，null 表示开箱失败（箱子/钥匙不足或箱子不存在）
     */
    public BoxRollResult openBox(String boxId, ServerPlayer player) {
        CS2BoxConfig config = boxConfigs.get(boxId);
        if (config == null) {
            Noellesroles.LOGGER.warn("[CS2Box] Box not found: {}", boxId);
            return null;
        }

        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);

        // 验证箱子和钥匙
        if (inv.getBoxCount(boxId) <= 0) {
            Noellesroles.LOGGER.warn("[CS2Box] Player {} has no box: {}", player.getName().getString(), boxId);
            return null;
        }
        String keyName = config.getKeyName();
        if (keyName != null && !keyName.isEmpty() && inv.getKeyCount(keyName) <= 0) {
            Noellesroles.LOGGER.warn("[CS2Box] Player {} has no key: {}", player.getName().getString(), keyName);
            return null;
        }

        // 预检查抽奖池（在消耗材料之前，防止空池导致玩家损失）
        List<Pair<Double, List<String>>> qualityGroups = config.getQualityListGroup();
        if (qualityGroups.isEmpty()) {
            Noellesroles.LOGGER.error("[CS2Box] Box '{}' has no valid quality groups, aborting", boxId);
            return null;
        }

        // 消耗箱子和钥匙
        inv.removeBox(boxId, 1);
        if (keyName != null && !keyName.isEmpty()) {
            inv.removeKey(keyName, 1);
        }

        // 抽奖 + 发放（异常时返还箱子和钥匙，防止玩家损失材料）
        try {
            return rollAndGrant(config, boxId, player, inv);
        } catch (Exception e) {
            inv.addBox(boxId, 1);
            if (keyName != null && !keyName.isEmpty()) {
                inv.addKey(keyName, 1);
            }
            Noellesroles.LOGGER.error("[CS2Box] Error opening box '{}' for player {}, refunded box and key",
                    boxId, player.getName().getString(), e);
            return null;
        }
    }

    /**
     * 抽奖并发放物品（在箱子/钥匙已消耗后调用）
     */
    private BoxRollResult rollAndGrant(CS2BoxConfig config, String boxId, ServerPlayer player,
                                       CS2InventoryComponent inv) {
        List<Pair<Double, List<String>>> qualityGroups = config.getQualityListGroup();

        // 抽奖
        RandomSource random = player.getRandom();
        int curNum = random.nextInt(10000); // 粒度 10000
        double level = 0.0;
        int qualityIdx = 0;
        String skinId = null;

        for (int i = 0; i < qualityGroups.size(); i++) {
            level += qualityGroups.get(i).first;
            if (curNum < level * 10000) {
                qualityIdx = i;
                List<String> skins = qualityGroups.get(i).second;
                skinId = skins.get(random.nextInt(skins.size()));
                break;
            }
        }

        if (skinId == null) {
            // 兜底：取第一个品质
            qualityIdx = 0;
            List<String> skins = qualityGroups.get(0).second;
            skinId = skins.get(random.nextInt(skins.size()));
        }

        // 将结果品质映射回原始6级品质（考虑跳过空品质的偏移）
        int trueQuality = mapToTrueQuality(config, qualityIdx);

        // 解锁物品并添加到仓库
        boolean isDuplicate = false;
        String[] parts = skinId.split("/", 2);
        if (parts.length >= 2 && "musicbox".equals(parts[0])) {
            // 音乐盒：直接添加到音乐盒仓库
            String musicBoxId = parts[1];
            if (inv.hasMusicBox(musicBoxId)) {
                isDuplicate = true;
            }
            inv.addMusicBox(musicBoxId, 1);
        } else if (parts.length >= 2) {
            String itemType = parts[0];
            String skinName = parts[1];
            // 直接使用物品类型字符串查询，避免 getSkinItemStack 对 revolver/ 等前缀
            // 返回 null 导致空指针异常（曾造成开箱后箱子钥匙被吞且无物品发放）
            if (io.wifi.starrailexpress.data.PlayerEconomyManager
                    .isSkinUnlockedForItemType(player, itemType, skinName)) {
                isDuplicate = true;
            } else {
                ItemSkinManager.unlockSkinForItemType(player, itemType, skinName);
            }
            // 无论是否重复，都添加到仓库存储
            inv.addSkin(skinId, 1);
        }

        Noellesroles.LOGGER.info("[CS2Box] Player {} opened box '{}', got: quality={}, skin={}, duplicate={}",
                player.getName().getString(), boxId, trueQuality, skinId, isDuplicate);

        return new BoxRollResult(trueQuality, skinId, isDuplicate);
    }

    /**
     * 将过滤后的品质索引映射回原始6级品质
     * <p>
     * 例如：如果 common 和 rare 为空，qualityGroups 只有4项，
     * 索引 0 对应 uncommon(1)，索引 1 对应 epic(3)
     * </p>
     */
    private int mapToTrueQuality(CS2BoxConfig config, int filteredIdx) {
        List<Pair<Double, List<String>>> allGroups = config.getAllQualityGroups();
        int filtered = 0;
        for (int i = 0; i < allGroups.size(); i++) {
            Pair<Double, List<String>> group = allGroups.get(i);
            if (group.second != null && !group.second.isEmpty() && group.first > 0) {
                if (filtered == filteredIdx) {
                    return i;
                }
                filtered++;
            }
        }
        return filteredIdx;
    }

    /**
     * 为客户端生成滚动展示用的干扰卡片数据
     *
     * @param boxId     箱子 ID
     * @param result    真实结果
     * @param cardCount 需要的卡片数量
     * @param random    随机源
     * @return 卡片列表（每项为 (trueQuality, skinId)），目标卡片在 endCardIdx 位置
     */
    public List<Pair<Integer, String>> generateRollCards(String boxId, BoxRollResult result,
                                                         int cardCount, int endCardIdx, RandomSource random) {
        CS2BoxConfig config = boxConfigs.get(boxId);
        if (config == null) return new ArrayList<>();

        List<Pair<Double, List<String>>> qualityGroups = config.getQualityListGroup();
        List<Pair<Integer, String>> cards = new ArrayList<>();

        for (int i = 0; i < cardCount; i++) {
            if (i == endCardIdx) {
                // 目标位置放入真实结果（已经是 trueQuality）
                cards.add(new Pair<>(result.quality, result.skinId));
            } else {
                // 随机填充（靠近目标的位置增加高品质概率）
                Pair<Integer, String> card = rollRandomCard(config, qualityGroups, random,
                        Math.abs(i - endCardIdx));
                cards.add(card);
            }
        }

        return cards;
    }

    private Pair<Integer, String> rollRandomCard(CS2BoxConfig config,
                                                  List<Pair<Double, List<String>>> qualityGroups,
                                                  RandomSource random, int distanceFromTarget) {
        // 距离目标越近，高品质概率越高
        double highQualityBoost = 0;
        if (distanceFromTarget <= 5 && distanceFromTarget > 0) {
            highQualityBoost = 0.3 * (1.0 - (double)(distanceFromTarget - 1) / 5.0);
        }

        int curNum = random.nextInt(10000);
        double level = 0.0;
        for (int i = 0; i < qualityGroups.size(); i++) {
            double prob = qualityGroups.get(i).first;
            // 对最后几个品质增加概率
            if (i >= qualityGroups.size() - 2) {
                prob += highQualityBoost / 2.0;
            }
            level += prob;
            if (curNum < level * 10000) {
                List<String> skins = qualityGroups.get(i).second;
                if (!skins.isEmpty()) {
                    // 映射回真实品质索引（而非过滤后的索引）
                    int trueQuality = mapToTrueQuality(config, i);
                    return new Pair<>(trueQuality, skins.get(random.nextInt(skins.size())));
                }
            }
        }

        // 兜底
        int trueQuality = mapToTrueQuality(config, 0);
        List<String> firstSkins = qualityGroups.get(0).second;
        return new Pair<>(trueQuality, firstSkins.get(random.nextInt(firstSkins.size())));
    }

    /**
     * 在所有箱子配置中查找指定皮肤的品质等级
     *
     * @param skinId 皮肤 ID（如 "knife/knife_yingfeng"）
     * @return 品质等级（0~5），未找到返回 0
     */
    public int findSkinQuality(String skinId) {
        for (CS2BoxConfig config : boxConfigs.values()) {
            List<Pair<Double, List<String>>> allGroups = config.getAllQualityGroups();
            for (int i = 0; i < allGroups.size(); i++) {
                Pair<Double, List<String>> group = allGroups.get(i);
                if (group.second != null && group.second.contains(skinId)) {
                    return i;
                }
            }
        }
        return 0; // 默认 common
    }
}
