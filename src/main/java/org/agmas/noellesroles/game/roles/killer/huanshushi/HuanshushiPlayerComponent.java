package org.agmas.noellesroles.game.roles.killer.huanshushi;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.HolderLookup;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.entity.IllusionDecoyEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 幻术师组件 - 杀手阵营
 *
 * <p>技能一：在自身半径6格范围内随机位置释放4个同自身皮肤举刀的假人向最近的平民玩家靠拢，假人存在10s，被击中后释放闪光弹。
 * <p>技能二：在自身周围释放4个假人跟随，假人与你的行动一致（包括视角移动），被击中后释放闪光弹，击中者失明10s。
 * <p>技能三：在你的位置释放一个和你皮肤一样的假人原地不动，被击中后释放闪光弹，使半径10格内玩家受到黑暗I+失明I+缓慢I效果8s，并扣除25%理智值。
 * <p>三个技能共用CD 30s。
 * <p>被动：免疫霉运效果（minecraft:unluck）。
 */
public class HuanshushiPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final int SHARED_COOLDOWN_TICKS = 30 * 20; // 30秒
    public static final int DECOY_LIFETIME_TICKS = 10 * 20;  // 10秒

    private final Player player;

    /** 当前活跃的假人实体UUID列表 */
    private final List<UUID> activeDecoys = new ArrayList<>();

    /** 技能二的假人UUID列表（需要跟随玩家） */
    private final List<UUID> followDecoys = new ArrayList<>();

    /** 延迟设置共享CD标记（等待框架 markSkillUsed 完成后再设置） */
    private boolean pendingSharedCooldown = false;

    public HuanshushiPlayerComponent(Player player) {
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
        ModComponents.HUANSHUSHI.sync(this.player);
    }

    @Override
    public void init() {
        activeDecoys.clear();
        followDecoys.clear();
        pendingSharedCooldown = false;
        sync();
    }

    @Override
    public void clear() {
        // 清理所有活跃假人
        if (player.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : activeDecoys) {
                var entity = serverLevel.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        activeDecoys.clear();
        followDecoys.clear();
        init();
    }

    /**
     * 检查共享冷却是否就绪
     */
    public boolean isCooldownReady() {
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        return ability.cooldown <= 0;
    }

    /**
     * 标记需要设置共享CD（延迟到下一tick，等待框架 markSkillUsed 完成）
     */
    public void markSharedCooldown() {
        pendingSharedCooldown = true;
    }

    /**
     * 实际执行共享冷却设置
     */
    private void applySharedCooldown() {
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        ability.setCooldown(SHARED_COOLDOWN_TICKS);
        ability.setSkillCooldown(SRE.id("huanshushi_skill1"), SHARED_COOLDOWN_TICKS);
        ability.setSkillCooldown(SRE.id("huanshushi_skill2"), SHARED_COOLDOWN_TICKS);
        ability.setSkillCooldown(SRE.id("huanshushi_skill3"), SHARED_COOLDOWN_TICKS);
        ability.sync();
    }

    /**
     * 技能一：释放4个假人向最近平民靠拢
     */
    public boolean useSkill1() {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;
        if (!isCooldownReady()) return false;

        ServerLevel serverLevel = serverPlayer.serverLevel();
        UUID skinUuid = serverPlayer.getUUID();

        for (int i = 0; i < 4; i++) {
            // 在自身半径6格范围内随机位置
            double angle = Math.toRadians(i * 90 + serverPlayer.getRandom().nextFloat() * 60 - 30);
            double radius = 2.0 + serverPlayer.getRandom().nextDouble() * 4.0; // 2~6格
            double x = player.getX() + Math.sin(angle) * radius;
            double z = player.getZ() + Math.cos(angle) * radius;
            double y = player.getY();

            IllusionDecoyEntity decoy = new IllusionDecoyEntity(ModEntities.ILLUSION_DECOY, serverLevel);
            decoy.setPos(x, y, z);
            decoy.setup(serverPlayer, skinUuid, IllusionDecoyEntity.MODE_CHASE, DECOY_LIFETIME_TICKS, 0.0F, true);
            // 技能一：若手持刀则设置举刀姿态（举枪由 BipedEntityModelMixin 自动处理）
            if (serverPlayer.getMainHandItem().is(io.wifi.starrailexpress.index.TMMItems.KNIFE)) {
                decoy.setPoseFlags(1); // bit0 = 举刀
            }
            serverLevel.addFreshEntity(decoy);
            activeDecoys.add(decoy.getUUID());
        }

        markSharedCooldown();
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.huanshushi.skill1_used")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
                true);
        sync();
        return true;
    }

    /**
     * 技能二：释放4个跟随假人
     */
    public boolean useSkill2() {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;
        if (!isCooldownReady()) return false;

        ServerLevel serverLevel = serverPlayer.serverLevel();
        UUID skinUuid = serverPlayer.getUUID();

        // 清理旧的跟随假人
        cleanupFollowDecoys(serverLevel);

        for (int i = 0; i < 4; i++) {
            float offsetAngle = i * 90.0F;
            double x = player.getX() + Math.sin(Math.toRadians(player.getYRot() + offsetAngle)) * 2.0;
            double z = player.getZ() + Math.cos(Math.toRadians(player.getYRot() + offsetAngle)) * 2.0;
            double y = player.getY();

            IllusionDecoyEntity decoy = new IllusionDecoyEntity(ModEntities.ILLUSION_DECOY, serverLevel);
            decoy.setPos(x, y, z);
            // 跟随假人生存时间与共享CD一致，物品不锁定（动态同步）
            decoy.setup(serverPlayer, skinUuid, IllusionDecoyEntity.MODE_FOLLOW, SHARED_COOLDOWN_TICKS, offsetAngle, false);
            serverLevel.addFreshEntity(decoy);
            activeDecoys.add(decoy.getUUID());
            followDecoys.add(decoy.getUUID());
        }

        markSharedCooldown();
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.huanshushi.skill2_used")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
                true);
        sync();
        return true;
    }

    /**
     * 技能三：释放1个原地不动的假人
     */
    public boolean useSkill3() {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;
        if (!isCooldownReady()) return false;

        ServerLevel serverLevel = serverPlayer.serverLevel();
        UUID skinUuid = serverPlayer.getUUID();

        IllusionDecoyEntity decoy = new IllusionDecoyEntity(ModEntities.ILLUSION_DECOY, serverLevel);
        decoy.setPos(player.getX(), player.getY(), player.getZ());
        decoy.setYRot(player.getYRot());
        // 技能三：幻影陷阱持续到游戏结束，物品锁定
        decoy.setup(serverPlayer, skinUuid, IllusionDecoyEntity.MODE_STATIONARY, Integer.MAX_VALUE / 2, 0.0F, true);
        serverLevel.addFreshEntity(decoy);
        activeDecoys.add(decoy.getUUID());

        markSharedCooldown();
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.huanshushi.skill3_used")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY),
                true);
        sync();
        return true;
    }

    /**
     * 假人被击中时回调（由 IllusionDecoyEntity 调用）
     */
    public void onDecoyHit(int mode) {
        // 从活跃列表中移除（实体已在 IllusionDecoyEntity 中 discard）
        sync();
    }

    /**
     * 清理跟随假人
     */
    private void cleanupFollowDecoys(ServerLevel serverLevel) {
        for (UUID uuid : followDecoys) {
            var entity = serverLevel.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        // 先从 activeDecoys 中移除，再清空 followDecoys
        activeDecoys.removeAll(followDecoys);
        followDecoys.clear();
    }

    /**
     * 被动：免疫霉运（unluck）与闪光弹致盲（RAID_OMEN）效果
     */
    public void applyPassiveImmunities() {
        if (player.hasEffect(MobEffects.BAD_OMEN)) {
            player.removeEffect(MobEffects.BAD_OMEN);
        }
        // 检查并移除 unluck 效果
        var unluckEffect = player.getEffect(MobEffects.UNLUCK);
        if (unluckEffect != null) {
            player.removeEffect(MobEffects.UNLUCK);
        }
        // 免疫闪光弹致盲（RAID_OMEN 会使屏幕渐黑）
        if (player.hasEffect(MobEffects.RAID_OMEN)) {
            player.removeEffect(MobEffects.RAID_OMEN);
        }
    }

    @Override
    public void serverTick() {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 角色判定：仅幻术师生效（参照血仇者组件模式）
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.HUANSHUSHI)) return;

        // 延迟设置共享CD（在框架 markSkillUsed 之后执行）
        if (pendingSharedCooldown) {
            applySharedCooldown();
            pendingSharedCooldown = false;
        }

        // 被动：每 tick 检查并移除霉运与闪光弹致盲
        applyPassiveImmunities();

        // 清理已失效的假人 UUID
        if (player.level() instanceof ServerLevel serverLevel) {
            activeDecoys.removeIf(uuid -> {
                var entity = serverLevel.getEntity(uuid);
                return entity == null || !entity.isAlive();
            });
            followDecoys.removeIf(uuid -> {
                var entity = serverLevel.getEntity(uuid);
                return entity == null || !entity.isAlive();
            });
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 假人状态不需要持久化
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 假人状态不需要持久化
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 同步活跃假人数量给客户端（可用于 HUD）
        tag.putInt("activeDecoyCount", activeDecoys.size());
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 客户端接收同步数据
    }
}
