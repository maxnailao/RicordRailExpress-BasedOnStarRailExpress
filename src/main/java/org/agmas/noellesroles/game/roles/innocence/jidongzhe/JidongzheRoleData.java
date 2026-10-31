package org.agmas.noellesroles.game.roles.innocence.jidongzhe;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.JidongzheSlidePoseS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 机动者的滑铲与超载状态（服务端权威）。
 *
 * <p>体力不单独存储：本仓库的体力就是 vanilla {@code Player.sprintingTicks}，通过
 * {@link PlayerStaminaGetter} 读写，上限由 {@link JidongzheRole#staminaCap} 随超载状态即时计算，
 * 恢复与耗尽钳制都由 {@code PlayerEntityMixin} 负责。</p>
 *
 * <p>同步给本人的只有两样：是否处于超载、超载剩余刻数。客户端据此换算本地截止点画倒计时 HUD，
 * 不需要每刻发包。</p>
 */
public class JidongzheRoleData extends SimpleRoleData {

    /** 滑铲冷却结束时刻（本局开始以来的刻数，0 表示随时可用） */
    private long slideReadyAt;
    /** 是否正在滑铲 */
    private boolean sliding;
    /** 本次滑铲的目标水平位移（格） */
    private double slideDistance;
    /** 本次滑铲规划刻数与当前刻序号 */
    private int slideTicksTotal;
    private int slideTickIndex;
    /** 速度权重之和，用于把曲线归一化到「累计位移正好等于目标距离」 */
    private double slideWeightSum;
    /** 本次滑铲锁定方向（起始朝向的水平分量，单位向量） */
    private double dirX;
    private double dirZ;

    /** 超载结束时刻与是否处于超载 */
    private long overloadEndAt;
    private boolean overloadActive;
    /** 仅客户端使用：超载结束的本地世界刻截止点 */
    private long clientOverloadEndGameTick;
    /** 超载期间每秒向本人重同步一次剩余刻数，避免客户端本地倒数与服务端「本局时钟」在时停期间越走越偏 */
    private int overloadSyncTickCounter;

    public JidongzheRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 超载状态需要同步给本人客户端：HUD 的体力上限与倒计时都会走本地 RoleData。
     * 只同步「是否超载 + 剩余刻数」，不同步滑铲内部状态。
     */
    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public boolean isOverloaded() {
        return overloadActive;
    }

    // ------------------------------------------------------------------
    // 被动·滑铲
    // ------------------------------------------------------------------

    /**
     * 疾跑中按下蹲键起滑。冷却不做硬拦截：剩余冷却越长滑得越短，攒满才给满距离。
     */
    public boolean beginSlide(ServerPlayer sp) {
        if (!canAct(sp)) {
            return false;
        }
        if (sliding || !sp.isSprinting()) {
            return false;
        }
        if (!hasStamina(sp, JidongzheRole.SLIDE_STAMINA_COST_PERCENT)) {
            return false;
        }

        long now = GameUtils.getTicksFromGameStart(sp.level());
        long remainingCooldown = Math.max(0L, slideReadyAt - now);
        double ratio;
        if (remainingCooldown > JidongzheRole.SLIDE_SHORT_RANGE_TICKS) {
            // 剩余冷却超过 1 秒：距离固定为最短档
            ratio = 0.0D;
        } else {
            // 剩余 0~1 秒：剩余冷却越少，距离按百分比提升到 3.5 格
            ratio = (JidongzheRole.SLIDE_SHORT_RANGE_TICKS - remainingCooldown)
                    / (double) JidongzheRole.SLIDE_SHORT_RANGE_TICKS;
        }
        slideDistance = JidongzheRole.SLIDE_MIN_DISTANCE
                + (JidongzheRole.SLIDE_MAX_DISTANCE - JidongzheRole.SLIDE_MIN_DISTANCE) * ratio;

        // 只取朝向的水平分量：抬头或低头都不该让滑铲变成飞天或原地
        float yawRadians = (float) (sp.getYRot() * Math.PI / 180.0D);
        dirX = -Math.sin(yawRadians);
        dirZ = Math.cos(yawRadians);
        planSlideTicks();
        sliding = true;
        slideReadyAt = now + JidongzheRole.SLIDE_COOLDOWN_TICKS;

        spendStamina(sp, JidongzheRole.SLIDE_STAMINA_COST_PERCENT);
        broadcastPose(sp, true, slideTicksTotal);
        spawnSlideDust(sp);
        return true;
    }

    /**
     * 滑铲期间按下跳键：把滑铲转化为一次向前跃进，跃距按本次滑铲的名义距离等值映射。
     */
    public boolean beginSlideJump(ServerPlayer sp) {
        if (!canAct(sp) || !sliding) {
            return false;
        }
        if (!hasStamina(sp, JidongzheRole.SLIDE_JUMP_STAMINA_COST_PERCENT)) {
            return false;
        }
        spendStamina(sp, JidongzheRole.SLIDE_JUMP_STAMINA_COST_PERCENT);

        double horizontalPerTick = slideDistance / JidongzheRole.SLIDE_JUMP_AIR_TICKS;
        sp.setDeltaMovement(dirX * horizontalPerTick, JidongzheRole.SLIDE_JUMP_VERTICAL_MOTION,
                dirZ * horizontalPerTick);
        sp.hurtMarked = true;

        endSlide(sp);
        return true;
    }

    /**
     * 规划本次滑铲的刻数与速度权重：起手快、收尾缓，权重之和用于保证累计位移正好等于目标距离。
     */
    private void planSlideTicks() {
        // 至少 3 刻：短距离滑铲若只有 1 刻会像瞬移，姿态也来不及淡入
        slideTicksTotal = (int) Math.max(3L,
                Math.round(slideDistance / JidongzheRole.SLIDE_NOMINAL_SPEED_PER_TICK));
        slideTickIndex = 0;
        slideWeightSum = 0.0D;
        for (int i = 0; i < slideTicksTotal; i++) {
            slideWeightSum += slideWeight(i);
        }
    }

    private double slideWeight(int tickIndex) {
        if (slideTicksTotal <= 1) {
            return 1.0D;
        }
        double progress = tickIndex / (double) (slideTicksTotal - 1);
        return JidongzheRole.SLIDE_START_SPEED_RATIO
                - (JidongzheRole.SLIDE_START_SPEED_RATIO - JidongzheRole.SLIDE_END_SPEED_RATIO) * progress;
    }

    private void endSlide(ServerPlayer sp) {
        if (!sliding) {
            return;
        }
        sliding = false;
        slideTickIndex = 0;
        broadcastPose(sp, false, 0);
    }

    /**
     * 逐刻推进滑铲位移：每刻按曲线取一段位移，覆写水平速度并强制回包（本仓库位移类角色的通用做法），
     * 撞墙或走完规划刻数即结束。
     */
    private void tickSlide(ServerPlayer sp) {
        double step = slideDistance * slideWeight(slideTickIndex) / slideWeightSum;

        Vec3 mid = sp.position().add(0.0D, 0.9D, 0.0D);
        Vec3 target = mid.add(dirX * step, 0.0D, dirZ * step);
        BlockHitResult hit = sp.level().clip(new ClipContext(mid, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, sp));
        if (hit.getType() == HitResult.Type.BLOCK) {
            endSlide(sp);
            return;
        }

        Vec3 current = sp.getDeltaMovement();
        sp.setDeltaMovement(dirX * step, current.y, dirZ * step);
        sp.hurtMarked = true;

        slideTickIndex++;
        if (slideTickIndex >= slideTicksTotal) {
            endSlide(sp);
        }
    }

    /**
     * 起手蹭地烟尘：一次调用只发一个包（AGENT.md 的粒子发包约束），不要按刻累加。
     */
    private void spawnSlideDust(ServerPlayer sp) {
        Vec3 feet = sp.position();
        ParticleFx.burst(sp.serverLevel(), net.minecraft.core.particles.ParticleTypes.CLOUD,
                feet.x, feet.y + 0.1D, feet.z, 10, 0.3D, 0.08D, 0.3D, 0.06D);
    }

    // ------------------------------------------------------------------
    // 主动·超载
    // ------------------------------------------------------------------

    /**
     * 进入超载：体力上限翻倍（由 {@link JidongzheRole#staminaCap} 生效）、获得速度 I，
     * 并立即回复「翻倍后体力上限」65% 的体力。
     */
    public boolean startOverload(ServerPlayer sp) {
        if (!canAct(sp) || overloadActive) {
            return false;
        }
        overloadActive = true;
        overloadEndAt = GameUtils.getTicksFromGameStart(sp.level()) + JidongzheRole.OVERLOAD_DURATION_TICKS;

        // 此刻 supplier 已经会返回翻倍后的上限，直接按它取 65%
        float newMax = ModRoles.JIDONGZHE.getMaxSprintTime(sp);
        PlayerStaminaGetter stamina = (PlayerStaminaGetter) sp;
        stamina.starrailexpress$setStamina(Math.min(newMax,
                stamina.starrailexpress$getStamina() + newMax * JidongzheRole.OVERLOAD_RECOVERY_PERCENT / 100.0F));

        applySpeed(sp);
        notify(sp, "message.noellesroles.jidongzhe.overload_start", ChatFormatting.AQUA);
        sync();
        return true;
    }

    /**
     * 超载期间击杀玩家：超载延长 10 秒并体力回满（延长后总时长不超过 {@link JidongzheRole#OVERLOAD_MAX_TICKS}）。
     */
    public void extendOverloadOnKill(ServerPlayer killer) {
        if (!overloadActive || !canAct(killer)) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(killer.level());
        overloadEndAt = Math.min(now + JidongzheRole.OVERLOAD_MAX_TICKS,
                overloadEndAt + JidongzheRole.OVERLOAD_KILL_EXTENSION_TICKS);
        ((PlayerStaminaGetter) killer).starrailexpress$setStamina(ModRoles.JIDONGZHE.getMaxSprintTime(killer));
        applySpeed(killer);
        notify(killer, "message.noellesroles.jidongzhe.overload_extend", ChatFormatting.GOLD);
        sync();
    }

    private void endOverload(ServerPlayer sp, boolean announce) {
        overloadActive = false;
        overloadEndAt = 0L;
        sp.removeEffect(MobEffects.MOVEMENT_SPEED);
        // 体力钳制回基础上限交给 PlayerEntityMixin 每刻的 min 结算，这里不手动削减
        if (announce) {
            notify(sp, "message.noellesroles.jidongzhe.overload_end", ChatFormatting.GRAY);
        }
        sync();
    }

    /**
     * 速度效果只在等级不符或剩余时长变短时重刷，避免每刻播放粒子与音效。
     */
    private void applySpeed(ServerPlayer sp) {
        int remaining = (int) Math.max(1L, overloadEndAt - GameUtils.getTicksFromGameStart(sp.level()));
        MobEffectInstance existing = sp.getEffect(MobEffects.MOVEMENT_SPEED);
        if (existing != null && existing.getAmplifier() == JidongzheRole.SPEED_AMPLIFIER
                && existing.getDuration() >= remaining) {
            return;
        }
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, remaining, JidongzheRole.SPEED_AMPLIFIER,
                false, false, true));
    }

    // ------------------------------------------------------------------
    // tick 与工具
    // ------------------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.JIDONGZHE)) {
            resetState(sp);
            return;
        }
        if (!canAct(sp)) {
            resetState(sp);
            return;
        }

        if (overloadActive) {
            long now = GameUtils.getTicksFromGameStart(sp.level());
            if (now >= overloadEndAt) {
                endOverload(sp, true);
            } else if (++overloadSyncTickCounter >= 20) {
                overloadSyncTickCounter = 0;
                sync();
            }
        }
        if (sliding) {
            tickSlide(sp);
        }
    }

    private boolean canAct(ServerPlayer sp) {
        return !sp.isSpectator() && GameUtils.isPlayerAliveAndSurvival(sp);
    }

    private void resetState(ServerPlayer sp) {
        if (sliding) {
            endSlide(sp);
        }
        if (overloadActive) {
            endOverload(sp, false);
        }
    }

    private boolean hasStamina(ServerPlayer sp, int percentOfBase) {
        float base = JidongzheRole.BASE_STAMINA_TICKS;
        return ((PlayerStaminaGetter) sp).starrailexpress$getStamina() >= base * percentOfBase / 100.0F;
    }

    private void spendStamina(ServerPlayer sp, int percentOfBase) {
        // 消耗量按基础上限计算，超载期间体力上限翻倍不会让滑铲变得更贵
        float base = JidongzheRole.BASE_STAMINA_TICKS;
        PlayerStaminaGetter stamina = (PlayerStaminaGetter) sp;
        stamina.starrailexpress$setStamina(
                Math.max(0.0F, stamina.starrailexpress$getStamina() - base * percentOfBase / 100.0F));
    }

    private void notify(ServerPlayer sp, String key, ChatFormatting color) {
        sp.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    private void broadcastPose(ServerPlayer sp, boolean start, int durationTicks) {
        JidongzheSlidePoseS2CPacket packet = new JidongzheSlidePoseS2CPacket(sp.getId(), start, durationTicks);
        for (ServerPlayer target : sp.serverLevel().players()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target, packet);
        }
    }

    /**
     * 客户端 HUD 用：超载剩余刻数，0 表示未在超载或已走完。
     * 截止点由同步到达时的「剩余刻数 + 本地世界刻」推算，之后靠客户端自行倒数。
     */
    public long overloadRemainingTicks() {
        if (!overloadActive) {
            return 0L;
        }
        if (player.level().isClientSide) {
            return Math.max(0L, clientOverloadEndGameTick - player.level().getGameTime());
        }
        return Math.max(0L, overloadEndAt - GameUtils.getTicksFromGameStart(player.level()));
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("OverloadActive", overloadActive);
        tag.putInt("OverloadRemaining", (int) overloadRemainingTicks());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        overloadActive = tag.getBoolean("OverloadActive");
        clientOverloadEndGameTick = player.level().getGameTime() + tag.getInt("OverloadRemaining");
    }
}
