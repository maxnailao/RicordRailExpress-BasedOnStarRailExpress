package org.agmas.noellesroles.init;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block.CustomPlayerPlushBlock;
import org.agmas.noellesroles.content.block.Jiale2PlushBlock;
import org.agmas.noellesroles.content.block.SPBGCPPlushBlock;
import org.agmas.noellesroles.content.block.SREPlushBlock;
import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;
import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.SRE;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public interface SREFumoBlocks {

    public static ResourceKey<CreativeModeTab> BLOCK_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            SRE.id("fumo"));

    public static ResourceKey<CreativeModeTab> SPECIAL_FUMO_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            SRE.id("special_fumo"));

    public static final BlockRegistrar blockRegistrar = new BlockRegistrar(Noellesroles.MOD_ID);
    public static final BlockEntityTypeRegistrar blockEntityRegistrar = new BlockEntityTypeRegistrar(
            Noellesroles.MOD_ID);
    
    // === 普通Fumo玩偶 ===
    Block MILK_DRAGON_PLUSH = registerBlock("milk_dragon_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block BAKA_PLUSH = registerBlock("baka_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block FURANDORU_PLUSH = registerBlock("furandoru_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block REMILIA_PLUSH = registerBlock("remilia_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block MISTIA_PLUSH = registerBlock("mystia_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block MARISA_PLUSH = registerBlock("marisa_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block REIMU_PLUSH = registerBlock("reimu_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block BAMBOO_PLUSH = registerBlock("bamboo_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block KAORUKO_PLUSH = registerBlock("kaoruko_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block BACKVOICE_PLUSH = registerBlock("backvoice_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block BIANTWIN_PLUSH = registerBlock("biantwin_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block CANYUESAMA_PLUSH = registerBlock("canyuesama_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block DIO_PLUSH = registerBlock("dio_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block FUSHIMI_KONIRO_PLUSH = registerBlock("fushimi_koniro_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block GUANZHEQWQ_PLUSH = registerBlock("guanzheqwq_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block HAIMAN233_PLUSH = registerBlock("haiman233_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block LENGXIAOCN_PLUSH = registerBlock("lengxiaocn_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block LICRAFTLQ_PLUSH = registerBlock("licraftlq_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block LUOYERUOSHUI_PLUSH = registerBlock("luoyeruoshui_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block MIFAN520_PLUSH = registerBlock("mifan520_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block NONE_PLUSH = registerBlock("none_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block OTITH_PLUSH = registerBlock("otith_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block THEF0RS4KEN_PLUSH = registerBlock("thef0rs4ken_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block TOMATO_PLUSH = registerBlock("tomato_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block XIAO_HEI_HAND_PLUSH = registerBlock("xiao_hei_hand_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block XIAOZHANQWQ_PLUSH = registerBlock("xiaozhanqwq_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block ALLINTOKYO_PLUSH = registerBlock("allintokyo_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    
    // === 特殊Fumo玩偶===
    Block JUSTACHEESE_PLUSH = registerSpecialBlock("justacheese_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block SPBGCP_PLUSH = registerSpecialBlock("spbgcp_plush",
            new SPBGCPPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block HUAJI_PLUSH = registerSpecialBlock("huaji_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block QINGMEI_PLUSH = registerSpecialBlock("qingmei_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block JIALE2_PLUSH = registerSpecialBlock("jiale2_plush",
            new Jiale2PlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block EGG_PLUSH = registerSpecialBlock("egg_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block TANGYE_PLUSH = registerSpecialBlock("tangye_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block XGD_PLUSH = registerSpecialBlock("xgd_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block YCHENNOC_PLUSH = registerSpecialBlock("ychennoc_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block MONGOOSE_PLUSH = registerSpecialBlock("mongoose_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block XIAOXIAN_PLUSH = registerSpecialBlock("xiaoxian_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block LIFELINE_PLUSH = registerSpecialBlock("lifeline_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block RLINGKONG_PLUSH = registerSpecialBlock("rlingkong_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block X1AOBA_PLUSH = registerSpecialBlock("x1aoba_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block CAIZI_PLUSH = registerSpecialBlock("caizi_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block JIALE114514_PLUSH = registerSpecialBlock("jiale114514_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block AKASPING_PLUSH = registerSpecialBlock("akasping_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block CHAORENQIANG_PLUSH = registerSpecialBlock("chaorenqiang_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block SLHCAT_PLUSH = registerSpecialBlock("slhcat_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block CRYINGSNOW_PLUSH = registerSpecialBlock("cryingsnow_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block CUTEFISH_PLUSH = registerSpecialBlock("cutefish_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block XITOMAOTSLX_PLUSH = registerSpecialBlock("xitomaotslx_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block CUSTOM_PLAYER_PLUSH = registerSpecialBlock("custom_player_plush",
            new CustomPlayerPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    /**
     * Block Entity
     */
    BlockEntityType<SREPlushBlockEntity> PLUSH_BLOCK_ENTITY = blockEntityRegistrar.create("plush",
            BlockEntityType.Builder.of(SREPlushBlockEntity::new, new Block[] { BAKA_PLUSH, FURANDORU_PLUSH,
                    REMILIA_PLUSH, MISTIA_PLUSH, MARISA_PLUSH, REIMU_PLUSH, BAMBOO_PLUSH,
                    KAORUKO_PLUSH, BACKVOICE_PLUSH, BIANTWIN_PLUSH, CANYUESAMA_PLUSH,
                    DIO_PLUSH, FUSHIMI_KONIRO_PLUSH, GUANZHEQWQ_PLUSH, HAIMAN233_PLUSH,
                    LENGXIAOCN_PLUSH, LICRAFTLQ_PLUSH, LUOYERUOSHUI_PLUSH, MIFAN520_PLUSH,
                    NONE_PLUSH, OTITH_PLUSH, THEF0RS4KEN_PLUSH, TOMATO_PLUSH,
                    XIAO_HEI_HAND_PLUSH, XIAOZHANQWQ_PLUSH, ALLINTOKYO_PLUSH, MILK_DRAGON_PLUSH,
                    JUSTACHEESE_PLUSH, SPBGCP_PLUSH, HUAJI_PLUSH, QINGMEI_PLUSH, JIALE2_PLUSH,
                    EGG_PLUSH, TANGYE_PLUSH, XGD_PLUSH, YCHENNOC_PLUSH, MONGOOSE_PLUSH, XIAOXIAN_PLUSH, LIFELINE_PLUSH,
                    RLINGKONG_PLUSH, X1AOBA_PLUSH, CAIZI_PLUSH, JIALE114514_PLUSH, AKASPING_PLUSH, CHAORENQIANG_PLUSH,
                    SLHCAT_PLUSH, CRYINGSNOW_PLUSH, CUTEFISH_PLUSH, XITOMAOTSLX_PLUSH, CUSTOM_PLAYER_PLUSH }));

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerSpecialBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, SPECIAL_FUMO_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerOpBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, CreativeModeTabs.OP_BLOCKS);
    }

    static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BLOCK_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.starrailexpress.fumo_blocks")).icon(() -> {
                    return new ItemStack(BAKA_PLUSH.asItem());
                })
                .build());
        
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SPECIAL_FUMO_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.starrailexpress.special_fumo_blocks")).icon(() -> {
                    return new ItemStack(JUSTACHEESE_PLUSH.asItem());
                })
                .build());
        
        blockRegistrar.registerEntries();
        blockEntityRegistrar.registerEntries();
    }
}
