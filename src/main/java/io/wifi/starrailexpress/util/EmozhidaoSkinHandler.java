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
        // 1) 物品栈显式携带恶魔之刃（指令/创造/开箱直接指定）→ 直接命中
        if (SKIN_ID.equals(itemStack.get(SREDataComponentTypes.SKIN))) {
            return true;
        }
        // 2) 否则实时查询玩家当前装备的皮肤。
        //    关键修复：物品栈上「过期/默认」的 SKIN 组件不能提前返回 false——
        //    杀手刀跨局留存，其组件在 inventoryTick 首次写入后便被冻结，
        //    一旦那次写入的是 default，旧逻辑会让特效永久消失且无法恢复。
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