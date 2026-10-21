package org.agmas.noellesroles.utils.lottery;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽奖管理器
 * - 实现抽奖功能
 * - 管理抽奖数据的调用
 */
public class LotteryManager {
    public static class LotteryPool {
        public LotteryPool(int poolID, String name, String type,
                List<LotteryPoolsConfig.PoolConfig.QualityListItemConfig> qualityListGroupConfigs) {
            this.poolID = poolID;
            this.name = name;
            this.type = type;
            this.qualityListGroupConfigs = new ArrayList<>();
            for (LotteryPoolsConfig.PoolConfig.QualityListItemConfig qualityListItemConfig : qualityListGroupConfigs) {
                this.qualityListGroupConfigs.add(
                        new Pair<>(qualityListItemConfig.getProbability(), qualityListItemConfig.getItemList()));
            }
        }

        /** 使用已有卡池类构造，为了防止形参冲突，与读取配置的构造相区别，可以接受不同的列表 */
        public LotteryPool(String name, int poolID, String type,
                List<Pair<Double, List<String>>> qualityListGroupConfigs) {
            this.poolID = poolID;
            this.name = name;
            this.type = type;
            this.qualityListGroupConfigs = qualityListGroupConfigs;
        }

        /**
         * 抽奖一次
         *
         * @param player 基于玩家进行随机:抽奖结果需要对玩家信息查询修改，因此必然有player参数传入，所以直接用玩家源
         * @return 返回抽奖结果：结果的品质和在该品质内的索引
         */
        public Pair<Integer, Integer> rollOnce(ServerPlayer player) {
            return rollOnce(player, null);
        }

        /**
         * 抽奖一次（带新解锁标志输出）
         * @param newSkinOut 长度>=1 的布尔数组时，out[0] 回填本次是否新解锁了皮肤（用于客户端快速出售判定）
         */
        public Pair<Integer, Integer> rollOnce(ServerPlayer player, boolean[] newSkinOut) {
            RandomSource randomSource = player.getRandom();
            LotteryRecordData record = LotteryRecordStorage.getInstance()
                    .getPlayerLotteryRecord(player.getUUID());

            int resultQuality = -1;
            int resultIdx = -1;

            // 保底优先：第 80 抽直接锁定传承，不参与摇号，任何自然结果都无法顶掉保底
            if (record.getPityCount() + 1 >= PITY_THRESHOLD) {
                List<String> inheritList = getInheritItemList();
                if (inheritList != null && !inheritList.isEmpty()) {
                    resultQuality = INHERIT_QUALITY_INDEX;
                    resultIdx = randomSource.nextInt(inheritList.size());
                } else {
                    Noellesroles.LOGGER.error("[LootSys] 卡池 {} 没有传承品质条目，保底无法生效", poolID);
                }
            }
            // 未触发保底才正常摇号
            if (resultQuality < 0) {
                int curNum = randomSource.nextInt(maxGranularity);// 0 ~ maxGranularity -1
                double level = 0.0f;
                for (int i = 0; i < qualityListGroupConfigs.size(); ++i) {
                    level += qualityListGroupConfigs.get(i).first;
                    if (curNum < level * maxGranularity) {
                        resultQuality = i;
                        resultIdx = randomSource.nextInt(qualityListGroupConfigs.get(i).second.size());
                        break;
                    }
                }
            }
            if (resultQuality < 0) {
                Noellesroles.LOGGER.warn("[LootSys] 玩家UUID:" + player.getUUID() + "抽奖失败");
                return new Pair<>(-1, -1);
            }

            List<String> curQualityList = qualityListGroupConfigs.get(resultQuality).second;
            // 设置itemStack
            ItemStack itemStack = getSkinItemStack(curQualityList.get(resultIdx));

            // 去除前缀
            String trueName = getTrueName(curQualityList.get(resultIdx));
            boolean isNewUnlock = false;
            String itemType = getSkinItemType(curQualityList.get(resultIdx));
            if (itemType != null && !ItemSkinManager.isSkinUnlockedForItemType(player, itemType, trueName)) {
                ItemSkinManager.unlockSkinForItemType(player, itemType, trueName);
                isNewUnlock = true;
                // 抽奖新解锁的皮肤必须同步写入仓库组件（与开箱路径一致），
                // 否则 CS2WarehouseScreen 因只遍历 CS2InventoryComponent.getSkins() 而看不到该皮肤
                io.wifi.starrailexpress.cca.CS2InventoryComponent.KEY.get(player)
                        .addSkin(itemType + "/" + trueName, 1);
                LotteryManager.getInstance().recordLootGrant(player.getUUID(), trueName, resultQuality);
            }
            //coin
            else if (trueName.equals("coin")) {
                ItemSkinManager.addCoinNum(player, (int) (baseLootConsumeCoin * getSkinToCoinPercentage(resultQuality) * COIN_CARD_EXTRA_REWARD));
            }
            // 处理重复皮肤：自动折币，同时记录本次结果允许快速出售（出售会回收已拥有的皮肤再折算一次）
            else {
                ItemSkinManager.addCoinNum(player, (int) (baseLootConsumeCoin * getSkinToCoinPercentage(resultQuality)));
                if (itemType != null)
                    LotteryManager.getInstance().recordLootGrant(player.getUUID(), trueName, resultQuality);
            }

            // 更新保底计数：出传承归零，否则 +1，并落盘持久化
            if (resultQuality == INHERIT_QUALITY_INDEX)
                record.setPityCount(0);
            else
                record.setPityCount(record.getPityCount() + 1);
            LotteryRecordStorage.getInstance().markPlayerDirty(player.getUUID());
            LotteryRecordStorage.getInstance().savePlayerData(player.getUUID());

            if (newSkinOut != null && newSkinOut.length > 0)
                newSkinOut[0] = isNewUnlock;
            return new Pair<>(resultQuality, resultIdx);
        }

