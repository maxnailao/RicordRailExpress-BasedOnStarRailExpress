package org.agmas.noellesroles.game.roles.innocence.tegong;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 特工玩家组件 - 警长阵营
 * 技能：潜行模式（10秒速度I + 无脚步声 + 反透视），冷却90秒
 * 被动：与警卫相同，拥有被动给予金钱的能力（通过 setCanAutoAddMoney 实现）
 */
public class TegongPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 技能持续时间：10秒 = 200 ticks */
    private static final int SKILL_DURATION_TICKS = 200;

    private final Player player;

    /** 技能是否激活（同步到客户端，用于 HUD 和反透视判断） */
    public boolean skillActive = false;

    /** 技能结束时间戳（服务端使用） */
    private int skillEndTick = 0;

    public TegongPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        // 同步给所有玩家，以便其他客户端检查 skillActive 实现反透视
        return true;
    }

    public void sync() {
        ModComponents.TEGONG.sync(this.player);
    }

    /**
     * 激活技能：速度I + 脚步消失 + 反透视，持续10秒
     *
     * @return true 如果技能成功激活
     */
    public boolean activateSkill() {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRunning()) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;
        if (skillActive) return false;

        // 设置技能状态
        skillActive = true;
        skillEndTick = (int) player.level().getGameTime() + SKILL_DURATION_TICKS;

        // 施加速度I效果（10秒，等级0 = Speed I，隐藏粒子，显示图标）
        serverPlayer.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                SKILL_DURATION_TICKS,
                0,      // 等级 0 = Speed I
                false,   // ambient
                false,   // showParticles = false（隐藏粒子）
                true     // showIcon = true
        ));

        // 施加静默潜行效果（10秒，隐藏移动粒子，由 FootstepVanishEffectSync 自动广播给所有客户端）
        serverPlayer.addEffect(new MobEffectInstance(
                ModEffects.JINGMOQIANXING,
                SKILL_DURATION_TICKS,
                0,
                false,   // ambient
                false,   // showParticles = false
                true     // showIcon = true
        ));

        // 施加静步效果（10秒，屏蔽脚步声，由 FootstepVanishEffectSync 自动广播给所有客户端）
        serverPlayer.addEffect(new MobEffectInstance(
                ModEffects.JINGBU,
                SKILL_DURATION_TICKS,
                0,
                false,   // ambient
                false,   // showParticles = false
                true     // showIcon = true
        ));

        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!skillActive) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRunning()) {
            deactivateSkill();
            return;
        }

        // 检查技能是否到期
        if (player.level().getGameTime() >= skillEndTick) {
            deactivateSkill();
        }
    }

    /**
     * 停用技能，移除效果
     */
    private void deactivateSkill() {
        skillActive = false;
        skillEndTick = 0;

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.removeEffect(MobEffects.MOVEMENT_SPEED);
            serverPlayer.removeEffect(ModEffects.JINGMOQIANXING);
            serverPlayer.removeEffect(ModEffects.JINGBU);
        }

        sync();
    }

    @Override
    public void init() {
        this.skillActive = false;
        this.skillEndTick = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    // ==================== NBT 同步 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider lookup) {
        tag.putBoolean("skillActive", skillActive);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider lookup) {
        skillActive = tag.getBoolean("skillActive");
    }

    // 持久化数据（局内状态，不写入磁盘）
    @Override
    public void writeToNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider lookup) {}

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider lookup) {}
}
