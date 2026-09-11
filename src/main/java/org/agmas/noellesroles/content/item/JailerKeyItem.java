package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.IronDoorKeyItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 狱警钥匙
 * <p>
 * - 材质模型同铁门钥匙；继承 {@link IronDoorKeyItem}，因此可打开房间门
 * （核心 {@code TrainDoorBlock} 以 {@code instanceof IronDoorKeyItem} 判定）与关押门（见 DetentionDoorBlock）。
 * - 无限耐久：注册时不设置耐久值，使其不可损坏，门逻辑中的 {@code hurtAndBreak} 对其无效。
 * </p>
 */
public class JailerKeyItem extends IronDoorKeyItem {
    public JailerKeyItem(Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        // 不调用 super，避免显示铁门钥匙的"剩余次数"；改为提示无限耐久。
        tooltipComponents.add(Component.translatable("item.noellesroles.jailer_key.infinite")
                .withStyle(ChatFormatting.AQUA));
    }
}
