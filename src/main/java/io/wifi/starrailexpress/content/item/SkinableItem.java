package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 可切换皮肤的物品
 * 实现此接口的物品可以在皮肤管理界面中进行皮肤更换
 */
public abstract class SkinableItem extends Item {
    public SkinableItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        String itemName = this.getItemSkinType();
        if (itemName == null)
            return;
        Player player = null;
        if (tooltipContext instanceof net.minecraft.world.entity.player.Player p) {
            player = p;
        } else {
            player = null;
        }
        if (player == null) {
            super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
            return;
        }

        String skinName = "default";
        skinName = ItemSkinManager.getEquippedSkin(player, itemStack);
        if (skinName == null) {
            skinName = "default";
        }
        ItemSkinManager.Skin skin = ItemSkinManager.Skin.fromString(itemName, skinName);

        if (skin != null) {
            list.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatableWithFallback(
                            "screen.sre.skins." + itemName + "." + (skin.tooltipName) + ".name", skin.tooltipName)
                            .withStyle(style -> style.withColor(skin.getColor()))));
        } else if (skinName.equals("default") || skinName == null) {
            list.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatableWithFallback("screen.sre.skins.default", "Default"))
                    .withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
    }

    public abstract String getItemSkinType();

    /**
     * 获取物品的默认皮肤名称
     * 
     * @return 默认皮肤名称
     */
    public String getDefaultSkin() {
        return "default";
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (!(entity instanceof Player player) || level.isClientSide()) {
            return;
        }
        // 装备皮肤仅 personalize "正拿在手上"的 stack（主手/副手）：
        // 若对所有无组件 stack 写组件，背包里每把普通刀都会被打上爪子等 3D 皮肤，
        // 在物品栏渲染成被压扁的 3D 模型（"所有刀被 3D 化"的根因）。
        boolean held = itemStack == player.getMainHandItem() || itemStack == player.getOffhandItem();
        if (itemStack.get(SREDataComponentTypes.SKIN) == null) {
            if (held) {
                String skin = ItemSkinManager.getEquippedSkin(player, itemStack);
                if (skin != null) {
                    itemStack.set(SREDataComponentTypes.SKIN, skin);
                }
            }
        } else if (!held && "default".equals(ItemSkinManager.getEquippedSkin(player, itemStack))) {
            // 历史污染清洗：旧版 inventoryTick 给所有 stack 打过组件。
            // 仅当玩家当前未装备该类型皮肤时清除——与"普通刀应保持 2D 默认"的诉求一致；
            // 若仍装备着皮肤，手持 stack 会重新打上组件，表现不受影响。
            itemStack.remove(SREDataComponentTypes.SKIN);
        }
    }

    /**
     * 获取物品支持的皮肤列表
     * 
     * @return 皮肤名称数组
     */
    public String[] getAvailableSkins() {
        return new String[] { "default" };
    }
}
