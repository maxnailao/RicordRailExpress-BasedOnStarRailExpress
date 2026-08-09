package org.agmas.noellesroles.cs2;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.SREFumoBlocks;

/**
 * CS2 皮肤中文名称和介绍信息
 * <p>
 * 提供仓库和开箱界面使用的本地化皮肤名称与描述。
 * </p>
 */
public final class CS2SkinInfo {

    private CS2SkinInfo() {}

    /** skinId → 中文名称 */
    private static final Map<String, String> NAMES = new HashMap<>();
    /** skinId → 中文介绍 */
    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();

    static {
        // ═══════════════════════════════════════════════════════════════════
        // KNIFE（刀）
        // ═══════════════════════════════════════════════════════════════════
        register("knife/testofknifeskin", "测试刀皮", "用于测试的简易刀皮");
        register("knife/knife_bunana", "香蕉刀", "刀身被包裹在黄色香蕉皮中，滑稽而致命");
        register("knife/knife_tangdao", "唐刀", "东方古韵，锋芒毕露");
        register("knife/knife_cheese", "芝士刀", "散发浓郁奶香的奇怪武器");
        register("knife/knife_stonetool", "石器刀", "原始但可靠，回归最朴素的杀戮本能");
        register("knife/knife_brokenbottle", "碎瓶刀", "用碎玻璃瓶打造的即兴武器");
        register("knife/knife_ceremonial", "仪式刀", "精美的仪式用刀，刻有神秘符文");
        register("knife/knife_goldcross", "金十字刀", "镶嵌金色十字架的华丽匕首");
        register("knife/knife_hammer", "锤刀", "兼具锤子与刀刃的多功能武器");
        register("knife/knife_bananaknife", "香蕉匕首", "弯曲如月的香蕉造型匕首");
        register("knife/knife_yingfeng", "影锋", "暗影中的利刃，传说级收藏品");
        register("knife/knife_beijixing", "北极星", "指引方向的星辰之刃，传说级收藏品");
        register("knife/knife_bingjingliandao", "冰晶镰刀", "寒冰凝聚而成的死神之镰，挥舞时带起刺骨冷风");
        register("knife/knife_dujinxiaodao", "镀金小刀", "表面镀金的小刀，低调中透露着奢华");
        register("knife/knife_fenstemaikefeng", "粉色麦克风", "用麦克风当武器，每一击都是致命的高音");
        register("knife/knife_meigongdao", "美工刀", "日常办公文具，此刻却成为致命凶器");
        register("knife/knife_miyinxiaodao", "秘银小刀", "秘银锻造的轻盈小刀，削铁如泥");
        register("knife/knife_mushao", "木勺", "厨房里的木勺，敲起人来意外地疼");
        register("knife/knife_yaoshijian", "钥匙剑", "剑柄如钥匙般精巧，开启胜利之门的利刃");
        register("knife/knife_yinren", "银刃", "纯银打造的利刃，在月光下闪耀着冷冽光芒");
        register("knife/knife_yingren", "影刃", "藏于暗影中的传说之刃，出鞘必见血");
        register("knife/knife_sushuikunai", "塑水苦无", "以流水塑形的苦无，切刀与击杀之时，唯有持者能听见它的潮鸣");
        register("knife/knife_anxing", "暗星", "天使与恶魔交织的双生之刃，饮血之后光暗逆转");

        // ═══════════════════════════════════════════════════════════════════
        // REVOLVER（左轮）
        // ═══════════════════════════════════════════════════════════════════
        register("revolver/revolver_g7", "G7 战术", "轻量化战术左轮，可靠性极高");
        register("revolver/revolver_m1911", "M1911 经典", "致敬经典手枪设计，永不过时");
        register("revolver/revolver_crude", "粗制左轮", "做工粗糙但火力不减，性价比之选");
        register("revolver/revolver_liberator", "解放者", "为自由而战的传奇武器");
        register("revolver/revolver_longbarrel", "长管左轮", "加长枪管提供更远的射程和精准度");
        register("revolver/revolver_banana", "香蕉左轮", "外表滑稽，但子弹可不会拐弯");
        register("revolver/revolver_xiangzadi", "象鼻左轮", "独特象鼻造型的异域风格手枪");
        register("revolver/revolver_shengxuan", "圣宣", "黑枪敬向逝者白枪指向生者");
        register("revolver/revolver_dujinzuolun", "镀金左轮", "枪身镀金的左轮手枪，彰显非凡身份");
        register("revolver/revolver_jisuqiang", "激素枪", "注射激素般令人亢奋的狂暴火力");

        // ═══════════════════════════════════════════════════════════════════
        // BAT（球棒）
        // ═══════════════════════════════════════════════════════════════════
        register("bat/baseball_bat_studded", "铆钉球棒", "镶嵌铆钉的暴力球棒");
        register("bat/baseball_bat_dayiwan", "大一万", "价值不菲的定制球棒，身份的象征");
        register("bat/baseball_bat_hanger", "衣架球棒", "用晾衣架改造的临时武器");
        register("bat/baseball_bat_megammer", "巨锤球棒", "锤头加重的暴力版球棒");
        register("bat/baseball_bat_wrench", "扳手球棒", "工业风十足的改造球棒");
        register("bat/baseball_bat_badminton", "羽毛球拍", "伪装成运动器材的致命武器");
        register("bat/baseball_bat_shovel", "铲子球棒", "铲型球棒，兼具挖掘与战斗");
        register("bat/baseball_bat_longaxe", "长斧球棒", "斧型球棒，劈砍威力惊人");
        register("bat/bat_dangxinxiaonao", "当心小脑", "敲人要当心，别伤了小脑");
        register("bat/bat_dujinqiubang", "镀金球棒", "镀金球棒，挥棒间尽显贵气");
        register("bat/bat_jita", "吉他", "用吉他奏响战斗的乐章");
        register("bat/bat_kanglongjian", "亢龙锏", "传说中的亢龙锏，威力无穷");
        register("bat/bat_pobanwangzheren", "破败王者之刃", "破败王者遗留的传说之刃，蕴含王者之力");
        register("bat/bat_sushuiren", "塑水刃", "以流水塑形的利刃，挥动时水光潋滟，唯有持刃者能听见它的潮声");

        // ═══════════════════════════════════════════════════════════════════
        // GRENADE（手雷）
        // ═══════════════════════════════════════════════════════════════════
        register("grenade/grenade_kanshenmekan", "看我丢雷", "名字虽怪但投掷精准");
        register("grenade/grenade_zisefurui", "紫色弗雷", "散发紫色光芒的神秘手雷");
        register("grenade/grenade_zhumei", "竹梅", "以竹梅为饰的雅致手雷");
        register("grenade/grenade_toumatou", "偷猫头", "造型诡异的猫头手雷");
        register("grenade/grenade_blueberry", "蓝莓手雷", "小巧的蓝莓造型手雷");
        register("grenade/grenade_coconut", "椰子手雷", "热带风情，爆炸同样热情");
        register("grenade/grenade_explosives", "烈性炸药", "高威力炸药，爆炸范围极大");
        register("grenade/grenade_talisman", "符咒手雷", "刻有古老符咒的魔法手雷");
        register("grenade/grenade_dujinshoulei", "镀金手雷", "镀金手雷，爆炸也要金光闪闪");
        register("grenade/grenade_fennujiweiniao", "愤怒几维鸟", "愤怒的几维鸟，爆炸时发出尖啸");
        register("grenade/grenade_heidong", "黑洞", "仿佛能吞噬一切的黑洞手雷");
        register("grenade/grenade_zuzhouzhiyan", "诅咒之眼", "被诅咒的邪眼，注视着它的爆炸");

        // ═══════════════════════════════════════════════════════════════════
        // HAT（帽子）
        // ═══════════════════════════════════════════════════════════════════
        register("hat/hat_jiale114514", "JiaLe114514 玩偶帽", "把 JiaLe114514 玩偶戴在头上，全场最靓的崽");
    }

