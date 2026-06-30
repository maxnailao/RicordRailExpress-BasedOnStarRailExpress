package org.agmas.noellesroles.game.roles.innocence.wushujia;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 武术家组件
 *
 * 管理武术家的心流状态、冷却和连击记录。
 * 该组件会自动在客户端和服务端之间同步。
 *
 * 功能：
 * - 心流状态管理（10s持续，速度I效果）
 * - 技能冷却管理
 * - 连击状态追踪（用于HUD显示）
 */
public class WushujiaPlayerComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 */
    public static final ComponentKey<WushujiaPlayerComponent> KEY = ModComponents.WUSHUJIA;

    private final Player player;

    /** 心流状态结束时间（gameTime），0表示未激活 */
    public long flowEndTime = 0;

    /** 技能冷却时间（tick） */
    public int cooldown = 0;

    /** 当前连击次数（用于HUD显示） */
    public int comboCount = 0;

    public WushujiaPlayerComponent(Player player) {
        this.player = player;
    }

    /** 是否处于心流状态 */
    public boolean isInFlow() {
        return player.level().getGameTime() < flowEndTime;
    }

    /** 获取心流状态剩余秒数 */
    public int getFlowRemainingSeconds() {
        long remaining = flowEndTime - player.level().getGameTime();
        return remaining > 0 ? (int) (remaining / 20) : 0;
    }

    /** 激活心流状态 */
    public boolean activateFlow() {
        if (cooldown > 0) return false;
        if (GameUtils.isPlayerEliminated(player)) return false;

        // 设置心流结束时间（10秒 = 200 tick）
        flowEndTime = player.level().getGameTime() + 200;

        // 施加速度I效果（amplifier 0 = 等级I），持续10秒
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, true, false, true));

        cooldown = 1200; // 60秒冷却
        comboCount = 0;
        sync();
        return true;
    }

    @Override
    public void init() {
        this.flowEndTime = 0;
        this.cooldown = 0;
        this.comboCount = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning()) return;
        if (!gameWorldComponent.isRole(player, ModRoles.WUSHUJIA)) return;

        boolean shouldSync = false;

        // 冷却递减
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 100 == 0 || cooldown == 0) shouldSync = true;
        }

        // 心流状态结束
        if (flowEndTime > 0 && !isInFlow()) {
            flowEndTime = 0;
            comboCount = 0;
            shouldSync = true;
        }

        if (shouldSync) sync();
    }

    @Override
    public void clientTick() {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning()) return;
        if (!gameWorldComponent.isRole(player, ModRoles.WUSHUJIA)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 客户端冷却预测
        if (cooldown > 0) {
            cooldown--;
        }

        // 心流状态结束
        if (flowEndTime > 0 && !isInFlow()) {
            flowEndTime = 0;
            comboCount = 0;
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("flowEndTime", flowEndTime);
        tag.putInt("cooldown", cooldown);
        tag.putInt("comboCount", comboCount);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        flowEndTime = tag.contains("flowEndTime") ? tag.getLong("flowEndTime") : 0;
        cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        comboCount = tag.contains("comboCount") ? tag.getInt("comboCount") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
