package org.agmas.noellesroles.game.roles.vigilante.jailer;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 狱警防爆盾技能 —— 完全参照保安 {@code GuardPlayerHandler} 的防爆盾牌交互：
 * <ul>
 *   <li>副手已是防爆盾牌 → 收回背包（背包满则丢出世界），清空副手；</li>
 *   <li>主手是防爆盾牌 → 换到副手；</li>
 *   <li>否则从背包找一个防爆盾牌装到副手。</li>
 * </ul>
 *
 * <p>狱警可花费 150 金币从狱警商店购买防爆盾牌（见 {@code RoleShopHandler}），
 * 按统一技能键即可快速装 / 卸副手进行格挡。</p>
 */
public class JailerPlayerHandler {

    public static void register() {
        RoleSkill.register(ModRoles.JAILER, JailerPlayerHandler::useSkill);
    }

    public static void useSkill(RoleSkillContext context) {
        ServerPlayer player = context.player();
        if (player.level().isClientSide)
            return;

        // 副手已是防爆盾牌 → 卸下收回背包（背包满则丢出世界）
        ItemStack off = player.getOffhandItem();
        if (off != null && off.is(ModItems.RIOT_SHIELD)) {
            ItemStack copy = off.copy();
            boolean inserted = RoleUtils.insertStackInFreeSlot(player, copy);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            if (!inserted) {
                ItemEntity ent = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), copy);
                player.level().addFreshEntity(ent);
            }
            return;
        }

        // 主手是防爆盾牌 → 换到副手
        ItemStack main = player.getMainHandItem();
        if (main != null && main.is(ModItems.RIOT_SHIELD)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, main);
            return;
        }

        // 从背包中寻找一个防爆盾并放到副手
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s != null && s.is(ModItems.RIOT_SHIELD)) {
                ItemStack one = s.copy();
                one.setCount(1);
                s.shrink(1);
                if (s.isEmpty())
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, one);
                return;
            }
        }
    }
}
