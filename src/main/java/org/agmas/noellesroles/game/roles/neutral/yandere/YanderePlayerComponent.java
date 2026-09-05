package org.agmas.noellesroles.game.roles.neutral.yandere;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 病娇（Yandere）—— 独立中立角色。
 * <p>
 * 开局随机绑定一名爱慕对象：
 * - 拥有真实 SAN 值，且消耗速度为正常的 2 倍；SAN 过低不会造成减速
 * - 唯一任务：观察自己的爱慕对象（永不完成）
 * - 本能仅可透视爱慕对象与目标；在杀手本能下显示为平民阵营
 * - 其他玩家在爱慕对象身边停留超过 15 秒即成为目标（病娇视角下发光）
 * - 击杀其他玩家 SAN 下降；击杀目标 SAN 略微恢复
 * - 爱慕对象被他人杀死 → 病娇进入疯魔，疯魔结束时死亡
 * - 亲手杀死爱慕对象 → 病娇进入疯魔，疯魔结束后获得一层护盾
 * - 病娇存活时游戏不会结束；仅剩病娇自己或病娇与爱慕对象时，病娇获胜
 * </p>
 */
public class YanderePlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<YanderePlayerComponent> KEY = ModComponents.YANDERE;

    /** 靠近爱慕对象的判定半径（格） */
    public static final double NEAR_CRUSH_RADIUS = 8.0;
    /** 持续靠近爱慕对象成为目标所需时间（15秒 = 300 tick） */
    public static final int TARGET_MARK_TICKS = 15 * 20;
    /** 击杀目标恢复的 SAN 值（0.10 = 10点） */
    public static final float SAN_GAIN_KILL_TARGET = 0.10f;
    /** 击杀其他玩家损失的 SAN 值（0.15 = 15点） */
    public static final float SAN_LOSE_KILL_OTHER = 0.15f;

    private final Player player;

    /** 爱慕对象 UUID（开局随机分配） */
    private UUID crushUuid = null;
    /** 爱慕对象已死亡（不再重新分配） */
    private boolean crushLost = false;
    /** 被标记为目标的玩家 UUID 集合 */
    private final Set<UUID> targets = new HashSet<>();
    /** 各玩家持续靠近爱慕对象的累计 tick */
    private final Map<UUID, Integer> nearCrushTicks = new HashMap<>();

    /** 疯魔结束后是否死亡（爱慕对象被他人杀死时置位） */
    public boolean deathAfterPsycho = false;
    /** 疯魔结束后是否获得一层护盾（亲手杀死爱慕对象时置位） */
    public boolean shieldAfterPsycho = false;

    public YanderePlayerComponent(Player player) {
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
        this.crushUuid = null;
        this.crushLost = false;
        this.targets.clear();
        this.nearCrushTicks.clear();
        this.deathAfterPsycho = false;
        this.shieldAfterPsycho = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    // ── 查询接口 ──────────────────────────────────────────────

    public UUID getCrushUuid() {
        return crushUuid;
    }

    public boolean isCrush(UUID uuid) {
        return uuid != null && uuid.equals(this.crushUuid);
    }

    public boolean isTarget(UUID uuid) {
        return uuid != null && this.targets.contains(uuid);
    }

    // ── 服务端每 Tick ──────────────────────────────────────────

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (gameWorld == null || !gameWorld.isRunning()) return;
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) return;

        // 确保当前玩家是病娇角色（CCA 会为所有玩家创建组件实例）
        if (!gameWorld.isRole(serverPlayer, ModRoles.YANDERE)) return;

        boolean changed = false;

        // 1. 随机分配爱慕对象：仅在本局尚未绑定时分配。
        //    爱慕对象死亡由 OnKillPlayerTriggered 置 crushLost 并清空 crushUuid；
        //    掉线/切维度时 getPlayerByUUID 同样返回 null，不能据此重新绑定，
        //    否则违背「开局随机绑定一名爱慕对象」。
        if (!crushLost && crushUuid == null) {
            changed |= assignCrush(serverPlayer, serverLevel);
        }

        // 2. 确保始终持有唯一任务：观察爱慕对象
        SREPlayerTaskComponent taskComp = SREPlayerTaskComponent.KEY.get(serverPlayer);
        if (!taskComp.tasks.containsKey(SREPlayerTaskComponent.Task.YANDERE_OBSERVE)) {
            taskComp.tasks.put(SREPlayerTaskComponent.Task.YANDERE_OBSERVE,
                    new SREPlayerTaskComponent.YandereObserveTask());
            taskComp.sync();
        }

        if (crushUuid != null) {
            Player crush = serverLevel.getPlayerByUUID(crushUuid);
            boolean crushAlive = crush != null && GameUtils.isPlayerAliveAndSurvival(crush);

            // 3. 检测其他玩家持续靠近爱慕对象
            if (crushAlive) {
                for (ServerPlayer p : serverLevel.players()) {
                    if (p.equals(serverPlayer) || p.getUUID().equals(crushUuid)) continue;
                    if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
                    UUID uuid = p.getUUID();
                    if (targets.contains(uuid)) continue;
                    if (p.distanceTo(crush) <= NEAR_CRUSH_RADIUS) {
                        int ticks = nearCrushTicks.getOrDefault(uuid, 0) + 1;
                        if (ticks >= TARGET_MARK_TICKS) {
                            targets.add(uuid);
                            nearCrushTicks.remove(uuid);
                            serverPlayer.displayClientMessage(
                                    Component.translatable("message.noellesroles.yandere.new_target",
                                            p.getName().getString())
                                            .withStyle(ChatFormatting.RED), false);
                            changed = true;
                        } else {
                            nearCrushTicks.put(uuid, ticks);
                        }
                    } else {
                        nearCrushTicks.remove(uuid);
                    }
                }
            }

            // 4. 清理已死亡的目标：仅当玩家对象可得且确认死亡时移除。
            //    掉线时 getPlayerByUUID 返回 null，此时保留标记，
            //    使其重连后仍是目标（成为目标后在病娇视角下持续发光）。
            List<UUID> deadTargets = new ArrayList<>();
            for (UUID uuid : targets) {
                Player t = serverLevel.getPlayerByUUID(uuid);
                if (t != null && !GameUtils.isPlayerAliveAndSurvival(t)) {
                    deadTargets.add(uuid);
                }
            }
            if (!deadTargets.isEmpty()) {
                targets.removeAll(deadTargets);
                nearCrushTicks.keySet().removeAll(deadTargets);
                changed = true;
            }
        }

        // 每秒同步一次，保证客户端高亮与 HUD 状态更新
        if (changed || serverPlayer.tickCount % 20 == 0) {
            sync();
        }
    }

    /**
     * 从存活玩家中随机挑选一名爱慕对象（不能是病娇自己）。
     */
    private boolean assignCrush(ServerPlayer self, ServerLevel serverLevel) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer p : serverLevel.players()) {
            if (p.equals(self)) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) return false;
        ServerPlayer chosen = candidates.get(self.getRandom().nextInt(candidates.size()));
        this.crushUuid = chosen.getUUID();
        self.displayClientMessage(
                Component.translatable("message.noellesroles.yandere.crush_assigned",
                        chosen.getName().getString())
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return true;
    }

    // ── 疯魔触发 ──────────────────────────────────────────────

    /**
     * 强制进入疯魔模式（默认时长、1 层疯魔护盾，默认疯魔武器）。
     *
     * @return 是否成功进入疯魔；若因背包满等原因失败，
     *         死亡惩罚会在此处立即结算，护盾分支需调用方补发
     */
    public boolean triggerFrenzy() {
        if (!(player instanceof ServerPlayer)) return false;
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0) return true; // 已在疯魔中，视为已触发
        if (!psycho.startPsycho(1d, 1)) {
            // 疯魔启动失败 → 死亡分支立即结算，避免标记永远无法兑现
            if (deathAfterPsycho) {
                deathAfterPsycho = false;
                sync();
                GameUtils.killPlayer(player, true, null,
                        org.agmas.noellesroles.Noellesroles.id("yandere"));
            }
            return false;
        }
        return true;
    }

    // ── 事件注册 ──────────────────────────────────────────────

    static {
        // 击杀事件：SAN 增减 + 疯魔触发
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReason, forceKill) -> {
            if (!(victim.level() instanceof ServerLevel serverLevel)) return;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            if (gameWorld == null || !gameWorld.isRunning()) return;

            // 找到本局的病娇
            ServerPlayer yandere = null;
            for (ServerPlayer p : serverLevel.players()) {
                if (gameWorld.isRole(p, ModRoles.YANDERE)) {
                    yandere = p;
                    break;
                }
            }
            if (yandere == null) return;
            YanderePlayerComponent comp = ModComponents.YANDERE.get(yandere);

            if (killer != null && killer.getUUID().equals(yandere.getUUID())) {
                // 病娇亲手击杀
                if (comp.isCrush(victim.getUUID())) {
                    // 亲手杀死爱慕对象 → 疯魔，结束后获得护盾
                    comp.shieldAfterPsycho = true;
                    comp.deathAfterPsycho = false;
                    comp.crushUuid = null;
                    comp.crushLost = true;
                    yandere.displayClientMessage(
                            Component.translatable("message.noellesroles.yandere.kill_crush")
                                    .withStyle(ChatFormatting.DARK_RED), false);
                    if (!comp.triggerFrenzy() && comp.shieldAfterPsycho) {
                        // 疯魔启动失败时补发护盾，避免标记永远无法兑现
                        comp.shieldAfterPsycho = false;
                        io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(yandere).addArmor();
                        yandere.displayClientMessage(
                                Component.translatable("message.noellesroles.yandere.shield_gained")
                                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    }
                } else if (comp.isTarget(victim.getUUID())) {
                    // 击杀目标 → SAN 略微恢复
                    comp.targets.remove(victim.getUUID());
                    SREPlayerMoodComponent.KEY.get(yandere).addMood(SAN_GAIN_KILL_TARGET);
                    yandere.displayClientMessage(
                            Component.translatable("message.noellesroles.yandere.kill_target")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                } else {
                    // 击杀无关玩家 → SAN 下降
                    SREPlayerMoodComponent.KEY.get(yandere).addMood(-SAN_LOSE_KILL_OTHER);
                    yandere.displayClientMessage(
                            Component.translatable("message.noellesroles.yandere.kill_other")
                                    .withStyle(ChatFormatting.GRAY), true);
                }
                comp.sync();
            } else if (comp.isCrush(victim.getUUID())) {
                // 爱慕对象被其他玩家（或环境）杀死 → 疯魔，结束后病娇死亡
                comp.deathAfterPsycho = true;
                comp.shieldAfterPsycho = false;
                comp.crushUuid = null;
                comp.crushLost = true;
                comp.targets.clear();
                if (GameUtils.isPlayerAliveAndSurvival(yandere)) {
                    yandere.displayClientMessage(
                            Component.translatable("message.noellesroles.yandere.crush_dead")
                                    .withStyle(ChatFormatting.DARK_RED), false);
                    comp.triggerFrenzy();
                }
                comp.sync();
            }
        });

        // 胜利拦截：病娇存活时游戏不会结束；满足条件时病娇独赢
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEndsMode) -> {
            if (isLooseEndsMode) return GameUtils.WinStatus.NOT_MODIFY;
            if (winStatus != GameUtils.WinStatus.KILLERS
                    && winStatus != GameUtils.WinStatus.PASSENGERS) {
                return GameUtils.WinStatus.NOT_MODIFY;
            }

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            if (gameWorld == null) return GameUtils.WinStatus.NOT_MODIFY;

            ServerPlayer yandere = null;
            int aliveCount = 0;
            boolean crushAlive = false;
            for (ServerPlayer p : serverLevel.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
                aliveCount++;
                if (gameWorld.isRole(p, ModRoles.YANDERE)) {
                    yandere = p;
                }
            }
            if (yandere == null) return GameUtils.WinStatus.NOT_MODIFY;

            YanderePlayerComponent comp = ModComponents.YANDERE.get(yandere);
            if (comp.getCrushUuid() != null) {
                Player crush = serverLevel.getPlayerByUUID(comp.getCrushUuid());
                crushAlive = crush != null && GameUtils.isPlayerAliveAndSurvival(crush);
            }

            // 病娇独赢：仅剩病娇自己，或仅剩病娇与爱慕对象
            if (aliveCount == 1 || (aliveCount == 2 && crushAlive)) {
                RoleUtils.customWinnerWin(serverLevel,
                        ModRoles.YANDERE_ID.getPath(),
                        ModRoles.YANDERE.color());
                return GameUtils.WinStatus.CUSTOM;
            }

            // 病娇存活且条件未满足 → 阻止游戏结束
            return GameUtils.WinStatus.NONE;
        });
    }

    // ── NBT 同步 ──────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        if (crushUuid != null) {
            tag.putUUID("crushUuid", crushUuid);
        }
        tag.putBoolean("crushLost", crushLost);
        ListTag list = new ListTag();
        for (UUID uuid : targets) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("targets", list);
        tag.putBoolean("deathAfterPsycho", deathAfterPsycho);
        tag.putBoolean("shieldAfterPsycho", shieldAfterPsycho);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        this.crushUuid = tag.hasUUID("crushUuid") ? tag.getUUID("crushUuid") : null;
        this.crushLost = tag.getBoolean("crushLost");
        this.targets.clear();
        ListTag list = tag.getList("targets", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                this.targets.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.deathAfterPsycho = tag.getBoolean("deathAfterPsycho");
        this.shieldAfterPsycho = tag.getBoolean("shieldAfterPsycho");
        // 高亮状态变化后清除本能透视缓存，确保即时更新
        io.wifi.starrailexpress.client.SREClient.cachedHighLightMap.clear();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
