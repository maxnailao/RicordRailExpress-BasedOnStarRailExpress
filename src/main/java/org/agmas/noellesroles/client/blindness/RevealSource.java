package org.agmas.noellesroles.client.blindness;

/**
 * 方块轮廓揭示来源（移植自"失明症"模组 RevealSource）
 * <p>
 * 不同来源的揭示具有不同优先级与视觉强度：导盲杖敲击的中心块最清晰，
 * 生物声纹只能给出微弱、短暂的模糊轮廓——信息量按来源分级。
 * 重复揭示同一方块时，来源只会向更高优先级升级，不会降级。
 */
public enum RevealSource {

    /** 导盲杖敲击命中的中心块：最清晰，零延迟出现 */
    CANE_CENTER(5),
    /** 导盲杖揭示的邻接块：略暗，延迟出现 */
    CANE_ADJACENT(4),
    /** 危险生物声纹 */
    ENTITY_DANGER(3),
    /** 普通生物声纹 */
    ENTITY_AMBIENT(2),
    /** 玩家脚步声纹：最弱 */
    ENTITY_FOOTSTEP(1);

    private final int priority;

    RevealSource(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return this.priority;
    }
}
