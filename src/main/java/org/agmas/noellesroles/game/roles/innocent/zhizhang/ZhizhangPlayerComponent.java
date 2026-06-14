package org.agmas.noellesroles.game.roles.innocent.zhizhang;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * 智力障碍患者角色组件
 * - 被动：持续获得语音禁用和聊天混乱效果
 * - 被动：监护人技能释放期间免疫上述效果
 * - 被动：移动时10%概率视角随机偏移
 * - 技能：探查周围3.5格玩家背包是否有刀，5秒后高亮3秒，CD60秒
 */
public class ZhizhangPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<ZhizhangPlayerComponent> KEY = ModComponents.ZHIZHANG;

    private final Player player;

    // ===== 技能相关 =====
    /** 技能冷却（tick），60秒 = 1200 tick */
    public static final int SKILL_COOLDOWN = 1200;
    /** 检测延迟（tick），5秒 = 100 tick */
    public static final int DETECT_DELAY = 100;
    /** 高亮持续时间（tick），3秒 = 60 tick */
    public static final int HIGHLIGHT_DURATION = 60;
    /** 检测半径 */
    public static final double DETECT_RADIUS = 3.5;

    /** 技能冷却计时器 */
    public int skillCooldown = 0;

    // ===== 被动：监护人免疫 =====
    /** 监护人技能免疫计时器（tick） */
    public int guardianImmunityTicks = 0;

    // ===== 技能：延迟检测 =====
    /** 延迟检测计时器（tick），> 0 表示正在等待 */
    public int pendingDetectTimer = 0;
    /** 延迟检测期间记录的目标 UUID */
    public List<UUID> pendingTargets = new ArrayList<>();

    /** 当前高亮中的玩家 UUID 列表（已同步到客户端） */
    public List<UUID> highlightedPlayers = new ArrayList<>();
    /** 高亮剩余时间（tick） */
    public int highlightTicks = 0;

    public ZhizhangPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.skillCooldown = 0;
        this.guardianImmunityTicks = 0;
        this.pendingDetectTimer = 0;
        this.pendingTargets.clear();
        this.highlightedPlayers.clear();
        this.highlightTicks = 0;
        sync();
    }

    @Override
    public void clear() {
        this.skillCooldown = 0;
        this.guardianImmunityTicks = 0;
        this.pendingDetectTimer = 0;
        this.pendingTargets.clear();
        this.highlightedPlayers.clear();
        this.highlightTicks = 0;
        // 移除被动效果
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(ModEffects.VOICE_SILENCE);
            sp.removeEffect(ModEffects.CHAT_MUDDLEDNESS);
        }
        sync();
    }

    /**
     * 使用技能：探查周围3.5格内有刀的玩家
     */
    public boolean useSkill() {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (skillCooldown > 0) {
            sp.displayClientMessage(
                Component.translatable("tip.noellesroles.cooldown", skillCooldown / 20)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 遍历周围3.5格玩家，检查背包是否有刀
        List<UUID> detected = new ArrayList<>();
        for (ServerPlayer target : sp.serverLevel().players()) {
            if (target == sp) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
            if (sp.distanceTo(target) > DETECT_RADIUS) continue;

            // 检查背包是否有刀
            boolean hasKnife = false;
            for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
                ItemStack stack = target.getInventory().getItem(i);
                if (stack.getItem() == TMMItems.KNIFE) {
                    hasKnife = true;
                    break;
                }
            }
            if (hasKnife) {
                detected.add(target.getUUID());
            }
        }

        if (detected.isEmpty()) {
            sp.displayClientMessage(
                Component.translatable("message.noellesroles.zhizhang.no_knife_detected")
                    .withStyle(ChatFormatting.GRAY), true);
            // 仍然进入冷却
            skillCooldown = SKILL_COOLDOWN;
            sync();
            return true;
        }

        // 进入延迟状态
        pendingDetectTimer = DETECT_DELAY;
        pendingTargets = new ArrayList<>(detected);
        skillCooldown = SKILL_COOLDOWN;

        sp.displayClientMessage(
            Component.translatable("message.noellesroles.zhizhang.detecting")
                .withStyle(ChatFormatting.AQUA), true);
        sync();
        return true;
    }

    /**
     * 设置监护人免疫时间
     */
    public void setGuardianImmunity(int ticks) {
        this.guardianImmunityTicks = ticks;
        // 立即移除当前 debuff
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(ModEffects.VOICE_SILENCE);
            sp.removeEffect(ModEffects.CHAT_MUDDLEDNESS);
        }
        sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || gameWorld.gameStatus != SREGameWorldComponent.GameStatus.ACTIVE) return;
        if (!gameWorld.isRole(player, ModRoles.ZHIZHANG)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 冷却递减
        if (skillCooldown > 0) {
            skillCooldown--;
        }

        // 监护人免疫递减
        if (guardianImmunityTicks > 0) {
            guardianImmunityTicks--;
        }

        // 被动1：每20tick检查并施加 debuff（除非免疫中）
        if (player.level().getGameTime() % 20 == 0) {
            if (guardianImmunityTicks <= 0) {
                if (!sp.hasEffect(ModEffects.VOICE_SILENCE)) {
                    sp.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE,
                        40, 0, false, false, false)); // 2秒，每20tick刷新
                }
                if (!sp.hasEffect(ModEffects.CHAT_MUDDLEDNESS)) {
                    sp.addEffect(new MobEffectInstance(ModEffects.CHAT_MUDDLEDNESS,
                        40, 0, false, false, false)); // 2秒，每20tick刷新
                }
            }
        }

        // 被动2：移动时每秒10%概率视角偏移（在客户端处理）

        // 处理延迟检测
        if (pendingDetectTimer > 0) {
            pendingDetectTimer--;
            if (pendingDetectTimer == 0) {
                // 延迟结束，验证目标是否仍在范围内且存活，然后高亮
                List<UUID> validTargets = new ArrayList<>();
                for (UUID uuid : pendingTargets) {
                    var targetRaw = sp.serverLevel().getPlayerByUUID(uuid);
                    if (!(targetRaw instanceof ServerPlayer target)) continue;
                    if (!GameUtils.isPlayerAliveAndSurvival(target)
                        || sp.distanceTo(target) > DETECT_RADIUS * 2) continue; // 稍微放宽范围
                    // 再次检查是否有刀
                    boolean hasKnife = false;
                    for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
                        if (target.getInventory().getItem(i).getItem() == TMMItems.KNIFE) {
                            hasKnife = true;
                            break;
                        }
                    }
                    if (hasKnife) {
                        validTargets.add(uuid);
                    }
                }

                if (!validTargets.isEmpty()) {
                    highlightedPlayers = validTargets;
                    highlightTicks = HIGHLIGHT_DURATION;
                    sp.displayClientMessage(
                        Component.translatable("message.noellesroles.zhizhang.highlight_start", validTargets.size())
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    sp.displayClientMessage(
                        Component.translatable("message.noellesroles.zhizhang.highlight_missed")
                            .withStyle(ChatFormatting.GRAY), true);
                }
                pendingTargets.clear();
                sync();
            }
        }

        // 高亮递减
        if (highlightTicks > 0) {
            highlightTicks--;
            if (highlightTicks == 0) {
                highlightedPlayers.clear();
                sync();
            }
        }

        // 每10秒同步一次
        if (player.level().getGameTime() % 200 == 0) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (player == null || player.level() == null) return;
        // 仅在本地玩家是智力障碍患者时执行客户端逻辑
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) return;
        if (!SREClient.gameComponent.isRole(player, ModRoles.ZHIZHANG)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 客户端冷却模拟
        if (skillCooldown > 0) {
            skillCooldown--;
        }
        if (guardianImmunityTicks > 0) {
            guardianImmunityTicks--;
        }
        if (highlightTicks > 0) {
            highlightTicks--;
            if (highlightTicks == 0) {
                highlightedPlayers.clear();
            }
        }
        if (pendingDetectTimer > 0) {
            pendingDetectTimer--;
            if (pendingDetectTimer == 0) {
                pendingTargets.clear();
            }
        }

        // 被动2：移动时10%概率视角随机偏移（每秒判定一次）
        if (player.level().getGameTime() % 20 == 0) {
            if (player.isShiftKeyDown() || player.getDeltaMovement().horizontalDistance() > 0.01) {
                // 玩家正在移动
                var random = player.level().getRandom();
                if (random.nextFloat() < 0.1f) {
                    // 随机偏移视角
                    float yawOffset = (random.nextFloat() - 0.5f) * 60f; // ±30度
                    float pitchOffset = (random.nextFloat() - 0.5f) * 20f; // ±10度
                    player.setYRot(player.getYRot() + yawOffset);
                    player.setXRot(Math.max(-90f, Math.min(90f, player.getXRot() + pitchOffset)));
                }
            }
        }

        // 高亮渲染：对有刀玩家施加客户端发光效果（与承太郎看迪奥相同的GLOWING机制）
        if (!highlightedPlayers.isEmpty() && highlightTicks > 0) {
            for (UUID uuid : highlightedPlayers) {
                Player target = player.level().getPlayerByUUID(uuid);
                if (target != null && !target.hasEffect(MobEffects.GLOWING)) {
                    target.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING, 40, 0, false, false, true));
                }
            }
        }
    }

    public void sync() {
        ModComponents.ZHIZHANG.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("skillCooldown", skillCooldown);
        tag.putInt("guardianImmunityTicks", guardianImmunityTicks);
        tag.putInt("pendingDetectTimer", pendingDetectTimer);
        tag.putInt("highlightTicks", highlightTicks);

        // 写入高亮玩家列表
        ListTag highlightList = new ListTag();
        for (UUID uuid : highlightedPlayers) {
            highlightList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("highlightedPlayers", highlightList);

        // 写入待检测目标列表
        ListTag pendingList = new ListTag();
        for (UUID uuid : pendingTargets) {
            pendingList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("pendingTargets", pendingList);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.skillCooldown = tag.contains("skillCooldown") ? tag.getInt("skillCooldown") : 0;
        this.guardianImmunityTicks = tag.contains("guardianImmunityTicks") ? tag.getInt("guardianImmunityTicks") : 0;
        this.pendingDetectTimer = tag.contains("pendingDetectTimer") ? tag.getInt("pendingDetectTimer") : 0;
        this.highlightTicks = tag.contains("highlightTicks") ? tag.getInt("highlightTicks") : 0;

        // 读取高亮玩家列表
        this.highlightedPlayers.clear();
        if (tag.contains("highlightedPlayers")) {
            ListTag list = tag.getList("highlightedPlayers", 8); // 8 = STRING type
            for (int i = 0; i < list.size(); i++) {
                try {
                    highlightedPlayers.add(UUID.fromString(list.getString(i)));
                } catch (Exception ignored) {}
            }
        }

        // 读取待检测目标列表
        this.pendingTargets.clear();
        if (tag.contains("pendingTargets")) {
            ListTag list = tag.getList("pendingTargets", 8);
            for (int i = 0; i < list.size(); i++) {
                try {
                    pendingTargets.add(UUID.fromString(list.getString(i)));
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
