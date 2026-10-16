package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Dream 铁斧的商店条目：具备「耐久」与「第二次购买半价」两个动态行为。
 *
 * <ul>
 * <li>购买时若背包内已有「耐久耗尽」的铁斧，则原地补满耐久（而不是再发一把）；</li>
 * <li>否则发放一把满耐久的铁斧；</li>
 * <li>首次购买后，后续购买价格 -50%（写入玩家的 {@link DynamicShopComponent}）。</li>
 * </ul>
 *
 * <p>上游直接在 {@code KillerKnifeShopEntry} 上加了 {@code (ItemStack, price, discount)}
 * 构造；本仓库的 {@code KillerKnifeShopEntry} 只认杀手刀，因此这里按 {@code ToxinShopEntry}
 * 的写法单独实现。
 */
public class DreamAxeShopEntry extends ShopEntry {
    /** 首购之后的折扣百分比。 */
    private static final int REPEAT_DISCOUNT_PERCENT = 50;

    public DreamAxeShopEntry(ItemStack stack, int price) {
        super(stack, price, ShopEntry.Type.WEAPON);
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        boolean success;
        if (DreamAxeDurability.refreshDepletedAxes(player)) {
            // 已有耗尽的铁斧 -> 原地补满，并清除其余多余的耗尽铁斧
            success = true;
        } else {
            // 没有耗尽的铁斧 -> 发放一把新铁斧
            success = RoleUtils.insertStackInFreeSlot(player, this.stack().copy());
        }

        if (success) {
            applyFirstPurchaseDiscount(player);
        }
        return success;
    }

    /** 首次购买后为后续购买挂上 -50% 折扣。 */
    private void applyFirstPurchaseDiscount(@NotNull Player player) {
        DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
        ResourceLocation axeId = BuiltInRegistries.ITEM.getKey(this.stack().getItem());
        if (dynamicShop.getPurchaseCount(axeId) == 0) {
            dynamicShop.setPercentDiscount(axeId, REPEAT_DISCOUNT_PERCENT);
        }
        dynamicShop.recordPurchase(axeId);
    }
}
