package org.agmas.noellesroles.game.roles.innocence.shilaimu;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 史莱姆组件
 *
 * <p>技能「史莱姆领域」：花费 75 金币将脚下 3x3 的方块临时变成史莱姆块，
 * 持续 20 秒后自动恢复为原有方块（服务端真实方块），冷却 30 秒由统一技能系统管理。
 *
 * <p>被动：存活期间自带跳跃提升 II 效果。
 */
public class ShilaimuPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ShilaimuPlayerComponent> KEY = ModComponents.SHILAIMU;

    /** 技能花费（金币） */
    public static final int SKILL_COST = 75;
    /** 史莱姆块持续时间（20 秒） */
    public static final int SLIME_DURATION_TICKS = 20 * 20;
    /** 技能影响范围：以脚下为中心的 3x3 */
    public static final int FIELD_RADIUS = 1;

    private final Player player;

    /** 生效中的临时方块：位置 -> 原始方块数据 */
    private final Map<BlockPos, SlimeBlockData> activeBlocks = new LinkedHashMap<>();

    public ShilaimuPlayerComponent(Player player) {
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

    @Override
    public void init() {
        // 注意：角色分配事件（ModdedRoleAssigned）在开局时会多次触发 init（分波分配职业/修饰符），
        // 不能无条件清理进行中的临时方块，否则刚放出的史莱姆块会凭空消失；
        // 开局/结束的彻底清理由 clear() 保证（框架在 onStartGame/onEndGame 先调 clear）。
        sync();
    }

    @Override
    public void clear() {
        // 回合结束：恢复所有未过期的史莱姆块为原有方块
        if (player instanceof ServerPlayer sp && !activeBlocks.isEmpty()) {
            ServerLevel serverLevel = sp.serverLevel();
            activeBlocks.forEach((pos, data) -> restoreBlock(serverLevel, pos, data.originalState));
            if (GameUtils.isPlayerAliveAndSurvival(sp)) {
                sp.removeEffect(MobEffects.JUMP);
            }
        }
        activeBlocks.clear();
        sync();
    }

    /**
     * 技能入口：将脚下 3x3 的方块临时变成史莱姆块。
     * 返回 true 才会消耗冷却。
     */
    public boolean useSkill(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.SHILAIMU)) {
            return false;
        }
        // 上一轮史莱姆块尚未完全过期时不允许再次释放（冷却 30s > 持续 20s，正常不会触发）
        if (!activeBlocks.isEmpty()) {
            return false;
        }

        // 检查金币
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < SKILL_COST) {
            sp.displayClientMessage(
                    Component.translatable("hud.noellesroles.shilaimu.not_enough_money", SKILL_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 收集脚下 3x3 的可替换方块（跳过空气、已是史莱姆块、带方块实体的方块）
        ServerLevel serverLevel = sp.serverLevel();
        BlockPos center = sp.blockPosition().below();
        List<BlockPos> targets = new ArrayList<>();
        for (int dx = -FIELD_RADIUS; dx <= FIELD_RADIUS; dx++) {
            for (int dz = -FIELD_RADIUS; dz <= FIELD_RADIUS; dz++) {
                BlockPos pos = center.offset(dx, 0, dz).immutable();
                BlockState state = serverLevel.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.SLIME_BLOCK)) {
                    continue;
                }
                if (serverLevel.getBlockEntity(pos) != null) {
                    continue;
                }
                targets.add(pos);
            }
        }
        if (targets.isEmpty()) {
            sp.displayClientMessage(
                    Component.translatable("hud.noellesroles.shilaimu.no_blocks")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 扣除金币
        shop.addToBalance(-SKILL_COST);

        // 记录原始方块状态并替换为史莱姆块
        for (BlockPos pos : targets) {
            BlockState original = serverLevel.getBlockState(pos);
            activeBlocks.put(pos, new SlimeBlockData(SLIME_DURATION_TICKS, original));
            serverLevel.setBlock(pos, Blocks.SLIME_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }

        // 音效与提示
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
        sp.displayClientMessage(
                Component.translatable("hud.noellesroles.shilaimu.placed")
                        .withStyle(ChatFormatting.GREEN),
                true);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }

        // 处理临时史莱姆块过期，恢复原有方块
        if (!activeBlocks.isEmpty()) {
            ServerLevel serverLevel = sp.serverLevel();
            Iterator<Map.Entry<BlockPos, SlimeBlockData>> iterator = activeBlocks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, SlimeBlockData> entry = iterator.next();
                SlimeBlockData data = entry.getValue();
                data.remainingTicks--;
                if (data.remainingTicks <= 0) {
                    restoreBlock(serverLevel, entry.getKey(), data.originalState);
                    iterator.remove();
                }
            }
        }

        // 被动：每秒为存活的史莱姆续上跳跃提升 II（短持续时间，周期续杯）
        if (sp.level().getGameTime() % 20 == 0 && GameUtils.isPlayerAliveAndSurvival(sp)) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (gameWorld.isRole(sp, ModRoles.SHILAIMU)) {
                sp.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false, false));
            }
        }
    }

    /**
     * 恢复单个方块为原有状态。
     * 仅在当前方块仍是史莱姆块时恢复，避免覆盖期间被其他玩家改动过的方块。
     */
    private static void restoreBlock(ServerLevel serverLevel, BlockPos pos, BlockState original) {
        if (!serverLevel.getBlockState(pos).is(Blocks.SLIME_BLOCK)) {
            return;
        }
        serverLevel.setBlock(pos, original, Block.UPDATE_ALL);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 临时方块由服务端管理，无需同步
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

    /** 临时史莱姆块数据：剩余时间与替换前的原始方块状态 */
    private static class SlimeBlockData {
        public int remainingTicks;
        public final BlockState originalState;

        public SlimeBlockData(int durationTicks, BlockState originalState) {
            this.remainingTicks = durationTicks;
            this.originalState = originalState;
        }
    }
}
