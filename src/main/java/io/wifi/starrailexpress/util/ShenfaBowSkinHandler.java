package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 神罚弓皮肤处理器（仿照塑水刃实现）。
 * 蓄力粒子箭与白拖尾箭特效仅对装备神罚皮肤的人生效。
 */
public final class ShenfaBowSkinHandler {

    private ShenfaBowSkinHandler() {}

    /** 皮肤ID常量（不含类型前缀，与装备系统存储格式一致） */
    public static final String SKIN_ID = "bow_shenfa";

    /** 检查玩家是否装备了神罚弓皮肤：组件命中优先，回落实时装备查询 */
    public static boolean hasShenfaSkinEquipped(Player player, ItemStack itemStack) {
        if (SKIN_ID.equals(itemStack.get(SREDataComponentTypes.SKIN))) {
            return true;
        }
        String skinType;
        if (itemStack.getItem() instanceof SkinableItem skinable && skinable.getItemSkinType() != null) {
            skinType = skinable.getItemSkinType();
        } else {
            skinType = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
        }
        String equippedSkin = PlayerEconomyManager.getEquippedSkinForItemType(player, skinType);
        return SKIN_ID.equals(equippedSkin);
    }
}