        /** 本卡池的传承品质条目列表，卡池没有传承组时返回 null */
        private List<String> getInheritItemList() {
            if (qualityListGroupConfigs.size() > INHERIT_QUALITY_INDEX)
                return qualityListGroupConfigs.get(INHERIT_QUALITY_INDEX).second;
            return null;
        }

        /**
         *  获取卡池真实皮肤名称
         *  - 皮肤名组成：type/truename[/resources_location]
         *  - 方括号为可选内容
         * @param rawName 待处理的配置文件皮肤名
         */
        public static String getTrueName(String rawName) {
            String[] names = rawName.split("/");
            if (names.length > 1)
                return names[1];
            return "coin";
        }

        /**
         *  获取皮肤对应物品的itemStack
         *  - 皮肤名组成：type/truename[/resources_location]
         *  - 方括号为可选内容
         * @param rawName 待处理的配置文件皮肤名
         */
        public static ItemStack getSkinItemStack(String rawName) {
            ItemStack itemStack = null;
            // 设置itemStack
            if (rawName.startsWith("knife/")) {
                itemStack = TMMItems.KNIFE.getDefaultInstance();
            }
            else if (rawName.startsWith("gun/") || rawName.startsWith("revolver/")) {
                itemStack = TMMItems.REVOLVER.getDefaultInstance();
            }
            else if (rawName.startsWith("bat/")) {
                itemStack = TMMItems.BAT.getDefaultInstance();
            }
            else if (rawName.startsWith("grenade/")) {
                itemStack = TMMItems.GRENADE.getDefaultInstance();
            }
            else if (rawName.startsWith("bow/")) {
                itemStack = TMMItems.BOW.getDefaultInstance();
            }
            else if (rawName.startsWith("crossbow/")) {
                itemStack = TMMItems.CROSSBOW.getDefaultInstance();
            }
            return itemStack;
        }

        /** 返回奖池配置使用的皮肤类型；帽子没有可作为类型载体的装备物品。 */
        public static String getSkinItemType(String rawName) {
            if (rawName == null) {
                return null;
            }
            int slash = rawName.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            return switch (rawName.substring(0, slash)) {
                case "knife" -> "knife";
                case "gun", "revolver" -> "revolver";
                case "bat", "grenade", "hat", "bow", "crossbow" -> rawName.substring(0, slash);
                default -> null;
            };
        }

        public int getPoolID() {
            return poolID;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public List<Pair<Double, List<String>>> getQualityListGroupConfigs() {
            return qualityListGroupConfigs;
        }

        private final int poolID;
        private final String name;
        private final String type;

        /**
         * 各品质卡池内容及其概率
         * <p>
         * - 品质为由低到高，概率为从高到低
         * </p>
         */
        private final List<Pair<Double, List<String>>> qualityListGroupConfigs;
    }

