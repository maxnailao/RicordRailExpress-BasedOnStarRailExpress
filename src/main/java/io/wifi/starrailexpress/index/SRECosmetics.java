package io.wifi.starrailexpress.index;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static io.wifi.starrailexpress.cca.SREPlayerSkinsComponent.KEY;

public interface SRECosmetics {
    // 不再重复注册，而是使用PlayerSkinsComponent中已注册的实例

    static String getSkin(ItemStack itemStack) {
        // 获取物品的owner NBT数据，如果没有则使用默认UUID
        String skin = itemStack.getOrDefault(SREDataComponentTypes.SKIN, "default");
        return skin;
    }

    static void setSkin(Player player, ItemStack itemStack, String skinName) {
        // 直接通过 CCA 组件的方法更新，而不是修改 getEquippedSkins() 返回的副本
        final var playerSkinsComponent = KEY.get(player);
        String itemPath = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
        playerSkinsComponent.setEquippedSkinForItemType(itemPath, skinName);
        playerSkinsComponent.sync();
    }
}