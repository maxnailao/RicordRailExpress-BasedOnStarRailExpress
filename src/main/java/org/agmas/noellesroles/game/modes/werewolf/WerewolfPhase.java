package org.agmas.noellesroles.game.modes.werewolf;

/**
 * 狼人杀游戏阶段枚举
 * Author: jiale
 */
public enum WerewolfPhase {
    // === 夜晚阶段 ===
    NIGHT_GUARDIAN(15 * 20, "werewolf.phase.night_guardian"),      // 守护者行动（15s）
    NIGHT_WOLVES(30 * 20, "werewolf.phase.night_wolves"),          // 狼方讨论+投票（30s）
    NIGHT_ALCHEMIST(15 * 20, "werewolf.phase.night_alchemist"),    // 炼药师行动（15s）
    NIGHT_PROPHET(15 * 20, "werewolf.phase.night_prophet"),        // 预言家行动（15s）
    NIGHT_KNIGHT(15 * 20, "werewolf.phase.night_knight"),          // 骑士行动（15s）
    NIGHT_RESOLVE(0, "werewolf.phase.night_resolve"),              // 夜晚结算（瞬时）

    // === 白天阶段 ===
    DAY_ANNOUNCE(5 * 20, "werewolf.phase.day_announce"),           // 公示死亡（5s）
    DAY_HUNTER_SHOT(10 * 20, "werewolf.phase.day_hunter_shot"),    // 猎人开枪（10s，条件触发）
    DAY_SPEECH(20 * 20, "werewolf.phase.day_speech"),              // 轮流发言（每人20s）
    DAY_VOTE(30 * 20, "werewolf.phase.day_vote"),                  // 投票（30s）
    DAY_VOTE_PK(10 * 20, "werewolf.phase.day_vote_pk"),            // PK发言（10s）
    DAY_VOTE_PK_RESULT(30 * 20, "werewolf.phase.day_vote_pk_result"), // PK投票（30s）
    DAY_EXECUTE(10 * 20, "werewolf.phase.day_execute"),            // 处决+白狼王技能（10s）
    DAY_LAST_WORDS(20 * 20, "werewolf.phase.day_last_words"),      // 遗言（20s）

    // === 结束 ===
    GAME_OVER(0, "werewolf.phase.game_over");                      // 游戏结束

    /** 阶段持续时间（tick），0表示瞬时或无限 */
    public final int durationTicks;
    /** 翻译键 */
    public final String translationKey;

    WerewolfPhase(int durationTicks, String translationKey) {
        this.durationTicks = durationTicks;
        this.translationKey = translationKey;
    }

    /**
     * 是否是夜晚阶段
     */
    public boolean isNight() {
        return this.name().startsWith("NIGHT_");
    }

    /**
     * 是否是白天阶段
     */
    public boolean isDay() {
        return this.name().startsWith("DAY_");
    }

    /**
     * 获取下一个夜晚阶段（用于夜晚行动顺序）
     */
    public WerewolfPhase nextNightPhase() {
        return switch (this) {
            case NIGHT_GUARDIAN -> NIGHT_WOLVES;
            case NIGHT_WOLVES -> NIGHT_ALCHEMIST;
            case NIGHT_ALCHEMIST -> NIGHT_PROPHET;
            case NIGHT_PROPHET -> NIGHT_KNIGHT;
            case NIGHT_KNIGHT -> NIGHT_RESOLVE;
            default -> NIGHT_RESOLVE;
        };
    }
}
