package org.agmas.noellesroles.game.roles.innocence.pathfinder;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * 探路者组件
 *
 * 管理探路者技能：花费75金币在原地放置一盏灯（服务端真实方块）
 * - 按 G 放置灯（花费75金币，无放置冷却）
 * - 灯存在75秒后消失（服务端移除方块）
 * - 灯使用原版火把方块，为周围提供照明
 */
public class PathfinderPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<PathfinderPlayerComponent> KEY = ModComponents.PATHFINDER;

    // ==================== 常量定义 ====================

    /** 放置花费（金币） */
    public static final int PLACE_COST = 75;

    /** 灯存在时间（75秒 = 1500 tick） */
    public static final int LIGHT_DURATION = 1500;

    /** 灯方块：火把（原版发光方块，亮度等级14） */
    private static final Block LIGHT_BLOCK = Blocks.TORCH;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 已放置的灯数据：lightId -> LightData */
    private final Map<UUID, LightData> placedLights = new LinkedHashMap<>();

    // ==================== 构造函数 ====================

    public PathfinderPlayerComponent(Player player) {
        this.player = player;
    }

    // ==================== 初始化/清理 ====================

    @Override
    public void init() {
        this.placedLights.clear();
        this.sync();
    }

    @Override
    public void clear() {
        removeAllPlacedLights();
        this.init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    // ==================== 技能逻辑 ====================

    /**
     * 放置灯（服务端真实方块）
     * 无放置冷却，只要金币足够即可放置
     */
    public boolean placeLight() {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 检查角色
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.PATHFINDER)) {
            return false;
        }

        // 检查金币
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < PLACE_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.pathfinder.not_enough_money", PLACE_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 计算放置位置（玩家脚下方块位置）
        BlockPos placePos = player.blockPosition();

        // 检查放置位置是否为空气
        if (!serverLevel.getBlockState(placePos).isAir()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.pathfinder.no_space")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 扣除金币
        shop.addToBalance(-PLACE_COST);

        // 保存原始方块状态
        BlockState original = serverLevel.getBlockState(placePos);

        // 服务端放置灯方块
        serverLevel.setBlock(placePos, LIGHT_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        // 记录灯数据
        UUID lightId = UUID.randomUUID();
        LightData data = new LightData(lightId, placePos, LIGHT_DURATION, original);
        placedLights.put(lightId, data);

        // 播放放置音效
        serverLevel.playSound(null,
                serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);

        serverPlayer.displayClientMessage(
                Component.translatable("hud.noellesroles.pathfinder.placed")
                        .withStyle(ChatFormatting.GREEN),
                true);

        this.sync();
        return true;
    }

    /**
     * 移除所有已放置的灯（服务端）
     */
    private void removeAllPlacedLights() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        for (LightData data : placedLights.values()) {
            removeLightBlock(serverLevel, data);
        }
        placedLights.clear();
    }

    /**
     * 移除单盏灯的方块（服务端）
     */
    private void removeLightBlock(ServerLevel serverLevel, LightData data) {
        BlockPos pos = data.position;
        // 恢复原始方块状态（通常是空气）
        if (data.originalState != null) {
            serverLevel.setBlock(pos, data.originalState, Block.UPDATE_ALL);
        } else {
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ==================== Tick 处理 ====================

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void serverTick() {
        if (placedLights.isEmpty()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 处理灯过期
        Iterator<Map.Entry<UUID, LightData>> iterator = placedLights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LightData> entry = iterator.next();
            LightData light = entry.getValue();
            light.remainingTicks--;
            if (light.remainingTicks <= 0) {
                // 过期：移除方块并恢复原始状态
                removeLightBlock(serverLevel, light);
                iterator.remove();
            }
        }
    }

    // ==================== 同步 ====================

    public void sync() {
        ModComponents.PATHFINDER.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 无需同步额外数据，灯的位置由服务端管理
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 无需读取额外数据
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    // ==================== 灯数据类 ====================

    public static class LightData {
        public final UUID lightId;
        public final BlockPos position;
        public int remainingTicks;
        public final BlockState originalState;

        public LightData(UUID lightId, BlockPos position, int durationTicks, BlockState originalState) {
            this.lightId = lightId;
            this.position = position;
            this.remainingTicks = durationTicks;
            this.originalState = originalState;
        }
    }
}
