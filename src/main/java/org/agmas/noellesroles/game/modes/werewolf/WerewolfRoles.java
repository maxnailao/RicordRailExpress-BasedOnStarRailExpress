package org.agmas.noellesroles.game.modes.werewolf;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;

/**
 * 狼人杀专属角色注册
 * 参考修机模式（RepairRoles）：每个狼人杀身份注册为独立的 SRERole，
 * 而不是复用主玩法角色后再替换。
 * Author: jiale
 */
public class WerewolfRoles {
    // === 好人阵营 ===
    public static final SRERole WW_INNOCENT = TMMRoles.registerRole(new NormalRole(
            SRE.jialeId("ww_innocent"), 0x9E9E9E, true, false,
            SRERole.MoodType.NONE, -1, false))
            .setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    public static final SRERole WW_ALCHEMIST = TMMRoles.registerRole(new NormalRole(
            SRE.jialeId("ww_alchemist"), 0xB24BF3, true, false,
            SRERole.MoodType.NONE, -1, false))
            .setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    public static final SRERole WW_PROPHET = TMMRoles.registerRole(new NormalRole(
            SRE.jialeId("ww_prophet"), 0x3FB4E8, true, false,
            SRERole.MoodType.NONE, -1, false))
            .setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    public static final SRERole WW_GUARDIAN = TMMRoles.registerRole(new NormalRole(
            SRE.jialeId("ww_guardian"), 0x3FA35C, true, false,
            SRERole.MoodType.NONE, -1, false))
            .setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    public static final SRERole WW_KNIGHT = TMMRoles.registerRole(new NormalRole(
            SRE.jialeId("ww_knight"), 0xE8C33F, true, false,
            SRERole.MoodType.NONE, -1, false))
            .setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    public static final SRERole WW_HUNTER = TMMRoles.registerRole(new NormalRole(
            SRE.jialeId("ww_hunter"), 0xD97A2B, true, false,
            SRERole.MoodType.NONE, -1, false))
            .setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    // === 狼方阵营 ===
    public static final SRERole WW_WEREWOLF = TMMRoles.registerRole(new NormalRole(
            SRE.jialeId("ww_werewolf"), 0xC13838, false, true,
            SRERole.MoodType.NONE, -1, false))
            .setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    public static final SRERole WW_WHITE_WOLF_KING = TMMRoles.registerRole(new NormalRole(
            SRE.jialeId("ww_white_wolf_king"), 0xEDEDED, false, true,
            SRERole.MoodType.NONE, -1, false))
            .setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    // === 兼容旧引用 ===
    public static final SRERole GOOD = WW_INNOCENT;
    public static final SRERole WOLF = WW_WEREWOLF;

    /**
     * 根据狼人杀身份定义获取注册的 SRERole
     */
    public static SRERole forDef(WerewolfRoleDef def) {
        return switch (def) {
            case INNOCENT -> WW_INNOCENT;
            case ALCHEMIST -> WW_ALCHEMIST;
            case PROPHET -> WW_PROPHET;
            case GUARDIAN -> WW_GUARDIAN;
            case KNIGHT -> WW_KNIGHT;
            case HUNTER -> WW_HUNTER;
            case WEREWOLF -> WW_WEREWOLF;
            case WHITE_WOLF_KING -> WW_WHITE_WOLF_KING;
        };
    }

    /**
     * 初始化（触发类加载与角色注册）
     */
    public static void init() {
        // 空方法，仅用于触发静态字段初始化
    }
}
