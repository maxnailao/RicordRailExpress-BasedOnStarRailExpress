package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 狱警钥匙
 * <p>
 * - 材质模型同铁门钥匙，但<b>不继承</b> {@code IronDoorKeyItem}：
 *   核心 {@code TrainDoorBlock} 以 {@code instanceof IronDoorKeyItem} 放行铁门，
 *   不继承就能保证狱警钥匙<b>打不开铁门</b>。
 * - 可开的门：关押门（{@code DetentionDoorBlock} 直接判定本类）与带房号的房间门
 *   （{@code JailerKeyDoorHandler} 以 {@code UseBlockCallback} 接管）。
 * - 无限耐久：注册时不设置耐久值，使其不可损坏。
 * </p>
 */
public class JailerKeyItem extends Item {
    public JailerKeyItem(Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        // 不调用 super，改为提示无限耐久。
        tooltipComponents.add(Component.translatable("item.noellesroles.jailer_key.infinite")
                .withStyle(ChatFormatting.AQUA));
    }
}
