package io.wifi.starrailexpress.index;

import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.util.ItemSkinManager.QualityColor;
import io.wifi.starrailexpress.util.ItemSkinManager.SkinTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 物品皮肤集中注册类
 *
 * 在模组初始化时调用 {@link #register()} 注册所有皮肤。
 *
 * 每个皮肤需要提供：
 * - skinType: 物品类型（SkinTypes 中的常量）
 * - skinID: 皮肤唯一标识（全小写，下划线分隔）
 * - color: 品质颜色（QualityColor 枚举）
 *
 * 注册后还需要：
 * 1. 创建模型 JSON: assets/starrailexpress/models/item/skins/{物品注册名}/{skinID}.json
 * 2. 创建贴图: assets/starrailexpress/textures/item/skins/{物品注册名}/{skinID}.png
 * 3. 添加翻译: lang/*.json 中的 "screen.sre.skins.{物品类型}.{skinID}.name"
 */
public final class SRESkinRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(SRESkinRegistry.class);

    private SRESkinRegistry() {}

    /**
     * 注册所有物品皮肤
     * 在 SRE.onInitialize() 的 initRegistries() 之后调用
     */
    public static void register() {
        LOGGER.info("[SkinRegistry] 开始注册物品皮肤...");

        // ═══════════════════════════════════════════════════════════════════
        // KNIFE（刀）皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.KNIFE, "testofknifeskin", QualityColor.COMMON);
        registerSkin(SkinTypes.KNIFE, "test", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.KNIFE, "knife_bunana", QualityColor.EPIC);
        registerSkin(SkinTypes.KNIFE, "knife_tangdao", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.KNIFE, "knife_cheese", QualityColor.RARE);
        registerSkin(SkinTypes.KNIFE, "knife_stonetool", QualityColor.COMMON);
        registerSkin(SkinTypes.KNIFE, "knife_brokenbottle", QualityColor.COMMON);
        registerSkin(SkinTypes.KNIFE, "knife_ceremonial", QualityColor.RARE);
        registerSkin(SkinTypes.KNIFE, "knife_goldcross", QualityColor.EPIC);
        registerSkin(SkinTypes.KNIFE, "knife_hammer", QualityColor.COMMON);
        registerSkin(SkinTypes.KNIFE, "knife_bananaknife", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.KNIFE, "knife_yingfeng", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.KNIFE, "knife_beijixing", QualityColor.LEGENDARY);

        // ═══════════════════════════════════════════════════════════════════
        // REVOLVER（左轮）皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.REVOLVER, "revolver_default", QualityColor.COMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_ammo", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_beach", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_no1", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_rose", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_blood", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_circuit", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_neon", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_blossom", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_frost", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_jade", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_sakura", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_golden", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.REVOLVER, "revolver_onyx", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.REVOLVER, "revolver_xiangzadi", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_g7", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_m1911", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_crude", QualityColor.COMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_liberator", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_longbarrel", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_banana", QualityColor.EPIC);

        // ═══════════════════════════════════════════════════════════════════
        // BAT（球棒）皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.BAT, "baseball_bat_default", QualityColor.COMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_wooden", QualityColor.COMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_nether", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_burst", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "baseball_bat_duck", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "baseball_bat_fish", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "baseball_bat_ice", QualityColor.EPIC);
        registerSkin(SkinTypes.BAT, "baseball_bat_sonic", QualityColor.EPIC);
        registerSkin(SkinTypes.BAT, "baseball_bat_studded", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_dayiwan", QualityColor.EPIC);
        registerSkin(SkinTypes.BAT, "baseball_bat_hanger", QualityColor.COMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_megammer", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "baseball_bat_wrench", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_badminton", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_shovel", QualityColor.COMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_longaxe", QualityColor.RARE);

        // ═══════════════════════════════════════════════════════════════════
        // GRENADE（手雷）皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.GRENADE, "grenade_smoke", QualityColor.COMMON);
        registerSkin(SkinTypes.GRENADE, "grenade_pineapple", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.GRENADE, "grenade_toxic", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.GRENADE, "grenade_fire", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_frost", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_skull", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_christmas", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_heart", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_star", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_diamond", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.GRENADE, "grenade_kanshenmekan", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_zisefurui", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_zhumei", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_toumatou", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_blueberry", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_coconut", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.GRENADE, "grenade_explosives", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_talisman", QualityColor.EPIC);

        // ═══════════════════════════════════════════════════════════════════
        // HAT（帽子）皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.HAT, "hat_cap_blue", QualityColor.COMMON);
        registerSkin(SkinTypes.HAT, "hat_cap_green", QualityColor.COMMON);
        registerSkin(SkinTypes.HAT, "hat_cap_purple", QualityColor.COMMON);
        registerSkin(SkinTypes.HAT, "hat_cap_red", QualityColor.COMMON);
        registerSkin(SkinTypes.HAT, "hat_tophat", QualityColor.RARE);
        registerSkin(SkinTypes.HAT, "hat_witch", QualityColor.RARE);
        registerSkin(SkinTypes.HAT, "hat_pirate", QualityColor.EPIC);
        registerSkin(SkinTypes.HAT, "hat_santa", QualityColor.EPIC);
        registerSkin(SkinTypes.HAT, "hat_crown", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_halo", QualityColor.LEGENDARY);

        LOGGER.info("[SkinRegistry] 物品皮肤注册完成");
    }

    /**
     * 注册单个皮肤的便捷方法
     *
     * @param skinType 物品类型（SkinTypes 常量）
     * @param skinID   皮肤唯一标识
     * @param quality  品质枚举
     */
    private static void registerSkin(String skinType, String skinID, QualityColor quality) {
        ItemSkinManager.registerACustomSkin(skinType, skinID, quality.getColor());
    }
}
