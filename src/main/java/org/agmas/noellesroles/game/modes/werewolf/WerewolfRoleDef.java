package org.agmas.noellesroles.game.modes.werewolf;

import java.util.ArrayList;
import java.util.List;

/**
 * 狼人杀角色定义
 * Author: jiale
 */
public enum WerewolfRoleDef {
    // === 好人阵营 ===
    INNOCENT("innocent", Faction.GOOD, false),           // 无辜者（平民）
    ALCHEMIST("alchemist", Faction.GOOD, true),          // 炼药师（女巫）
    PROPHET("prophet", Faction.GOOD, true),              // 预言家
    GUARDIAN("guardian", Faction.GOOD, true),            // 守护者
    KNIGHT("knight", Faction.GOOD, true),                // 骑士
    HUNTER("hunter", Faction.GOOD, true),                // 猎人

    // === 狼方阵营 ===
    WEREWOLF("werewolf", Faction.WOLF, false),           // 狼人
    WHITE_WOLF_KING("white_wolf_king", Faction.WOLF, false); // 白狼王

    /** 角色ID */
    public final String id;
    /** 阵营 */
    public final Faction faction;
    /** 是否是神职（非平民的好人） */
    public final boolean isGod;

    WerewolfRoleDef(String id, Faction faction, boolean isGod) {
        this.id = id;
        this.faction = faction;
        this.isGod = isGod;
    }

    /**
     * 阵营枚举
     */
    public enum Faction {
        GOOD,  // 好人阵营
        WOLF   // 狼方阵营
    }

    /**
     * 是否是好人阵营
     */
    public boolean isGood() {
        return faction == Faction.GOOD;
    }

    /**
     * 是否是狼方阵营
     */
    public boolean isWolf() {
        return faction == Faction.WOLF;
    }

    /**
     * 是否是平民（无辜者）
     */
    public boolean isInnocent() {
        return this == INNOCENT;
    }

    /**
     * 根据ID获取角色定义
     */
    public static WerewolfRoleDef byId(String id) {
        for (WerewolfRoleDef role : values()) {
            if (role.id.equals(id)) {
                return role;
            }
        }
        return INNOCENT;
    }

    /**
     * 根据玩家人数获取角色配置
     * 标准狼人杀配置：
     * 6人: 2狼 + 预言家 + 守护者 + 2无辜者
     * 8人: 2狼 + 预言家 + 炼药师 + 守护者 + 猎人 + 2无辜者
     * 9人: 3狼 + 预言家 + 炼药师 + 守护者 + 猎人 + 3无辜者
     * 12人: 3狼(含白狼王) + 预言家 + 炼药师 + 守护者 + 骑士 + 猎人 + 4无辜者
     */
    public static List<WerewolfRoleDef> getRoleConfig(int playerCount) {
        List<WerewolfRoleDef> roles = new ArrayList<>();

        if (playerCount <= 6) {
            // 6人局: 2狼 + 预言家 + 守护者 + 2无辜者
            roles.add(WEREWOLF);
            roles.add(WEREWOLF);
            roles.add(PROPHET);
            roles.add(GUARDIAN);
            roles.add(INNOCENT);
            roles.add(INNOCENT);
        } else if (playerCount <= 8) {
            // 8人局: 2狼 + 预言家 + 炼药师 + 守护者 + 猎人 + 2无辜者
            roles.add(WEREWOLF);
            roles.add(WEREWOLF);
            roles.add(PROPHET);
            roles.add(ALCHEMIST);
            roles.add(GUARDIAN);
            roles.add(HUNTER);
            roles.add(INNOCENT);
            roles.add(INNOCENT);
        } else if (playerCount <= 9) {
            // 9人局: 3狼 + 预言家 + 炼药师 + 守护者 + 猎人 + 3无辜者
            roles.add(WEREWOLF);
            roles.add(WEREWOLF);
            roles.add(WEREWOLF);
            roles.add(PROPHET);
            roles.add(ALCHEMIST);
            roles.add(GUARDIAN);
            roles.add(HUNTER);
            roles.add(INNOCENT);
            roles.add(INNOCENT);
        } else {
            // 12人局: 3狼(含白狼王) + 预言家 + 炼药师 + 守护者 + 骑士 + 猎人 + 4无辜者
            roles.add(WHITE_WOLF_KING);
            roles.add(WEREWOLF);
            roles.add(WEREWOLF);
            roles.add(PROPHET);
            roles.add(ALCHEMIST);
            roles.add(GUARDIAN);
            roles.add(KNIGHT);
            roles.add(HUNTER);
            roles.add(INNOCENT);
            roles.add(INNOCENT);
            roles.add(INNOCENT);
            roles.add(INNOCENT);
        }

        // 如果人数超过配置，补充无辜者
        while (roles.size() < playerCount) {
            roles.add(INNOCENT);
        }

        // 如果人数少于配置（不应该发生），移除多余的无辜者
        while (roles.size() > playerCount) {
            roles.remove(roles.size() - 1);
        }

        return roles;
    }

    /**
     * 获取翻译键
     */
    public String getTranslationKey() {
        return "werewolf.role." + id;
    }
}
