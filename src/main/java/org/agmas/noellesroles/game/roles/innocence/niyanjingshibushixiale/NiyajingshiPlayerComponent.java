package org.agmas.noellesroles.game.roles.innocence.niyanjingshibushixiale;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 盲人组件
 *
 * 管理盲人的被动效果：
 * - 被动1：存活时持续获得黑暗效果（3秒），同时客户端叠加黑白视角（ImmersiveFilterShader 的 nostalgist_gray pass），旁观时均不生效
 * - 被动2：脚步声纹检测（在 AgentListenStepHandler 中处理，不需要蹲下）
 */
public class NiyajingshiPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<NiyajingshiPlayerComponent> KEY = ModComponents.NIYAJINGSHIBUSHIXIALE;

    /** 黑暗效果持续时间（tick）：3秒 = 60 tick */
    private static final int DARKNESS_DURATION = 60;

    /** 持有该组件的玩家 */
    private final Player player;

    public NiyajingshiPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(net.minecraft.server.level.ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 游戏开始分配角色时调用（重置状态）
     */
    @Override
    public void init() {
        sync();
    }

    /**
     * 游戏结束时调用（清理状态）
     */
    @Override
    public void clear() {
        init();
    }

    /**
     * 服务端每 tick 执行
     * - 存活时：每 20 tick 给予 3 秒黑暗效果
     * - 旁观时：移除残留的黑暗效果
     * 黑白视角为纯客户端着色器，无需服务端处理
     */
    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.NIYAJINGSHIBUSHIXIALE)) {
            return;
        }
        if (player.level().getGameTime() % 20 == 0) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                // 存活时给予黑暗效果 3 秒
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION, 0, false, false, false));
            } else {
                // 旁观/死亡时移除残留的黑暗效果
                if (player.hasEffect(MobEffects.DARKNESS)) {
                    player.removeEffect(MobEffects.DARKNESS);
                }
            }
        }
    }

    // ==================== 同步数据 ====================

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        // 无需同步额外数据
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        // 无需读取同步数据
    }

    // ==================== 持久化数据 ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        // 不需要跨局保存
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        // 不需要跨局读取
    }
}
