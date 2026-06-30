package org.agmas.noellesroles.game.roles.killer.ghostofanying;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 暗影组件
 *
 * 管理"暗影步"技能：
 * - 最多存储3次使用次数，每次回转CD为30秒
 * - 游戏开始时存储为1次
 * - 使用后进入隐身形态，进行自动移动
 * - 初速度100格/s，移动0.3秒(30格)后减速至停止(再约20格)
 * - 移动期间可转动视角控制方向
 * - 移动路径留下黑色、灰色粒子
 */
public class GhostofanyingPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<GhostofanyingPlayerComponent> KEY = ModComponents.GHOSTOFANYING;

    // ==================== 常量定义 ====================

    /** 最大存储次数 */
    public static final int MAX_CHARGES = 3;

    /** 单次回转冷却时间（30秒 = 600 tick） */
    public static final int RECHARGE_COOLDOWN = 600;

    /** 暗影步总持续时间（14 tick = 0.7秒） */
    private static final int DASH_TOTAL_TICKS = 14;

    /** 匀速阶段持续 tick（6 tick = 0.3秒，移动30格） */
    private static final int CONSTANT_PHASE_TICKS = 6;

    /** 初速度（100格/s = 5格/tick） */
    private static final double INITIAL_SPEED = 5.0;

    /** 减速阶段每 tick 的速度衰减量（8tick内从5.0降至0） */
    private static final double DECELERATION_PER_TICK = INITIAL_SPEED / (DASH_TOTAL_TICKS - CONSTANT_PHASE_TICKS);

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前存储次数 */
    public int charges = 1;

    /** 回转冷却时间（tick），每次消耗一个charge后重置 */
    public int rechargeCooldown = 0;

    /** 暗影步剩余 tick */
    public int dashTicks = 0;

    /** 是否正在暗影步中 */
    public boolean isDashing = false;

    public GhostofanyingPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.charges = 1;
        this.rechargeCooldown = 0;
        this.dashTicks = 0;
        this.isDashing = false;
        // 清除可能残留的隐身和移动限制效果
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.INVISIBILITY);
            sp.removeEffect(ModEffects.MOVE_BANED);
        }
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /**
     * 使用暗影步技能
     *
     * @return 是否成功使用
     */
    public boolean useShadowStep() {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return false;
        if (isDashing)
            return false;
        if (charges <= 0) {
            if (player instanceof ServerPlayer sp) {
                if (rechargeCooldown > 0) {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.ghostofanying.no_charges",
                                    (rechargeCooldown + 19) / 20).withStyle(ChatFormatting.RED),
                            true);
                } else {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.ghostofanying.no_charges_ready")
                                    .withStyle(ChatFormatting.YELLOW),
                            true);
                }
            }
            return false;
        }

        // 验证是暗影
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld.getRole(player.getUUID()) != ModRoles.GHOSTOFANYING)
            return false;

        // 消耗一次次数
        charges--;

        // 开始暗影步
        this.isDashing = true;
        this.dashTicks = DASH_TOTAL_TICKS;

        // 施加隐身效果（持续到暗影步结束+1 tick 的安全余量）
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DASH_TOTAL_TICKS + 2, 0, false, false, true));
        // 禁止玩家自身的移动输入（WASD/跳跃/潜行），但不影响鼠标转向
        player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, DASH_TOTAL_TICKS + 2, 0, false, false, true));

        // 播放龙息弹发射音效
        if (player instanceof ServerPlayer sp) {
            sp.serverLevel().playSound(null, sp.blockPosition(),
                    SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ghostofanying.shadow_step_activate")
                            .withStyle(ChatFormatting.DARK_PURPLE),
                    true);
        }

        // 如果次数未满且没有正在回转的冷却，开始回转冷却
        if (charges < MAX_CHARGES && rechargeCooldown <= 0) {
            rechargeCooldown = RECHARGE_COOLDOWN;
        }

        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld.getRole(player.getUUID()) != ModRoles.GHOSTOFANYING)
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer))
            return;

        // 处理暗影步移动
        if (isDashing && dashTicks > 0) {
            // 计算当前 tick 的速度
            int elapsed = DASH_TOTAL_TICKS - dashTicks;
            double speed;
            if (elapsed < CONSTANT_PHASE_TICKS) {
                // 匀速阶段
                speed = INITIAL_SPEED;
            } else {
                // 减速阶段
                int decelTick = elapsed - CONSTANT_PHASE_TICKS;
                speed = INITIAL_SPEED - DECELERATION_PER_TICK * (decelTick + 1);
                if (speed < 0)
                    speed = 0;
            }

            // 获取玩家视角方向（水平方向，忽略俯仰角）
            Vec3 look = player.getLookAngle();
            Vec3 flatLook = new Vec3(look.x, 0, look.z);
            if (flatLook.lengthSqr() > 0) {
                flatLook = flatLook.normalize();
            } else {
                flatLook = Vec3.ZERO;
            }

            // 设置玩家速度（自动移动，不受玩家自身移动影响）
            player.setDeltaMovement(flatLook.x * speed, player.getDeltaMovement().y, flatLook.z * speed);
            player.hurtMarked = true;

            // 生成黑色和灰色粒子
            ServerLevel level = serverPlayer.serverLevel();
            double px = player.getX();
            double py = player.getY() + 0.1;
            double pz = player.getZ();
            for (int i = 0; i < 5; i++) {
                level.sendParticles(ParticleTypes.SMOKE,
                        px + (player.level().random.nextDouble() - 0.5) * 0.5,
                        py + player.level().random.nextDouble() * 0.3,
                        pz + (player.level().random.nextDouble() - 0.5) * 0.5,
                        1, 0, 0, 0, 0.01);
            }
            for (int i = 0; i < 3; i++) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        px + (player.level().random.nextDouble() - 0.5) * 0.5,
                        py + player.level().random.nextDouble() * 0.3,
                        pz + (player.level().random.nextDouble() - 0.5) * 0.5,
                        1, 0, 0, 0, 0.01);
            }

            dashTicks--;

            // 暗影步结束
            if (dashTicks <= 0) {
                endShadowStep();
            } else if (dashTicks % 3 == 0) {
                sync();
            }
        }

        // 处理回转冷却
        if (rechargeCooldown > 0) {
            rechargeCooldown--;
            if (rechargeCooldown <= 0 && charges < MAX_CHARGES) {
                charges++;
                rechargeCooldown = RECHARGE_COOLDOWN;
                sync();
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.ghostofanying.charge_recharged",
                                    charges, MAX_CHARGES).withStyle(ChatFormatting.GREEN),
                            true);
                }
            } else if (rechargeCooldown % 100 == 0) {
                sync();
            }
        }
    }

    /**
     * 结束暗影步
     */
    private void endShadowStep() {
        this.isDashing = false;
        this.dashTicks = 0;

        // 移除隐身和移动限制效果
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.MOVE_BANED);

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ghostofanying.shadow_step_end")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }

        sync();
    }

    @Override
    public void clientTick() {
        // 客户端本地模拟冷却和技能时间（用于 HUD 显示）
        if (rechargeCooldown > 1) {
            rechargeCooldown--;
            if (rechargeCooldown <= 0 && charges < MAX_CHARGES) {
                charges++;
                rechargeCooldown = RECHARGE_COOLDOWN;
            }
        }
        if (dashTicks > 0) {
            dashTicks--;
            if (dashTicks <= 0) {
                isDashing = false;
            }
        }
    }

    // ==================== HUD 辅助方法 ====================

    public float getCooldownSeconds() {
        return rechargeCooldown / 20.0f;
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("charges", charges);
        tag.putInt("rechargeCooldown", rechargeCooldown);
        tag.putInt("dashTicks", dashTicks);
        tag.putBoolean("isDashing", isDashing);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        charges = tag.getInt("charges");
        rechargeCooldown = tag.getInt("rechargeCooldown");
        dashTicks = tag.getInt("dashTicks");
        isDashing = tag.getBoolean("isDashing");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
