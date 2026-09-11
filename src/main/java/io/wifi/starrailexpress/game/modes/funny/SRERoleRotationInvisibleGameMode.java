package io.wifi.starrailexpress.game.modes.funny;

import net.minecraft.resources.ResourceLocation;

/**
 * 不可见职业轮选模式。
 * <p>
 * 玩法与 {@link SRERoleRotationGameMode}（闪电轮抽）完全一致，唯一区别是轮选期间任何人都看不到
 * 别人选了什么职业：服务端逐人脱敏同步包（只保留接收者自己的职业、随机标记与候选池），
 * 客户端玩家列表把其他人渲染成「已选择/未选择」而不是具体职业名。
 */
public class SRERoleRotationInvisibleGameMode extends SRERoleRotationGameMode {

    public SRERoleRotationInvisibleGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    protected boolean hideOthersSelections() {
        return true;
    }
}