    /** 检查玩家的抽奖次数 > 0 */
    public boolean canRoll(ServerPlayer player) {
        if (player == null)
            return false;

//        LotteryRecordData lotteryRecordData = LotteryRecordStorage.getInstance()
//                .getPlayerLotteryRecord(player.getUUID());
//        return lotteryRecordData.lotteryChance > 0;
        return ItemSkinManager.getLootChance(player) > 0;
    }

    /** 购买一抽的价格（硬币） */
    public static final int LOTTERY_CHANCE_COIN_COST = 500;

    /** 保底：连续这么多抽未出传承品质，下一抽强制传承 */
    public static final int PITY_THRESHOLD = 80;

    /** 抽奖新解锁皮肤的快速出售有效窗口：10 分钟 */
    public static final long LOOT_GRANT_SELL_WINDOW_MS = 10 * 60 * 1000L;

    /** 抽奖发放记录：仅窗口内通过抽奖新解锁的皮肤允许快速出售 */
    public record LootGrant(String trueName, int quality, long timeMs) {}

    private final Map<UUID, List<LootGrant>> recentLootGrants = new ConcurrentHashMap<>();

    /** 记录一次抽奖新解锁的皮肤（服务端权威，用于出售价校验） */
    public void recordLootGrant(UUID uuid, String trueName, int quality) {
        long now = net.minecraft.Util.getMillis();
        List<LootGrant> list = recentLootGrants.computeIfAbsent(uuid, k -> new ArrayList<>());
        list.removeIf(g -> now - g.timeMs() > LOOT_GRANT_SELL_WINDOW_MS);
        list.add(new LootGrant(trueName, quality, now));
    }

    /** 皮肤出售折算价值：与商店快速出售同一价格表（ShopConfig.sellprice，按品质定价） */
    public static int getSkinSellValue(int quality) {
        int maxQuality = ItemSkinManager.QualityColor.values().length - 1; // 含传承(6)，勿再硬编码 5
        return org.agmas.noellesroles.cs2.ShopConfig.getInstance().getSellPriceConfig()
                .getSkinPriceByQuality(Math.min(Math.max(quality, 0), maxQuality));
    }

    /**
     * 快速出售本次抽奖结果：逐条比对发放记录，命中则回收皮肤（lockSkin）并按品质折算发币
     * @return 实际售出的总价值
     */
    public int sellLootResults(ServerPlayer player, int poolID, List<int[]> results) {
        if (player == null || results == null || results.isEmpty())
            return 0;
        LotteryPool pool = getLotteryPool(poolID);
        if (pool == null)
            return 0;
        List<LootGrant> grants = recentLootGrants.get(player.getUUID());
        if (grants == null || grants.isEmpty())
            return 0;
        long now = net.minecraft.Util.getMillis();
        grants.removeIf(g -> now - g.timeMs() > LOOT_GRANT_SELL_WINDOW_MS);
        int total = 0;
        for (int[] r : results) {
            if (r == null || r.length < 2)
                continue;
            int quality = r[0], ansID = r[1];
            if (quality < 0 || quality >= pool.getQualityListGroupConfigs().size())
                continue;
            List<String> itemList = pool.getQualityListGroupConfigs().get(quality).second;
            if (ansID < 0 || ansID >= itemList.size())
                continue;
            String itemName = itemList.get(ansID);
            // 金币卡等无皮肤前缀条目不可出售
            if (!itemName.contains("/"))
                continue;
            String trueName = LotteryPool.getTrueName(itemName);
            boolean matched = false;
            Iterator<LootGrant> it = grants.iterator();
            while (it.hasNext()) {
                if (it.next().trueName().equals(trueName)) {
                    it.remove();
                    matched = true;
                    break;
                }
            }
            if (!matched)
                continue;
            String itemType = LotteryPool.getSkinItemType(itemName);
            if (itemType != null && ItemSkinManager.isSkinUnlockedForItemType(player, itemType, trueName)) {
                io.wifi.starrailexpress.data.PlayerEconomyManager.lockSkinForItemType(player, itemType, trueName);
                total += getSkinSellValue(quality);
            }
        }
        if (total > 0)
            ItemSkinManager.addCoinNum(player, total);
        return total;
    }

