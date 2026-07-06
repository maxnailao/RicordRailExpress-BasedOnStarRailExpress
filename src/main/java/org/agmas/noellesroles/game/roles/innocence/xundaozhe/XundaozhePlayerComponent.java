package org.agmas.noellesroles.game.roles.innocence.xundaozhe;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 殉道者组件
 *
 * 技能：花费300金币，对准尸体按G键，保持静止5秒后复活目标玩家，殉道者同时死亡（生命耗尽）。
 * 被动：当殉道者存活时，旁观者模式下无法查看其他玩家的身份（与阴谋家同机制）。
 */
public class XundaozhePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 */
    public static final ComponentKey<XundaozhePlayerComponent> KEY = ModComponents.XUNDAOZHE;

    // ==================== 常量定义 ====================

    /** 复活持续时间（5秒 = 100 tick） */
    public static final int REVIVAL_DURATION = 100;

    /** 复活所需金币 */
    public static final int REVIVAL_COST = 300;

    /** 殉道者自身移动阈值（超过则中断复活） */
    public static final double MOVEMENT_THRESHOLD = 0.05;

    /** 最大射线检测距离 */
    public static final double MAX_RAYCAST_DISTANCE = 3.0;

    /** 生命耗尽死因 */
    public static final net.minecraft.resources.ResourceLocation LIFE_EXHAUSTED = Noellesroles.id("life_exhausted");

    // ==================== 状态变量 ====================

    private final Player player;

    /** 是否正在复活 */
    public boolean isReviving = false;

    /** 复活目标的UUID */
    public UUID revivalTarget = null;

    /** 复活目标名称（用于显示） */
    public String revivalTargetName = "";

    /** 已持续复活时间（tick） */
    public int revivalTicks = 0;

    /** 冷却时间（tick） */
    public int cooldown = 0;

    /** 本局是否已使用过复活 */
    public boolean hasRevived = false;

    /** 殉道者开始复活时的位置 */
    private Vec3 startRevivalPos = null;

    public XundaozhePlayerComponent(Player player) {
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
        this.isReviving = false;
        this.revivalTarget = null;
        this.revivalTargetName = "";
        this.revivalTicks = 0;
        this.cooldown = 0;
        this.hasRevived = false;
        this.startRevivalPos = null;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    // ==================== 技能逻辑 ====================

    /**
     * 尝试开始复活（由 RoleSkill 回调触发）
     *
     * @return 是否成功触发技能（进入冷却）
     */
    public boolean tryStartRevival() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        // 验证角色
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.XUNDAOZHE)) {
            return false;
        }

        // 旁观者无法使用
        if (player.isSpectator()) {
            return false;
        }

        // 存活检查
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        // 技能不可用检查
        if (!gameWorld.isSkillAvailable) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.xundaozhe.skill_not_available")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 本局是否已使用过
        if (hasRevived) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.xundaozhe.already_used")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 冷却检查
        if (cooldown > 0) {
            return false;
        }

        // 金币检查
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < REVIVAL_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.xundaozhe.no_coins")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 射线检测尸体
        HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(
                player,
                entity -> entity instanceof PlayerBodyEntity,
                MAX_RAYCAST_DISTANCE);

        if (!(hitResult instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof PlayerBodyEntity body)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.xundaozhe.no_target")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 获取尸体对应的玩家
        UUID targetUuid = body.getPlayerUuid();
        if (targetUuid == null) {
            return false;
        }

        ServerPlayer targetPlayer = (ServerPlayer) serverPlayer.serverLevel().getPlayerByUUID(targetUuid);
        if (targetPlayer == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.xundaozhe.target_lost")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 目标必须是旁观者（已死亡）
        if (!targetPlayer.isSpectator()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.xundaozhe.target_not_dead")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 开始复活
        this.isReviving = true;
        this.revivalTarget = targetUuid;
        this.revivalTargetName = targetPlayer.getName().getString();
        this.revivalTicks = 0;
        this.startRevivalPos = player.position();

        // 扣除金币
        shop.addToBalance(-REVIVAL_COST);

        // 通知殉道者
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.xundaozhe.revive_started", revivalTargetName)
                        .withStyle(ChatFormatting.GOLD), true);

        sync();
        return true;
    }

    /**
     * 中断复活
     */
    private void cancelRevival(String reasonKey) {
        if (!isReviving) return;

        isReviving = false;
        revivalTarget = null;
        revivalTargetName = "";
        revivalTicks = 0;
        startRevivalPos = null;

        if (reasonKey != null && player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable(reasonKey).withStyle(ChatFormatting.RED), true);
        }
        sync();
    }

    /**
     * 完成复活
     */
    private void completeRevival() {
        if (!(player instanceof ServerPlayer martyr)) return;
        if (revivalTarget == null) return;

        ServerLevel serverLevel = martyr.serverLevel();
        ServerPlayer targetPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(revivalTarget);

        if (targetPlayer == null) {
            cancelRevival("message.noellesroles.xundaozhe.target_lost");
            return;
        }

        // 复活目标玩家
        double x = martyr.getX();
        double y = martyr.getY();
        double z = martyr.getZ();

        // 清除死亡惩罚并复活
        GameUtils.revivePlayer(targetPlayer, x, y, z);
        targetPlayer.setHealth(targetPlayer.getMaxHealth());

        // 移除尸体
        PlayerBodyEntity bodyEntity = GameUtils.findPlayerBodyEntity(targetPlayer);
        if (bodyEntity != null) {
            bodyEntity.remove(Entity.RemovalReason.DISCARDED);
        }

        // 重置语音
        TrainVoicePlugin.resetPlayer(targetPlayer.getUUID());

        // 通知所有玩家
        String targetName = this.revivalTargetName;
        serverLevel.players().forEach(p -> {
            p.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            p.displayClientMessage(
                    Component.translatable("message.noellesroles.xundaozhe.revive_broadcast",
                                    martyr.getName().getString(), targetName)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        });

        // 标记已使用
        this.hasRevived = true;
        this.isReviving = false;
        this.revivalTarget = null;
        this.revivalTargetName = "";
        this.revivalTicks = 0;
        this.startRevivalPos = null;
        this.sync();

        // 殉道者死亡（生命耗尽）— 延迟1 tick 确保复活完全完成
        // 使用 forceKillPlayer 跳过 AllowPlayerDeath 拦截
        serverLevel.getServer().execute(() -> {
            GameUtils.killPlayer(martyr, true, null, LIFE_EXHAUSTED);
        });
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.XUNDAOZHE)) {
            return;
        }

        // 减少冷却
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 200 == 0 || cooldown == 0) {
                sync();
            }
        }

        // 处理复活进度
        if (isReviving && revivalTarget != null) {
            // 检查殉道者是否还存活
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                cancelRevival(null);
                return;
            }

            // 检查殉道者是否移动
            if (startRevivalPos != null) {
                double moved = player.position().distanceTo(startRevivalPos);
                if (moved > MOVEMENT_THRESHOLD) {
                    cancelRevival("message.noellesroles.xundaozhe.revive_failed_moved");
                    return;
                }
            }

            // 检查目标是否仍在线
            ServerLevel serverLevel = (ServerLevel) player.level();
            ServerPlayer targetPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(revivalTarget);
            if (targetPlayer == null) {
                cancelRevival("message.noellesroles.xundaozhe.target_lost");
                return;
            }

            // 增加复活时间
            revivalTicks++;

            // 每秒同步并显示进度
            if (revivalTicks % 20 == 0) {
                int seconds = revivalTicks / 20;
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.xundaozhe.revive_progress",
                                    revivalTargetName, seconds, REVIVAL_DURATION / 20)
                                    .withStyle(ChatFormatting.GOLD), true);
                }
                sync();
            }

            // 检查是否完成
            if (revivalTicks >= REVIVAL_DURATION) {
                completeRevival();
            }
        }
    }

    @Override
    public void clientTick() {
        // 客户端冷却倒计时
        if (cooldown > 1) {
            cooldown--;
        }
        // 客户端复活进度（用于HUD显示）
        if (isReviving) {
            revivalTicks++;
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isReviving", this.isReviving);
        tag.putInt("revivalTicks", this.revivalTicks);
        tag.putString("revivalTargetName", this.revivalTargetName);
        tag.putInt("cooldown", this.cooldown);
        tag.putBoolean("hasRevived", this.hasRevived);
        if (this.revivalTarget != null) {
            tag.putUUID("revivalTarget", this.revivalTarget);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isReviving = tag.contains("isReviving") && tag.getBoolean("isReviving");
        this.revivalTicks = tag.contains("revivalTicks") ? tag.getInt("revivalTicks") : 0;
        this.revivalTargetName = tag.contains("revivalTargetName") ? tag.getString("revivalTargetName") : "";
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.hasRevived = tag.contains("hasRevived") && tag.getBoolean("hasRevived");
        if (tag.hasUUID("revivalTarget")) {
            this.revivalTarget = tag.getUUID("revivalTarget");
        } else {
            this.revivalTarget = null;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
