package org.agmas.noellesroles.game.roles.killer.huanmozhe;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 幻魔者玩家组件
 *
 * 技能1 - 地刺：向前8格召唤地刺，击杀路径上的玩家，最多击杀2名，施法前摇2s
 * 技能2 - 恼鬼召唤：选择一个玩家，在其周围释放3个恼鬼，恼鬼存在15s，半径5格内玩家每秒掉2点理智+缓慢I
 * 被动 - 不死图腾：死亡15秒后复活，复活无声音，复活后10秒无敌，一局一次
 * 技能存储：商店购买，80金币一次，最多3次，初始不给予
 */
public class HuanmozhePlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<HuanmozhePlayerComponent> KEY = ModComponents.HUANMOZHE;

    private final Player player;

    // ========== 被动：不死图腾 ==========
    /** 是否已使用过复活（一局一次） */
    public boolean revivalUsed = false;
    /** 是否正在等待复活 */
    public boolean waitingForRevival = false;
    /** 复活倒计时（tick） */
    public int revivalTimer = 0;
    /** 复活后无敌倒计时（tick） */
    public int invincibleTimer = 0;
    /** 死亡时记录的位置（用于复活） */
    public double deathX = 0, deathY = 0, deathZ = 0;

    // ========== 技能存储 ==========
    /** 技能存储次数（最多3） */
    public int skillStorage = 0;

    // ========== 技能1：地刺 ==========
    /** 地刺是否正在施法 */
    public boolean spikeCasting = false;
    /** 地刺施法前摇计时器 */
    public int spikeCastTimer = 0;
    /** 地刺序列当前位置索引 */
    public int spikeProgressIndex = 0;
    /** 地刺击杀计数（每次释放最多2） */
    public int spikeKillCount = 0;
    /** 地刺起始位置 */
    public double spikeStartX = 0, spikeStartY = 0, spikeStartZ = 0;
    /** 地刺方向 */
    public double spikeDirX = 0, spikeDirZ = 0;
    /** 地刺是否正在推进 */
    public boolean spikeAdvancing = false;
    /** 地刺推进间隔计时器 */
    public int spikeAdvanceTimer = 0;

    // ========== 技能2：恼鬼 ==========
    /** 恼鬼实体UUID列表 */
    public final List<UUID> vexUuids = new ArrayList<>();
    /** 恼鬼存在倒计时（tick） */
    public int vexDurationTimer = 0;

    /** 施法前摇时间：1.2秒 = 24 ticks */
    public static final int SPIKE_CAST_TIME = 24;
    /** 地刺总格数（8+3=11） */
    public static final int SPIKE_RANGE = 11;
    /** 地刺推进间隔：2 ticks（原3tick，速度+50%） */
    public static final int SPIKE_ADVANCE_INTERVAL = 2;
    /** 地刺最大击杀数 */
    public static final int SPIKE_MAX_KILLS = 2;
    /** 恼鬼持续时间：15秒 = 300 ticks */
    public static final int VEX_DURATION = 300;
    /** 恼鬼数量 */
    public static final int VEX_COUNT = 3;
    /** 恼鬼影响半径 */
    public static final double VEX_RADIUS = 5.0;
    /** 复活等待时间：15秒 = 300 ticks */
    public static final int REVIVAL_WAIT_TIME = 300;
    /** 复活后无敌时间：10秒 = 200 ticks */
    public static final int INVINCIBLE_DURATION = 200;
    /** 技能存储上限 */
    public static final int MAX_SKILL_STORAGE = 3;

    public HuanmozhePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.revivalUsed = false;
        this.waitingForRevival = false;
        this.revivalTimer = 0;
        this.invincibleTimer = 0;
        this.skillStorage = 0; // 初始不给予技能存储
        this.spikeCasting = false;
        this.spikeCastTimer = 0;
        this.spikeProgressIndex = 0;
        this.spikeKillCount = 0;
        this.spikeAdvancing = false;
        this.spikeAdvanceTimer = 0;
        this.vexUuids.clear();
        this.vexDurationTimer = 0;
        sync();
    }

    @Override
    public void clear() {
        // 清除恼鬼实体
        discardVexes();
        init();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel level = serverPlayer.serverLevel();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRunning()) return;

        // ========== 被动：复活倒计时 ==========
        if (waitingForRevival) {
            revivalTimer--;
            if (revivalTimer <= 0) {
                performRevival(serverPlayer, level);
            }
            return; // 等待复活期间不处理其他逻辑
        }

        // ========== 无敌倒计时 ==========
        if (invincibleTimer > 0) {
            invincibleTimer--;
            if (invincibleTimer <= 0) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.huanmozhe.invincible_end")
                                .withStyle(ChatFormatting.GRAY), true);
            }
        }

        // ========== 技能1：地刺施法前摇 ==========
        if (spikeCasting && !spikeAdvancing) {
            spikeCastTimer--;
            // 前摇期间显示粒子效果（跟随玩家当前朝向）
            spawnCastParticles(level, serverPlayer);
            if (spikeCastTimer <= 0) {
                // 前摇结束，此时获取最终位置和方向
                Vec3 look = serverPlayer.getLookAngle();
                spikeStartX = serverPlayer.getX();
                spikeStartY = serverPlayer.getY();
                spikeStartZ = serverPlayer.getZ();
                spikeDirX = look.x;
                spikeDirZ = look.z;
                double len = Math.sqrt(spikeDirX * spikeDirX + spikeDirZ * spikeDirZ);
                if (len > 0) {
                    spikeDirX /= len;
                    spikeDirZ /= len;
                }
                // 开始地刺推进
                spikeAdvancing = true;
                spikeProgressIndex = 0;
                spikeKillCount = 0;
                spikeAdvanceTimer = 0;
                // 播放地刺开始音效
                level.playSound(null, serverPlayer.blockPosition(),
                        SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }

        // ========== 技能1：地刺推进 ==========
        if (spikeAdvancing) {
            spikeAdvanceTimer++;
            if (spikeAdvanceTimer >= SPIKE_ADVANCE_INTERVAL) {
                spikeAdvanceTimer = 0;
                advanceSpike(level, serverPlayer);
                spikeProgressIndex++;
                if (spikeProgressIndex >= SPIKE_RANGE || spikeKillCount >= SPIKE_MAX_KILLS) {
                    // 地刺结束
                    spikeAdvancing = false;
                    spikeCasting = false;
                }
            }
        }

        // ========== 技能2：恼鬼持续时间 ==========
        if (vexDurationTimer > 0) {
            vexDurationTimer--;
            // 每秒（每20tick）对恼鬼附近玩家施加效果
            if (vexDurationTimer % 20 == 0) {
                applyVexEffects(level);
            }
            if (vexDurationTimer <= 0) {
                discardVexes();
            }
        }
    }

    // ==================== 技能1：地刺 ====================

    /**
     * 开始施放地刺技能
     */
    public boolean startSpikeCast(ServerPlayer serverPlayer) {
        if (spikeCasting || spikeAdvancing) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) return false;

        // 前摇开始时不固定方向，等前摇结束时再获取最终位置和方向
        spikeCasting = true;
        spikeCastTimer = SPIKE_CAST_TIME;
        spikeAdvancing = false;
        spikeProgressIndex = 0;
        spikeKillCount = 0;
        spikeAdvanceTimer = 0;
        sync();
        return true;
    }

    /**
     * 推进一格地刺
     */
    private void advanceSpike(ServerLevel level, ServerPlayer caster) {
        double x = spikeStartX + spikeDirX * (spikeProgressIndex + 1);
        double z = spikeStartZ + spikeDirZ * (spikeProgressIndex + 1);
        double y = spikeStartY;

        // 寻找地面位置
        BlockPos groundPos = findGround(level, BlockPos.containing(x, y, z));
        if (groundPos == null) return;

        double spikeX = groundPos.getX() + 0.5;
        double spikeY = groundPos.getY() + 1.0;
        double spikeZ = groundPos.getZ() + 0.5;

        // 播放地刺粒子效果（模拟原版幻魔者地刺）
        spawnSpikeParticles(level, spikeX, spikeY, spikeZ);
        // 播放地刺音效
        level.playSound(null, groundPos, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 0.8f, 1.0f);

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);

        // 检测该位置附近的玩家（2.0格范围内，左右各加宽了1/4格）
        if (spikeKillCount < SPIKE_MAX_KILLS) {
            for (ServerPlayer target : level.players()) {
                if (target == caster) continue;
                if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
                if (target.isSpectator()) continue;

                // 无法击杀杀手阵营和偏狼中立玩家
                var targetRole = gameWorld.getRole(target);
                if (targetRole != null && (targetRole.canUseKiller() || targetRole.isNeutralForKiller())) continue;

                double dist = target.distanceToSqr(spikeX, spikeY, spikeZ);
                if (dist <= 4.0) { // 2.0格半径 (1.75 + 0.25)
                    // 击杀玩家
                    GameUtils.killPlayer(target, true, caster, GameConstants.DeathReasons.GENERIC);
                    spikeKillCount++;
                    if (spikeKillCount >= SPIKE_MAX_KILLS) break;
                }
            }
        }
    }

    /**
     * 寻找地面位置（向下搜索实心方块）
     */
    private BlockPos findGround(ServerLevel level, BlockPos start) {
        for (int dy = 0; dy >= -3; dy--) {
            BlockPos check = start.offset(0, dy, 0);
            if (!level.getBlockState(check).isAir() && level.getBlockState(check.above()).isAir()) {
                return check;
            }
        }
        return start;
    }

    /**
     * 施法前摇粒子效果
     */
    private void spawnCastParticles(ServerLevel level, ServerPlayer caster) {
        Vec3 look = caster.getLookAngle();
        for (int i = 1; i <= SPIKE_RANGE; i++) {
            double px = caster.getX() + look.x * i;
            double py = caster.getY() + 0.1;
            double pz = caster.getZ() + look.z * i;
            level.sendParticles(ParticleTypes.SMOKE, px, py, pz, 1, 0.1, 0.05, 0.1, 0.01);
        }
    }

    /**
     * 地刺出现粒子效果
     */
    private void spawnSpikeParticles(ServerLevel level, double x, double y, double z) {
        // 向上喷射的粒子，模拟地刺冒出
        level.sendParticles(ParticleTypes.CRIT, x, y + 0.5, z, 15, 0.3, 0.8, 0.3, 0.1);
        level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 8, 0.2, 0.5, 0.2, 0.02);
    }

    // ==================== 技能2：恼鬼 ====================

    /**
     * 召唤恼鬼（仅可对存活的平民阵营玩家释放）
     */
    public boolean summonVexes(ServerPlayer caster, ServerPlayer target) {
        if (target == null) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(target)) return false;
        if (vexDurationTimer > 0) return false; // 已有恼鬼存在

        // 仅可对平民阵营玩家释放
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(caster.level());
        if (!gameWorld.isInnocent(target)) return false;

        ServerLevel level = caster.serverLevel();
        vexUuids.clear();

        // 在目标周围召唤3个恼鬼实体
        for (int i = 0; i < VEX_COUNT; i++) {
            double angle = (2 * Math.PI / VEX_COUNT) * i;
            double offsetX = Math.cos(angle) * 2.0;
            double offsetZ = Math.sin(angle) * 2.0;

            HuanmozheVexEntity vex = new HuanmozheVexEntity(
                    org.agmas.noellesroles.init.ModEntities.HUANMOZHE_VEX, level);
            vex.setPos(target.getX() + offsetX, target.getY() + 1.0, target.getZ() + offsetZ);
            vex.setOwnerUuid(caster.getUUID());
            level.addFreshEntity(vex);
            vexUuids.add(vex.getUUID());
        }

        vexDurationTimer = VEX_DURATION;
        sync();

        // 播放召唤音效
        level.playSound(null, target.blockPosition(),
                SoundEvents.VEX_AMBIENT, SoundSource.HOSTILE, 1.0f, 1.0f);
        return true;
    }

    /**
     * 对恼鬼附近玩家施加效果（每秒调用一次，仅对平民阵营生效）
     */
    private void applyVexEffects(ServerLevel level) {
        // 收集所有存活恼鬼的位置
        List<Vec3> vexPositions = new ArrayList<>();
        for (UUID uuid : vexUuids) {
            if (level.getEntity(uuid) instanceof HuanmozheVexEntity vex && vex.isAlive()) {
                vexPositions.add(vex.position());
            }
        }
        if (vexPositions.isEmpty()) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);

        // 对范围内的玩家施加效果（仅平民阵营）
        for (ServerPlayer target : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
            if (target.isSpectator()) continue;
            // 杀手阵营玩家不受影响
            if (!gameWorld.isInnocent(target)) continue;

            boolean inRange = false;
            for (Vec3 vexPos : vexPositions) {
                if (target.position().distanceToSqr(vexPos) <= VEX_RADIUS * VEX_RADIUS) {
                    inRange = true;
                    break;
                }
            }

            if (inRange) {
                // 每秒掉2点理智（1点=0.01 mood）
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(target);
                mood.addMood(-0.02f);
                // 缓慢I效果（等级0 = 缓慢I），持续2秒（40tick），每秒刷新
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
            }
        }
    }

    /**
     * 清除所有恼鬼实体
     */
    public void discardVexes() {
        if (player instanceof ServerPlayer sp) {
            ServerLevel level = sp.serverLevel();
            for (UUID uuid : vexUuids) {
                if (level.getEntity(uuid) != null) {
                    level.getEntity(uuid).discard();
                }
            }
        }
        vexUuids.clear();
        vexDurationTimer = 0;
    }

    // ==================== 被动：不死图腾 ====================

    /**
     * 尝试触发复活（在死亡事件中调用）
     * @return true 表示触发复活，阻止正常死亡流程
     */
    public boolean tryRevival(ServerPlayer serverPlayer) {
        if (revivalUsed) return false;
        if (waitingForRevival) return false;

        revivalUsed = true;
        waitingForRevival = true;
        revivalTimer = REVIVAL_WAIT_TIME;
        // 记录死亡位置
        deathX = serverPlayer.getX();
        deathY = serverPlayer.getY();
        deathZ = serverPlayer.getZ();
        sync();
        return true;
    }

    /**
     * 执行复活
     */
    private void performRevival(ServerPlayer serverPlayer, ServerLevel level) {
        waitingForRevival = false;
        revivalTimer = 0;
        invincibleTimer = INVINCIBLE_DURATION;

        // 清除原来的尸体
        clearBody(level, serverPlayer.getUUID());

        // 复活玩家（无声音）
        GameUtils.revivePlayer(serverPlayer, deathX, deathY, deathZ);

        // 给予无敌效果（抗性提升V = 完全免伤）
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, INVINCIBLE_DURATION, 4, false, false, false));
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.huanmozhe.revived")
                        .withStyle(ChatFormatting.GOLD), true);
        sync();
    }

    /**
     * 清除玩家的尸体实体
     */
    private void clearBody(ServerLevel level, UUID playerUuid) {
        var bodies = level.getEntities(
                io.wifi.starrailexpress.index.TMMEntities.PLAYER_BODY,
                body -> playerUuid.equals(body.getPlayerUuid()));
        for (var body : bodies) {
            body.discard();
        }
    }

    /**
     * 是否处于无敌状态
     */
    public boolean isInvincible() {
        return invincibleTimer > 0;
    }

    // ==================== 技能存储 ====================

    /**
     * 购买技能存储
     * @return true 购买成功
     */
    public boolean buySkillStorage() {
        if (skillStorage >= MAX_SKILL_STORAGE) return false;
        skillStorage++;
        sync();
        return true;
    }

    // ==================== NBT 同步 ====================

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("revivalUsed", revivalUsed);
        tag.putBoolean("waitingForRevival", waitingForRevival);
        tag.putInt("invincibleTimer", invincibleTimer);
        tag.putInt("skillStorage", skillStorage);
        tag.putBoolean("spikeCasting", spikeCasting);
        tag.putInt("vexDurationTimer", vexDurationTimer);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        revivalUsed = tag.getBoolean("revivalUsed");
        waitingForRevival = tag.getBoolean("waitingForRevival");
        invincibleTimer = tag.getInt("invincibleTimer");
        skillStorage = tag.getInt("skillStorage");
        spikeCasting = tag.getBoolean("spikeCasting");
        vexDurationTimer = tag.getInt("vexDurationTimer");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    // ==================== 静态事件注册 ====================

    static {
        // 被动：不死图腾 - 死亡后15秒复活（允许死亡，然后计时复活）
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (!gameWorld.isRunning()) return;
            if (!gameWorld.isRole(victim, ModRoles.HUANMOZHE)) return;

            HuanmozhePlayerComponent comp = KEY.get(victim);
            if (comp.revivalUsed || comp.waitingForRevival) return;
            if (victim instanceof ServerPlayer sp) {
                // 玩家已经死亡，开始复活计时
                comp.revivalUsed = true;
                comp.waitingForRevival = true;
                comp.revivalTimer = REVIVAL_WAIT_TIME;
                comp.deathX = sp.getX();
                comp.deathY = sp.getY();
                comp.deathZ = sp.getZ();
                comp.sync();
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.huanmozhe.revival_triggered")
                                .withStyle(ChatFormatting.GOLD), true);
            }
        });

        // 复活后10秒无敌：拦截死亡事件
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
            if (!gameWorld.isRunning()) return true;
            if (!gameWorld.isRole(victim, ModRoles.HUANMOZHE)) return true;

            HuanmozhePlayerComponent comp = KEY.get(victim);
            // 无敌期间阻止死亡
            if (comp.invincibleTimer > 0) {
                return false;
            }
            return true;
        });

        // 游戏结束时清除恼鬼
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (ServerPlayer p : serverLevel.players()) {
                SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(serverLevel);
                if (gw.isRole(p, ModRoles.HUANMOZHE)) {
                    HuanmozhePlayerComponent comp = KEY.get(p);
                    comp.discardVexes();
                }
            }
        });
    }
}
