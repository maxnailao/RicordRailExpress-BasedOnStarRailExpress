package org.agmas.noellesroles.content.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dream 铁斧「有限耐久」行为的工具类（与 {@link ToxinDurability} 同构）。
 *
 * <p>铁斧在注册时就带 12 点耐久，每次命中消耗 1 点。耐久耗尽后铁斧不会消失，
 * 但无法继续攻击；回商店再买一次会原地补满耐久（第二次购买半价，见
 * {@link DreamAxeShopEntry}）。
 */
public final class DreamAxeDurability {

    private DreamAxeDurability() {
    }

    /** 该物品栈是否为 Dream 的铁斧。 */
    public static boolean isDreamAxe(@NotNull ItemStack stack) {
        return stack.is(ModItems.DREAM_AXE);
    }

    /** 铁斧耐久是否已耗尽。 */
    public static boolean isDepleted(@NotNull ItemStack stack) {
        return isDreamAxe(stack) && stack.getMaxDamage() > 0
                && stack.getDamageValue() >= stack.getMaxDamage();
    }

    /** 把铁斧重置为满耐久。 */
    public static void applyFreshDurability(@NotNull ItemStack stack) {
        stack.setDamageValue(0);
    }

    /** 在玩家背包中查找一把「耐久耗尽」的铁斧。 */
    @Nullable
    public static ItemStack findDepletedAxe(@NotNull Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isDepleted(stack)) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 把背包内所有「耐久耗尽」的铁斧整理为最多一把满耐久：原地刷新第一把，清除其余多余的耗尽铁斧，
     * 避免玩家重复购买后背包残留没耐久的铁斧。
     *
     * @return 是否找到并刷新了至少一把耗尽的铁斧
     */
    public static boolean refreshDepletedAxes(@NotNull Player player) {
        var inventory = player.getInventory();
        ItemStack refreshed = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !isDepleted(stack)) {
                continue;
            }
            if (refreshed == null) {
                applyFreshDurability(stack);
                refreshed = stack;
            } else {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        return refreshed != null;
    }
}
