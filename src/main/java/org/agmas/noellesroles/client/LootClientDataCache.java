package org.agmas.noellesroles.client;

/**
 * 抽奖页数据缓存：抽奖动画界面（LootScreen/LootMultiScreen）遮挡期间收到的
 * 服务端刷新数据先存这里，回到 LootInfoScreen 后由 tick 实时对账应用。
 */
public class LootClientDataCache {
    public static int coinNumber = -1;
    public static int lootChance = -1;
    public static int pityCount = -1;

    public static void update(int coin, int chance, int pity) {
        coinNumber = coin;
        lootChance = chance;
        pityCount = pity;
    }
}