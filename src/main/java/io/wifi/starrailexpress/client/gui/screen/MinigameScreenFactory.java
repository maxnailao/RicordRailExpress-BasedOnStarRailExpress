package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端侧：根据小游戏ID创建对应的Screen实例
 */
public class MinigameScreenFactory {

    @FunctionalInterface
    public interface ScreenConstructor {
        Screen create(BlockPos pos, Runnable onSuccess);
    }

    private static final Map<String, ScreenConstructor> FACTORY = new LinkedHashMap<>();

    static {
        FACTORY.put("lockpick", LockpickMinigameScreen::new);
        FACTORY.put("key_making", KeyMakingMinigameScreen::new);
        FACTORY.put("math", MathMinigameScreen::new);
        FACTORY.put("cooking", CookingMinigameScreen::new);
        FACTORY.put("gomoku", GomokuMinigameScreen::new);
        FACTORY.put("target_shooting", TargetShootingMinigameScreen::new);
        FACTORY.put("xiangqi", XiangqiMinigameScreen::new);
        FACTORY.put("tetris", TetrisMinigameScreen::new);
        FACTORY.put("doudizhu", DoudizhuMinigameScreen::new);
        FACTORY.put("mahjong", MahjongMinigameScreen::new);
    }

    public static Screen create(String minigameId, BlockPos pos, Runnable onSuccess) {
        ScreenConstructor ctor = FACTORY.get(minigameId);
        return ctor != null ? ctor.create(pos, onSuccess) : null;
    }
}
