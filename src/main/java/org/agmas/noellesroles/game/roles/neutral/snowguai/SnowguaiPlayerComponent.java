package org.agmas.noellesroles.game.roles.neutral.snowguai;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.scene.BlizzardManager;
import org.agmas.noellesroles.utils.RoleUtils;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 雪怪（Snowguai）—— 独立中立角色。
 * <p>
 * 被动 1：当有人因冻死而亡时，雪怪获得 +150 金币。
 * 被动 2：当场上仅剩杀手阵营或平民阵营时，雪怪获得速度 III 和发光效果，
 *          并触发最终暴风雪（游戏不会因此结束）。
 * 商店：雪球 15、关灯(3s) 100、触发暴风雪 250。
 * </p>
 */
public class SnowguaiPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<SnowguaiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "snowguai_wow"),
            SnowguaiPlayerComponent.class);

    private final Player player;

    /** 被动 2 是否已激活（仅剩单一阵营时） */
    private boolean endgameBuffActive = false;

    /** 最终暴风雪是否已由雪怪触发（全局标记，游戏结束时重置） */
    private static boolean finalBlizzardTriggered = false;

    public SnowguaiPlayerComponent(Player player) {
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

    // ── 初始化 / 清理 ──────────────────────────────────────────

    @Override
    public void init() {
        this.endgameBuffActive = false;
        sync();
    }

    @Override
    public void clear() {
        init();
        finalBlizzardTriggered = false;
    }

    // ── 服务端每 Tick ──────────────────────────────────────────

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (gameWorld == null || !gameWorld.isRunning()) return;

        // 检查雪怪自己是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) return;

        // 确保当前玩家是雪怪角色（CCA 会为所有玩家创建组件实例）
        if (!gameWorld.isRole(serverPlayer, ModRoles.SNOWGUAI_WOW)) return;

        // 亡命徒游荡期间不触发被动
        if (!gameWorld.isSkillAvailable) {
            if (endgameBuffActive) {
                endgameBuffActive = false;
                serverPlayer.removeEffect(MobEffects.MOVEMENT_SPEED);
                serverPlayer.removeEffect(MobEffects.GLOWING);
                sync();
            }
            return;
        }

        // 被动 2：判断场上是否仅剩杀手阵营或仅剩平民阵营
        boolean hasAliveKiller = false;
        boolean hasAliveInnocent = false;

        for (ServerPlayer p : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            // 跳过雪怪自己（中立阵营不影响判断）
            if (gameWorld.isRole(p, ModRoles.SNOWGUAI_WOW)) continue;

            if (gameWorld.isKillerTeam(p)) {
                hasAliveKiller = true;
            } else {
                hasAliveInnocent = true;
            }

            // 两个阵营都有人存活，无需继续遍历
            if (hasAliveKiller && hasAliveInnocent) break;
        }

        // 仅剩单一阵营时激活 buff
        boolean shouldActivate = hasAliveKiller != hasAliveInnocent;

        if (shouldActivate && !endgameBuffActive) {
            endgameBuffActive = true;
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    MobEffectInstance.INFINITE_DURATION, 2, false, false, true)); // Speed III (amplifier 2)
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                    MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.snowguai_wow.endgame_buff")
                            .withStyle(ChatFormatting.AQUA), true);

            // 触发最终暴风雪
            if (!finalBlizzardTriggered) {
                finalBlizzardTriggered = true;
                BlizzardManager.activateFinalBlizzard(serverLevel);
            }
        } else if (!shouldActivate && endgameBuffActive) {
            endgameBuffActive = false;
            serverPlayer.removeEffect(MobEffects.MOVEMENT_SPEED);
            serverPlayer.removeEffect(MobEffects.GLOWING);
        }

        sync();
    }

    // ── 被动 1：冻死奖励 ──────────────────────────────────────

    /**
     * 当有玩家因冻死而亡时调用，给雪怪 +150 金币。
     */
    public void onSomeoneFrozenDeath() {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        shop.addToBalance(150);
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.snowguai_wow.frozen_bonus")
                            .withStyle(ChatFormatting.GOLD), true);
        }
        sync();
    }

    // ── 商店：触发暴风雪 ──────────────────────────────────────

    /**
     * 触发一次 20 秒的暴风雪（商店购买用）。
     *
     * @param player 购买者
     * @return 是否成功触发
     */
    public static boolean triggerBlizzard(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        return BlizzardManager.triggerForcedBlizzard(serverLevel, 20 * 20);
    }

    /**
     * 注册事件监听器（在组件类加载时通过 static 块执行）。
     */
    static {
        // 被动 1：有人冻死时给所有存活的雪怪 +150 金币
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (!deathReason.equals(GameConstants.DeathReasons.FROZEN)) return;
            if (!(victim.level() instanceof ServerLevel serverLevel)) return;

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            if (gameWorld == null) return;

            for (ServerPlayer p : serverLevel.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
                if (!gameWorld.isRole(p, ModRoles.SNOWGUAI_WOW)) continue;
                SnowguaiPlayerComponent comp = ModComponents.SNOWGUAI_WOW.get(p);
                if (comp != null) {
                    comp.onSomeoneFrozenDeath();
                }
            }
        });

        // 被动 2：当雪怪存活且场上仅剩单一阵营时，阻止游戏结束；仅剩雪怪时独赢
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEndsMode) -> {
            if (isLooseEndsMode) return GameUtils.WinStatus.NOT_MODIFY;
            // 仅在杀手胜或平民胜时检查是否需要阻止
            if (winStatus != GameUtils.WinStatus.KILLERS
                    && winStatus != GameUtils.WinStatus.PASSENGERS) {
                return GameUtils.WinStatus.NOT_MODIFY;
            }

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            if (gameWorld == null) return GameUtils.WinStatus.NOT_MODIFY;

            boolean snowguaiAlive = false;
            int aliveCount = 0;
            for (ServerPlayer p : serverLevel.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
                aliveCount++;
                if (gameWorld.isRole(p, ModRoles.SNOWGUAI_WOW)) {
                    snowguaiAlive = true;
                }
            }

            if (!snowguaiAlive) return GameUtils.WinStatus.NOT_MODIFY;

            // 雪怪独赢：仅剩雪怪自己时触发独立胜利
            if (aliveCount == 1) {
                RoleUtils.customWinnerWin(serverLevel,
                        ModRoles.SNOWGUAI_WOW_ID.getPath(),
                        ModRoles.SNOWGUAI_WOW.color());
                return GameUtils.WinStatus.CUSTOM;
            }

            // 雪怪存活且还有其他玩家，阻止游戏结束
            return GameUtils.WinStatus.NONE;
        });

        // 游戏结束时重置最终暴风雪标记
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            finalBlizzardTriggered = false;
        });
    }

    // ── NBT 同步 ──────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("endgameBuffActive", endgameBuffActive);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        endgameBuffActive = tag.getBoolean("endgameBuffActive");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {}
}
