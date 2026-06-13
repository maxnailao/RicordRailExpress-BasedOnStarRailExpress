package org.agmas.noellesroles.game.roles.innocent.shushi;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.HashSet;
import java.util.Map;

/**
 * 术士玩家组件
 *
 * 平民阵营，真实心情，标准冲刺时间
 *
 * 技能：按下技能键花费125金币为前方最近玩家施加术语（射线距离6格）
 * Shift+技能键切换当前术语
 *
 * 术语：
 * - 速：对施加者赋予15秒速度1效果
 * - 疾：对施加者减少20s技能冷却和其背包内所有道具cd 3秒
 * - 缓：对施加者给予5秒缓慢2效果
 * - 聪：对施加者恢复15%的理智值
 */
public class ShuShiPlayerComponent implements RoleComponent {

    /** 组件键 */
    public static final ComponentKey<ShuShiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "shushi"),
            ShuShiPlayerComponent.class);

    /** 术语：速 - 15秒速度1 */
    public static final int TERM_SPEED = 0;

    /** 术语：疾 - 减少20s技能冷却和3s道具cd */
    public static final int TERM_SWIFT = 1;

    /** 术语：缓 - 5秒缓慢2 */
    public static final int TERM_SLOW = 2;

    /** 术语：聪 - 恢复15%理智 */
    public static final int TERM_SMART = 3;

    /** 术语总数 */
    public static final int TERM_COUNT = 4;

    /** 技能花费（金币） */
    public static final int SPELL_COST = 125;

    /** 射线距离（格） */
    public static final double RAY_RANGE = 6.0;

    private final Player player;

    /** 当前选择的术语索引 */
    private int currentTermIndex = TERM_SPEED;

    /**
     * 构造函数
     */
    public ShuShiPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.currentTermIndex = TERM_SPEED;
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        if (!gameWorldComponent.isRunning()) {
            return;
        }
        if (!gameWorldComponent.isRole(this.player, ModRoles.SHUSHI)) {
            return;
        }
        tag.putInt("CurrentTermIndex", this.currentTermIndex);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (!tag.contains("CurrentTermIndex")) {
            this.currentTermIndex = TERM_SPEED;
            return;
        }
        this.currentTermIndex = tag.getInt("CurrentTermIndex");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    /**
     * 切换术语（Shift+技能键）
     */
    public void switchTerm() {
        currentTermIndex = (currentTermIndex + 1) % TERM_COUNT;

        if (player instanceof ServerPlayer serverPlayer) {
            Component termName = getTermName(currentTermIndex);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.shushi.term_selected", termName)
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        }

        sync();
    }

    /**
     * 施放术语（技能键）
     */
    public void castSpell() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        ServerLevel serverLevel = serverPlayer.serverLevel();
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
        if (!gameWorldComponent.isRunning())
            return;

        // 检查金币是否足够
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < SPELL_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.shushi.insufficient_gold", SPELL_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 查找前方最近的玩家
        ServerPlayer target = findTargetInFront(serverPlayer, RAY_RANGE);
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.shushi.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 扣除金币
        shopComponent.balance -= SPELL_COST;
        shopComponent.sync();

        // 施加术语效果
        applyTermEffect(target, currentTermIndex);

        // 播放施法音效
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);

        // 通知施法者
        Component termName = getTermName(currentTermIndex);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.shushi.spell_cast", termName, target.getName())
                        .withStyle(ChatFormatting.GREEN),
                true);

        // 通知被施法者
        target.displayClientMessage(
                Component.translatable("message.noellesroles.shushi.spell_received", termName, serverPlayer.getName())
                        .withStyle(ChatFormatting.AQUA),
                true);

        sync();
    }

    /**
     * 查找前方最近的存活玩家
     *
     * @param caster 施法者
     * @param range  最大距离
     * @return 目标玩家，若无则返回 null
     */
    private ServerPlayer findTargetInFront(ServerPlayer caster, double range) {
        ServerLevel level = caster.serverLevel();
        Vec3 eyePos = caster.getEyePosition();
        Vec3 lookVec = caster.getLookAngle();

        ServerPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (ServerPlayer candidate : level.players()) {
            // 排除自己
            if (candidate == caster)
                continue;
            // 排除已淘汰的玩家
            if (GameUtils.isPlayerEliminated(candidate))
                continue;
            // 排除旁观者和创造模式
            if (!GameUtils.isPlayerAliveAndSurvival(candidate))
                continue;

            // 计算距离
            Vec3 targetPos = candidate.getEyePosition();
            Vec3 toTarget = targetPos.subtract(eyePos);
            double distance = toTarget.length();

            if (distance > range)
                continue;

            // 检查方向：看向量的点积是否为正（即在前方）
            // 允许约 60 度的锥形范围（dot > 0.5 相当于夹角 < 60°）
            double dot = lookVec.dot(toTarget.normalize());
            if (dot < 0.5)
                continue;

            if (distance < nearestDist) {
                nearestDist = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    /**
     * 对目标施加术语效果
     */
    private void applyTermEffect(ServerPlayer target, int termIndex) {
        switch (termIndex) {
            case TERM_SPEED:
                // 速：15秒速度1
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 15 * 20, 1,
                        false, true, true));
                break;
            case TERM_SWIFT:
                // 疾：减少20s技能冷却 + 背包内所有道具cd减少3s
                applySwiftEffect(target);
                break;
            case TERM_SLOW:
                // 缓：5秒缓慢2
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5 * 20, 2,
                        false, true, true));
                break;
            case TERM_SMART:
                // 聪：恢复15%理智值
                SREPlayerMoodComponent moodComponent = SREPlayerMoodComponent.KEY.get(target);
                if (moodComponent != null) {
                    moodComponent.addMood(0.15f);
                }
                break;
        }
    }

    /**
     * 施加"疾"效果：减少20s技能冷却 + 背包道具cd减少3s
     */
    private void applySwiftEffect(ServerPlayer target) {
        final int SKILL_CD_REDUCTION = 20 * 20; // 20秒 = 400 tick
        final int ITEM_CD_REDUCTION = 3 * 20;   // 3秒 = 60 tick

        // 减少技能冷却（SREAbilityPlayerComponent）
        SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(target);
        if (abilityComponent != null && abilityComponent.cooldown > 0) {
            abilityComponent.cooldown = Math.max(0, abilityComponent.cooldown - SKILL_CD_REDUCTION);
            abilityComponent.sync();
        }

        // 减少背包内所有道具的冷却
        ItemCooldowns cooldowns = target.getCooldowns();
        Map<Item, ItemCooldowns.CooldownInstance> cooldownMap = cooldowns.cooldowns;

        if (!cooldownMap.isEmpty()) {
            // 创建副本避免并发修改
            HashSet<Map.Entry<Item, ItemCooldowns.CooldownInstance>> entries = new HashSet<>(cooldownMap.entrySet());
            int currentTick = cooldowns.tickCount;

            for (Map.Entry<Item, ItemCooldowns.CooldownInstance> entry : entries) {
                ItemCooldowns.CooldownInstance instance = entry.getValue();
                int remaining = instance.endTime - currentTick;

                if (remaining > 0) {
                    // 减少3秒冷却，确保不会出现负数
                    int newRemaining = Math.max(0, remaining - ITEM_CD_REDUCTION);
                    int newEndTime = currentTick + newRemaining;

                    if (newRemaining <= 0) {
                        // 冷却已结束，移除该条目
                        cooldowns.removeCooldown(entry.getKey());
                    } else {
                        // 更新结束时间
                        cooldowns.cooldowns.put(entry.getKey(),
                                new ItemCooldowns.CooldownInstance(instance.startTime, newEndTime));
                    }
                }
            }
        }
    }

    /**
     * 获取当前术语索引
     */
    public int getCurrentTermIndex() {
        return currentTermIndex;
    }

    /**
     * 获取术语名称（翻译组件）
     */
    public static Component getTermName(int termIndex) {
        return switch (termIndex) {
            case TERM_SPEED -> Component.translatable("term.noellesroles.shushi.speed");
            case TERM_SWIFT -> Component.translatable("term.noellesroles.shushi.swift");
            case TERM_SLOW -> Component.translatable("term.noellesroles.shushi.slow");
            case TERM_SMART -> Component.translatable("term.noellesroles.shushi.smart");
            default -> Component.literal("未知术语");
        };
    }

    /**
     * 获取术语的翻译 key（用于 HUD 拼接）
     */
    public static String getTermKey(int termIndex) {
        return switch (termIndex) {
            case TERM_SPEED -> "speed";
            case TERM_SWIFT -> "swift";
            case TERM_SLOW -> "slow";
            case TERM_SMART -> "smart";
            default -> "unknown";
        };
    }

    /**
     * 同步组件数据到客户端
     */
    private void sync() {
        if (!player.level().isClientSide) {
            KEY.sync(player);
        }
    }
}