    /** 添加抽奖机会 */
    public void addOrDegreeLotteryChance(ServerPlayer player, int chance) {
//        LotteryRecordStorage.getInstance().updatePlayerLotteryData(player.getUUID(),
//                lotteryRecordData -> lotteryRecordData.lotteryChance += chance);
//        LotteryRecordStorage.getInstance().savePlayerData(player.getUUID());
        ItemSkinManager.addLootChance(player, chance);
    }

    /**
     * 添加卡池
     * <p>
     * NOTE:
     * 该函数用于客户端初次同步服务端卡池，而不是用来运行时添加卡池的；具体而言：
     * 指客户端首次打开抽奖界面会发送完整卡池信息，客户端保存缓存（仅内存），下次访问卡池信息时会从缓存中读取而不用再发送卡池信息
     * TODO:未来需要将卡池信息缓存为本地文件，每次启动自动读取，调用时与服务器卡池信息文件hash值进行对比同步，进一步减少发包
     * </p>
     */
    public void addLotteryPool(LotteryPool lotteryPool) {
        lotteryPoolList.add(lotteryPool);
    }

    public List<Integer> getPoolIDs() {
        List<Integer> poolIDs = new ArrayList<>();
        for (LotteryPool lotteryPool : lotteryPoolList) {
            poolIDs.add(lotteryPool.getPoolID());
        }
        return poolIDs;
    }

    /** 获取指定 ID的卡池 */
    public LotteryPool getLotteryPool(int poolID) {
        for (LotteryPool lotteryPool : lotteryPoolList) {
            if (lotteryPool.getPoolID() == poolID) {
                return lotteryPool;
            }
        }
        return null;
    }

    public List<LotteryPool> getLotteryPools() {
        return lotteryPoolList;
    }

    public void setLotteryPoolByID(int poolID, LotteryPool lotteryPool) {
        for (int i = 0; i < lotteryPoolList.size(); ++i) {
            if (lotteryPoolList.get(i).poolID == poolID) {
                lotteryPoolList.set(i, lotteryPool);
                return;
            }
        }
    }

    public void sortPools() {
        lotteryPoolList.sort(Comparator.comparingInt(LotteryPool::getPoolID));
    }

    public static ResourceLocation getQualityBgResourceLocation(int index) {
        if (index < 0)
            return qualityBgList[0];
        else if (index >= qualityBgList.length)
            return qualityBgList[qualityBgList.length - 1];
        return qualityBgList[index];
    }

    public static float getSkinToCoinPercentage(int idx) {
        if (idx < 0)
            return skinToCoin[0];
        else if (idx >= skinToCoin.length)
            return skinToCoin[skinToCoin.length - 1];
        return skinToCoin[idx];
    }

    public void clearPools() {
        lotteryPoolList.clear();
    }

