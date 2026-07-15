package io.wifi.starrailexpress.content.musicbox;

import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 音乐盒注册表——管理所有可用的音乐盒定义。
 * <p>回退策略：整个文件删除即可。</p>
 */
public final class MusicBoxRegistry {

    private static final Map<String, MusicBox> REGISTRY = new LinkedHashMap<>();

    private MusicBoxRegistry() {}

    /**
     * 注册一个音乐盒。
     */
    public static void register(MusicBox box) {
        REGISTRY.put(box.id(), box);
    }

    /**
     * 按 ID 获取音乐盒，不存在返回 null。
     */
    public static MusicBox get(String id) {
        return REGISTRY.get(id);
    }

    /**
     * 获取所有已注册的音乐盒（不可变视图）。
     */
    public static Collection<MusicBox> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * 是否包含指定 ID。
     */
    public static boolean contains(String id) {
        return REGISTRY.containsKey(id);
    }

    /**
     * 注册所有内置音乐盒。在 SRE.onInitialize() 中调用。
     */
    public static void registerBuiltins() {
        register(new MusicBox("triumph_01", MusicBoxSounds.MUSICBOX_TRIUMPH_01,
                Component.translatable("musicbox.starrailexpress.triumph_01"), 3.0f));
        register(new MusicBox("triumph_02", MusicBoxSounds.MUSICBOX_TRIUMPH_02,
                Component.translatable("musicbox.starrailexpress.triumph_02"), 3.0f));
        register(new MusicBox("triumph_03", MusicBoxSounds.MUSICBOX_TRIUMPH_03,
                Component.translatable("musicbox.starrailexpress.triumph_03"), 3.0f));
        register(new MusicBox("gaoshouruyun", MusicBoxSounds.MUSICBOX_GAOSHOURUYUN,
                Component.translatable("musicbox.starrailexpress.gaoshouruyun"), 3.0f));
        register(new MusicBox("inhuman", MusicBoxSounds.MUSICBOX_INHUMAN,
                Component.translatable("musicbox.starrailexpress.inhuman"), 3.9f));
        // ── 新增 24 个音乐盒（音量根据文件大小调整）──
        register(new MusicBox("everybody_dies_in_their_nightmares", MusicBoxSounds.MUSICBOX_EVERYBODY_DIES,
                Component.translatable("musicbox.starrailexpress.everybody_dies_in_their_nightmares"), 2.0f));
        register(new MusicBox("nevada", MusicBoxSounds.MUSICBOX_NEVADA,
                Component.translatable("musicbox.starrailexpress.nevada"), 1.3f));
        register(new MusicBox("wasted", MusicBoxSounds.MUSICBOX_WASTED,
                Component.translatable("musicbox.starrailexpress.wasted"), 1.3f));
        register(new MusicBox("qinaide_ni_bei_huozang_le", MusicBoxSounds.MUSICBOX_QINAIDE,
                Component.translatable("musicbox.starrailexpress.qinaide_ni_bei_huozang_le"), 1.8f));
        register(new MusicBox("ni_mishi_le_ma", MusicBoxSounds.MUSICBOX_NIMISHILEMA,
                Component.translatable("musicbox.starrailexpress.ni_mishi_le_ma"), 2.0f));
        register(new MusicBox("dongmian", MusicBoxSounds.MUSICBOX_DONGMIAN,
                Component.translatable("musicbox.starrailexpress.dongmian"), 1.3f));
        register(new MusicBox("juedou", MusicBoxSounds.MUSICBOX_JUEDOU,
                Component.translatable("musicbox.starrailexpress.juedou"), 1.8f));
        register(new MusicBox("shuangnan", MusicBoxSounds.MUSICBOX_SHUANGNAN,
                Component.translatable("musicbox.starrailexpress.shuangnan"), 1.8f));
        register(new MusicBox("jita_gudu_yu_lanse_xingqiu", MusicBoxSounds.MUSICBOX_JITA_GUDU,
                Component.translatable("musicbox.starrailexpress.jita_gudu_yu_lanse_xingqiu"), 1.3f));
        register(new MusicBox("zanmen_jiehunba", MusicBoxSounds.MUSICBOX_ZANMEN_JIEHUNBA,
                Component.translatable("musicbox.starrailexpress.zanmen_jiehunba"), 1.5f));
        register(new MusicBox("fengchengzhe", MusicBoxSounds.MUSICBOX_FENGCHENGZHE,
                Component.translatable("musicbox.starrailexpress.fengchengzhe"), 1.8f));
        register(new MusicBox("balalaika", MusicBoxSounds.MUSICBOX_BALALAIKA,
                Component.translatable("musicbox.starrailexpress.balalaika"), 1.7f));
        register(new MusicBox("dangni", MusicBoxSounds.MUSICBOX_DANGNI,
                Component.translatable("musicbox.starrailexpress.dangni"), 1.3f));
        register(new MusicBox("guaiwu_zhige", MusicBoxSounds.MUSICBOX_GUAIWU_ZHIGE,
                Component.translatable("musicbox.starrailexpress.guaiwu_zhige"), 1.8f));
        register(new MusicBox("wo_conglai_dou_buhui_shu", MusicBoxSounds.MUSICBOX_WO_CONGLAI,
                Component.translatable("musicbox.starrailexpress.wo_conglai_dou_buhui_shu"), 1.5f));
        register(new MusicBox("xinsanbuqu", MusicBoxSounds.MUSICBOX_XINSANBUQU,
                Component.translatable("musicbox.starrailexpress.xinsanbuqu"), 1.5f));
        register(new MusicBox("xingxing_yu_women", MusicBoxSounds.MUSICBOX_XINGXING_YU_WOMEN,
                Component.translatable("musicbox.starrailexpress.xingxing_yu_women"), 1.5f));
        register(new MusicBox("xingkong_xuaiqu", MusicBoxSounds.MUSICBOX_XINGKONG_XUAIQU,
                Component.translatable("musicbox.starrailexpress.xingkong_xuaiqu"), 1.5f));
        register(new MusicBox("shuishou_dj", MusicBoxSounds.MUSICBOX_SHUISHOU_DJ,
                Component.translatable("musicbox.starrailexpress.shuishou_dj"), 1.6f));
        register(new MusicBox("liuguang_sijian", MusicBoxSounds.MUSICBOX_LIUGUANG_SIJIAN,
                Component.translatable("musicbox.starrailexpress.liuguang_sijian"), 1.6f));
        register(new MusicBox("kong_no_xiang", MusicBoxSounds.MUSICBOX_KONG_NO_XIANG,
                Component.translatable("musicbox.starrailexpress.kong_no_xiang"), 1.7f));
        register(new MusicBox("luoshengmen", MusicBoxSounds.MUSICBOX_LUOSHENGMEN,
                Component.translatable("musicbox.starrailexpress.luoshengmen"), 1.6f));
        register(new MusicBox("an_ni_tokete_ku", MusicBoxSounds.MUSICBOX_AN_NI_TOKETE_KU,
                Component.translatable("musicbox.starrailexpress.an_ni_tokete_ku"), 1.6f));
        register(new MusicBox("maque", MusicBoxSounds.MUSICBOX_MAQUE,
                Component.translatable("musicbox.starrailexpress.maque"), 1.5f));
        // ── 新增 19 个音乐盒（音量根据文件大小调整）──
        register(new MusicBox("ez4ence", MusicBoxSounds.MUSICBOX_EZ4ENCE,
                Component.translatable("musicbox.starrailexpress.ez4ence"), 1.6f));
        register(new MusicBox("lone_digger", MusicBoxSounds.MUSICBOX_LONE_DIGGER,
                Component.translatable("musicbox.starrailexpress.lone_digger"), 1.6f));
        register(new MusicBox("reverse", MusicBoxSounds.MUSICBOX_REVERSE,
                Component.translatable("musicbox.starrailexpress.reverse"), 1.5f));
        register(new MusicBox("the_wandering_ronin", MusicBoxSounds.MUSICBOX_THE_WANDERING_RONIN,
                Component.translatable("musicbox.starrailexpress.the_wandering_ronin"), 1.3f));
        register(new MusicBox("aihei", MusicBoxSounds.MUSICBOX_AIHEI,
                Component.translatable("musicbox.starrailexpress.aihei"), 1.3f));
        register(new MusicBox("chongjixing", MusicBoxSounds.MUSICBOX_CHONGJIXING,
                Component.translatable("musicbox.starrailexpress.chongjixing"), 1.6f));
        register(new MusicBox("chumo_nengliang", MusicBoxSounds.MUSICBOX_CHUMO_NENGLIANG,
                Component.translatable("musicbox.starrailexpress.chumo_nengliang"), 1.6f));
        register(new MusicBox("dashang_huahuo", MusicBoxSounds.MUSICBOX_DASHANG_HUAHUO,
                Component.translatable("musicbox.starrailexpress.dashang_huahuo"), 1.3f));
        register(new MusicBox("geren_jianjie", MusicBoxSounds.MUSICBOX_GEREN_JIANJIE,
                Component.translatable("musicbox.starrailexpress.geren_jianjie"), 1.6f));
        register(new MusicBox("hualian", MusicBoxSounds.MUSICBOX_HUALIAN,
                Component.translatable("musicbox.starrailexpress.hualian"), 1.3f));
        register(new MusicBox("kuchazi", MusicBoxSounds.MUSICBOX_KUCHAZI,
                Component.translatable("musicbox.starrailexpress.kuchazi"), 1.5f));
        register(new MusicBox("rensheng_hechu_bu_qingshan", MusicBoxSounds.MUSICBOX_RENSHENG_HECHU,
                Component.translatable("musicbox.starrailexpress.rensheng_hechu_bu_qingshan"), 1.4f));
        register(new MusicBox("tangping_qingnian", MusicBoxSounds.MUSICBOX_TANGPING_QINGNIAN,
                Component.translatable("musicbox.starrailexpress.tangping_qingnian"), 1.3f));
        register(new MusicBox("tongxiao_dadan", MusicBoxSounds.MUSICBOX_TONGXIAO_DADAN,
                Component.translatable("musicbox.starrailexpress.tongxiao_dadan"), 1.5f));
        register(new MusicBox("wanzhong_zhumu", MusicBoxSounds.MUSICBOX_WANZHONG_ZHUMU,
                Component.translatable("musicbox.starrailexpress.wanzhong_zhumu"), 1.7f));
        register(new MusicBox("yilian_jieyuan", MusicBoxSounds.MUSICBOX_YILIAN_JIEYUAN,
                Component.translatable("musicbox.starrailexpress.yilian_jieyuan"), 1.6f));
        register(new MusicBox("youwei_qingnian", MusicBoxSounds.MUSICBOX_YOUWEI_QINGNIAN,
                Component.translatable("musicbox.starrailexpress.youwei_qingnian"), 1.3f));
        register(new MusicBox("zhanwubusheng", MusicBoxSounds.MUSICBOX_ZHANWUBUSHENG,
                Component.translatable("musicbox.starrailexpress.zhanwubusheng"), 1.8f));
        register(new MusicBox("zhiyin_ni_tai_mei", MusicBoxSounds.MUSICBOX_ZHIYIN_NI_TAI_MEI,
                Component.translatable("musicbox.starrailexpress.zhiyin_ni_tai_mei"), 1.6f));
        // ── 新增 2 个音乐盒 ──
        register(new MusicBox("aizo", MusicBoxSounds.MUSICBOX_AIZO,
                Component.translatable("musicbox.starrailexpress.aizo"), 1.5f));
        register(new MusicBox("dage_xiaoqu", MusicBoxSounds.MUSICBOX_DAGE_XIAOQU,
                Component.translatable("musicbox.starrailexpress.dage_xiaoqu"), 1.5f));
        // ── 新增 3 个音乐盒 ──
        register(new MusicBox("xiang_bendan_yiyang", MusicBoxSounds.MUSICBOX_XIANG_BENDAN_YIYANG,
                Component.translatable("musicbox.starrailexpress.xiang_bendan_yiyang"), 1.5f));
        register(new MusicBox("guanyu_zhige", MusicBoxSounds.MUSICBOX_GUANYU_ZHIGE,
                Component.translatable("musicbox.starrailexpress.guanyu_zhige"), 1.5f));
        register(new MusicBox("haoxiang_ting_ni_shuo_yuese_zhenmei", MusicBoxSounds.MUSICBOX_HAOXIANG_TING_NI_SHUO_YUESE_ZHENMEI,
                Component.translatable("musicbox.starrailexpress.haoxiang_ting_ni_shuo_yuese_zhenmei"), 1.3f));
    }
}
