package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 重刑犯手铐
 * <p>
 * - 纹理、佩戴与缓慢逻辑同普通手铐（复用 {@link HandCuffsItem#SLOT_HANDCUFFS} 与其渲染/姿势 mixin）。
 * - 无限耐久、无法挣脱：覆盖 {@link #inventoryTick} 移除"蹲下挣脱扣耐久"逻辑，永不损耗。
 * - 开局自动铐在重刑犯身上；仅能由杀手阵营/杀手方中立/警长阵营成员解开：
 * 对其蹲下空手右键（即时）或蹲下注视约 1 秒（引导式），解开后手铐消失（不进入解开者背包）。
 * </p>
 */
public class ConvictHandcuffsItem extends HandCuffsItem {
    public ConvictHandcuffsItem(Item.Properties settings) {
        super(settings);
    }

    /** 该玩家佩戴的是否为"重刑犯手铐"（区别于普通手铐）。 */
    public static boolean hasConvictHandCuff(Player player) {
        return ExtraSlotComponent.getSlot(player, SLOT_HANDCUFFS).getItem() instanceof ConvictHandcuffsItem;
    }

    /**
     * 解除重刑犯手铐：从额外槽移除并直接丢弃，不进入任何人的背包。
     *
     * @return 若原本佩戴着重刑犯手铐并被成功解除则返回 {@code true}，否则 {@code false}
     */
    public static boolean removeConvictHandcuff(Player player) {
        if (!hasConvictHandCuff(player)) {
            return false;
        }
        // putOffHandCuff 仅从额外槽移除并返回该 ItemStack；此处不回收 → 手铐消失
        HandCuffsItem.putOffHandCuff(player);
        return true;
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int slot, boolean bl) {
        // 逻辑同普通手铐：被铐时施加缓慢 IV；但重刑犯手铐无法挣脱，永不扣耐久。
        if (entity instanceof Player player && !player.isSpectator() && hasHandCuff(player)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    20, // 持续时间（tick），逐 tick 刷新
                    3, // 等级（同普通手铐）
                    false, // ambient
                    true, // showParticles
                    true // showIcon
            ));
        }
    }

    // 重刑犯手铐不由玩家主动使用或给他人佩戴：开局自动铐上，解除走专用交互。
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand interactionHand) {
        return InteractionResultHolder.pass(user.getItemInHand(interactionHand));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity,
            InteractionHand hand) {
        return InteractionResult.PASS;
    }
}
