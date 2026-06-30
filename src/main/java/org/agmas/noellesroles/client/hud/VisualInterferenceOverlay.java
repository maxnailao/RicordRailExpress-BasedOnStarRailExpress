package org.agmas.noellesroles.client.hud;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Random;

/**
 * 视觉干扰花屏覆盖渲染器
 * <p>
 * 当玩家拥有 {@link ModEffects#SHIJUEGANRAO} 效果时，在屏幕上随机绘制
 * 白色、灰色、黑色的小像素块，模拟"花屏"视觉干扰效果。
 * <p>
 * 像素位置基于游戏 tick 种子随机生成，每 tick 刷新一次，产生闪烁干扰感。
 */
public class VisualInterferenceOverlay {

    /** 每帧绘制的像素块数量 */
    private static final int PIXEL_COUNT = 2500;

    /** 每个像素块的大小（像素） */
    private static final int PIXEL_SIZE = 2;

    /** 花屏像素的可选颜色：白、浅灰、深灰、黑 */
    private static final int[] COLORS = {
            0xFFFFFFFF, // 白色
            0xFFC0C0C0, // 浅灰
            0xFF808080, // 中灰
            0xFF404040, // 深灰
            0xFF000000  // 黑色
    };

    private static final Random rng = new Random();

    public static void register() {
        CommonHudRenderCallback.EVENT.register(VisualInterferenceOverlay::render);
    }

    private static void render(FakeGuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!client.player.hasEffect(ModEffects.SHIJUEGANRAO)) return;

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        // 使用游戏时间作为种子，确保每 tick 像素位置不同但同一帧内一致
        long seed = client.level != null ? client.level.getGameTime() : System.nanoTime();
        rng.setSeed(seed);

        for (int i = 0; i < PIXEL_COUNT; i++) {
            int x = rng.nextInt(screenWidth);
            int y = rng.nextInt(screenHeight);
            int color = COLORS[rng.nextInt(COLORS.length)];

            guiGraphics.fill(x, y, x + PIXEL_SIZE, y + PIXEL_SIZE, 300, color);
        }
    }
}
