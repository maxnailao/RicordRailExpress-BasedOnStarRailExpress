package org.agmas.noellesroles.game.roles.innocence.niyanjingshibushixiale;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.compat.BlindnessCompat;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 盲人组件
 *
 * 管理盲人的被动效果：
 * - 被动1：分配角色时开启失明症 fork 的完整失明体验（黑屏+导盲杖+声音揭示），
 *   死亡/旁观/游戏结束时自动关闭，被复活时自动恢复
 * - 被动2：脚步声纹检测（在 AgentListenStepHandler 中处理，不需要蹲下）
 * - 被动3：免疫失明与黑暗效果（关灯、烟雾弹等任何来源均不生效）
 * - 被动4（被保护者）：顶部倒计时到达 4分30秒 时获得一层护盾
 */
public class NiyajingshiPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<NiyajingshiPlayerComponent> KEY = ModComponents.NIYAJINGSHIBUSHIXIALE;

    /** 被保护者：顶部倒计时 <= 4分30秒 = 270秒 = 5400 tick 时发放护盾 */
    private static final int SHIELD_TIME_TICKS = 4 * 60 * 20 + 30 * 20;

    /** 持有该组件的玩家 */
    private final Player player;

    /** 本局是否已发放护盾 */
    private boolean shieldGranted = false;

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
        shieldGranted = false;
        sync();
    }

    /**
     * 游戏结束时调用（清理状态）
     */
    @Override
    public void clear() {
        // 角色移除/游戏结束时关闭失明体验
        BlindnessCompat.setBlind(player, false);
        shieldGranted = false;
        sync();
    }

    /**
     * 服务端每 tick 执行：
     * 1. 失明视野状态机：仅"游戏进行中 + 盲女 + 存活"时开启，
     *    死亡/旁观/游戏结束立即关闭，被复活后自动恢复（无需依赖 clear 调用时机）
     * 2. 被保护者被动：游戏开始 4分30秒 获得一层护盾
     * 3. 免疫失明/黑暗效果
     */
    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        boolean isBlindRoleInGame = gameWorld != null
                && gameWorld.gameStatus == SREGameWorldComponent.GameStatus.ACTIVE
                && gameWorld.isRole(player, ModRoles.NIYAJINGSHIBUSHIXIALE);

        // 失明视野状态机：死亡（旁观）、游戏结束自动关闭；复活后自动恢复
        boolean shouldBlind = isBlindRoleInGame && GameUtils.isPlayerAliveAndSurvival(sp);
        if (BlindnessCompat.isBlind(player) != shouldBlind) {
            BlindnessCompat.setBlind(player, shouldBlind);
        }

        if (!isBlindRoleInGame) return;

        // === 被保护者：顶部倒计时到达 4分30秒 时获得一层护盾 ===
        if (!shieldGranted) {
            int remaining = SREGameTimeComponent.KEY.get(player.level()).getTime();
            if (remaining > 0 && remaining <= SHIELD_TIME_TICKS) {
                shieldGranted = true;
                SREArmorPlayerComponent.KEY.get(sp).addArmor();
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.niyajingshi.shield_gained")
                                .withStyle(ChatFormatting.GOLD),
                        true);
                sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }

        // === 免疫失明/黑暗效果（关灯、烟雾弹等任何来源） ===
        if (GameUtils.isPlayerAliveAndSurvival(sp)) {
            if (sp.hasEffect(MobEffects.BLINDNESS)) {
                sp.removeEffect(MobEffects.BLINDNESS);
            }
            if (sp.hasEffect(MobEffects.DARKNESS)) {
                sp.removeEffect(MobEffects.DARKNESS);
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