    /** 清空配置，重新加载卡池 */
    public void reload() {
        // 为了让单人测试也生效，暂时让客户端也能读取配置，并且未来可以加入本地数据存储，进行卡池信息hash值对比更新，也能减少数据传输量
        // // 仅运行在服务端
        // if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT)
        // {
        // return;
        // }
        lotteryPoolList.clear();
        // 配置文件应该存放在主目录lottery_data目录下的lottery_pool.json文件中
        Path configPath = LotteryRecordStorage.getInstance().getLotteryDataDir().resolve("lottery_pool.json");
        LotteryPoolsConfig lotteryPoolsConfig = LotteryPoolsConfigParser.parse(configPath);
        if (lotteryPoolsConfig == null || lotteryPoolsConfig.getPools().isEmpty()) {
            Noellesroles.LOGGER.error("[LootSys] No valid pool configuration found.Path:{}", configPath);
            return;
        }
        for (LotteryPoolsConfig.PoolConfig poolConfig : lotteryPoolsConfig.getPools()) {
            // 验证配置数据
            try {
                if (!poolConfig.isEnable())
                    continue;
                if (poolConfig.getPoolName() == null || poolConfig.getPoolName().isEmpty()) {
                    Noellesroles.LOGGER.error("[LootSys] Pool name is null or empty.");
                    continue;
                }
                if (poolConfig.getPoolType() == null || poolConfig.getPoolType().isEmpty()) {
                    Noellesroles.LOGGER.error("[LootSys] Pool type is null or empty.");
                    continue;
                }
                Double sumProbability = 0.0;
                for (LotteryPoolsConfig.PoolConfig.QualityListItemConfig qualityListItemConfig : poolConfig
                        .getQualityListGroup()) {
                    if (qualityListItemConfig.getItemList() == null || qualityListItemConfig.getItemList().isEmpty()) {
                        Noellesroles.LOGGER.error("[LootSys] Quality list is null or empty.");
                        continue;
                    }
                    if (qualityListItemConfig.getProbability() == null || qualityListItemConfig.getProbability() <= 0) {
                        Noellesroles.LOGGER.error("[LootSys] Quality list probability is null or <= 0.");
                        continue;
                    }
                    sumProbability += qualityListItemConfig.getProbability();
                }
                if (sumProbability < 0.999 || sumProbability > 1.001) {
                    Noellesroles.LOGGER.error("[LootSys] Quality list probability sum({}) is not equal to 1.",
                            sumProbability);
                    continue;
                }
                LotteryPool pool = new LotteryPool(
                        poolConfig.getPoolID(), poolConfig.getPoolName(), poolConfig.getPoolType(),
                        poolConfig.getQualityListGroup());
                // 将卡池添加进列表
                this.lotteryPoolList.add(pool);
                Noellesroles.LOGGER.info("[LootSys] Loaded pool:ID:{},Name{},Type{}", pool.poolID, pool.name,
                        pool.type);
            } catch (Exception e) {
                Noellesroles.LOGGER.error("[LootSys] Failed to parse pool config : {}", poolConfig.getPoolName(), e);
            }
        }
        Noellesroles.LOGGER.info("[LootSys] Loaded {} pools.", lotteryPoolList.size());
    }
    private void init() {
        reload();
    }
    private LotteryManager() {
        init();
    };

    public static LotteryManager getInstance() {
        if (instance == null)
            instance = new LotteryManager();
        return instance;
    }

    private static LotteryManager instance = null;

    private static final ResourceLocation[] qualityBgList = {
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/common_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/uncommon_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/rare_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/epic_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/legendary_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/unbelievable.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/inherit_skin.png"),
    };
    /** 传承品质档位索引（QualityColor.LEGACY 的 ordinal，也是卡池 QualityListGroup 的第 7 组） */
    public static final int INHERIT_QUALITY_INDEX = 6;
    /**
     * 传承品质的"时不时爆出红光"脉冲：返回 0~1 的强度。
     * 每 2.6 秒一个窗口，峰值时刻与脉宽用伪随机决定，平时为 0，爆发时快速冲高再回落。
     */
    public static float inheritRedPulse() {
        long t = net.minecraft.Util.getMillis();
        final long cycle = 2600L;
        long seed = t / cycle;
        long rnd = (seed * 6364136223846793005L + 1442695040888963407L) >>> 44;
        float peak = 0.18f + (rnd % 640) / 1000.0f;
        float halfW = 0.05f + (rnd % 40) / 1000.0f;
        float phase = (t % cycle) / (float) cycle;
        float d = Math.abs(phase - peak) / halfW;
        if (d >= 1.0f)
            return 0.0f;
        float s = (float) Math.cos(d * Math.PI * 0.5);
        return s * s;
    }
    /** 不同品质重复皮肤对应折算的为单抽硬币数量的比例 */
    public static final float[] skinToCoin = {
            0.1f, 0.125f, 0.25f, 0.5f, 1f, 2f, 3f
    };
    /** 抽到金币时的额外奖励，默认1.1倍 */
    public static final float COIN_CARD_EXTRA_REWARD = 1.1f;
    /**
     * 抽奖粒度
     * <p>
     * - 抽奖粒度越小，抽奖概率越准确
     * - 具体实现：抽取时生成0~该值的随机数，由pollProbability数组的百分比进行按顺序切分，直到找到对应的概率区间
     * </p>
     */
    public static final int maxGranularity = 1000;
    /** 抽奖消耗的硬币数量 */
    public static final int baseLootConsumeCoin = 648;
    private final ArrayList<LotteryPool> lotteryPoolList = new ArrayList<>();
    @SuppressWarnings("unused")
    private final String defaultPoolItem = "coin_common";
}
