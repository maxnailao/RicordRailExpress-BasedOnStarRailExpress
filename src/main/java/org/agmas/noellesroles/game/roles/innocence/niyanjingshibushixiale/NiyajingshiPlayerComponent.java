package org.agmas.noellesroles.game.roles.innocence.niyanjingshibushixiale;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.compat.BlindnessCompat;
import org.agmas.noellesroles.component.ModComponents;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 盲人组件
 *
 * 管理盲人的被动效果：
 * - 被动1：分配角色时开启失明症 fork 的完整失明体验（黑屏+导盲杖+声音揭示），
 *   旁观/游戏结束时关闭
 * - 被动2：脚步声纹检测（在 AgentListenStepHandler 中处理，不需要蹲下）
 */
public class NiyajingshiPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<NiyajingshiPlayerComponent> KEY = ModComponents.NIYAJINGSHIBUSHIXIALE;

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
        // 开启失明体验（fork 版默认全关，仅盲女分配时开启），并发放导盲杖
        BlindnessCompat.setBlind(player, true);
        BlindnessCompat.giveGuidanceCane(player);
        sync();
    }

    /**
     * 游戏结束时调用（清理状态）
     */
    @Override
    public void clear() {
        // 角色移除/游戏结束时关闭失明体验
        BlindnessCompat.setBlind(player, false);
        sync();
    }

    /**
     * 服务端每 tick 执行：失明体验由失明症模组自身驱动，此处无需处理
     */
    @Override
    public void serverTick() {
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
