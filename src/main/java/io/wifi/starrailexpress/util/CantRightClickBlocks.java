package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.AreasSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.FlowerPotBlock;

import java.util.*;

public class CantRightClickBlocks {
    public static final Set<String> CANNOT_INTERACT_IDS = new HashSet<>(Set.of(
            "charta:wheat_beer_glass",
            "charta:sorghum_beer_glass",
            "supplementaries:safe",
            "supplementaries:statue",
            "handcrafted:yellow_crockery_combo",
            "supplementaries:fire_pit",
            "supplementaries:item_shelf",
            "supplementaries:pedestal"));
    // 原版工作方块集合
    public static final Set<Block> ALLOWED_BLOCKS = new HashSet<>();

    static {
        // 允许的方块集合
        Collections.addAll(ALLOWED_BLOCKS,
                Blocks.LECTERN

        // 这里可以添加其他允许的方块
        );
    }
    public static final Set<Block> VANILLA_WORKSTATIONS = new HashSet<>(Set.of(
            Blocks.CHISELED_BOOKSHELF,
            Blocks.CRAFTING_TABLE,
            Blocks.FURNACE,
            Blocks.BLAST_FURNACE,
            Blocks.SMOKER,
            Blocks.CAMPFIRE,
            Blocks.SOUL_CAMPFIRE,
            Blocks.CARTOGRAPHY_TABLE,
            Blocks.FLETCHING_TABLE,
            Blocks.SMITHING_TABLE,
            Blocks.GRINDSTONE,
            Blocks.STONECUTTER,
            Blocks.LOOM,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL,
            Blocks.BREWING_STAND,
            Blocks.CAULDRON,
            Blocks.BELL,
            Blocks.ENCHANTING_TABLE,
            Blocks.BEACON,
            Blocks.RESPAWN_ANCHOR,
            Blocks.ENDER_CHEST,
            Blocks.SHULKER_BOX,
            Blocks.FLOWER_POT,
            // 更多需要限制的方块...
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.BARREL,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.HOPPER,
            Blocks.COMPOSTER,
            Blocks.CRAFTER));
    public static List<String> cantClickItems = new ArrayList<>(List.of(
            "supplementaries:item_shelf",
            "supplementaries:notice_board"));

    /**
     * 判断是否应该阻止与方块的交互
     * 
     * @param player
     */
    public static boolean shouldPreventInteraction(Player player, Block block, Level level) {
        if (SRE.isLobby)
            return false;
        // 开启摇铃会议的地图：放行钟方块。否则服务端 ServerGamePacketListenerImpl.handleUseItemOn
        // 会因 canInteractWithBlock 返回 false 直接丢弃右键包，MeetingManager 监听钟交互的
        // UseBlockCallback 永远不会触发，摇铃会议形同虚设。
        if (block == Blocks.BELL && isBellMeetingAllowed(level))
            return false;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        String string = blockId.toString();

        return !isAllowedBlock(player, block, level) || cantClickItems.contains(string)
                || isCannotInteractId(blockId);
    }

    /** 当前地图是否开启摇铃会议，判定条件与 MeetingManager.tryBellMeeting 保持一致。 */
    private static boolean isBellMeetingAllowed(Level level) {
        if (level == null)
            return false;
        try {
            AreasSettings settings = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level).areasSettings;
            return settings != null && settings.meetingEnabled && settings.bellMeetingEnabled;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 检查方块是否在允许的列表中
     * 
     * @param player
     */
    public static boolean isAllowedBlock(Player player, Block block, Level level) {
        // 如果在允许列表中，直接返回true

        if (ALLOWED_BLOCKS.contains(block)) {
            return true;
        }
        if (block instanceof FlowerPotBlock)
            return false;
        if (block instanceof DecoratedPotBlock)
            return false;
        // 如果是原版工作方块，禁止交互
        if (VANILLA_WORKSTATIONS.contains(block)) {
            return false;
        }
        ResourceLocation keys = BuiltInRegistries.BLOCK.getKey(block);
        if (isCannotInteractId(keys)) {
            return false;
        }
        if (keys.getPath().contains("shulker_box")) {
            return false;
        }
        if (keys.getNamespace().equals("handcrafted")) {
            if (player.isShiftKeyDown()) {
                return false;
            }
            if (keys.getPath().contains("counter")) {
                return false;
            }
            if (keys.getPath().contains("cupboard")) {
                return false;
            }
            if (keys.getPath().contains("drawer")) {
                return false;
            }
            if (keys.getPath().contains("table")) {
                return false;
            }
            if (keys.getPath().contains("nightstand")) {
                return false;
            }
            if (keys.getPath().contains("desk")) {
                return false;
            }
            if (keys.getPath().contains("shelf")) {
                return false;
            }
            if (keys.getPath().contains("fancy_bed")) {
                return false;
            }
        }
        // 检查是否为TMM模组的方块
        // ResourceLocation blockId = level().registryAccess()
        // .registryOrThrow(Registries.BLOCK)
        // .getKey(block);
        //
        // String namespace = blockId.getNamespace();

        return true;
        // 允许TMM模组的方块（除了"minopp"命名空间）
    }

    private static boolean isCannotInteractId(ResourceLocation blockId) {
        if (CANNOT_INTERACT_IDS.contains(blockId.toString())) {
            return true;
        }
        return "charta".equals(blockId.getNamespace())
                && (blockId.getPath().endsWith("_card_table") || blockId.getPath().endsWith("_bar_shelf"));
    }
}
