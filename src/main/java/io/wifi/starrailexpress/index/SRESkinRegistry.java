package io.wifi.starrailexpress.index;

import io.wifi.starrailexpress.util.SkinManager;
import io.wifi.starrailexpress.util.SkinManager.QualityColor;
import io.wifi.starrailexpress.util.SkinManager.SkinTypes;
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

        // ═══════════════════════════════════════════════════════════════════
        // REVOLVER（左轮）皮肤
        // ═══════════════════════════════════════════════════════════════════
        // registerSkin(SkinTypes.REVOLVER, "silver_storm", QualityColor.RARE);
        // registerSkin(SkinTypes.REVOLVER, "dragon_breath", QualityColor.LEGENDARY);

        // ═══════════════════════════════════════════════════════════════════
        // BAT（球棒）皮肤
        // ═══════════════════════════════════════════════════════════════════
        // registerSkin(SkinTypes.BAT, "neon_strike", QualityColor.UNCOMMON);
        // registerSkin(SkinTypes.BAT, "thunder_rod", QualityColor.EPIC);

        // ═══════════════════════════════════════════════════════════════════
        // GRENADE（手雷）皮肤
        // ═══════════════════════════════════════════════════════════════════
        // registerSkin(SkinTypes.GRENADE, "toxic_bomb", QualityColor.RARE);
        // registerSkin(SkinTypes.GRENADE, "inferno_grenade", QualityColor.LEGENDARY);

        // ═══════════════════════════════════════════════════════════════════
        // HAT（帽子）皮肤
        // ═══════════════════════════════════════════════════════════════════
        // registerSkin(SkinTypes.HAT, "top_hat", QualityColor.UNCOMMON);
        // registerSkin(SkinTypes.HAT, "crown", QualityColor.LEGENDARY);

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
        SkinManager.registerACustomSkin(skinType, skinID, quality.getColor());
    }
}
