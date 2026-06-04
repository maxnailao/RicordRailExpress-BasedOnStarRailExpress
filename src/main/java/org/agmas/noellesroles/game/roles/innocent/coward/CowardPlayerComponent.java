package org.agmas.noellesroles.game.roles.innocent.coward;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 胆小鬼组件
 * 被动：当自身半径5格内没有其他玩家时，获得速度I效果（无粒子显示），但理智值每秒额外消耗2点
 */
public class CowardPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<CowardPlayerComponent> KEY = ModComponents.COWARD;

    private final Player player;

    /** 检测半径 */
    public static final double DETECTION_RADIUS = 5.0;

    /** 每秒额外理智值消耗（1点/秒 = 0.01/20tick = 0.0005f per tick） */
    public static final float MOOD_DRAIN_PER_TICK = 0.0005f;

    public CowardPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 检查玩家半径5格内是否有其他存活的玩家
     */
    private boolean isAlone() {
        if (this.player.level() == null || this.player.level().isClientSide()) {
            return false;
        }
        for (ServerPlayer otherPlayer : this.player.getServer().getPlayerList().getPlayers()) {
            if (otherPlayer == this.player) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(otherPlayer)) continue;
            if (otherPlayer.distanceTo(this.player) <= DETECTION_RADIUS) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void serverTick() {
        if (this.player.level() == null || this.player.level().isClientSide()) {
            return;
        }

        // 检查游戏是否正在进行中，以及玩家是否拥有胆小鬼角色
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        if (!gameWorldComponent.isRunning() || !gameWorldComponent.isRole(this.player, ModRoles.COWARD)) {
            return;
        }

        if (isAlone()) {
            // 施加速度I效果（amplifier=0, 持续40tick即2秒, 无粒子显示）
            this.player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    40,     // 持续40tick（2秒），每秒刷新一次
                    0,      // amplifier 0 = Speed I
                    true,   // ambient
                    false,  // showParticles = false 无粒子显示
                    true    // showIcon
            ));

            // 额外消耗理智值：每秒1点 = 每tick 0.0005f
            SREPlayerMoodComponent moodComponent = SREPlayerMoodComponent.KEY.get(this.player);
            moodComponent.addMood(-MOOD_DRAIN_PER_TICK);
        }
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            KEY.sync(this.player);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
