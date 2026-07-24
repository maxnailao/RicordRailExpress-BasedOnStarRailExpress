package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.cs2.*;
import org.agmas.noellesroles.utils.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CS2 系统所有 C2S 网络包的服务端接收器注册
 */
public class CS2ServerReceiverRegister {

    /** 正在开箱的玩家集合，防止同一玩家并发开箱导致箱子被吞 */
    private static final Set<UUID> openingBoxPlayers = new HashSet<>();

    public static void registerAll() {
        registerOpenBox();
        registerShopBuy();
        registerShopSell();
        registerBlackMarketList();
        registerBlackMarketBuy();
        registerBlackMarketCancel();
        registerBlackMarketSyncRequest();
        registerBlackMarketClaim();
        registerEquipSkin();
        registerEquipMusicBox();
    }

    // ── 开箱 ──

    private static void registerOpenBox() {
        ServerPlayNetworking.registerGlobalReceiver(OpenBoxC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                // 防止同一玩家并发开箱
                if (openingBoxPlayers.contains(player.getUUID())) {
                    ServerPlayNetworking.send(player, new OpenBoxResultS2CPayload(
                            false, 0, "", false, 0, List.of(), List.of()));
                    return;
                }
                openingBoxPlayers.add(player.getUUID());
                try {
                    String boxId = payload.boxId();
                    CS2BoxManager boxManager = CS2BoxManager.getInstance();
                    CS2BoxManager.BoxRollResult result = boxManager.openBox(boxId, player);

                    if (result == null) {
                        ServerPlayNetworking.send(player, new OpenBoxResultS2CPayload(
                                false, 0, "", false, 0, List.of(), List.of()));
                        return;
                    }

                    // 生成滚动卡片数据
                    int cardCount = 60;
                    int endCardIdx = 45 + player.getRandom().nextInt(5);
                    List<Pair<Integer, String>> cards = boxManager.generateRollCards(
                            boxId, result, cardCount, endCardIdx, player.getRandom());

                    List<Integer> cardQualities = new ArrayList<>(cards.size());
                    List<String> cardSkinIds = new ArrayList<>(cards.size());
                    for (Pair<Integer, String> card : cards) {
                        cardQualities.add(card.first);
                        cardSkinIds.add(card.second);
                    }

                    ServerPlayNetworking.send(player, new OpenBoxResultS2CPayload(
                            true, result.quality, result.skinId, result.isDuplicate,
                            endCardIdx, cardQualities, cardSkinIds));

                    Noellesroles.LOGGER.info("[CS2Box] Sent result to {}: quality={}, skin={}",
                            player.getName().getString(), result.quality, result.skinId);
                } finally {
                    openingBoxPlayers.remove(player.getUUID());
                }
            });
        });
    }

    // ── 商店购买 ──

    private static void registerShopBuy() {
        ServerPlayNetworking.registerGlobalReceiver(ShopBuyC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                String itemType = payload.itemType();
                String itemId = payload.itemId();

                ShopConfig shopConfig = ShopConfig.getInstance();
                ShopConfig.ShopItem shopItem = null;
                for (ShopConfig.ShopItem item : shopConfig.getShopItems()) {
                    if (item.type.equals(itemType) && item.id.equals(itemId)) {
                        shopItem = item;
                        break;
                    }
                }

                if (shopItem == null) {
                    player.displayClientMessage(
                            Component.literal("§c商品不存在").withStyle(ChatFormatting.RED), true);
                    return;
                }

                int coins = PlayerEconomyManager.getCoinNum(player);
                if (coins < shopItem.price) {
                    player.displayClientMessage(
                            Component.literal("§c货币不足，需要 " + shopItem.price + " 货币"), true);
                    return;
                }

                // 扣款
                PlayerEconomyManager.addCoinNum(player, -shopItem.price);

                // 发放物品
                CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
                if ("box".equals(itemType)) {
                    inv.addBox(itemId, 1);
                } else if ("key".equals(itemType)) {
                    inv.addKey(itemId, 1);
                }
                inv.sync();

                player.displayClientMessage(
                        Component.literal("§a购买成功: " + shopItem.name + " (-" + shopItem.price + " 货币)"), true);
                Noellesroles.LOGGER.info("[CS2Shop] {} bought {} {} for {} coins",
                        player.getName().getString(), itemType, itemId, shopItem.price);
            });
        });
    }

    // ── 商店出售 ──

    private static void registerShopSell() {
        ServerPlayNetworking.registerGlobalReceiver(ShopSellC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                String itemType = payload.itemType();
                String itemId = payload.itemId();

                CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
                int sellPrice = 0;

                if ("box".equals(itemType)) {
                    if (inv.getBoxCount(itemId) <= 0) {
                        player.displayClientMessage(Component.literal("§c你没有该箱子"), true);
                        return;
                    }
                    inv.removeBox(itemId, 1);
                    sellPrice = ShopConfig.getInstance().getSellPriceConfig()
                            .boxPrices.getOrDefault(itemId, 10);
                } else if ("skin".equals(itemType)) {
                    // 皮肤出售：itemId 格式为 "type/skinName"
                    if (inv.getSkinCount(itemId) <= 0) {
                        player.displayClientMessage(Component.literal("§c你没有该皮肤"), true);
                        return;
                    }
                    inv.removeSkin(itemId, 1);
                    // 根据品质定价
                    int quality = CS2BoxManager.getInstance().findSkinQuality(itemId);
                    sellPrice = ShopConfig.getInstance().getSellPriceConfig()
                            .getSkinPriceByQuality(quality);
                } else {
                    player.displayClientMessage(Component.literal("§c暂不支持出售该类型物品"), true);
                    return;
                }

                PlayerEconomyManager.addCoinNum(player, sellPrice);
                inv.sync();

                player.displayClientMessage(
                        Component.literal("§a出售成功 (+" + sellPrice + " 货币)"), true);
                Noellesroles.LOGGER.info("[CS2Shop] {} sold {} {} for {} coins",
                        player.getName().getString(), itemType, itemId, sellPrice);
            });
        });
    }

    // ── 黑市上架 ──

    private static void registerBlackMarketList() {
        ServerPlayNetworking.registerGlobalReceiver(BlackMarketListC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                boolean success = CS2BlackMarketManager.getInstance().list(
                        player, payload.itemType(), payload.itemId(), payload.price());
                if (success) {
                    player.displayClientMessage(
                            Component.literal("§a上架成功，价格: " + payload.price() + " 货币"), true);
                } else {
                    player.displayClientMessage(
                            Component.literal("§c上架失败（物品不足或参数无效）"), true);
                }
            });
        });
    }

    // ── 黑市购买 ──

    private static void registerBlackMarketBuy() {
        ServerPlayNetworking.registerGlobalReceiver(BlackMarketBuyC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                boolean success = CS2BlackMarketManager.getInstance().buy(player, payload.listingId());
                if (success) {
                    player.displayClientMessage(
                            Component.literal("§a购买成功"), true);
                } else {
                    player.displayClientMessage(
                            Component.literal("§c购买失败（物品已售出或货币不足）"), true);
                }
            });
        });
    }

    // ── 黑市取消 ──

    private static void registerBlackMarketCancel() {
        ServerPlayNetworking.registerGlobalReceiver(BlackMarketCancelC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                boolean success = CS2BlackMarketManager.getInstance().cancelListing(player, payload.listingId());
                if (success) {
                    player.displayClientMessage(
                            Component.literal("§a已取消上架"), true);
                } else {
                    player.displayClientMessage(
                            Component.literal("§c取消失败"), true);
                }
            });
        });
    }

    // ── 黑市同步请求 ──

    private static void registerBlackMarketSyncRequest() {
        ServerPlayNetworking.registerGlobalReceiver(BlackMarketSyncRequestC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                CS2BlackMarketManager.getInstance().syncToPlayer(player);
            });
        });
    }

    // ── 黑市领取货币 ──

    private static void registerBlackMarketClaim() {
        ServerPlayNetworking.registerGlobalReceiver(BlackMarketClaimC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                CS2BlackMarketManager.getInstance().claimPendingCoins(player);
            });
        });
    }

    // ── 装备皮肤 ──

    private static void registerEquipSkin() {
        ServerPlayNetworking.registerGlobalReceiver(EquipSkinC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                String itemType = payload.itemType();
                String skinName = payload.skinName();

                CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
                String skinId = itemType + "/" + skinName;

                // 检查仓库中是否拥有该皮肤
                if (!inv.hasSkin(skinId)) {
                    player.displayClientMessage(
                            Component.literal("§c你没有该皮肤"), true);
                    return;
                }

                // 使用 ItemSkinManager 统一接口（同时更新 PlayerEconomyManager + CCA 组件）
                String currentEquipped = SREPlayerSkinsComponent.KEY.get(player).getEquippedSkin(itemType);

                if (skinName.equals(currentEquipped)) {
                    // 卸下皮肤
                    ItemSkinManager.setEquippedSkinForItemType(player, itemType, "default");
                    player.displayClientMessage(
                            Component.literal("§c已卸下皮肤: §e" + skinName.replace('_', ' ')), true);
                } else {
                    // 装备皮肤
                    ItemSkinManager.setEquippedSkinForItemType(player, itemType, skinName);
                    player.displayClientMessage(
                            Component.literal("§a已装备皮肤: §e" + skinName.replace('_', ' ')), true);
                }
                ItemSkinManager.sync(player);

                Noellesroles.LOGGER.info("[CS2Warehouse] {} equipped skin: {}/{}",
                        player.getName().getString(), itemType, skinName);
            });
        });
    }

    // ── 装备音乐盒 ──

    private static void registerEquipMusicBox() {
        ServerPlayNetworking.registerGlobalReceiver(EquipMusicBoxC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                String musicBoxId = payload.musicBoxId();

                CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);

                MusicBoxPlayerComponent musicComp = MusicBoxPlayerComponent.KEY.get(player);

                if (musicBoxId.isEmpty()) {
                    // 卸下音乐盒
                    musicComp.setEquippedBox(null);
                    player.displayClientMessage(
                            Component.literal("§c已卸下音乐盒"), true);
                } else {
                    // 检查仓库中是否拥有该音乐盒
                    if (!inv.hasMusicBox(musicBoxId)) {
                        player.displayClientMessage(
                                Component.literal("§c你没有该音乐盒"), true);
                        return;
                    }

                    if (musicBoxId.equals(musicComp.getEquippedBox())) {
                        // 卸下
                        musicComp.setEquippedBox(null);
                        player.displayClientMessage(
                                Component.literal("§c已卸下音乐盒: §e" + musicBoxId.replace('_', ' ')), true);
                    } else {
                        // 装备
                        musicComp.setEquippedBox(musicBoxId);
                        player.displayClientMessage(
                                Component.literal("§a已装备音乐盒: §e" + musicBoxId.replace('_', ' ')), true);
                    }
                }

                Noellesroles.LOGGER.info("[CS2Warehouse] {} equipped musicbox: {}",
                        player.getName().getString(), musicBoxId);
            });
        });
    }
}