    private static void register(String skinId, String name, String description) {
        NAMES.put(skinId, name);
        DESCRIPTIONS.put(skinId, description);
    }

    /**
     * 获取皮肤中文名称。如果没有注册则返回格式化后的英文ID。
     */
    public static String getName(String skinId) {
        if (skinId == null) return "?";
        String name = NAMES.get(skinId);
        if (name != null) return name;
        // 回退: 取皮肤名部分，下划线替换为空格
        String fallback = skinId.contains("/") ? skinId.substring(skinId.lastIndexOf('/') + 1) : skinId;
        return fallback.replace('_', ' ');
    }

    /**
     * 获取皮肤中文介绍。如果没有注册则返回空字符串。
     */
    public static String getDescription(String skinId) {
        if (skinId == null) return "";
        return DESCRIPTIONS.getOrDefault(skinId, "");
    }

    /**
     * 获取皮肤在仓库/开箱界面中的图标物品。
     * 帽子皮肤无对应武器物品，用复用的玩偶物品作为图标；返回 null 表示使用默认映射。
     */
    public static ItemStack getIconStack(String skinId) {
        if ("hat/hat_jiale114514".equals(skinId)) {
            return new ItemStack(SREFumoBlocks.JIALE114514_PLUSH.asItem());
        }
        return null;
    }

    /**
     * 根据 itemType 获取中文类型名称
     */
    public static String getItemTypeName(String itemType) {
        return switch (itemType) {
            case "knife" -> "刀";
            case "revolver", "gun" -> "左轮";
            case "bat" -> "球棒";
            case "grenade" -> "手雷";
            case "hat" -> "帽子";
            default -> itemType;
        };
    }
}
