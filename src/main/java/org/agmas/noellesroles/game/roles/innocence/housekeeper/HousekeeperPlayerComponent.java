package org.agmas.noellesroles.game.roles.innocence.housekeeper;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * 管家组件
 *
 * 管理管家技能：在原地放置真实的临时功能方块（服务端放置）
 * - 按 G 放置当前选中的家具（花费100金币）
 * - 按 Shift+G 循环切换家具类型
 * - 家具存在30秒后消失（服务端移除方块）
 * - 必须在落地时才能放置（空中不可使用）
 *
 * 家具类型：
 * 1. 床（双格方块）
 * 2. 沙发（单格方块）
 * 3. 马桶（单格方块）
 * 4. 音符盒（单格方块）
 */
public class HousekeeperPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<HousekeeperPlayerComponent> KEY = ModComponents.HOUSEKEEPER;

    // ==================== 常量定义 ====================

    /** 放置花费（金币） */
    public static final int PLACE_COST = 100;

    /** 家具存在时间（30秒 = 600 tick） */
    public static final int FURNITURE_DURATION = 600;

    // ==================== 家具类型枚举 ====================

    public enum FurnitureType {
        BED("trainmurdermystery:white_trimmed_bed", "bed"),
        SOFA("trainmurdermystery:white_lounge_couch", "sofa"),
        TOILET("trainmurdermystery:light_toilet", "toilet"),
        NOTE_BLOCK("minecraft:note_block", "note_block");

        public final String blockId;
        public final String typeName;

        FurnitureType(String blockId, String typeName) {
            this.blockId = blockId;
            this.typeName = typeName;
        }

        public boolean isBed() {
            return this == BED;
        }

        public FurnitureType next() {
            FurnitureType[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        /**
         * 获取该家具类型对应的方块对象
         */
        public Block getBlock() {
            ResourceLocation id = ResourceLocation.tryParse(blockId);
            if (id == null) return Blocks.AIR;
            return BuiltInRegistries.BLOCK.get(id);
        }
    }

    // ==================== 状态变量 ====================

    private final Player player;

    /** 当前选中的家具类型 */
    public FurnitureType currentType = FurnitureType.BED;

    /** 已放置的家具数据：furnitureId -> FurnitureData */
    private final Map<UUID, FurnitureData> placedFurniture = new LinkedHashMap<>();

    // ==================== 构造函数 ====================

    public HousekeeperPlayerComponent(Player player) {
        this.player = player;
    }

    // ==================== 初始化/清理 ====================

    @Override
    public void init() {
        this.currentType = FurnitureType.BED;
        this.placedFurniture.clear();
        this.sync();
    }

    @Override
    public void clear() {
        // 先移除所有已放置的方块
        removeAllPlacedBlocks();
        this.init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    // ==================== 技能逻辑 ====================

    /**
     * 切换家具类型
     */
    public void cycleType() {
        this.currentType = this.currentType.next();
        if (player instanceof ServerPlayer serverPlayer) {
            Component typeName = Component.translatable("hud.noellesroles.housekeeper.type." + currentType.typeName);
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.housekeeper.type_switched", typeName)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
        this.sync();
    }

    /**
     * 放置家具（服务端真实方块）
     */
    public boolean placeFurniture() {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 检查是否在空中（包括跳跃滞空）
        if (!player.onGround()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.housekeeper.in_air")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 检查角色
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.HOUSEKEEPER)) {
            return false;
        }

        // 检查金币
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < PLACE_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.housekeeper.not_enough_money", PLACE_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 计算放置位置
        BlockPos basePos = player.blockPosition();
        Direction facing = player.getDirection();
        Block targetBlock = currentType.getBlock();

        if (targetBlock == Blocks.AIR) {
            return false; // 方块未注册
        }

        if (currentType.isBed()) {
            // 床是双格方块：脚部 + 头部
            BlockPos footPos = basePos;
            BlockPos headPos = basePos.relative(facing);

            // 检查两个位置是否都是空气
            if (!serverLevel.getBlockState(footPos).isAir()
                    || !serverLevel.getBlockState(headPos).isAir()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("hud.noellesroles.housekeeper.no_space")
                                .withStyle(ChatFormatting.RED),
                        true);
                return false;
            }

            // 检查维度是否允许放置床
            if (!serverLevel.dimensionType().bedWorks()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("hud.noellesroles.housekeeper.bed_not_work")
                                .withStyle(ChatFormatting.RED),
                        true);
                return false;
            }

            // 扣除金币
            shop.addToBalance(-PLACE_COST);

            // 保存原始方块状态
            BlockState originalFoot = serverLevel.getBlockState(footPos);
            BlockState originalHead = serverLevel.getBlockState(headPos);

            // 服务端放置床方块（带 BedPart 属性）
            BlockState footState = targetBlock.defaultBlockState();
            if (footState.hasProperty(BedBlock.PART)) {
                footState = footState
                        .setValue(BedBlock.PART, BedPart.FOOT)
                        .setValue(BedBlock.FACING, facing)
                        .setValue(BedBlock.OCCUPIED, false);
            }
            BlockState headState = targetBlock.defaultBlockState();
            if (headState.hasProperty(BedBlock.PART)) {
                headState = headState
                        .setValue(BedBlock.PART, BedPart.HEAD)
                        .setValue(BedBlock.FACING, facing)
                        .setValue(BedBlock.OCCUPIED, false);
            }

            serverLevel.setBlock(footPos, footState, Block.UPDATE_ALL);
            serverLevel.setBlock(headPos, headState, Block.UPDATE_ALL);

            // 记录家具数据
            UUID furnitureId = UUID.randomUUID();
            FurnitureData data = new FurnitureData(furnitureId,
                    List.of(footPos, headPos), currentType, FURNITURE_DURATION, facing,
                    Map.of(footPos, originalFoot, headPos, originalHead));
            placedFurniture.put(furnitureId, data);

        } else {
            // 单格方块
            if (!serverLevel.getBlockState(basePos).isAir()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("hud.noellesroles.housekeeper.no_space")
                                .withStyle(ChatFormatting.RED),
                        true);
                return false;
            }

            // 扣除金币
            shop.addToBalance(-PLACE_COST);

            // 保存原始方块状态
            BlockState original = serverLevel.getBlockState(basePos);

            // 服务端放置方块
            serverLevel.setBlock(basePos, targetBlock.defaultBlockState(), Block.UPDATE_ALL);

            // 记录家具数据
            UUID furnitureId = UUID.randomUUID();
            FurnitureData data = new FurnitureData(furnitureId,
                    List.of(basePos), currentType, FURNITURE_DURATION, facing,
                    Map.of(basePos, original));
            placedFurniture.put(furnitureId, data);
        }

        // 播放放置音效
        serverLevel.playSound(null,
                serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);

        serverPlayer.displayClientMessage(
                Component.translatable("hud.noellesroles.housekeeper.placed",
                        Component.translatable("hud.noellesroles.housekeeper.type." + currentType.typeName))
                        .withStyle(ChatFormatting.GREEN),
                true);

        this.sync();
        return true;
    }

    /**
     * 移除所有已放置的方块（服务端）
     */
    private void removeAllPlacedBlocks() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        for (FurnitureData data : placedFurniture.values()) {
            removeFurnitureBlocks(serverLevel, data);
        }
        placedFurniture.clear();
    }

    /**
     * 移除单件家具的方块（服务端）
     */
    private void removeFurnitureBlocks(ServerLevel serverLevel, FurnitureData data) {
        for (BlockPos pos : data.positions) {
            // 恢复原始方块状态（通常是空气）
            BlockState original = data.originalStates.get(pos);
            if (original != null) {
                serverLevel.setBlock(pos, original, Block.UPDATE_ALL);
            } else {
                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * 游戏结束时清除所有家具方块
     */
    public void clearAllFurniture() {
        removeAllPlacedBlocks();
    }

    // ==================== Getter ====================

    public FurnitureType getCurrentType() {
        return currentType;
    }

    // ==================== Tick 处理 ====================

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void serverTick() {
        if (placedFurniture.isEmpty()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 处理家具过期
        Iterator<Map.Entry<UUID, FurnitureData>> iterator = placedFurniture.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FurnitureData> entry = iterator.next();
            FurnitureData furniture = entry.getValue();
            furniture.remainingTicks--;
            if (furniture.remainingTicks <= 0) {
                // 过期：移除方块并恢复原始状态
                removeFurnitureBlocks(serverLevel, furniture);
                iterator.remove();
            }
        }
    }

    // ==================== 同步 ====================

    public void sync() {
        ModComponents.HOUSEKEEPER.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("currentType", this.currentType.ordinal());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.currentType = tag.contains("currentType")
                ? FurnitureType.values()[tag.getInt("currentType")]
                : FurnitureType.BED;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    // ==================== 家具数据类 ====================

    public static class FurnitureData {
        public final UUID furnitureId;
        public final List<BlockPos> positions;
        public final FurnitureType type;
        public int remainingTicks;
        public final Direction facing;
        public final Map<BlockPos, BlockState> originalStates;

        public FurnitureData(UUID furnitureId, List<BlockPos> positions,
                FurnitureType type, int durationTicks, Direction facing,
                Map<BlockPos, BlockState> originalStates) {
            this.furnitureId = furnitureId;
            this.positions = positions;
            this.type = type;
            this.remainingTicks = durationTicks;
            this.facing = facing;
            this.originalStates = originalStates;
        }
    }
}
