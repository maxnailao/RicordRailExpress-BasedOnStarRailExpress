package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.neutral.witch_accomplice.PressureReagentEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 压力试剂 - 魔女共犯专属投掷道具。
 * 右键投掷，落点为中心展开持续 30s、半径 10 格的领域；
 * 领域内拥有真实心情值的玩家持续掉理智（预备魔女更多），
 * 离开 / 结束后被扣理智逐渐恢复其中的 50%。
 */
public class PressureReagentItem extends Item {
    public PressureReagentItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        // 仅魔女共犯、游戏进行中、存活时方可投掷
        if (!game.isRunning() || !game.isRole(user, ModRoles.WITCH_ACCOMPLICE)
                || !GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.fail(stack);
        }

        // 投掷音效
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL, 0.5F,
                1F + (world.random.nextFloat() - .5f) / 10f);

        if (!world.isClientSide) {
            PressureReagentEntity projectile = new PressureReagentEntity(ModEntities.PRESSURE_REAGENT, world);
            projectile.setOwner(user);
            projectile.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());
            projectile.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 0.9F, 1.0F);
            world.addFreshEntity(projectile);
            if (!user.isCreative()) {
                stack.shrink(1);
            }
        }

        user.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }
}
