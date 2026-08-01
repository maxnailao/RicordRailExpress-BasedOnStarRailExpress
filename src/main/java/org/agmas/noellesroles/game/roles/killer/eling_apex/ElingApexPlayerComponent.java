package org.agmas.noellesroles.game.roles.killer.eling_apex;

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
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 恶灵组件
 *
 * 管理"相位转移"技能：
 * - 按下技能键后有1.5s前摇，期间播放地狱传送门前1.5s声音，按键禁用
 * - 前摇结束后进入"空间状态"8s，获得速度I+隐身+按键禁用，留下黑色/紫色/灰色粒子
 * - 进入空间状态瞬间脚下生成末影人传送粒子
 * - 8s后自动退出空间状态，隐身和速度效果结束
 * - 退出后有1.5s后摇，播放传送门后1.5s声音，生成传送粒子，按键禁用
 * - 空间状态期间可提前按技能键退出，同样有后摇
 * - 技能CD 45s（从进入空间状态开始计算）
 */
public class ElingApexPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ElingApexPlayerComponent> KEY = ModComponents.ELING_APEX;

    // ==================== 常量定义 ====================

    /** 前摇时间（1.5秒 = 30 tick） */
    private static final int WINDUP_TICKS = 30;

    /** 空间状态持续时间（8秒 = 160 tick） */
    private static final int PHASE_TICKS = 160;

    /** 后摇时间（1.5秒 = 30 tick） */
    private static final int WIND_DOWN_TICKS = 30;

    /** 技能冷却时间（45秒 = 900 tick），从进入空间状态开始计算 */
    private static final int SKILL_COOLDOWN_TICKS = 900;

    // ==================== 状态枚举 ====================

    public enum PhaseState {
        IDLE,    // 空闲状态
        WINDUP,  // 前摇中
        PHASE,   // 空间状态中
        WIND_DOWN // 后摇中
    }

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前状态 */
    public PhaseState state = PhaseState.IDLE;

    /** 当前状态剩余 tick */
    public int stateTicks = 0;

    /** 技能冷却（从进入空间状态开始计算） */
    public int cooldown = 0;

    public ElingApexPlayerComponent(Player player) {
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
        this.state = PhaseState.IDLE;
        this.stateTicks = 0;
        this.cooldown = 0;
        // 清除可能残留的效果
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.INVISIBILITY);
            sp.removeEffect(MobEffects.MOVEMENT_SPEED);
            sp.removeEffect(ModEffects.USED_BANED);
            sp.removeEffect(ModEffects.SKILL_BANED);
            sp.removeEffect(ModEffects.INVINCIBLE);
        }
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /**
     * 使用相位转移技能（由技能系统调用）
     *
     * @return 是否成功触发
     */
    public boolean usePhaseShift() {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return false;

        // 验证角色
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld.getRole(player.getUUID()) != ModRoles.ELING_APEX)
            return false;

        if (state == PhaseState.PHASE) {
            // 空间状态中再次按键 → 提前退出
            cancelPhase();
            return true;
        }

        if (state != PhaseState.IDLE)
            return false;

        // 检查冷却
        if (cooldown > 0) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.eling_apex.on_cooldown",
                                (cooldown + 19) / 20).withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        }

        // 开始前摇
        startWindup();
        return true;
    }

    /**
     * 开始前摇阶段
     */
    private void startWindup() {
        this.state = PhaseState.WINDUP;
        this.stateTicks = WINDUP_TICKS;

        // 施加按键禁用效果（前摇期间）
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, WINDUP_TICKS + 2, 0, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, WINDUP_TICKS + 2, 0, false, false, true));

        // 播放地狱传送门传送前1.5s声音（使用 PORTAL_TRAVEL 音效，pitch调高模拟前段）
        if (player instanceof ServerPlayer sp) {
            sp.serverLevel().playSound(null, sp.blockPosition(),
                    SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.7F, 0.5F);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.eling_apex.phase_shifting")
                            .withStyle(ChatFormatting.DARK_PURPLE),
                    true);
        }

        sync();
    }

    /**
     * 进入空间状态
     */
    private void enterPhase() {
        this.state = PhaseState.PHASE;
        this.stateTicks = PHASE_TICKS;

        // 移除前摇的按键禁用
        player.removeEffect(ModEffects.USED_BANED);
        player.removeEffect(ModEffects.SKILL_BANED);

        // 施加空间状态效果（含无敌）
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, PHASE_TICKS + 2, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PHASE_TICKS + 2, 1, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, PHASE_TICKS + 2, 0, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, PHASE_TICKS + 2, 0, false, false, true));

        // 脚下生成末影人传送粒子
        if (player instanceof ServerPlayer sp) {
            ServerLevel level = sp.serverLevel();
            double px = sp.getX();
            double py = sp.getY();
            double pz = sp.getZ();
            for (int i = 0; i < 30; i++) {
                level.sendParticles(ParticleTypes.PORTAL,
                        px + (level.random.nextDouble() - 0.5) * 1.0,
                        py + level.random.nextDouble() * 2.0,
                        pz + (level.random.nextDouble() - 0.5) * 1.0,
                        1, 0, 0, 0, 0.5);
            }
            // 播放传送门触发音效
            level.playSound(null, sp.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.5F);
        }

        // 设置技能冷却（从此刻开始计算45s）
        this.cooldown = SKILL_COOLDOWN_TICKS;

        sync();
    }

    /**
     * 提前取消空间状态（按下技能键）
     */
    private void cancelPhase() {
        // 移除空间状态效果（含无敌）
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(ModEffects.USED_BANED);
        player.removeEffect(ModEffects.INVINCIBLE);

        // 进入后摇
        startWindDown();
    }

    /**
     * 正常结束空间状态（8秒到）
     */
    private void endPhase() {
        // 移除空间状态效果（含无敌）
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(ModEffects.USED_BANED);
        player.removeEffect(ModEffects.INVINCIBLE);

        // 进入后摇
        startWindDown();
    }

    /**
     * 开始后摇阶段
     */
    private void startWindDown() {
        this.state = PhaseState.WIND_DOWN;
        this.stateTicks = WIND_DOWN_TICKS;

        // 施加按键禁用效果（后摇期间）
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, WIND_DOWN_TICKS + 2, 0, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, WIND_DOWN_TICKS + 2, 0, false, false, true));

        // 播放传送门后1.5s声音（使用 PORTAL_TRAVEL 音效，pitch调低模拟后段）
        if (player instanceof ServerPlayer sp) {
            ServerLevel level = sp.serverLevel();
            level.playSound(null, sp.blockPosition(),
                    SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.7F, 1.5F);
            // 生成末影人传送粒子
            double px = sp.getX();
            double py = sp.getY();
            double pz = sp.getZ();
            for (int i = 0; i < 30; i++) {
                level.sendParticles(ParticleTypes.PORTAL,
                        px + (level.random.nextDouble() - 0.5) * 1.0,
                        py + level.random.nextDouble() * 2.0,
                        pz + (level.random.nextDouble() - 0.5) * 1.0,
                        1, 0, 0, 0, 0.5);
            }
        }

        sync();
    }

    /**
     * 结束后摇，回到空闲状态
     */
    private void endWindDown() {
        this.state = PhaseState.IDLE;
        this.stateTicks = 0;

        // 移除按键禁用效果
        player.removeEffect(ModEffects.USED_BANED);
        player.removeEffect(ModEffects.SKILL_BANED);

        sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld.getRole(player.getUUID()) != ModRoles.ELING_APEX)
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer))
            return;

        // 处理冷却
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 100 == 0) {
                sync();
            }
        }

        // 处理状态转换
        if (stateTicks > 0) {
            stateTicks--;

            // 空间状态中生成粒子
            if (state == PhaseState.PHASE) {
                ServerLevel level = serverPlayer.serverLevel();
                double px = player.getX();
                double py = player.getY() + 0.1;
                double pz = player.getZ();

                // 黑色粒子（烟雾）
                for (int i = 0; i < 3; i++) {
                    level.sendParticles(ParticleTypes.SMOKE,
                            px + (level.random.nextDouble() - 0.5) * 0.8,
                            py + level.random.nextDouble() * 1.5,
                            pz + (level.random.nextDouble() - 0.5) * 0.8,
                            1, 0, 0, 0, 0.01);
                }
                // 紫色粒子（末影人传送粒子）
                for (int i = 0; i < 2; i++) {
                    level.sendParticles(ParticleTypes.PORTAL,
                            px + (level.random.nextDouble() - 0.5) * 0.8,
                            py + level.random.nextDouble() * 1.5,
                            pz + (level.random.nextDouble() - 0.5) * 0.8,
                            1, 0, 0, 0, 0.02);
                }
                // 灰色粒子（大烟雾）
                for (int i = 0; i < 2; i++) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            px + (level.random.nextDouble() - 0.5) * 0.8,
                            py + level.random.nextDouble() * 1.5,
                            pz + (level.random.nextDouble() - 0.5) * 0.8,
                            1, 0, 0, 0, 0.01);
                }
            }

            // 状态转换
            if (stateTicks <= 0) {
                switch (state) {
                    case WINDUP:
                        // 前摇结束 → 进入空间状态
                        enterPhase();
                        break;
                    case PHASE:
                        // 空间状态结束 → 进入后摇
                        endPhase();
                        break;
                    case WIND_DOWN:
                        // 后摇结束 → 回到空闲状态
                        endWindDown();
                        break;
                    default:
                        break;
                }
            } else if (stateTicks % 20 == 0) {
                // 定期同步
                sync();
            }
        }
    }

    // ==================== HUD 辅助方法 ====================

    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    public boolean isInPhase() {
        return state == PhaseState.PHASE;
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("state", state.ordinal());
        tag.putInt("stateTicks", stateTicks);
        tag.putInt("cooldown", cooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        int stateOrd = tag.getInt("state");
        this.state = stateOrd >= 0 && stateOrd < PhaseState.values().length
                ? PhaseState.values()[stateOrd]
                : PhaseState.IDLE;
        this.stateTicks = tag.getInt("stateTicks");
        this.cooldown = tag.getInt("cooldown");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
