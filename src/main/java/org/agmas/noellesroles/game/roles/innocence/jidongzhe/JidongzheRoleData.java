package org.agmas.noellesroles.game.roles.innocence.jidongzhe;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.JidongzheSlidePoseS2CPacket;
import org.agmas.noellesroles.packet.JidongzheStaminaS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 机动者的滑铲与超载状态（服务端权威）。
 *
 * <p>体力不单独存储：本仓库的体力就是 vanilla {@code Player.sprintingTicks}，通过
 * {@link PlayerStaminaGetter} 读写，上限由 {@link JidongzheRole#staminaCap} 随超载状态即时计算。
 * 服务端与客户端**各自独立模拟**同一份体力，所以服务端改动数值后必须用
 * {@link JidongzheStaminaS2CPacket} 下发一次，否则玩家自己的体力条不动。</p>
 *
 * <p>滑铲位移用「每刻覆写水平速度 + 量测实际位移」而不是 teleport：玩家位置以客户端为准，
 * 服务端逐刻 teleport 会和客户端自己的落地/碰撞判定打架，实测会把人卡进脚下方块里。
 * 现在改为持续给速度，直到**实际走过的水平距离**达到目标格数（撞墙时走不到就由刻数上限收尾），
 * 这样既不会卡块，距离也真的对得上。</p>
 */
public class JidongzheRoleData extends SimpleRoleData {

    /** 滑铲冷却结束时刻（本局开始以来的刻数，0 表示随时可用） */
    private long slideReadyAt;
    /** 是否正在滑铲 */
    private boolean sliding;
    /** 本次滑铲的目标水平位移（格）与已累计的实际位移（格） */
    private double slideDistance;
    private double slideTravelled;
    /** 上一刻的位置，用来量出这一刻真正走了多远 */
    private Vec3 slidePrevPos;
    /** 本次滑铲允许的刻数上限（防撞墙后无限滑行）与已用刻数 */
    private int slideTicksCap;
    private int slideTickIndex;
    /** 本次滑铲锁定方向（起始朝向的水平分量，单位向量） */
    private double dirX;
    private double dirZ;
    /** 滑铲结束时刻（本局时钟），用于滑铲跳的宽限期 */
    private long slideEndAt;
    /** 刚刚处于疾跑的记忆刻数：原版在按下蹲键的同刻关掉疾跑，不能直接判 isSprinting() */
    private int sprintMemoryTicks;

    /** 超载结束时刻与是否处于超载 */
    private long overloadEndAt;
    private boolean overloadActive;
    /** 仅客户端使用：超载结束的本地世界刻截止点 */
    private long clientOverloadEndGameTick;
    /** 超载期间每秒向本人重同步一次剩余刻数，避免客户端倒数与服务端「本局时钟」在时停期间越走越偏 */
    private int overloadSyncTickCounter;

    public JidongzheRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 超载状态需要同步给本人客户端：HUD 的体力上限与倒计时都读本地 RoleData。
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
        if (sliding || sprintMemoryTicks <= 0) {
            return false;
        }

        long now = GameUtils.getTicksFromGameStart(sp.level());
        slideDistance = JidongzheRole.slideDistanceForCooldown(Math.max(0L, slideReadyAt - now));

        // 只取朝向的水平分量：抬头或低头都不该让滑铲变成飞天或原地
        lockDirectionToFacing(sp);
        sliding = true;
        slideTravelled = 0.0D;
        slidePrevPos = sp.position();
        slideTicksCap = JidongzheRole.estimateSlideTicks(slideDistance) + JidongzheRole.SLIDE_TICK_TOLERANCE;
        slideTickIndex = 0;
        slideReadyAt = now + JidongzheRole.SLIDE_COOLDOWN_TICKS;
        // 起滑即清空记忆，避免同一刻的重复包触发第二次
        sprintMemoryTicks = 0;

        spendStamina(sp, JidongzheRole.SLIDE_STAMINA_COST_PERCENT);
        broadcastPose(sp, true, slideTicksCap);
        spawnSlideDust(sp);
        return true;
    }

    /**
     * 滑铲期间按下跳键：向前跃进，跃距按本次滑铲的名义距离等值映射。
     * 跃起方向取按跳那一刻玩家自己的朝向，而不是滑铲起始时锁定的方向，玩家可以边滑边转领带出斜向跃起。
     * 允许滑铲结束后 {@code SLIDE_JUMP_GRACE_TICKS} 刻内按下，用来吃掉网络与判定上的先后差。
     */
    public boolean beginSlideJump(ServerPlayer sp) {
        if (!canAct(sp)) {
            return false;
        }
        if (!sliding) {
            long now = GameUtils.getTicksFromGameStart(sp.level());
            boolean inGrace = slideEndAt > 0L && now - slideEndAt <= JidongzheRole.SLIDE_JUMP_GRACE_TICKS;
            if (!inGrace) {
                return false;
            }
        }
        // 吃过一次就不再重复给宽限
        slideEndAt = 0L;
        spendStamina(sp, JidongzheRole.SLIDE_JUMP_STAMINA_COST_PERCENT);

        // 按按跳这一刻的朝向重新取方向：滑行中转向后就能带出斜向跃起
        lockDirectionToFacing(sp);
        double horizontalPerTick = slideDistance / JidongzheRole.SLIDE_JUMP_AIR_TICKS;
        sp.setDeltaMovement(dirX * horizontalPerTick, JidongzheRole.SLIDE_JUMP_VERTICAL_MOTION,
                dirZ * horizontalPerTick);
        sp.hurtMarked = true;

        endSlide(sp);
        return true;
    }

    /**
     * 把方向锁定为「当前朝向的水平分量」：只看 yaw，抬头或低头都不该让位移变成飞天或原地。
     */
    private void lockDirectionToFacing(ServerPlayer sp) {
        float yawRadians = (float) (sp.getYRot() * Math.PI / 180.0D);
        dirX = -Math.sin(yawRadians);
        dirZ = Math.cos(yawRadians);
    }

    private void endSlide(ServerPlayer sp) {
        if (!sliding) {
            return;
        }
        sliding = false;
        slideTickIndex = 0;
        slideEndAt = GameUtils.getTicksFromGameStart(sp.level());
        broadcastPose(sp, false, 0);
    }

    /**
     * 逐刻推进滑铲：先量出上一刻实际走了多少格，再按剩余进度给这一刻的水平速度。
     * 速度从 1.6 倍基准衰减到 0.4 倍，所以起手冲得快、落点收得住。
     */
    private void tickSlide(ServerPlayer sp) {
        Vec3 pos = sp.position();
        slideTravelled += pos.subtract(slidePrevPos).horizontalDistance();
        slidePrevPos = pos;
        slideTickIndex++;

        if (slideTravelled >= slideDistance || slideTickIndex >= slideTicksCap) {
            endSlide(sp);
            return;
        }

        double progress = Math.min(1.0D, slideTravelled / slideDistance);
        double ratio = JidongzheRole.SLIDE_START_SPEED_RATIO
                - (JidongzheRole.SLIDE_START_SPEED_RATIO - JidongzheRole.SLIDE_END_SPEED_RATIO) * progress;
        double speed = JidongzheRole.SLIDE_SPEED_PER_TICK * ratio;

        Vec3 current = sp.getDeltaMovement();
        sp.setDeltaMovement(dirX * speed, current.y, dirZ * speed);
        sp.hurtMarked = true;
    }

    /**
     * 起手蹭地烟尘：一次调用只发一个包（AGENT.md 的粒子发包约束），不要按刻累加。
     */
    private void spawnSlideDust(ServerPlayer sp) {
        Vec3 feet = sp.position();
        ParticleFx.burst(sp.serverLevel(), ParticleTypes.CLOUD,
                feet.x, feet.y + 0.1D, feet.z, 10, 0.3D, 0.08D, 0.3D, 0.06D);
    }

    // ------------------------------------------------------------------
    // 主动·超载
    // ------------------------------------------------------------------

    /**
     * 进入超载：体力上限翻倍、获得速度 I，并立即回复「翻倍后体力上限」65% 的体力。
     */
    public boolean startOverload(ServerPlayer sp) {
        if (!canAct(sp) || overloadActive) {
            return false;
        }
        long now = GameUtils.getTicksFromGameStart(sp.level());
        overloadActive = true;
        overloadEndAt = now + JidongzheRole.OVERLOAD_DURATION_TICKS;
        overloadSyncTickCounter = 0;

        // 直接按常量算翻倍后的上限，不依赖 supplier 的读回值，避免读到尚未生效的状态
        float doubledCap = JidongzheRole.BASE_STAMINA_TICKS * (float) JidongzheRole.OVERLOAD_STAMINA_MULTIPLIER;
        PlayerStaminaGetter stamina = (PlayerStaminaGetter) sp;
        float current = Math.max(0.0F, stamina.starrailexpress$getStamina());
        stamina.starrailexpress$setStamina(Math.min(doubledCap,
                current + doubledCap * JidongzheRole.OVERLOAD_RECOVERY_PERCENT / 100.0F));

        applySpeed(sp);
        syncStamina(sp);
        notify(sp, "message.noellesroles.jidongzhe.overload_start", ChatFormatting.AQUA);
        sync();
        return true;
    }

    /**
     * 超载期间击杀玩家：超载延长 10 秒并体力回满（总时长不超过 OVERLOAD_MAX_TICKS）。
     */
    public void extendOverloadOnKill(ServerPlayer killer) {
        if (!overloadActive || !canAct(killer)) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(killer.level());
        overloadEndAt = Math.min(now + JidongzheRole.OVERLOAD_MAX_TICKS,
                overloadEndAt + JidongzheRole.OVERLOAD_KILL_EXTENSION_TICKS);
        ((PlayerStaminaGetter) killer).starrailexpress$setStamina(
                JidongzheRole.BASE_STAMINA_TICKS * (float) JidongzheRole.OVERLOAD_STAMINA_MULTIPLIER);
        applySpeed(killer);
        syncStamina(killer);
        notify(killer, "message.noellesroles.jidongzhe.overload_extend", ChatFormatting.GOLD);
        sync();
    }

    private void endOverload(ServerPlayer sp, boolean announce) {
        overloadActive = false;
        overloadEndAt = 0L;
        overloadSyncTickCounter = 0;
        sp.removeEffect(MobEffects.MOVEMENT_SPEED);
        // 超出新上限的体力由 PlayerEntityMixin 每刻的 min 结算钳回，两端各自收敛
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

        // 疾跑记忆：按下蹲键的同刻原版已关掉疾跑，滑铲包到达时只能靠这个窗口判定
        if (sp.isSprinting()) {
            sprintMemoryTicks = JidongzheRole.SPRINT_MEMORY_TICKS;
        } else if (sprintMemoryTicks > 0) {
            sprintMemoryTicks--;
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
        sprintMemoryTicks = 0;
        slideEndAt = 0L;
    }

    /**
     * 只扣体力、不做一票否决：体力条本身会限制持续疾跑，把「体力不足」做成静默失败只会让滑铲莫名失灵。
     */
    private void spendStamina(ServerPlayer sp, int percentOfBase) {
        float base = JidongzheRole.BASE_STAMINA_TICKS;
        PlayerStaminaGetter stamina = (PlayerStaminaGetter) sp;
        float current = Math.max(0.0F, stamina.starrailexpress$getStamina());
        stamina.starrailexpress$setStamina(Math.max(0.0F, current - base * percentOfBase / 100.0F));
        syncStamina(sp);
    }

    /**
     * 把服务端的体力数值推给本人客户端：两端各自模拟同一份体力，不发包玩家看不到变化。
     */
    private void syncStamina(ServerPlayer sp) {
        float value = ((PlayerStaminaGetter) sp).starrailexpress$getStamina();
        ServerPlayNetworking.send(sp, new JidongzheStaminaS2CPacket(value));
    }

    private void notify(ServerPlayer sp, String key, ChatFormatting color) {
        sp.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    private void broadcastPose(ServerPlayer sp, boolean start, int durationTicks) {
        JidongzheSlidePoseS2CPacket packet = new JidongzheSlidePoseS2CPacket(sp.getId(), start, durationTicks);
        for (ServerPlayer target : sp.serverLevel().players()) {
            ServerPlayNetworking.send(target, packet);
        }
    }

    /**
     * HUD 用：超载剩余刻数，0 表示未在超载或已走完。
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
