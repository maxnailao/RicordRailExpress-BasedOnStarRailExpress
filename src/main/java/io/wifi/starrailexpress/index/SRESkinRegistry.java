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
        // KNIFE（刀）皮肤 — 仅保留有贴图+模型资源的皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.KNIFE, "testofknifeskin", QualityColor.COMMON);
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
        registerSkin(SkinTypes.KNIFE, "knife_bingzhui", QualityColor.RARE);
        registerSkin(SkinTypes.KNIFE, "knife_guaizhangtang", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.KNIFE, "knife_lianhuadao", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.KNIFE, "knife_luosidao", QualityColor.COMMON);
        registerSkin(SkinTypes.KNIFE, "knife_gangbi", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.KNIFE, "knife_bingjingliandao", QualityColor.EPIC);
        registerSkin(SkinTypes.KNIFE, "knife_dujinxiaodao", QualityColor.RARE);
        registerSkin(SkinTypes.KNIFE, "knife_fenstemaikefeng", QualityColor.EPIC);
        registerSkin(SkinTypes.KNIFE, "knife_meigongdao", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.KNIFE, "knife_miyinxiaodao", QualityColor.RARE);
        registerSkin(SkinTypes.KNIFE, "knife_mushao", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.KNIFE, "knife_yaoshijian", QualityColor.RARE);
        registerSkin(SkinTypes.KNIFE, "knife_yinren", QualityColor.EPIC);
        registerSkin(SkinTypes.KNIFE, "knife_yingren", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.KNIFE, "knife_sushuikunai", QualityColor.UNBELIEVABLE); // 特别皮肤：专属切刀/击杀音效
        registerSkin(SkinTypes.KNIFE, "knife_anxing", QualityColor.UNBELIEVABLE); // 特别皮肤：双形态+专属切刀音效
        registerSkin(SkinTypes.KNIFE, "knife_anxing_1", QualityColor.UNBELIEVABLE); // 暗星形态1（天使）
        registerSkin(SkinTypes.KNIFE, "knife_anxing_2", QualityColor.UNBELIEVABLE); // 暗星形态2（恶魔）

        // ═══════════════════════════════════════════════════════════════════
        // REVOLVER（左轮）皮肤 — 仅保留有贴图+模型资源的皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.REVOLVER, "revolver_g7", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_m1911", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_crude", QualityColor.COMMON);
        registerSkin(SkinTypes.REVOLVER, "revolver_liberator", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_longbarrel", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_banana", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_xiangzadi", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_weilai", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.REVOLVER, "revolver_qidingqiang", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_tugaibandai", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_tangguofasheqi", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_shengxuan", QualityColor.UNBELIEVABLE);
        registerSkin(SkinTypes.REVOLVER, "revolver_shengxuan_1", QualityColor.UNBELIEVABLE); // 圣宣形态1
        registerSkin(SkinTypes.REVOLVER, "revolver_shengxuan_2", QualityColor.UNBELIEVABLE); // 圣宣形态2
        registerSkin(SkinTypes.REVOLVER, "revolver_dujinzuolun", QualityColor.RARE);
        registerSkin(SkinTypes.REVOLVER, "revolver_jisuqiang", QualityColor.EPIC);
        registerSkin(SkinTypes.REVOLVER, "revolver_chuxingren", QualityColor.LEGENDARY);

        // ═══════════════════════════════════════════════════════════════════
        // BAT（球棒）皮肤 — 仅保留有贴图+模型资源的皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.BAT, "baseball_bat_studded", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_dayiwan", QualityColor.EPIC);
        registerSkin(SkinTypes.BAT, "baseball_bat_hanger", QualityColor.COMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_megammer", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "baseball_bat_wrench", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_badminton", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_shovel", QualityColor.COMMON);
        registerSkin(SkinTypes.BAT, "baseball_bat_longaxe", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "bat_zuoyeben", QualityColor.COMMON);
        registerSkin(SkinTypes.BAT, "bat_juxingbangbangtang", QualityColor.EPIC);
        registerSkin(SkinTypes.BAT, "bat_dianju", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "bat_dangxinxiaonao", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "bat_dujinqiubang", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "bat_jita", QualityColor.RARE);
        registerSkin(SkinTypes.BAT, "bat_kanglongjian", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.BAT, "bat_pobanwangzheren", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.BAT, "bat_nitai", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.BAT, "bat_sushuiren", QualityColor.UNBELIEVABLE); // 特别皮肤：专属击打音效

        // ═══════════════════════════════════════════════════════════════════
        // GRENADE（手雷）皮肤 — 仅保留有贴图+模型资源的皮肤
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.GRENADE, "grenade_kanshenmekan", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_zisefurui", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_zhumei", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_toumatou", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_blueberry", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_coconut", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.GRENADE, "grenade_explosives", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_talisman", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_moshuiping", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.GRENADE, "grenade_huyaoweiyingtang", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.GRENADE, "grenade_jingonglei", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_dujinshoulei", QualityColor.RARE);
        registerSkin(SkinTypes.GRENADE, "grenade_fennujiweiniao", QualityColor.UNCOMMON);
        registerSkin(SkinTypes.GRENADE, "grenade_heidong", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_zuzhouzhiyan", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_zhuzhu", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_yanxiao114514", QualityColor.EPIC);
        registerSkin(SkinTypes.GRENADE, "grenade_hongwen", QualityColor.EPIC);

        // ═══════════════════════════════════════════════════════════════════
        // HAT（帽子）皮肤 — 瑞科德列车玩偶帽系列（全部为金色品质 LEGENDARY）
        // 建模/贴图照搬玩偶方块（models/item/skins/hat/），
        // 由 HatFeatureRenderer 渲染在玩家头顶
        // ═══════════════════════════════════════════════════════════════════
        registerSkin(SkinTypes.HAT, "hat_jiale114514", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_justacheese", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_spbgcp", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_huaji", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_qingmei", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_jiale2", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_egg", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_tangye", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_xgd", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_ychennoc", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_mongoose", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_xiaoxian", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_lifeline", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_rlingkong", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_x1aoba", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_caizi", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_akasping", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_chaorenqiang", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_slhcat", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_cryingsnow", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_cutefish", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_xitomaotslx", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_san_hua_awa", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_box", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_white_koshi", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_qivvu_520", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_shilu", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_fetal_error", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_hengzai", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_milk_dragon", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_baka", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_furandoru", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_remilia", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_mystia", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_marisa", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_reimu", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_bamboo", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_kaoruko", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_backvoice", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_biantwin", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_canyuesama", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_dio", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_fushimi_koniro", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_guanzheqwq", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_haiman233", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_lengxiaocn", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_licraftlq", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_luoyeruoshui", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_mifan520", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_none", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_otith", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_thef0rs4ken", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_tomato", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_xiao_hei_hand", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_xiaozhanqwq", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_allintokyo", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_aqiong", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_haozi", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_liangjie", QualityColor.LEGENDARY);
        registerSkin(SkinTypes.HAT, "hat_liyu", QualityColor.LEGENDARY);

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
