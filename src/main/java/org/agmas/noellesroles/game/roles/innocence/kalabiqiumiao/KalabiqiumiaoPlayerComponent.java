package org.agmas.noellesroles.game.roles.innocence.kalabiqiumiao;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 纸片人组件 - 技能「弦化」
 *
 * <p>弦化期间：
 * <ul>
 * <li>玩家模型与判定箱变为纸片人（沿朝向压扁）</li>
 * <li>允许自由切换第一/第三人称视角（见 {@code KalabiqiumiaoClientHandle}）</li>
 * <li>获得缓降与跳跃提升 II</li>
 * <li>持续 30 秒，冷却 120 秒，结束后强制切回第一人称</li>
 * </ul>
 */
public class KalabiqiumiaoPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<KalabiqiumiaoPlayerComponent> KEY = ModComponents.KALABIQIUMIAO;
    public static final ResourceLocation SKILL_ID = Noellesroles.id("kalabiqiumiao_stringify");

    /** 弦化持续时间（tick）：30 秒 */
    public static final int ACTIVE_TICKS = 30 * 20;
    /** 技能冷却（tick）：120 秒 */
    public static final int COOLDOWN_TICKS = 120 * 20;
    /** 纸片人厚度（沿玩家侧面方向，与渲染压扁方向一致） */
    public static final double PAPER_THICKNESS = 0.125D;
    /** 纸片人前后宽度（与普通玩家一致） */
    public static final double PAPER_WIDTH = 0.6D;
    /** 渲染压扁比例（本地 X 轴，左右方向） */
    public static final float PAPER_RENDER_SCALE = 0.1F;

    private final Player player;
    /** 弦化剩余时间（tick），0 表示未弦化 */
    public int activeTicks;

    public KalabiqiumiaoPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        // 纸片人压扁形态需要对所有玩家可见
        return true;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        // 注意：角色分配事件（ModdedRoleAssigned）在开局时会多次触发 init（分波分配职业/修饰符），
        // 不能无条件清零，否则弦化刚激活就会被重置；
        // 开局/结束的彻底清理由 clear() 保证（框架在 onStartGame/onEndGame 先调 clear）。
        if (activeTicks <= 0) {
            sync();
        }
    }

    @Override
    public void clear() {
        activeTicks = 0;
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.SLOW_FALLING);
            sp.removeEffect(MobEffects.JUMP);
            restoreNormalBox(sp);
        }
        sync();
    }

    public boolean isActive() {
        return activeTicks > 0;
    }

    /** 技能入口：开始弦化。返回 true 才会消耗冷却 */
    public boolean useSkill(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.KALABIQIUMIAO)) {
            return false;
        }
        if (activeTicks > 0) {
            return false;
        }

        activeTicks = ACTIVE_TICKS;
        applyPaperEffects();
        refreshPaperBox(sp);
        spawnPaperParticles(sp);
        sync();
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(Component.translatable("message.noellesroles.kalabiqiumiao.start")
                .withStyle(ChatFormatting.WHITE), true);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (activeTicks <= 0) {
            return;
        }
        // 仅在玩家死亡/旁观时终止；不绑定回合状态与角色判定，
        // 避免开局安全期/状态切换瞬间误杀弦化状态（回合结束由框架 clear 清理）
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            finishActive(sp, false);
            return;
        }

        // 每秒刷新一次缓降与跳跃提升，并补充纸片粒子便于观察
        if (sp.level().getGameTime() % 20 == 0) {
            applyPaperEffects();
            spawnPaperParticles(sp);
        }
        // 判定箱跟随玩家朝向实时更新
        refreshPaperBox(sp);

        activeTicks--;
        if (activeTicks <= 0) {
            finishActive(sp, true);
            return;
        }
        if (activeTicks % 20 == 0) {
            sync();
        }
    }

    /** 结束弦化：移除效果、还原判定箱并同步 */
    private void finishActive(ServerPlayer sp, boolean naturalEnd) {
        activeTicks = 0;
        sp.removeEffect(MobEffects.SLOW_FALLING);
        sp.removeEffect(MobEffects.JUMP);
        restoreNormalBox(sp);
        sync();
        if (naturalEnd) {
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
            sp.displayClientMessage(Component.translatable("message.noellesroles.kalabiqiumiao.end")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** 缓降 + 跳跃提升 II（短持续时间，由 serverTick 周期续杯） */
    private void applyPaperEffects() {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false, false));
    }

    /** 纸片化粒子反馈（服务端生成，所有客户端可见） */
    private void spawnPaperParticles(ServerPlayer sp) {
        sp.serverLevel().sendParticles(ParticleTypes.CLOUD,
                sp.getX(), sp.getY() + 1.0D, sp.getZ(), 12, 0.2D, 0.8D, 0.2D, 0.01D);
    }

    /** 立即将判定箱更新为纸片人判定箱 */
    public static void refreshPaperBox(ServerPlayer sp) {
        sp.setBoundingBox(createPaperBoundingBox(sp));
    }

    /** 还原为原版判定箱 */
    public static void restoreNormalBox(ServerPlayer sp) {
        sp.setBoundingBox(sp.getDimensions(sp.getPose()).makeBoundingBox(sp.position()));
    }

    /**
     * 纸片人判定箱：沿玩家侧面方向（垂直于朝向）压扁（厚度 {@link #PAPER_THICKNESS}），
     * 前后宽度与高度保持与当前姿态一致，与渲染压扁方向保持一致。
     */
    public static AABB createPaperBoundingBox(Player p) {
        Vec3 pos = p.position();
        float height = p.getDimensions(p.getPose()).height();
        float yawRad = p.getYRot() * Mth.DEG_TO_RAD;
        // 玩家朝向（水平前向）
        double fx = -Mth.sin(yawRad);
        double fz = Mth.cos(yawRad);
        // 侧面轴（垂直于朝向，压扁方向）
        double rx = -fz;
        double rz = fx;
        double halfThickness = PAPER_THICKNESS * 0.5D;
        double halfWidth = PAPER_WIDTH * 0.5D;
        AABB box = new AABB(pos.x, pos.y, pos.z, pos.x, pos.y, pos.z);
        box = box.expandTowards(rx * halfThickness, 0.0D, rz * halfThickness)
                .expandTowards(-rx * halfThickness, 0.0D, -rz * halfThickness)
                .expandTowards(fx * halfWidth, 0.0D, fz * halfWidth)
                .expandTowards(-fx * halfWidth, 0.0D, -fz * halfWidth)
                .expandTowards(0.0D, height, 0.0D);
        return box;
    }

    /**
     * 任意玩家是否处于弦化状态（服务端与客户端均可调用）。
     * 实体构造期间（如其他模组创建的 FakePlayer）CCA 组件尚未附着，
     * 访问组件可能抛 NPE，这里防御性捕获后按未弦化处理。
     */
    public static boolean isPaperActive(Player p) {
        try {
            return KEY.maybeGet(p).map(KalabiqiumiaoPlayerComponent::isActive).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void clientTick() {
        // 客户端镜像递减，保障同步异常时判定也能按时过期
        if (activeTicks > 0) {
            activeTicks--;
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("activeTicks", activeTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        activeTicks = tag.getInt("activeTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
