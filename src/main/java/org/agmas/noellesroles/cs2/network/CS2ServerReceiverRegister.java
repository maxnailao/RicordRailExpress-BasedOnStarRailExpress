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
        registerBoxPreviewRequest();
        registerDailyShopSyncRequest();
        registerDailyShopBuy();
        registerDestroyItem();
        registerToggleFavorite();
        registerMythicShopBuy();
    }

    // ── 开箱 ──

    private static void registerOpenBox() {
        ServerPlayNetworking.registerGlobalReceiver(OpenBoxC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                final int count = Math.max(1, Math.min(100, payload.count()));
                // 防止同一玩家并发开箱
                if (openingBoxPlayers.contains(player.getUUID())) {
                    sendOpenFailure(player, count);
                    return;
                }
                openingBoxPlayers.add(player.getUUID());
                try {
                    String boxId = payload.boxId();
                    CS2BoxManager boxManager = CS2BoxManager.getInstance();

                    if (count > 1) {
                        // 批量开箱：逐次开（每次 openBox 自行扣料/发放/保底/同步），
                        // 材料（箱子或钥匙）用尽即停，聚合结果走专用批处理包。
                        List<Integer> qualities = new ArrayList<>();
                        List<String> skins = new ArrayList<>();
                        List<Boolean> dups = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            CS2BoxManager.BoxRollResult r = boxManager.openBox(boxId, player);
                            if (r == null) {
                                break; // 已无箱子或钥匙
                            }
                            qualities.add(r.quality);
                            skins.add(r.skinId);
                            dups.add(r.isDuplicate);
                        }
                        boolean success = !qualities.isEmpty();
                        ServerPlayNetworking.send(player,
                                new OpenBoxBatchResultS2CPayload(success, qualities, skins, dups));
                        Noellesroles.LOGGER.info("[CS2Box] Batch opened {} boxes for {} ({} results)",
                                count, player.getName().getString(), qualities.size());
                        return;
                    }

                    // 单个开箱：保留原有横向滚动动画流程
                    CS2BoxManager.BoxRollResult result = boxManager.openBox(boxId, player);

                    if (result == null) {
                        sendOpenFailure(player, 1);
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
                } catch (Exception e) {
                    // 异常兜底：通知客户端开箱失败，避免客户端 isBoxOpening 锁死
                    Noellesroles.LOGGER.error("[CS2Box] Unexpected error opening box for {}",
                            player.getName().getString(), e);
                    sendOpenFailure(player, count);
                } finally {
                    openingBoxPlayers.remove(player.getUUID());
                }
            });
        });
    }

    /**
     * 统一发送开箱失败：按本次请求数量选择回包类型，
     * 保证客户端无论处于单个还是批量等待状态都能解除 isBoxOpening 锁。
     */
    private static void sendOpenFailure(ServerPlayer player, int count) {
        if (count > 1) {
            ServerPlayNetworking.send(player,
                    new OpenBoxBatchResultS2CPayload(false, List.of(), List.of(), List.of()));
        } else {
            ServerPlayNetworking.send(player, new OpenBoxResultS2CPayload(
                    false, 0, "", false, 0, List.of(), List.of()));
        }
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
                } else if ("musicbox".equals(itemType)) {
                    inv.addMusicBox(itemId, 1);
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
                    // 出售后若该皮肤已彻底不再拥有且正装备中，自动卸下，避免出现"装备着一个仓库里已经不存在的皮肤"
                    unequipIfOrphan(player, itemId, inv);
                    // 根据品质定价
                    int quality = CS2BoxManager.getInstance().findSkinQuality(itemId);
                    // 神话(不可思议)品质：以神话碎片结算，不发现金货币
                    if (quality == 5) {
                        int shards = CS2InventoryComponent.MYTHIC_SHARD_SELL_AMOUNT;
                        inv.addMythicShards(shards);
                        inv.sync();
                        player.displayClientMessage(
                                Component.literal("§d神话品质出售成功：+" + shards + " 神话碎片"), true);
                        Noellesroles.LOGGER.info("[CS2Shop] {} sold mythic skin {} for {} shards",
                                player.getName().getString(), itemId, shards);
                        return;
                    }
                    sellPrice = ShopConfig.getInstance().getSellPriceConfig()
                            .getSkinPriceByQuality(quality);
                } else if ("musicbox".equals(itemType)) {
                    if (!inv.hasMusicBox(itemId)) {
                        player.displayClientMessage(Component.literal("§c你没有该音乐盒"), true);
                        return;
                    }
                    inv.removeMusicBox(itemId, 1);
                    // 如果出售的音乐盒正在装备中，自动卸下
                    io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent musicComp =
                            io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent.KEY.get(player);
                    if (itemId.equals(musicComp.getEquippedBox())) {
                        musicComp.setEquippedBox(null);
                    }
                    sellPrice = ShopConfig.getInstance().getSellPriceConfig().musicBoxSellPrice;
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
                try {
                    boolean success = CS2BlackMarketManager.getInstance().list(
                            player, payload.itemType(), payload.itemId(), payload.price(), payload.currency());
                    if (success) {
                        String unit = "shard".equals(payload.currency()) ? "神话碎片" : "货币";
                        player.displayClientMessage(
                                Component.literal("§a上架成功，价格: " + payload.price() + " " + unit), true);
                    } else {
                        player.displayClientMessage(
                                Component.literal("§c上架失败（物品不足或参数无效）"), true);
                    }
                } catch (Exception e) {
                    Noellesroles.LOGGER.error("[CS2Market] Error in list operation", e);
                    player.displayClientMessage(Component.literal("§c黑市上架出错: " + e.getMessage()), true);
                }
            });
        });
    }

    // ── 黑市购买 ──

    private static void registerBlackMarketBuy() {
        ServerPlayNetworking.registerGlobalReceiver(BlackMarketBuyC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                try {
                    boolean success = CS2BlackMarketManager.getInstance().buy(player, payload.listingId());
                    if (success) {
                        player.displayClientMessage(
                                Component.literal("§a购买成功"), true);
                    } else {
                        player.displayClientMessage(
                                Component.literal("§c购买失败（物品已售出或货币不足）"), true);
                    }
                } catch (Exception e) {
                    Noellesroles.LOGGER.error("[CS2Market] Error in buy operation", e);
                    player.displayClientMessage(Component.literal("§c黑市购买出错: " + e.getMessage()), true);
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
                try {
                    CS2BlackMarketManager.getInstance().syncToPlayer(player);
                } catch (Exception e) {
                    Noellesroles.LOGGER.error("[CS2Market] Error in sync request", e);
                }
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
                Noellesroles.LOGGER.info("[CS2Warehouse] {} equip request: {}/{} -> applied",
                        player.getName().getString(), itemType, skinName);
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

    // ── 箱子预览请求 ──

    private static void registerBoxPreviewRequest() {
        ServerPlayNetworking.registerGlobalReceiver(BoxPreviewRequestC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                String boxId = payload.boxId();
                // 读取原始 JSON 文件发送给客户端（使用游戏目录绝对路径）
                java.nio.file.Path configFile = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getGameDir().resolve("CS2_box").resolve(boxId + ".json");
                if (!java.nio.file.Files.exists(configFile)) {
                    Noellesroles.LOGGER.warn("[CS2Box] Box config file not found: {}", configFile);
                    return;
                }
                try {
                    String json = java.nio.file.Files.readString(configFile);
                    ServerPlayNetworking.send(player, new BoxPreviewS2CPayload(json));
                } catch (java.io.IOException e) {
                    Noellesroles.LOGGER.error("[CS2Box] Failed to read box config: {}", configFile, e);
                }
            });
        });
    }

    // ── 每日商店：同步请求 ──

    private static void registerDailyShopSyncRequest() {
        ServerPlayNetworking.registerGlobalReceiver(DailyShopSyncRequestC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                try {
                    org.agmas.noellesroles.cs2.DailyShopManager.getInstance().syncToPlayer(player);
                } catch (Exception e) {
                    Noellesroles.LOGGER.error("[DailyShop] Error in sync request", e);
                }
            });
        });
    }

    // ── 每日商店：购买 ──

    private static void registerDailyShopBuy() {
        ServerPlayNetworking.registerGlobalReceiver(DailyShopBuyC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                try {
                    org.agmas.noellesroles.cs2.DailyShopManager.getInstance().buy(player, payload.slot());
                } catch (Exception e) {
                    Noellesroles.LOGGER.error("[DailyShop] Error in buy operation", e);
                }
            });
        });
    }

    // ── 神话商店：购买（消耗神话碎片） ──

    private static void registerMythicShopBuy() {
        ServerPlayNetworking.registerGlobalReceiver(MythicShopBuyC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                try {
                    String skinId = payload.skinId();
                    String[] parts = skinId.split("/", 2);
                    if (parts.length < 2) return;
                    String itemType = parts[0];
                    String skinName = parts[1];

                    // 校验：必须是神话(不可思议)品质皮肤
                    int color = ItemSkinManager.getColorFromName(itemType, skinName);
                    if (ItemSkinManager.qualityFromColor(color) != 5) {
                        player.displayClientMessage(Component.literal("§c非法的神话商店商品"), true);
                        return;
                    }

                    // 已下架的神话皮肤不允许兑换（与客户端 MYTH_EXCLUDED 保持一致）
                    if ("bow/bow_shenfa".equals(skinId)) {
                        player.displayClientMessage(Component.literal("§c该神话皮肤已下架"), true);
                        return;
                    }

                    CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
                    // 仅当仓库中实际持有该神话皮肤（数量 > 0）才视为已拥有；
                    // 不能用永久解锁集合判断——出售只扣数量、不撤销永久解锁，
                    // 否则卖光后再次兑换会被误判为"已拥有"而永久无法再买。
                    if (inv.getSkinCount(skinId) > 0) {
                        player.displayClientMessage(Component.literal("§c你已拥有该神话皮肤"), true);
                        return;
                    }

                    int price = CS2InventoryComponent.MYTHIC_SKIN_SHARD_PRICE;
                    if (inv.getMythicShards() < price) {
                        player.displayClientMessage(
                                Component.literal("§c神话碎片不足，需要 " + price + " 神话碎片"), true);
                        return;
                    }
                    if (!inv.spendMythicShards(price)) {
                        player.displayClientMessage(Component.literal("§c扣除神话碎片失败"), true);
                        return;
                    }

                    ItemSkinManager.unlockSkinForItemType(player, itemType, skinName);
                    inv.addSkin(skinId, 1);
                    SREPlayerSkinsComponent.KEY.get(player).syncSkinsToClient();
                    inv.sync();

                    player.displayClientMessage(
                            Component.literal("§a神话商店兑换成功: " + skinName.replace('_', ' ')
                                    + " (-" + price + " 神话碎片)"), true);
                    Noellesroles.LOGGER.info("[CS2MythicShop] {} bought {} for {} shards",
                            player.getName().getString(), skinId, price);
                } catch (Exception e) {
                    Noellesroles.LOGGER.error("[CS2MythicShop] Error in buy operation", e);
                }
            });
        });
    }

    // ── 仓库：收藏 / 取消收藏 ──

    private static void registerToggleFavorite() {
        ServerPlayNetworking.registerGlobalReceiver(ToggleFavoriteC2SPayload.ID,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> {
                        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
                        boolean now = inv.toggleFavorite(payload.itemType(), payload.itemId());
                        player.displayClientMessage(Component
                                .literal(now ? "§a已收藏: " + payload.itemId()
                                        + "（收藏中无法销毁）"
                                        : "§e已取消收藏: " + payload.itemId())
                                .withStyle(now ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
                    });
                });
    }

    // ── 仓库：销毁物品（永久删除，用于清理幽灵物品）──

    private static void registerDestroyItem() {
        ServerPlayNetworking.registerGlobalReceiver(DestroyWarehouseItemC2SPayload.ID,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> {
                        String type = payload.itemType();
                        String id = payload.itemId();
                        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
                        // 收藏中的物品不可销毁（服务端强制，客户端也做了提示）
                        if (inv.isFavorite(type, id)) {
                            player.displayClientMessage(Component
                                    .literal("§c该物品已收藏，请先取消收藏再销毁")
                                    .withStyle(ChatFormatting.RED), true);
                            return;
                        }
                        boolean ok;
                        switch (type) {
                            case "box" -> ok = inv.removeBox(id, 1);
                            case "key" -> ok = inv.removeKey(id, 1);
                            case "skin" -> {
                                ok = inv.removeSkin(id, 1);
                                if (ok) {
                                    // 销毁后若该皮肤已彻底不再拥有且正装备中，自动卸下
                                    unequipIfOrphan(player, id, inv);
                                }
                            }
                            case "musicbox" -> {
                                ok = inv.removeMusicBox(id, 1);
                                // 如果销毁的正是当前装备中的音乐盒，顺手卸下，
                                // 否则会出现"装备着一个已经不在仓库里的音乐盒"
                                if (ok) {
                                    io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent musicComp =
                                            io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent.KEY
                                                    .get(player);
                                    if (id.equals(musicComp.getEquippedBox())) {
                                        musicComp.setEquippedBox(null);
                                    }
                                }
                            }
                            default -> ok = false;
                        }
                        if (ok) {
                            inv.sync();
                            player.displayClientMessage(Component
                                    .literal("§a已销毁: " + type + " " + id)
                                    .withStyle(ChatFormatting.GREEN), true);
                            Noellesroles.LOGGER.info("[CS2Warehouse] {} destroyed {} {}",
                                    player.getName().getString(), type, id);
                        } else {
                            player.displayClientMessage(Component
                                    .literal("§c销毁失败：仓库里没有这件物品（可能已被销毁）")
                                    .withStyle(ChatFormatting.RED), true);
                        }
                    });
                });
    }

    /**
     * 皮肤从仓库被移除（出售 / 销毁）后的收尾处理：
     * 若该皮肤已彻底不再拥有（数量为 0）且当前正装备在对应武器类型上，自动卸下还原为原皮
     * （与 {@link CS2BlackMarketManager} 上架时的处理保持一致，避免出现"装备着一个仓库里已经不存在的皮肤"的幽灵装备）。
     * 还剩副本（数量 &gt; 0）时不动装备状态。
     */
    private static void unequipIfOrphan(ServerPlayer player, String skinId, CS2InventoryComponent inv) {
        if (inv.getSkinCount(skinId) > 0) return; // 仍有副本，保留装备
        String[] parts = skinId.split("/", 2);
        if (parts.length < 2) return;
        String itemType = parts[0];
        String skinName = parts[1];
        SREPlayerSkinsComponent skins = SREPlayerSkinsComponent.KEY.get(player);
        if (skinName.equals(skins.getEquippedSkin(itemType))) {
            ItemSkinManager.setEquippedSkinForItemType(player, itemType, "default");
            ItemSkinManager.sync(player);
        }
    }
}
