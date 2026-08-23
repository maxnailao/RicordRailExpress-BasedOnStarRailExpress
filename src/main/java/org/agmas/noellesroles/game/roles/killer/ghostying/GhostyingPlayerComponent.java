package org.agmas.noellesroles.game.roles.killer.ghostying;

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
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 鬼影组件
 *
 * 管理"鬼影步"技能：
 * - 按下技能键向方向键方向瞬移 4 格，原地留下残影假人
 * - 释放后 0.7 秒现身（隐身结束），残影在现身后消失
 * - 技能释放无冷却，但必须现身后才可再次使用
 * - 最多储备 5 次技能，每 20 秒回转 1 次存储（注意：这是存储回转，不是技能冷却）
 */
public class GhostyingPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<GhostyingPlayerComponent> KEY = ModComponents.GHOSTYING;

    // ==================== 常量定义 ====================

    /** 最大技能储备次数 */
    public static final int MAX_CHARGES = 5;

    /** 技能存储回转时间（20秒 = 400 tick），每回转一次补充 1 次储备 */
    public static final int RECHARGE_TICKS = 20 * 20;

    /** 隐身边长持续时间（0.7秒 = 14 tick），结束后现身 */
    public static final int HIDDEN_TICKS = 14;

    /** 瞬移距离（格） */
    public static final double TELEPORT_DISTANCE = 4.0;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前技能储备次数 */
    public int charges = MAX_CHARGES;

    /** 存储回转剩余 tick（0 表示未在回转或已回满） */
    public int rechargeTicks = 0;

    /** 现身剩余 tick（>0 表示仍处于隐身状态，不可再次释放技能） */
    public int hiddenTicks = 0;

    /** 当前残影实体 UUID */
    @Nullable
    public UUID afterimageUuid = null;

    public GhostyingPlayerComponent(Player player) {
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
        // 防御性守卫：隐身期间 init 可能被开局分波分配事件重复调用，
        // 此时不清零计时器，避免技能状态被误杀（彻底清理交给 clear()）
        if (hiddenTicks > 0) {
            return;
        }
        this.charges = MAX_CHARGES;
        this.rechargeTicks = 0;
        discardAfterimage();
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.INVISIBILITY);
        }
        sync();
    }

    @Override
    public void clear() {
        this.charges = MAX_CHARGES;
        this.rechargeTicks = 0;
        this.hiddenTicks = 0;
        discardAfterimage();
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.INVISIBILITY);
        }
        sync();
    }

    /**
     * 使用鬼影步技能（由技能系统调用）
     *
     * @return 是否成功触发（返回 true 才消耗储备）
     */
    public boolean useBlink(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }

        // 验证角色
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorld.getRole(sp.getUUID()) != ModRoles.GHOSTYING) {
            return false;
        }

        // 必须现身后才可再次使用
        if (hiddenTicks > 0) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ghostying.still_hidden")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 检查技能储备
        if (charges <= 0) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ghostying.no_charges",
                            (rechargeTicks + 19) / 20).withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 计算方向键方向（无输入时退回视角方向）
        Vec3 dir = getInputDirection(sp);

        // 在 4 格内寻找无碰撞的落点
        Vec3 target = findSafeTeleportPos(sp, dir);
        if (target == null) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ghostying.blocked")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        ServerLevel level = sp.serverLevel();
        Vec3 from = sp.position();
        float yaw = sp.getYRot();
        float pitch = sp.getXRot();

        // 原地留下残影假人
        GhostyingAfterimageEntity afterimage =
                new GhostyingAfterimageEntity(ModEntities.GHOSTYING_AFTERIMAGE, level);
        afterimage.setPos(from.x, from.y, from.z);
        afterimage.setYRot(yaw);
        afterimage.setXRot(pitch);
        afterimage.setOwnerUuid(sp.getUUID());
        level.addFreshEntity(afterimage);
        this.afterimageUuid = afterimage.getUUID();

        // 起点/终点粒子与音效
        spawnBlinkParticles(level, from.x, from.y, from.z);
        level.playSound(null, sp.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.2F);

        // 瞬移
        sp.teleportTo(level, target.x, target.y, target.z, yaw, pitch);
        spawnBlinkParticles(level, target.x, target.y, target.z);
        level.playSound(null, target.x, target.y, target.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.2F);

        // 进入隐身状态，0.7 秒后现身
        this.hiddenTicks = HIDDEN_TICKS;
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, HIDDEN_TICKS + 2, 0, false, false, true));

        // 消耗储备并开始存储回转
        this.charges--;
        if (this.charges < MAX_CHARGES && this.rechargeTicks <= 0) {
            this.rechargeTicks = RECHARGE_TICKS;
        }

        sync();
        return true;
    }

    /**
     * 计算方向键输入方向（水平）。
     * 服务端通过移动包同步 zza/xxa 输入；无输入时退回视角朝向。
     */
    private Vec3 getInputDirection(ServerPlayer sp) {
        float yawRad = sp.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yawRad);
        float cos = Mth.cos(yawRad);
        // 与原版 LivingEntity 输入向量换算一致：
        // x = xxa * cos - zza * sin，z = zza * cos + xxa * sin
        double x = sp.xxa * cos - sp.zza * sin;
        double z = sp.zza * cos + sp.xxa * sin;
        if (x == 0 && z == 0) {
            // 未按方向键时沿视角方向
            x = -sin;
            z = cos;
        }
        return new Vec3(x, 0, z).normalize();
    }

    /**
     * 沿给定方向在 {@link #TELEPORT_DISTANCE} 格内寻找最远的无碰撞落点。
     *
     * @return 落点；若连 1 格都无法移动则返回 null
     */
    @Nullable
    private Vec3 findSafeTeleportPos(ServerPlayer sp, Vec3 dir) {
        ServerLevel level = sp.serverLevel();
        AABB box = sp.getBoundingBox();
        Vec3 from = sp.position();
        for (double d = TELEPORT_DISTANCE; d >= 1.0; d -= 0.25) {
            double dx = dir.x * d;
            double dz = dir.z * d;
            AABB targetBox = box.move(dx, 0, dz);
            if (level.noCollision(sp, targetBox)) {
                return from.add(dx, 0, dz);
            }
        }
        return null;
    }

    /** 生成瞬移粒子 */
    private void spawnBlinkParticles(ServerLevel level, double x, double y, double z) {
        for (int i = 0; i < 20; i++) {
            level.sendParticles(ParticleTypes.PORTAL,
                    x + (level.random.nextDouble() - 0.5) * 0.8,
                    y + level.random.nextDouble() * 1.8,
                    z + (level.random.nextDouble() - 0.5) * 0.8,
                    1, 0, 0, 0, 0.3);
        }
    }

    /** 现身：移除隐身并清除残影 */
    private void reveal() {
        this.hiddenTicks = 0;
        player.removeEffect(MobEffects.INVISIBILITY);
        discardAfterimage();
        sync();
    }

    /** 移除当前残影实体 */
    private void discardAfterimage() {
        if (afterimageUuid == null) {
            return;
        }
        if (player.level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(afterimageUuid) instanceof GhostyingAfterimageEntity afterimage) {
            afterimage.discard();
        }
        this.afterimageUuid = null;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }

        // 现身倒计时仅依赖实体状态，不做游戏阶段复判，避免技能状态被误杀
        if (hiddenTicks > 0) {
            hiddenTicks--;
            if (hiddenTicks <= 0) {
                reveal();
            }
        }

        // 角色与存活守卫（充能回转与 HUD）
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.GHOSTYING)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            // 死亡/旁观：清理残留状态
            if (afterimageUuid != null || player.hasEffect(MobEffects.INVISIBILITY)) {
                hiddenTicks = 0;
                player.removeEffect(MobEffects.INVISIBILITY);
                discardAfterimage();
                sync();
            }
            return;
        }

        // 存储回转：每 20 秒补充 1 次储备，回满后停止
        if (charges < MAX_CHARGES) {
            if (rechargeTicks > 0) {
                rechargeTicks--;
                if (rechargeTicks <= 0) {
                    charges++;
                    if (charges < MAX_CHARGES) {
                        rechargeTicks = RECHARGE_TICKS;
                    }
                    sync();
                }
            } else {
                rechargeTicks = RECHARGE_TICKS;
            }
        } else if (rechargeTicks > 0) {
            rechargeTicks = 0;
        }

        // ActionBar 状态提示
        sendActionBar(sp);
    }

    /** 发送 ActionBar 状态信息 */
    private void sendActionBar(ServerPlayer sp) {
        Component actionBar;
        if (hiddenTicks > 0) {
            actionBar = Component.translatable("message.noellesroles.ghostying.hidden")
                    .withStyle(ChatFormatting.GRAY);
        } else if (charges < MAX_CHARGES) {
            actionBar = Component.translatable("message.noellesroles.ghostying.actionbar_recharging",
                            charges, MAX_CHARGES, (rechargeTicks + 19) / 20)
                    .withStyle(ChatFormatting.DARK_AQUA);
        } else {
            actionBar = Component.translatable("message.noellesroles.ghostying.actionbar_full",
                            charges, MAX_CHARGES)
                    .withStyle(ChatFormatting.AQUA);
        }
        sp.sendSystemMessage(actionBar, true);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("charges", charges);
        tag.putInt("rechargeTicks", rechargeTicks);
        tag.putInt("hiddenTicks", hiddenTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.charges = tag.getInt("charges");
        this.rechargeTicks = tag.getInt("rechargeTicks");
        this.hiddenTicks = tag.getInt("hiddenTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
