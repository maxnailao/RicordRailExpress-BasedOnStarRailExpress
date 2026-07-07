package org.agmas.noellesroles.game.roles.killer.mengyan;

import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;

import java.util.UUID;

/**
 * 梦魇角色组件
 * 
 * 梦魇侧（当玩家是梦魇时）:
 * - 可对一名玩家施加"恐惧"
 * - 目标必须在20秒内上床睡满10秒，否则降低75%理智+获得噩梦效果+梦魇获得75金币
 * 
 * 目标侧（当玩家被施加恐惧时）:
 * - isUnderFear 标记 + fearEndTime 用于HUD显示
 */
public class MengyanPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<MengyanPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mengyan"),
            MengyanPlayerComponent.class);

    private final Player player;

    // ========== 梦魇侧字段 ==========
    /** 当前恐惧目标UUID */
    public UUID fearTarget;
    /** 恐惧倒计时剩余tick (20秒 = 400tick) */
    public int fearRemainingTicks;
    /** 目标已累积的睡眠tick (需达200tick = 10秒) */
    public int fearSleepAccumulated;
    /** 恐惧是否处于活跃状态 */
    public boolean fearActive;

    // ========== 目标侧字段 ==========
    /** 该玩家是否正处于被恐惧状态 */
    public boolean isUnderFear;
    /** 恐惧结束时的gameTime（用于客户端HUD倒计时显示） */
    public long fearEndTime;
    /** 施加恐惧的梦魇玩家UUID（用于检测梦魇是否死亡以清理目标状态） */
    public UUID fearedBy;

    // ========== 常量 ==========
    /** 恐惧倒计时总时长 (20秒 = 400tick) */
    private static final int FEAR_DURATION_TICKS = 400;
    /** 需要睡满的时间 (10秒 = 200tick) */
    private static final int REQUIRED_SLEEP_TICKS = 200;
    /** 恐惧失败惩罚：降低理智百分比 */
    private static final float FEAR_PENALTY_MOOD = 0.75f;
    /** 恐惧失败奖励金币 */
    private static final int FEAR_REWARD_COINS = 75;
    /** 噩梦效果持续时间（足够长以覆盖整局，用tick表示，约30分钟） */
    private static final int NIGHTMARE_DURATION_TICKS = 36000;

    public MengyanPlayerComponent(Player player) {
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
        KEY.sync(player);
    }

    /**
     * 使用恐惧技能
     * @param targetUuid 目标玩家UUID
     * @return 是否成功
     */
    public boolean useSkill(UUID targetUuid) {
        if (player.level().isClientSide()) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        // 验证：旁观者不可用
        if (GameUtils.isPlayerEliminated(serverPlayer)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) return false;

        // 验证：已有活跃恐惧不可用
        if (fearActive) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.mengyan.fear_already_active")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 验证目标
        ServerLevel serverLevel = serverPlayer.serverLevel();
        ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayer(targetUuid);
        if (target == null || target == serverPlayer) return false;

        // 验证目标存活
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.mengyan.target_invalid")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 设置梦魇侧状态
        this.fearTarget = targetUuid;
        this.fearRemainingTicks = FEAR_DURATION_TICKS;
        this.fearSleepAccumulated = 0;
        this.fearActive = true;

        // 设置目标侧状态
        MengyanPlayerComponent targetComp = ModComponents.MENGYAN.get(target);
        targetComp.isUnderFear = true;
        targetComp.fearEndTime = target.level().getGameTime() + FEAR_DURATION_TICKS;
        targetComp.fearedBy = this.player.getUUID();
        targetComp.sync();

        // 通知梦魇
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.mengyan.fear_applied",
                        target.getName().getString()).withStyle(ChatFormatting.DARK_PURPLE), true);

        // 通知目标
        target.displayClientMessage(
                Component.translatable("message.noellesroles.mengyan.you_are_feared")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);

        this.sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // ===== 目标侧：检查施恐者是否存活 =====
        if (isUnderFear && fearedBy != null) {
            ServerLevel level = serverPlayer.serverLevel();
            ServerPlayer mengyanPlayer = level.getServer().getPlayerList().getPlayer(fearedBy);
            if (mengyanPlayer == null || GameUtils.isPlayerEliminated(mengyanPlayer)) {
                // 梦魇已死亡或掉线：清除目标的恐惧状态
                this.isUnderFear = false;
                this.fearEndTime = 0;
                this.fearedBy = null;
                this.sync();
            }
        }

        // ===== 梦魇侧：恐惧倒计时逻辑 =====
        if (!fearActive || fearTarget == null) return;

        // 获取目标玩家
        ServerLevel serverLevel = serverPlayer.serverLevel();
        ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayer(fearTarget);

        // 目标不存在或已淘汰：重置
        if (target == null || GameUtils.isPlayerEliminated(target)) {
            clearFearState();
            return;
        }

        boolean stateChanged = false;

        // 恐惧期间：给目标施加缓慢I + 每秒-1理智值
        long gameTime = serverLevel.getGameTime();
        if (gameTime % 20 == 0) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    40, // 2秒持续，下次tick前覆盖
                    0,  // 等级0 = 缓慢I
                    true,  // ambient
                    false, // showParticles
                    false  // showIcon
            ));
            SREPlayerMoodComponent targetMood = SREPlayerMoodComponent.KEY.get(target);
            targetMood.addMood(-0.01f); // 1理智 = 0.01 mood值，每秒-1理智
        }

        if (target.isSleeping()) {
            // 目标正在睡觉：累积睡眠时间（必须连续），暂停倒计时
            fearSleepAccumulated++;
            stateChanged = true;

            if (fearSleepAccumulated >= REQUIRED_SLEEP_TICKS) {
                // 睡满10秒：成功解除恐惧
                target.displayClientMessage(
                        Component.translatable("message.noellesroles.mengyan.fear_released")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.mengyan.target_released",
                                target.getName().getString()).withStyle(ChatFormatting.GREEN), true);

                clearFearOnTarget(target);
                clearFearState();
                return;
            }
        } else {
            // 目标不在床上：重置睡眠进度（必须连续睡满10秒），继续倒计时
            if (fearSleepAccumulated > 0) {
                fearSleepAccumulated = 0;
                stateChanged = true;
            }
            fearRemainingTicks--;
            stateChanged = true;

            if (fearRemainingTicks <= 0) {
                // 超时惩罚
                applyPenalty(serverPlayer, target);

                clearFearOnTarget(target);
                clearFearState();
                return;
            }
        }

        // 每20tick同步一次（减少网络开销），状态突变时立即同步
        if (stateChanged || fearRemainingTicks % 20 == 0) {
            // 同步目标侧倒计时
            MengyanPlayerComponent targetComp = ModComponents.MENGYAN.get(target);
            if (targetComp.isUnderFear) {
                targetComp.fearEndTime = serverLevel.getGameTime() + fearRemainingTicks;
                targetComp.sync();
            }
            this.sync();
        }
    }

    /**
     * 施加恐惧失败惩罚
     */
    private void applyPenalty(ServerPlayer mengyan, ServerPlayer target) {
        // 降低目标75%理智值
        SREPlayerMoodComponent targetMood = SREPlayerMoodComponent.KEY.get(target);
        targetMood.addMood(-FEAR_PENALTY_MOOD);

        // 给目标施加/升级噩梦效果
        applyNightmareEffect(target);

        // 梦魇获得75金币
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(mengyan);
        shop.addToBalance(FEAR_REWARD_COINS);

        // 通知
        target.displayClientMessage(
                Component.translatable("message.noellesroles.mengyan.fear_failed")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        mengyan.displayClientMessage(
                Component.translatable("message.noellesroles.mengyan.fear_success",
                        target.getName().getString()).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /**
     * 给目标施加或升级噩梦效果
     */
    private void applyNightmareEffect(ServerPlayer target) {
        MobEffectInstance existing = target.getEffect(ModEffects.NIGHTMARE);

        if (existing == null) {
            // 无噩梦效果：施加1层 (amplifier=0)
            target.addEffect(new MobEffectInstance(
                    ModEffects.NIGHTMARE,
                    NIGHTMARE_DURATION_TICKS,
                    0,  // 1层
                    true,
                    true,
                    true
            ));
        } else if (existing.getAmplifier() == 0) {
            // 已有1层：升级为2层 (amplifier=1)
            target.removeEffect(ModEffects.NIGHTMARE);
            target.addEffect(new MobEffectInstance(
                    ModEffects.NIGHTMARE,
                    NIGHTMARE_DURATION_TICKS,
                    1,  // 2层
                    true,
                    true,
                    true
            ));
        } else {
            // 已有2层：刷新持续时间
            target.removeEffect(ModEffects.NIGHTMARE);
            target.addEffect(new MobEffectInstance(
                    ModEffects.NIGHTMARE,
                    NIGHTMARE_DURATION_TICKS,
                    1,  // 2层
                    true,
                    true,
                    true
            ));
        }
    }

    /**
     * 清除目标侧的恐惧状态
     */
    private void clearFearOnTarget(ServerPlayer target) {
        MengyanPlayerComponent targetComp = ModComponents.MENGYAN.get(target);
        targetComp.isUnderFear = false;
        targetComp.fearEndTime = 0;
        targetComp.fearedBy = null;
        targetComp.sync();
    }

    /**
     * 清除梦魇侧的恐惧状态
     */
    public void clearFearState() {
        this.fearTarget = null;
        this.fearRemainingTicks = 0;
        this.fearSleepAccumulated = 0;
        this.fearActive = false;
        this.sync();
    }

    @Override
    public void clientTick() {
        // 客户端无特殊tick逻辑，HUD通过组件数据渲染
    }

    @Override
    public void init() {
        this.fearTarget = null;
        this.fearRemainingTicks = 0;
        this.fearSleepAccumulated = 0;
        this.fearActive = false;
        this.isUnderFear = false;
        this.fearEndTime = 0;
        this.fearedBy = null;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    // ========== 同步数据（服务端 → 客户端） ==========

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 梦魇侧
        tag.putBoolean("fearActive", fearActive);
        if (fearTarget != null) {
            tag.putUUID("fearTarget", fearTarget);
        }
        tag.putInt("fearRemainingTicks", fearRemainingTicks);
        tag.putInt("fearSleepAccumulated", fearSleepAccumulated);

        // 目标侧
        tag.putBoolean("isUnderFear", isUnderFear);
        tag.putLong("fearEndTime", fearEndTime);
        if (fearedBy != null) {
            tag.putUUID("fearedBy", fearedBy);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 梦魇侧
        this.fearActive = tag.getBoolean("fearActive");
        this.fearTarget = tag.hasUUID("fearTarget") ? tag.getUUID("fearTarget") : null;
        this.fearRemainingTicks = tag.getInt("fearRemainingTicks");
        this.fearSleepAccumulated = tag.getInt("fearSleepAccumulated");

        // 目标侧
        this.isUnderFear = tag.getBoolean("isUnderFear");
        this.fearEndTime = tag.getLong("fearEndTime");
        this.fearedBy = tag.hasUUID("fearedBy") ? tag.getUUID("fearedBy") : null;
    }

    // ========== 持久化数据（不持久化，局内状态） ==========

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
