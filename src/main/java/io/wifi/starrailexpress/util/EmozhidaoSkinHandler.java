package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 恶魔之刃特别皮肤处理器（仅做击杀特效的皮肤判定，无状态）。
 * 判定逻辑与 {@link AnxingSkinHandler#hasAnxingSkinEquipped} 一致：
 * 优先看 ItemStack 的 SKIN 组件，否则按皮肤类型查询玩家装备的皮肤。
 */
public final class EmozhidaoSkinHandler {

    private EmozhidaoSkinHandler() {}

    /** 恶魔之刃皮肤 ID（与装备系统存储格式一致，不含类型前缀） */
    public static final String SKIN_ID = "knife_emozhidao_1";

    public static boolean hasEmozhidaoSkinEquipped(Player player, ItemStack itemStack) {
        if (itemStack.has(SREDataComponentTypes.SKIN)) {
            return SKIN_ID.equals(itemStack.get(SREDataComponentTypes.SKIN));
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