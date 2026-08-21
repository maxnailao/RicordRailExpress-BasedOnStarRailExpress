package org.agmas.noellesroles.content.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.blindness.CaneContactService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 导盲杖（移植自"失明症"模组 GuidanceCaneItem）
 * <p>
 * 失明症玩家的核心探路工具，两种使用方式：
 * <ul>
 *   <li><b>短按敲击</b>：按下后 5 tick 内松手，探测正前方 4 格命中的方块及其邻接面，
 *       客户端以发光轮廓短暂显示（见 {@link CaneContactService}）。</li>
 *   <li><b>长按横扫</b>：按住超过 5 tick 进入横扫模式，移动速度降低 40%，
 *       在第 6/10/14/18 tick 依次以 -24°/-8°/+8°/+24° 偏角探测，
 *       在水平方向扫出 ±24° 的扇形覆盖区，模拟真实导盲杖左右摆动扫路的手势。</li>
 * </ul>
 * 横扫期间冲刺会立即打断探测。敲击与横扫使用独立冷却。
 */
public class GuidanceCaneItem extends Item {

    /** 长按达到该 tick 数后进入横扫模式 */
    private static final int SWEEP_START_TICK = 5;
    /** 横扫模式自然结束的 tick 数 */
    private static final int SWEEP_END_TICK = 20;
    /** 横扫模式下的四次探测时机 */
    private static final int[] SWEEP_CONTACT_TICKS = { 6, 10, 14, 18 };
    /** 每次横扫探测对应的视角偏角 */
    private static final float[] SWEEP_YAW_OFFSETS = { -24F, -8F, 8F, 24F };
    /** 敲击探测冷却（tick） */
    public static final int TAP_COOLDOWN_TICKS = 20;
    /** 横扫探测冷却（tick） */
    public static final int SWEEP_COOLDOWN_TICKS = 40;

    private static final ResourceLocation SLOW_MODIFIER_ID = Noellesroles.id("cane_sweep_slowdown");
    private static final AttributeModifier SLOW_MODIFIER = new AttributeModifier(SLOW_MODIFIER_ID, -0.4,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** 每名玩家当前的横扫进度状态 */
    private static final Map<UUID, SweepState> SWEEP_STATES = new HashMap<>();

    public GuidanceCaneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 重置横扫状态机，开始引导
            SWEEP_STATES.put(serverPlayer.getUUID(), new SweepState());
            removeSlowdown(serverPlayer);
        }
        player.startUsingItem(usedHand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return;
        }
        int elapsed = getUseDuration(stack, user) - remainingUseDuration;
        SweepState state = SWEEP_STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        // 横扫中冲刺会立即打断（"急着跑就不能好好探路"）
        if (player.isSprinting()) {
            removeSlowdown(player);
            player.stopUsingItem();
            return;
        }
        if (elapsed >= SWEEP_START_TICK && !state.sweepStarted) {
            state.sweepStarted = true;
            addSlowdown(player);
        }
        while (state.sweepStarted && state.nextSweepContact < SWEEP_CONTACT_TICKS.length
                && elapsed >= SWEEP_CONTACT_TICKS[state.nextSweepContact]) {
            CaneContactService.performContact(player, SWEEP_YAW_OFFSETS[state.nextSweepContact], true);
            state.nextSweepContact++;
        }
        if (elapsed >= SWEEP_END_TICK && !state.sweepFinished) {
            state.sweepFinished = true;
            removeSlowdown(player);
            player.getCooldowns().addCooldown(this, SWEEP_COOLDOWN_TICKS);
            player.stopUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeCharged) {
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return;
        }
        removeSlowdown(player);
        SweepState state = SWEEP_STATES.remove(player.getUUID());
        if (state == null) {
            return;
        }
        int elapsed = getUseDuration(stack, user) - timeCharged;
        if (!state.sweepStarted && elapsed < SWEEP_START_TICK && !player.isSprinting()) {
            // 短按敲击：正前方单点探测
            CaneContactService.performContact(player, 0F, false);
            player.getCooldowns().addCooldown(this, TAP_COOLDOWN_TICKS);
        } else if (state.sweepStarted && !state.sweepFinished) {
            // 横扫被提前松手中断
            player.getCooldowns().addCooldown(this, SWEEP_COOLDOWN_TICKS);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // 无限引导时长，实际节奏由 useTick 状态机控制
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // 不播放任何原版使用动画
        return UseAnim.NONE;
    }

    /** 玩家掉线/服务器关闭时清理残留状态与减速修改器 */
    public static void clearSweepState(ServerPlayer player) {
        if (SWEEP_STATES.remove(player.getUUID()) != null) {
            removeSlowdown(player);
        }
    }

    /** 横扫时 -40% 移速：使用属性修改器而非药水效果，无粒子无图标 */
    private static void addSlowdown(ServerPlayer player) {
        var instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance != null && !instance.hasModifier(SLOW_MODIFIER_ID)) {
            instance.addTransientModifier(SLOW_MODIFIER);
        }
    }

    public static void removeSlowdown(ServerPlayer player) {
        var instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            instance.removeModifier(SLOW_MODIFIER_ID);
        }
    }

    /** 横扫进度：是否进入横扫、是否自然结束、下一次探测的下标 */
    private static final class SweepState {
        boolean sweepStarted;
        boolean sweepFinished;
        int nextSweepContact;
    }
}
