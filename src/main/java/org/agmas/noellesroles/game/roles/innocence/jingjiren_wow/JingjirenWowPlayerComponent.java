package org.agmas.noellesroles.game.roles.innocence.jingjiren_wow;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.innocence.singer.SingerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.super_star.SuperStarPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 经纪人组件
 *
 * 被动：可以透视到职业为歌手和明星的玩家
 * 主动技能：对准明星/歌手进行签约操作（花费 managerSignCost 金币，默认 200）
 * - 签约后双方可以互相透视位置
 * - 明星被签约后，其释放技能可使被签约的歌手和经纪人同时获得金钱
 * - 歌手被签约后，商店所有购买半价
 *
 * 签约状态每局开始时重置，不会跨局残留。
 *
 * 经纪人为平民阵营
 */
public class JingjirenWowPlayerComponent implements RoleComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<JingjirenWowPlayerComponent> KEY = ModComponents.JINGJIREN_WOW;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 已签约玩家的 UUID 集合（明星或歌手） */
    public final Set<UUID> signedPlayers = new LinkedHashSet<>();

    /**
     * 构造函数
     */
    public JingjirenWowPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    /**
     * 重置组件状态
     * 每局游戏开始时调用，清空上一局的签约状态，避免残留
     */
    @Override
    public void init() {
        this.signedPlayers.clear();
        this.sync();
    }

    @Override
    public void clear() {
        init();
    }

    /**
     * 判断目标是否已被自己签约
     */
    public boolean isSigned(UUID uuid) {
        return signedPlayers.contains(uuid);
    }

    // ==================== 签约技能 ====================

    /**
     * 签约技能：对明星/歌手进行签约
     *
     * @param manager 经纪人玩家（服务端）
     * @param target  目标玩家
     * @return 是否签约成功（成功才会消耗技能冷却）
     */
    public boolean trySign(ServerPlayer manager, Player target) {
        if (manager.level().isClientSide())
            return false;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(manager.level());
        if (!gameWorld.isRole(manager, ModRoles.JINGJIREN_WOW))
            return false;

        // 双方必须存活
        if (!GameUtils.isPlayerAliveAndSurvival(manager) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            manager.displayClientMessage(
                    Component.translatable("message.noellesroles.jingjiren_wow.invalid_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 目标必须是明星或歌手
        boolean isSinger = gameWorld.isRole(target, ModRoles.SINGER);
        boolean isStar = gameWorld.isRole(target, ModRoles.SUPERSTAR);
        if (!isSinger && !isStar) {
            manager.displayClientMessage(
                    Component.translatable("message.noellesroles.jingjiren_wow.invalid_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 不能重复签约
        if (isSigned(target.getUUID())) {
            manager.displayClientMessage(
                    Component.translatable("message.noellesroles.jingjiren_wow.already_signed")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 扣除签约费用
        int cost = NoellesRolesConfig.HANDLER.instance().managerSignCost;
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(manager);
        if (shop.balance < cost) {
            manager.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds_money", cost)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-cost);

        // 记录签约关系
        signedPlayers.add(target.getUUID());

        // 在目标侧记录经纪人，用于互相透视与技能联动
        if (isSinger) {
            SingerPlayerComponent singerComp = ModComponents.SINGER.get(target);
            singerComp.signedManagerUuid = manager.getUUID();
            singerComp.sync();
            // 歌手被签约后，商店所有购买半价
            applySingerShopDiscount(target);
        } else {
            SuperStarPlayerComponent starComp = ModComponents.STAR.get(target);
            starComp.signedManagerUuid = manager.getUUID();
            starComp.sync();
        }

        // 双方提示
        manager.displayClientMessage(
                Component.translatable("message.noellesroles.jingjiren_wow.sign_success",
                        target.getDisplayName()).withStyle(ChatFormatting.GOLD),
                true);
        target.displayClientMessage(
                Component.translatable("message.noellesroles.jingjiren_wow.signed_by",
                        manager.getDisplayName()).withStyle(ChatFormatting.GOLD),
                true);
        manager.level().playSound(null, manager.blockPosition(),
                SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0F, 1.0F);

        this.sync();
        return true;
    }

    /**
     * 歌手被签约后，商店所有商品半价
     * 通过 DynamicShopComponent 为歌手商店的每件商品挂 50% 折扣，
     * 该组件每局开始时自动清空，不会跨局残留
     */
    private static void applySingerShopDiscount(Player singer) {
        DynamicShopComponent dyn = DynamicShopComponent.KEY.get(singer);
        for (ShopEntry entry : ShopContent.getShopEntries(ModRoles.SINGER_ID)) {
            dyn.setPercentDiscount(BuiltInRegistries.ITEM.getKey(entry.stack().getItem()), 50);
        }
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            KEY.sync(this.player);
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (UUID uuid : signedPlayers) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("signedPlayers", list);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        signedPlayers.clear();
        ListTag list = tag.getList("signedPlayers", 8);
        for (int i = 0; i < list.size(); i++) {
            try {
                signedPlayers.add(UUID.fromString(list.getString(i)));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 仅局内状态，不写入磁盘
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
