package org.agmas.noellesroles.game.modes.werewolf;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;

/**
 * 狼人杀空壳 SRERole 实例
 * 仅用于框架兼容（gameWorldComponent.addRole()），不注册任何技能
 * Author: jiale
 */
public class WerewolfRoles {
    /**
     * 好人阵营空壳角色
     * isInnocent = true, canUseKiller = false
     */
    public static final SRERole GOOD = new NormalRole(
            SRE.jialeId("ww_good"),
            0x5CFF4A,     // 绿色
            true,         // isInnocent
            false,        // canUseKiller
            SRERole.MoodType.NONE,
            -1,           // maxSprintTime
            false         // canSeeTime
    ).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    /**
     * 狼方阵营空壳角色
     * isInnocent = false, canUseKiller = true
     */
    public static final SRERole WOLF = new NormalRole(
            SRE.jialeId("ww_wolf"),
            0xC13838,     // 红色
            false,        // isInnocent
            true,         // canUseKiller
            SRERole.MoodType.NONE,
            -1,           // maxSprintTime
            false         // canSeeTime
    ).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

    /**
     * 初始化（触发类加载）
     */
    public static void init() {
        // 空方法，仅用于触发静态字段初始化
    }
}
