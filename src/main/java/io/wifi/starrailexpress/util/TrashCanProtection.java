package io.wifi.starrailexpress.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.HoneyBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;

/**
 * 「垃圾桶不许销毁哪些物品」的唯一判定。
 *
 * <p>有两处垃圾桶，规则必须一致，所以抽到这里：
 * <ul>
 *   <li>物品栏界面右侧的垃圾桶（{@code LimitedInventoryScreen} → {@code DeleteCarriedItemC2SPayload}）；</li>
 *   <li>地图里的垃圾桶方块（{@code TrashCanBlock}，本来只有作者配的白/黑名单，
 *       现在额外受这里的硬保护约束）。</li>
 * </ul>
 *
 * <h2>为什么这些不能删</h2>
 * <ul>
 *   <li><b>武器</b>：误删直接毁掉一局（警察丢了巡警手枪、牛仔丢了德林加没法复仇、杀手丢了刀只能挨打）。</li>
 *   <li><b>炸弹客的炸弹</b>：它是传递型道具，丢了就凭空消失、炸弹客自己也拿不回来，
 *       等于把整个职业的核心机制废掉。</li>
 *   <li><b>食物与饮料</b>：局内是补给资源（回血/解饿/解渴/上 buff/厨师与调酒师玩法），
 *       误删会让一整局少一份补给，而且多数还能被队友捡走利用，没有销毁的理由。</li>
 * </ul>
 */
public final class TrashCanProtection {

    private TrashCanProtection() {
    }

    /** 该物品是否受保护（垃圾桶不许销毁）。 */
    public static boolean isProtected(ItemStack stack) {
        return protectedReason(stack) != null;
    }

    /**
     * 受保护时返回给玩家看的提示文案；可销毁时返回 {@code null}。
     *
     * <p>客户端与服务端都调这一个方法，保证两边口径一致，
     * 不会出现"客户端放行、服务端拒绝"或反过来的情况。
     */
    public static Component protectedReason(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (isWeapon(stack)) {
            return Component.literal("§c这件武器不能扔进垃圾桶");
        }
        if (isBomberBomb(stack)) {
            return Component.literal("§c炸弹客的炸弹不能扔进垃圾桶");
        }
        if (isFoodOrDrink(stack)) {
            return Component.literal("§c食物和饮料不能扔进垃圾桶");
        }
        return null;
    }

    /** 炸弹客的炸弹（{@code noellesroles:bomb}）。 */
    private static boolean isBomberBomb(ItemStack stack) {
        return stack.is(org.agmas.noellesroles.init.ModItems.BOMB);
    }

    /**
     * 食物与饮料。
     *
     * <p>用四种互补口径，避免漏掉自定义物品：
     * <ol>
     *   <li>{@code DataComponents.FOOD}：所有原版食物 + 本模组的鸡尾酒 / 厨师菜品 / 净水 / 零食；</li>
     *   <li>{@link PotionItem}：原版药水；</li>
     *   <li>{@link HoneyBottleItem}：蜂蜜瓶这类"饮用"型物品；</li>
     *   <li>使用动画是 {@code EAT} / {@code DRINK}：覆盖既没有 FOOD 组件、也不继承上述类的自定义饮料
     *       （例如 {@code SpellbreakerPotionItem}、{@code CocktailItem}）。</li>
     * </ol>
     *
     * <p>另外两个自定义饮料**四个口径都盖不到**，必须点名：
     * <ul>
     *   <li>{@code WizardPotionItem}：没有 FOOD 组件，也没设置使用动画；</li>
     *   <li>{@code AlchemistBuffPotionItem}：使用动画是 {@code SPEAR}（不是 DRINK）。</li>
     * </ul>
     */
    private static boolean isFoodOrDrink(ItemStack stack) {
        if (stack.has(DataComponents.FOOD)) {
            return true;
        }
        var item = stack.getItem();
        if (item instanceof PotionItem || item instanceof HoneyBottleItem) {
            return true;
        }
        UseAnim animation = item.getUseAnimation(stack);
        if (animation == UseAnim.EAT || animation == UseAnim.DRINK) {
            return true;
        }
        return item instanceof org.agmas.noellesroles.content.item.WizardPotionItem
                || item instanceof org.agmas.noellesroles.content.item.AlchemistBuffPotionItem;
    }

    /**
     * 战斗武器。
     *
     * <p>容易漏的几处：双枪是左右两件独立物品；手雷有三种；短管霰弹枪在 {@code ModItems}，
     * 其余多数在 {@code TMMItems}。
     */
    private static boolean isWeapon(ItemStack stack) {
        // 手枪类
        return stack.is(io.wifi.starrailexpress.index.TMMItems.REVOLVER)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.DERRINGER)
                || stack.is(org.agmas.noellesroles.init.ModItems.PATROLLER_REVOLVER)
                || stack.is(org.agmas.noellesroles.init.ModItems.BANDIT_REVOLVER)
                || stack.is(org.agmas.noellesroles.init.ModItems.SHERIFF_REVOLVER)
                || stack.is(org.agmas.noellesroles.init.ModItems.FAKE_REVOLVER)
                || stack.is(org.agmas.noellesroles.init.ModItems.DUAL_PISTOL_LEFT)
                || stack.is(org.agmas.noellesroles.init.ModItems.DUAL_PISTOL_RIGHT)
                // 近战与投掷物
                || stack.is(io.wifi.starrailexpress.index.TMMItems.KNIFE)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.BAT)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.GRENADE)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.STICKY_GRENADE)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.TIMED_GRENADE)
                // 长枪
                || stack.is(org.agmas.noellesroles.init.ModItems.SHORT_SHOTGUN)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.SNIPER_RIFLE);
    }
}
