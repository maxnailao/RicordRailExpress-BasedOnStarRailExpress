package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.item.ora.OraPunchManager;
import org.agmas.noellesroles.init.FunnyItems;

/**
 * 欧拉一拳：右键进入攻击期间，绑定第一个打到的目标，6 秒内右键 20 次即可击杀。
 * 被绑定目标无法移动，并始终固定在使用者前方。开门等其他特性仍由方块侧检测本物品。
 * <p>
 * <b>不限职业</b>：连打状态由 {@link OraPunchManager} 按玩家 UUID 维护，拿到物品的人都能用。
 * <p>
 * 原本的「波纹勋章」蓄力冲刺（蓄力 3 秒挥拳 + 旋转冲刺）已整体移除，改为欧拉连打；
 * 潜行右键小门卡门（{@link #useOn}）保持不变。
 */
public class BowenBadgeItem extends Item implements AdventureUsable {

    public BowenBadgeItem(Properties properties) {
        super(properties);
    }

    public static boolean isHolding(Player player) {
        return OraPunchManager.isHoldingOraPunch(player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        if (player.getCooldowns().isOnCooldown(this) || player.getCooldowns().isOnCooldown(FunnyItems.BOWEN_BADGE)) {
            return InteractionResultHolder.fail(itemStack);
        }
        if (player.isSpectator()) {
            return InteractionResultHolder.fail(itemStack);
        }
        if (OraPunchManager.isRushExpired(player)) {
            // 已超时：本次右键只用来结算超时（一半冷却），不算一次出拳。
            if (!level.isClientSide) {
                OraPunchManager.onOraUse(player);
            }
            return InteractionResultHolder.fail(itemStack);
        }
        if (!level.isClientSide) {
            OraPunchManager.onOraUse(player);
        }
        return InteractionResultHolder.consume(itemStack);
    }

    public static void fowardAndKnockbackPlayerNearby(Level level, Player player, float force) {
        // ── 击退周围的玩家（排除正前方碰撞目标）──
        float g = player.getYRot();

        // 水平朝向向量
        float k = -Mth.sin(g * ((float) Math.PI / 180F));
        float m = Mth.cos(g * ((float) Math.PI / 180F));
        float horizLen = Mth.sqrt(k * k + m * m);
        float kNorm = k / horizLen;
        float mNorm = m / horizLen;
        Vec3 playerPos = player.position();
        AABB nearbyBox = new AABB(playerPos, playerPos).inflate(3.0, 1.5, 3.0);

        Vec3 dashFront = playerPos.add(kNorm * 2.5, 0, mNorm * 2.5);
        AABB collisionBox = new AABB(dashFront, dashFront).inflate(0.8, 1.0, 0.8);

        for (var entity : level.getEntities(player, nearbyBox)) {
            if (!(entity instanceof LivingEntity target))
                continue;
            if (collisionBox.intersects(target.getBoundingBox()))
                continue;

            Vec3 knockback = target.position()
                    .subtract(playerPos)
                    .multiply(1, 0, 1)
                    .normalize()
                    .scale(0.5);
            target.push(knockback.x, 0, knockback.z);
            target.hurtMarked = true;
            // 被击退实体产生冲击波粒子
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                        target.getX(), target.getY() + 1, target.getZ(),
                        1, 0, 0, 0, 0);
            }
        }
        player.push(kNorm * force, 0.0, mNorm * force);
        player.hurtMarked = true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        if (player.getCooldowns().isOnCooldown(FunnyItems.BOWEN_BADGE))
            return InteractionResult.PASS;
        player.getCooldowns().addCooldown(FunnyItems.BOWEN_BADGE, 20);
        if (state.getBlock() instanceof SmallDoorBlock) {
            BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
                if (player.isShiftKeyDown()) {
                    entity.jam();

                    if (!player.isCreative()) {
                        if (SRE.REPLAY_MANAGER != null) {
                            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                        }

                    }

                    if (!world.isClientSide)
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        }

        return super.useOn(context);
    }
    // public InteractionResult useOn(UseOnContext context) {
    // Player player = context.getPlayer();
    // Level world = context.getLevel();
    // BlockPos pos = context.getClickedPos();
    // BlockState state = world.getBlockState(pos);
    // if (state.getBlock() instanceof SmallDoorBlock) {
    // return InteractionResult.PASS;
    // } else {
    // if (player != null) {
    // context.getItemInHand().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    // }
    // return super.useOn(context);
    // }
    // }
}
