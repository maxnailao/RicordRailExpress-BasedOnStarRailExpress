package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.jetbrains.annotations.NotNull;

/**
 * 弩皮肤载体（空壳）。
 * 作用与 SkinableBowItem 相同，对应 "crossbow" 皮肤类型。
 */
public class SkinableCrossbowItem extends SkinableItem {

    public SkinableCrossbowItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    @Override
    public String getItemSkinType() {
        return ItemSkinManager.SkinTypes.CROSSBOW;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.CROSSBOW;
    }
}