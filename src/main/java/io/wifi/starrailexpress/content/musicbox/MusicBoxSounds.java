package io.wifi.starrailexpress.content.musicbox;

import dev.doctor4t.ratatouille.util.registrar.SoundEventRegistrar;
import io.wifi.starrailexpress.SRE;
import net.minecraft.sounds.SoundEvent;

/**
 * 音乐盒音效注册。
 * <p>
 * 所有音乐盒音效统一放在 {@code assets/starrailexpress/sounds/musicbox/} 目录下，
 * 并在 {@code sounds.json} 中声明对应条目。
 * </p>
 * <p>回退策略：整个文件删除即可，不影响其他模块。</p>
 */
public interface MusicBoxSounds {
    SoundEventRegistrar registrar = new SoundEventRegistrar(SRE.MOD_ID);

    // ── 内置音乐盒音效 ──
    SoundEvent MUSICBOX_TRIUMPH_01 = registrar.create("musicbox.triumph_01");
    SoundEvent MUSICBOX_TRIUMPH_02 = registrar.create("musicbox.triumph_02");
    SoundEvent MUSICBOX_TRIUMPH_03 = registrar.create("musicbox.triumph_03");
    SoundEvent MUSICBOX_GAOSHOURUYUN = registrar.create("musicbox.gaoshouruyun");
    SoundEvent MUSICBOX_INHUMAN = registrar.create("musicbox.inhuman");
    SoundEvent MUSICBOX_EVERYBODY_DIES = registrar.create("musicbox.everybody_dies_in_their_nightmares");
    SoundEvent MUSICBOX_NEVADA = registrar.create("musicbox.nevada");
    SoundEvent MUSICBOX_WASTED = registrar.create("musicbox.wasted");
    SoundEvent MUSICBOX_QINAIDE = registrar.create("musicbox.qinaide_ni_bei_huozang_le");
    SoundEvent MUSICBOX_NIMISHILEMA = registrar.create("musicbox.ni_mishi_le_ma");
    SoundEvent MUSICBOX_DONGMIAN = registrar.create("musicbox.dongmian");
    SoundEvent MUSICBOX_JUEDOU = registrar.create("musicbox.juedou");
    SoundEvent MUSICBOX_SHUANGNAN = registrar.create("musicbox.shuangnan");
    SoundEvent MUSICBOX_JITA_GUDU = registrar.create("musicbox.jita_gudu_yu_lanse_xingqiu");
    SoundEvent MUSICBOX_ZANMEN_JIEHUNBA = registrar.create("musicbox.zanmen_jiehunba");
    SoundEvent MUSICBOX_FENGCHENGZHE = registrar.create("musicbox.fengchengzhe");
    SoundEvent MUSICBOX_BALALAIKA = registrar.create("musicbox.balalaika");
    SoundEvent MUSICBOX_DANGNI = registrar.create("musicbox.dangni");
    SoundEvent MUSICBOX_GUAIWU_ZHIGE = registrar.create("musicbox.guaiwu_zhige");
    SoundEvent MUSICBOX_WO_CONGLAI = registrar.create("musicbox.wo_conglai_dou_buhui_shu");
    SoundEvent MUSICBOX_XINSANBUQU = registrar.create("musicbox.xinsanbuqu");
    SoundEvent MUSICBOX_XINGXING_YU_WOMEN = registrar.create("musicbox.xingxing_yu_women");
    SoundEvent MUSICBOX_XINGKONG_XUAIQU = registrar.create("musicbox.xingkong_xuaiqu");
    SoundEvent MUSICBOX_SHUISHOU_DJ = registrar.create("musicbox.shuishou_dj");
    SoundEvent MUSICBOX_LIUGUANG_SIJIAN = registrar.create("musicbox.liuguang_sijian");
    SoundEvent MUSICBOX_KONG_NO_XIANG = registrar.create("musicbox.kong_no_xiang");
    SoundEvent MUSICBOX_LUOSHENGMEN = registrar.create("musicbox.luoshengmen");
    SoundEvent MUSICBOX_AN_NI_TOKETE_KU = registrar.create("musicbox.an_ni_tokete_ku");
    SoundEvent MUSICBOX_MAQUE = registrar.create("musicbox.maque");
    // ── 新增 19 个音乐盒音效 ──
    SoundEvent MUSICBOX_EZ4ENCE = registrar.create("musicbox.ez4ence");
    SoundEvent MUSICBOX_LONE_DIGGER = registrar.create("musicbox.lone_digger");
    SoundEvent MUSICBOX_REVERSE = registrar.create("musicbox.reverse");
    SoundEvent MUSICBOX_THE_WANDERING_RONIN = registrar.create("musicbox.the_wandering_ronin");
    SoundEvent MUSICBOX_AIHEI = registrar.create("musicbox.aihei");
    SoundEvent MUSICBOX_CHONGJIXING = registrar.create("musicbox.chongjixing");
    SoundEvent MUSICBOX_CHUMO_NENGLIANG = registrar.create("musicbox.chumo_nengliang");
    SoundEvent MUSICBOX_DASHANG_HUAHUO = registrar.create("musicbox.dashang_huahuo");
    SoundEvent MUSICBOX_GEREN_JIANJIE = registrar.create("musicbox.geren_jianjie");
    SoundEvent MUSICBOX_HUALIAN = registrar.create("musicbox.hualian");
    SoundEvent MUSICBOX_KUCHAZI = registrar.create("musicbox.kuchazi");
    SoundEvent MUSICBOX_RENSHENG_HECHU = registrar.create("musicbox.rensheng_hechu_bu_qingshan");
    SoundEvent MUSICBOX_TANGPING_QINGNIAN = registrar.create("musicbox.tangping_qingnian");
    SoundEvent MUSICBOX_TONGXIAO_DADAN = registrar.create("musicbox.tongxiao_dadan");
    SoundEvent MUSICBOX_WANZHONG_ZHUMU = registrar.create("musicbox.wanzhong_zhumu");
    SoundEvent MUSICBOX_YILIAN_JIEYUAN = registrar.create("musicbox.yilian_jieyuan");
    SoundEvent MUSICBOX_YOUWEI_QINGNIAN = registrar.create("musicbox.youwei_qingnian");
    SoundEvent MUSICBOX_ZHANWUBUSHENG = registrar.create("musicbox.zhanwubusheng");
    SoundEvent MUSICBOX_ZHIYIN_NI_TAI_MEI = registrar.create("musicbox.zhiyin_ni_tai_mei");
    // ── 新增 2 个音乐盒音效 ──
    SoundEvent MUSICBOX_AIZO = registrar.create("musicbox.aizo");
    SoundEvent MUSICBOX_DAGE_XIAOQU = registrar.create("musicbox.dage_xiaoqu");
    // ── 新增 3 个音乐盒音效 ──
    SoundEvent MUSICBOX_XIANG_BENDAN_YIYANG = registrar.create("musicbox.xiang_bendan_yiyang");
    SoundEvent MUSICBOX_GUANYU_ZHIGE = registrar.create("musicbox.guanyu_zhige");
    SoundEvent MUSICBOX_HAOXIANG_TING_NI_SHUO_YUESE_ZHENMEI = registrar.create("musicbox.haoxiang_ting_ni_shuo_yuese_zhenmei");
    // ── 新增 2 个音乐盒音效 ──
    SoundEvent MUSICBOX_DIE_FOR_U = registrar.create("musicbox.die_for_u");
    SoundEvent MUSICBOX_STYX_HELIX = registrar.create("musicbox.styx_helix");

    static void initialize() {
        registrar.registerEntries();
    }
}
