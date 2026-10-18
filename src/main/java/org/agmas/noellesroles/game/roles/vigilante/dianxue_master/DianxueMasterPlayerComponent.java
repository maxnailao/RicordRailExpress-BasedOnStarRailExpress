package org.agmas.noellesroles.game.roles.vigilante.dianxue_master;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.DianxueMasterSyncS2CPacket;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Random;
import java.util.UUID;

public class DianxueMasterPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<DianxueMasterPlayerComponent> KEY = ModComponents.DIANXUE_MASTER;

    public static final int POINT_COUNT = 5;
    // 每次点穴后等待“下一次点穴”的窗口：3 秒（60 tick）。超时则结束点穴并立即结算已点中穴位的效果。
    private static final int STEP_TICKS = 60;
    private static final double PUSH_STRENGTH = 0.5D;
    private static final double PUSH_UPWARD = 0.15D;
    private static final double MAX_RANGE_SQR = 64.0D * 64.0D;
    private static final int RESTRICT_TICKS = 40;
    private static final float DOT_RADIUS = 0.35F;
    // 防抖：一次点击（客户端可能因按下/抬起或冲刺多 tick 双触发）只推进一个穴位，
    // 两次被接受推进之间的最小间隔（tick），避免出现"点一下同时点亮两个穴位"。
    private static final long MIN_CLICK_INTERVAL_TICKS = 5L;

    private final Player player;
    private final Random random = new Random();

    private boolean active = false;
    private UUID targetId = null;
    private int activeIndex = 0;
    private final float[] dotHeightFracs = new float[POINT_COUNT];
    private final float[] dotAzimuths = new float[POINT_COUNT];
    private int stepRemainingTicks = 0;
    private int stepMaxTicks = STEP_TICKS;
    /** 上一次被接受的推进所在游戏 tick，用于点击防抖。 */
    private long lastClickAcceptedGameTime = Long.MIN_VALUE / 2;

    private static final int ACT_OPEN = 0;
    private static final int ACT_UPDATE = 1;
    private static final int ACT_CLOSE = 2;

    public DianxueMasterPlayerComponent(Player player) {
        this.player = player;
    }

    @Override public Player getPlayer() { return player; }
    @Override public boolean shouldSyncWith(ServerPlayer p) { return p == player; }
    public void sync() { KEY.sync(player); }

    @Override public void init() { resetState(); }
    @Override public void clear() { resetState(); }
    private void resetState() {
        active = false;
        targetId = null;
        activeIndex = 0;
        stepRemainingTicks = 0;
    }

    /** 是否处于点穴状态（供技能再次发动时判断是否取消）。 */
    public boolean isActive() {
        return active;
    }

    /**
     * 再次发动技能：取消当前点穴状态，不结算任何已点中穴位的效果。
     */
    public void cancelPressing() {
        if (!active) return;
        if (player instanceof ServerPlayer master) {
            master.displayClientMessage(Component.literal("点穴已取消").withStyle(ChatFormatting.GRAY), true);
            closeAndStop(master);
        } else {
            stop();
        }
    }

    public boolean startPressing(ServerPlayer target) {
        if (active) return false;
        if (!(player instanceof ServerPlayer master)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(master) || !GameUtils.isPlayerAliveAndSurvival(target)) return false;

        Vec3 dir = flatten(target.position().subtract(master.position()));
        if (dir.lengthSqr() < 1.0e-4) dir = flatten(master.getLookAngle());
        dir = dir.normalize();

        target.push(dir.x * PUSH_STRENGTH, PUSH_UPWARD, dir.z * PUSH_STRENGTH);
        target.hurtMarked = true;
        target.connection.send(new ClientboundSetEntityMotionPacket(target.getId(), target.getDeltaMovement()));

        applyRestrictions(target);
        target.setLastHurtByMob(master);
        target.setLastHurtByPlayer(master);
        target.displayClientMessage(Component.translatable("message.noellesroles.dianxue_master.victim")
                .withStyle(ChatFormatting.DARK_RED), true);

        // 生成 5 个随机位置小点：高度随机，方位偏向朝向大师一侧（可见半球内随机散布）
        Vec3 toMaster = flatten(master.position().subtract(target.position()));
        if (toMaster.lengthSqr() < 1.0e-4) toMaster = flatten(target.getLookAngle().scale(-1.0D));
        if (toMaster.lengthSqr() < 1.0e-4) toMaster = new Vec3(0.0D, 0.0D, 1.0D);
        toMaster = toMaster.normalize();
        double baseAngle = Math.atan2(-toMaster.x, toMaster.z) - Math.toRadians(target.getYRot());
        for (int i = 0; i < POINT_COUNT; i++) {
            this.dotHeightFracs[i] = (float) (0.15D + random.nextDouble() * 0.7D);
            double jitter = (random.nextDouble() - 0.5D) * Math.toRadians(160.0D);
            this.dotAzimuths[i] = (float) (baseAngle + jitter);
        }

        this.active = true;
        this.targetId = target.getUUID();
        this.activeIndex = 0;
        this.stepRemainingTicks = STEP_TICKS;
        this.stepMaxTicks = STEP_TICKS;
        this.lastClickAcceptedGameTime = master.level().getGameTime() - MIN_CLICK_INTERVAL_TICKS;
        send(master, ACT_OPEN);
        return true;
    }

    @Override
    public void serverTick() {
        if (!active) return;
        if (!(player instanceof ServerPlayer master)) { stop(); return; }
        if (!GameUtils.isPlayerAliveAndSurvival(master)) { closeAndStop(master); return; }
        if (!(master.level().getPlayerByUUID(targetId) instanceof ServerPlayer target)
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            closeAndStop(master);
            return;
        }
        if (master.distanceToSqr(target) > MAX_RANGE_SQR) { closeAndStop(master); return; }
        applyRestrictions(target);
        stepRemainingTicks--;
        send(master, ACT_UPDATE);
        if (stepRemainingTicks <= 0) finishSequenceAndApply(master, target);
    }

    /**
     * 点穴结束（3 秒内未进行下一次点穴）：把已点中穴位（前四次）的效果一次性结算并立即生效。
     * 第五次点穴的强制击杀只在“点击到该穴位”时立即触发，不会在此结算。
     */
    private void finishSequenceAndApply(ServerPlayer master, ServerPlayer target) {
        int completed = Math.min(activeIndex, POINT_COUNT - 1); // 只结算 0..3 的非致死效果
        for (int i = 0; i < completed; i++) {
            applyEffect(target, master, i);
        }
        closeAndStop(master);
    }

    private void advanceSequence(ServerPlayer master) {
        activeIndex++;
        if (activeIndex >= POINT_COUNT) {
            closeAndStop(master);
            return;
        }
        stepRemainingTicks = STEP_TICKS;
        stepMaxTicks = STEP_TICKS;
        send(master, ACT_UPDATE);
    }

    public void handleClientClick(ServerPlayer master, int index) {
        if (!active || index != activeIndex) return;
        if (!(master.level().getPlayerByUUID(targetId) instanceof ServerPlayer target)
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            closeAndStop(master);
            return;
        }

        // 防抖：一次点击只允许推进一个穴位，间隔内的重复推进直接忽略
        long now = master.level().getGameTime();
        if (now - lastClickAcceptedGameTime < MIN_CLICK_INTERVAL_TICKS) {
            return;
        }
        lastClickAcceptedGameTime = now;

        if (activeIndex == POINT_COUNT - 1) {
            // 第五次点穴：强制击杀立即生效，无需等待点穴结束
            applyEffect(target, master, activeIndex);
            closeAndStop(master);
            return;
        }
        // 前四次点穴：效果延后到点穴结束时统一生效，这里仅推进到下一个穴位并重置 3 秒窗口
        advanceSequence(master);
    }

    private void applyEffect(ServerPlayer target, ServerPlayer master, int index) {
        switch (index) {
            case 0 -> SREPlayerMoodComponent.KEY.get(target).addMood(0.25f);
            case 1 -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, GameConstants.getInTicks(0, 10), 0, false, true, true));
            case 2 -> target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, GameConstants.getInTicks(0, 10), 0, false, true, true));
            case 3 -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GameConstants.getInTicks(0, 10), 0, false, true, true));
            case 4 -> {
                target.setLastHurtByMob(master);
                target.setLastHurtByPlayer(master);
                Vec3 deathPos = target.position();
                GameUtils.forceKillPlayer(target, true, master, Noellesroles.id("dianxue"));
                dropRevolverAt(master, deathPos);
            }
        }
    }

    /** 点穴致死奖励：在死亡位置掉落一把左轮手枪 */
    private void dropRevolverAt(ServerPlayer master, Vec3 pos) {
        if (!(master.level() instanceof ServerLevel level)) return;
        ItemEntity revolver = new ItemEntity(level, pos.x, pos.y + 1.0D, pos.z, new ItemStack(TMMItems.REVOLVER));
        revolver.setDeltaMovement(0.0D, 0.2D, 0.0D);
        revolver.setPickUpDelay(30);
        level.addFreshEntity(revolver);
    }

    private void send(ServerPlayer master, int action) {
        String name = (targetId != null && master.level().getPlayerByUUID(targetId) != null)
                ? master.level().getPlayerByUUID(targetId).getGameProfile().getName() : "";
        ServerPlayNetworking.send(master, new DianxueMasterSyncS2CPacket(
                action, 1, activeIndex, targetId, name,
                Math.max(0, stepRemainingTicks), stepMaxTicks,
                dotHeightFracs.clone(), dotAzimuths.clone(), DOT_RADIUS));
    }

    private void closeAndStop(ServerPlayer master) {
        removeRestrictions(master);
        send(master, ACT_CLOSE);
        stop();
    }

    private void stop() { resetState(); }

    /** 点穴期间限制目标：锁移动/技能/道具使用/背包，仅保留视角转动（由 MobEffectKeyMixin 屏蔽按键）。 */
    private void applyRestrictions(ServerPlayer target) {
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, RESTRICT_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, RESTRICT_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, RESTRICT_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, RESTRICT_TICKS, 0, false, false, true));
    }

    /** 点穴结束/中断时移除上述限制效果。 */
    private void removeRestrictions(ServerPlayer master) {
        if (targetId == null) return;
        if (master.level().getPlayerByUUID(targetId) instanceof ServerPlayer target) {
            target.removeEffect(ModEffects.MOVE_BANED);
            target.removeEffect(ModEffects.SKILL_BANED);
            target.removeEffect(ModEffects.USED_BANED);
            target.removeEffect(ModEffects.INVENTORY_BANED);
        }
    }

    private static Vec3 flatten(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    @Override public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider p) {}
}