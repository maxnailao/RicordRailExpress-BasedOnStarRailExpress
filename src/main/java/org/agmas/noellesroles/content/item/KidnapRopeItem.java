// 文件路径：E:\AAA Happytrain\code\RicordRailExpress-BasedOnStarRailExpress-master\RicordRailExpress-BasedOnStarRailExpress-master\src\main\java\org\agmas\noellesroles\content\item\KidnapRopeItem.java
package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 捆绳 - 绑匪专属道具
 * - 绑匪必须持有捆绳才能使用捆绑技能绑架他人
 * - 2 点耐久，每绑架一次消耗 1 点耐久（一根捆绳最多捆绑 2 人）
 * - 不可被小偷偷走（不在小偷白名单内），不可从尸体上搜出（殡仪员/食尸鬼已封禁）
 * - 拿在手里时仅持有者自己可见，其他玩家看不到（见 client/InvisbleHandItem）
 */
public class KidnapRopeItem extends Item {
    public KidnapRopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.kidnap_rope.tooltip").withStyle(ChatFormatting.GRAY));
    }
}