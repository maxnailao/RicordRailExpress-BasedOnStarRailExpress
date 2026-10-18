package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMItems;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block.*;
import org.agmas.noellesroles.content.block_entity.*;

import static io.wifi.starrailexpress.index.TMMBlocks.DARK_STEEL;

public interface ModBlocks {
    public static ResourceKey<CreativeModeTab> BLOCK_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Noellesroles.id("block"));
    public static final BlockRegistrar blockRegistrar = new BlockRegistrar(Noellesroles.MOD_ID);
    public static final BlockEntityTypeRegistrar blockEntityRegistrar = new BlockEntityTypeRegistrar(
            Noellesroles.MOD_ID);

    Block VENDING_MACHINES_BLOCK = registerBlockMultiTab("vending_machines",
            new VendingMachinesBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion()),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    BlockEntityType<VendingMachinesBlockEntity> VENDING_MACHINES_BLOCK_ENTITY = blockEntityRegistrar.create(
            "vending_machines",
            BlockEntityType.Builder.of(VendingMachinesBlockEntity::new,
                    ModBlocks.VENDING_MACHINES_BLOCK));
    Block LOTTERY_MACHINE_BLOCK = registerBlockMultiTab("lottery_machine",
            new LotteryMachineBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion()),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    BlockEntityType<LotteryMachineBlockEntity> LOTTERY_MACHINE_BLOCK_ENTITY = blockEntityRegistrar.create(
            "lottery_machine",
            BlockEntityType.Builder.of(LotteryMachineBlockEntity::new,
                    ModBlocks.LOTTERY_MACHINE_BLOCK));
    // 创建轮盘赌桌方块
    Block DEVIL_ROULETTE_TABLE = registerBlockMultiTab("devil_roulette_table",
            new DevilRouletteTable(),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    Block FLARE_BLOCK = registerBlock("flare_block",
            new FlareBlock());
    Block SUPPLY_CRATE_BLOCK = registerBlockMultiTab("supply_crate",
            new SupplyCrateBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion()),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    BlockEntityType<SupplyCrateBlockEntity> SUPPLY_CRATE_BLOCK_ENTITY = blockEntityRegistrar.create(
            "supply_crate",
            BlockEntityType.Builder.of(SupplyCrateBlockEntity::new,
                    ModBlocks.SUPPLY_CRATE_BLOCK));

    // ===== 重刑犯 & 狱警：重刑犯生成方块 + 关押门，注册到 SRE 功能方块（DECORATION_GROUP） =====
    // 重刑犯生成方块：原版标靶材质（方块模型 parent 指向 minecraft:block/target）；开局用于定位并传送重刑犯。
    Block CONVICT_SPAWN_BLOCK = registerBlockMultiTab("convict_spawn_block",
            new ConvictSpawnBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(-1.0F, 3600000.0F)),
            TMMItems.DECORATION_GROUP, BLOCK_CREATIVE_GROUP);

    // 关押门：仅狱警钥匙/撬棍可开；被撬棍强行破坏时触发永久警报（见 DetentionDoorBlockEntity#blast）。
    Block DETENTION_DOOR = registerBlockMultiTab("detention_door",
            new DetentionDoorBlock(() -> ModBlocks.DETENTION_DOOR_ENTITY,
                    BlockBehaviour.Properties
                            .ofFullCopy(io.wifi.starrailexpress.index.TMMBlocks.SMALL_GLASS_DOOR)
                            .sound(SoundType.COPPER)),
            TMMItems.DECORATION_GROUP, BLOCK_CREATIVE_GROUP);
    BlockEntityType<SmallDoorBlockEntity> DETENTION_DOOR_ENTITY = blockEntityRegistrar.create("detention_door",
            BlockEntityType.Builder.<SmallDoorBlockEntity>of(
                    (pos, state) -> new DetentionDoorBlockEntity(ModBlocks.DETENTION_DOOR_ENTITY, pos, state),
                    ModBlocks.DETENTION_DOOR));

    // 反人员地雷（由地雷物品长按右键布设，不注册独立方块物品）
    Block LANDMINE_BLOCK = Registry.register(BuiltInRegistries.BLOCK, Noellesroles.id("landmine"),
            new LandmineBlock(BlockBehaviour.Properties.of().noCollission().noOcclusion().instabreak()
                    .sound(SoundType.METAL)));
    BlockEntityType<LandmineBlockEntity> LANDMINE_BLOCK_ENTITY = blockEntityRegistrar.create("landmine",
            BlockEntityType.Builder.of(LandmineBlockEntity::new, LANDMINE_BLOCK));

    // 创建轮盘赌桌方块实体类型
    BlockEntityType<DevilRouletteTableEntity> DEVIL_ROULETTE_TABLE_ENTITY = blockEntityRegistrar.create(
            "devil_roulette_table",
            BlockEntityType.Builder.of(DevilRouletteTableEntity::new,
                    new Block[] { ModBlocks.DEVIL_ROULETTE_TABLE }));
    // Kill blocks (OP utilities)
    @SuppressWarnings("unchecked")
    Block KILL_BLOCK = blockRegistrar.createWithItem("kill_block",
            new KillBlock(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).noOcclusion()),
            CreativeModeTabs.OP_BLOCKS, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    @SuppressWarnings("unchecked")
    Block KILL_BLOCK_PANEL = blockRegistrar.createWithItem("kill_block_panel",
            new KillBlockPanel(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).noOcclusion()),
            CreativeModeTabs.OP_BLOCKS, ModSceneBlocks.SCENE_CREATIVE_GROUP);

    // 厨师的「客户端」食物盘 / 饮料盘。
    // 只注册方块、不给 BlockItem：它们由服务端发 S2C 包让客户端 setBlock 画出来，
    // 不该被玩家在创造模式 legit 放置（服务端并不承认它们的存在）。
    // 放在所有其它字段之后声明，避开接口字段的初始化顺序问题。
    Block CHEF_FOOD_TRAY = registerBlockOnly("chef_food_tray", new ChefTrayBlock.Food(chefTrayProperties()));
    Block CHEF_DRINK_TRAY = registerBlockOnly("chef_drink_tray", new ChefTrayBlock.Drink(chefTrayProperties()));

    /**
     * 玩偶制作器：右键打开界面，输入玩家名 / UUID 就产出该玩家的玩偶。
     *
     * <p>贴图与模型暂用原版工作台（见 blockstates/plush_maker.json）。所有玩家可用、不消耗材料。</p>
     */
    Block PLUSH_MAKER = registerBlock("plush_maker",
            new org.agmas.noellesroles.content.block.PlushMakerBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.WOOD)));

    /**
     * 厨师盘子的方块实体：承载「盘里装着什么」，由 {@code ChefPlateRenderer} 渲染。
     *
     * <p>必须是一个独立类型：{@code BlockEntity} 构造函数会校验 {@code type.isValid(state)}，
     * 原版的 {@code trainmurdermystery:beverage_plate} 只接受 {@code food_platter} / {@code drink_tray}，
     * 用厨师的方块状态去 new 它会直接抛异常。
     *
     * <p>声明在两个方块字段之后，接口字段按声明顺序初始化，保证 {@code of(...)} 能拿到方块。
     */
    BlockEntityType<ChefPlateBlockEntity> CHEF_TRAY_BLOCK_ENTITY = blockEntityRegistrar.create(
            "chef_tray",
            BlockEntityType.Builder.of(ChefPlateBlockEntity::new, CHEF_FOOD_TRAY, CHEF_DRINK_TRAY));

    private static BlockBehaviour.Properties chefTrayProperties() {
        return BlockBehaviour.Properties.of()
                .noOcclusion()
                .noCollission()
                .instabreak()
                .noLootTable()
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.WOOD);
    }

    static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BLOCK_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.noellesroles.block")).icon(() -> {
                    return new ItemStack(VENDING_MACHINES_BLOCK.asItem());
                })
                .build());
        blockRegistrar.registerEntries();
        blockEntityRegistrar.registerEntries();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, BLOCK_CREATIVE_GROUP);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlockMultiTab(String id, T block,
            ResourceKey<CreativeModeTab> tab, ResourceKey<CreativeModeTab>... extraTabs) {
        if (extraTabs.length == 0) {
            return blockRegistrar.createWithItem(id, block, tab);
        }
        ResourceKey<CreativeModeTab>[] allTabs = new ResourceKey[extraTabs.length + 1];
        allTabs[0] = tab;
        System.arraycopy(extraTabs, 0, allTabs, 1, extraTabs.length);
        return blockRegistrar.createWithItem(id, block, allTabs);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerOpBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, CreativeModeTabs.OP_BLOCKS);
    }

    /**
     * 只注册方块、不附带 BlockItem，也不进任何创造分页。
     *
     * <p>用于「只由客户端画出来、服务端不承认存在」的方块（如厨师的客户端食物盘/饮料盘）。
     * 这些方块没有物品形态，不能被玩家 legit 放置。
     */
    public static <T extends Block> T registerBlockOnly(String id, T block) {
        return Registry.register(BuiltInRegistries.BLOCK, Noellesroles.id(id), block);
    }
}
