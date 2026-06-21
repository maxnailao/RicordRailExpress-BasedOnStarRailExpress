package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.network.packet.ScoreboardSubmitC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import org.agmas.noellesroles.client.animation.AbstractAnimation;
import org.agmas.noellesroles.client.animation.BezierAnimation;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

/**
 * 掌机撬锁积分小游戏
 * 3 分钟内无限撬锁，每成功撬开一把 +100 分，时间结束后提交分数到积分榜
 */
public class LockpickScoreMinigameScreen extends Screen {

    // ══════════════════════════════════════════════
    // 内部类（复用撬锁小游戏的结构）
    // ══════════════════════════════════════════════

    private static class SimulatedLock {
        private final ArrayList<Integer> series;
        private final int length;
        private final float resistance;
        private final RandomSource random;

        SimulatedLock(int length, float resistance) {
            this.length = length;
            this.resistance = resistance;
            this.series = new ArrayList<>(length);
            this.random = RandomSource.create();
            resetSeries();
        }

        void resetSeries() {
            series.clear();
            for (int i = 0; i < length; ++i)
                series.add(-1);
            for (int i = 0, randomIdx = random.nextInt(length); i < length; ++i) {
                while (series.get(randomIdx) != -1)
                    randomIdx = random.nextInt(length);
                series.set(randomIdx, i);
            }
        }

        int getSeriesUnlockIdx(int idx) { return series.get(idx); }
        float getResistance() { return resistance; }
        RandomSource getRandom() { return random; }
    }

    private static class LockPickWidget extends TextureWidget {
        private int length;
        private int bodyWidth;
        private int pixelSize;
        private static final int textureWidth = 16;
        private static final int textureHeight = 2;

        LockPickWidget() {
            super(0, 0, 0, 0, 16, 2,
                    ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/lock_game_lock_pick.png"));
        }

        void initLockPick(int x, int y, int length, int bodyLength, int pixelSize) {
            this.setX(x);
            this.setY(y);
            this.length = length;
            this.bodyWidth = bodyLength;
            this.pixelSize = pixelSize;
            this.width = ((length - 1) * bodyLength + textureWidth) * pixelSize;
            this.height = textureHeight * pixelSize;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float f) {
            for (int i = 0, offsetX = 0, curWidth = bodyWidth; i < length; ++i, offsetX += bodyWidth * pixelSize) {
                if (i == length - 1) curWidth = textureWidth;
                guiGraphics.blit(TEXTURE,
                        this.getX() + offsetX, this.getY(),
                        curWidth * pixelSize, this.height,
                        0, 0,
                        curWidth, textureHeight,
                        textureWidth, textureHeight);
            }
        }

        int getLockPickWidth() { return textureWidth; }
    }

    // ══════════════════════════════════════════════
    // 常量
    // ══════════════════════════════════════════════

    private static final String MINIGAME_ID = "lockpick_score";
    private static final int GAME_DURATION_SECONDS = 180; // 3 分钟
    private static final int SCORE_PER_LOCK = 100;
    private static final int LOCK_LENGTH = 6;
    private static final float RESISTANCE = 0.08f;
    private static int pixelSize = 8;
    private static final int lockWidth = 6;
    private static final int lockHeight = 16;
    private static final int lockCoreWidth = 2;
    private static final int lockCoreHeight = 11;
    private static final int lockInterval = 5;

    // 颜色常量
    private static final int C_BG = 0xCC0A0A14;
    private static final int C_SCORE = 0xFFFFD700;
    private static final int C_TIME = 0xFFFFFFFF;
    private static final int C_TIME_WARN = 0xFFFF4444;
    private static final int C_HINT = 0x99FFFFFF;

    // ══════════════════════════════════════════════
    // 游戏状态
    // ══════════════════════════════════════════════

    private enum GameState { PLAYING, ENDED }

    private final Runnable onSuccess;
    private GameState state = GameState.PLAYING;
    private int score = 0;
    private long gameEndTime; // 游戏结束的绝对时间戳
    private long endTime = 0;

    private final SimulatedLock simulatedLock;
    private final ArrayList<TextureWidget> lockCores = new ArrayList<>();
    private final Queue<AbstractAnimation> animations = new ArrayDeque<>();
    private LockPickWidget lockPick;
    private int curIdx = 0;
    private int unlockingIdx = 0;

    public LockpickScoreMinigameScreen(BlockPos questPos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.lockpick_score_minigame"));
        this.onSuccess = onSuccess;
        this.simulatedLock = new SimulatedLock(LOCK_LENGTH, RESISTANCE);
    }

    // ══════════════════════════════════════════════
    // 初始化
    // ══════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        lockCores.clear();
        animations.clear();
        curIdx = 0;
        unlockingIdx = 0;
        score = 0;
        state = GameState.PLAYING;
        endTime = 0;
        gameEndTime = System.currentTimeMillis() + GAME_DURATION_SECONDS * 1000L;

        int totalPixels = LOCK_LENGTH * 5 + 1;
        while ((width < totalPixels * pixelSize || height < lockHeight * pixelSize) && pixelSize > 1)
            --pixelSize;

        int lockStartX = width / 2 - totalPixels * pixelSize / 2;
        int lockStartY = height / 2 - lockHeight * pixelSize / 2 + 20; // 下移留出顶部 HUD 空间
        int lockCoreStartX = lockStartX + 2 * pixelSize;
        int lockCoreStartY = lockStartY + 2 * pixelSize;

        lockPick = new LockPickWidget();
        int lockPickX = lockStartX - (lockPick.getLockPickWidth() + (LOCK_LENGTH - 1) * (lockWidth - 1) - 4) * pixelSize;
        int lockPickY = lockStartY + 13 * pixelSize;
        lockPick.initLockPick(lockPickX, lockPickY, LOCK_LENGTH, lockInterval, pixelSize);
        addRenderableWidget(lockPick);

        for (int i = 0, offsetX = 0; i < LOCK_LENGTH; ++i, offsetX += (lockWidth - 1) * pixelSize) {
            int curWidth = i == LOCK_LENGTH - 1 ? 6 : 5;
            TextureWidget lockBody = new TextureWidget(
                    lockStartX + offsetX, lockStartY,
                    curWidth * pixelSize, lockHeight * pixelSize,
                    curWidth, lockHeight,
                    lockWidth, lockHeight,
                    ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/lock_game_lock.png"));
            addRenderableWidget(lockBody);
            TextureWidget lockCore = new TextureWidget(
                    lockCoreStartX + offsetX, lockCoreStartY,
                    lockCoreWidth * pixelSize, lockCoreHeight * pixelSize,
                    lockCoreWidth, lockCoreHeight,
                    ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/lock_game_core.png"));
            addRenderableWidget(lockCore);
            lockCores.add(lockCore);
        }
    }

    // ══════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 更新动画
        animations.forEach(animation -> animation.renderUpdate(partialTick));
        animations.removeIf(AbstractAnimation::isFinished);

        if (state == GameState.PLAYING) {
            // 基于绝对时间计算剩余秒数，避免帧率/多次init导致的计时误差
            float remainingSec = Math.max(0, (gameEndTime - System.currentTimeMillis()) / 1000f);

            if (remainingSec <= 0) {
                endGame();
            }

            // 绘制 HUD：分数（左上）、倒计时（右上）
            String scoreText = Component.translatable("screen.starrailexpress.lockpick_score.score", score).getString();
            String timeText = formatTime(remainingSec);

            g.drawString(this.font, scoreText, 10, 10, C_SCORE, true);

            int timeColor = remainingSec <= 30 ? C_TIME_WARN : C_TIME;
            int timeTextWidth = this.font.width(timeText);
            g.drawString(this.font, timeText, width - timeTextWidth - 10, 10, timeColor, true);

            // 底部提示
            g.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.lockpick_hint"),
                    width / 2, height - pixelSize * 2, C_HINT);
        } else {
            // 结算界面
            int cx = width / 2;
            int cy = height / 2;

            // 半透明背景遮罩
            g.fill(0, 0, width, height, C_BG);

            g.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.lockpick_score.time_up"),
                    cx, cy - 40, C_TIME_WARN);

            g.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.lockpick_score.final_score", score),
                    cx, cy, C_SCORE);

            int lockCount = score / SCORE_PER_LOCK;
            g.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.lockpick_score.lock_count", lockCount),
                    cx, cy + 25, C_TIME);

            g.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.lockpick_score.close_hint"),
                    cx, cy + 60, C_HINT);

            // 3 秒后自动退出
            if (endTime > 0 && System.currentTimeMillis() - endTime > 3000) {
                if (onSuccess != null) onSuccess.run();
                onClose();
            }
        }
    }

    // ══════════════════════════════════════════════
    // 键盘输入
    // ══════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state != GameState.PLAYING) {
            // 结算界面按任意键退出
            if (onSuccess != null) onSuccess.run();
            onClose();
            return true;
        }

        int upMovement = 1;
        return switch (keyCode) {
            case GLFW.GLFW_KEY_W -> {
                boolean alreadyUnlocked = false;
                for (int i = 0; i < unlockingIdx; ++i) {
                    if (curIdx == simulatedLock.getSeriesUnlockIdx(i)) {
                        alreadyUnlocked = true;
                        break;
                    }
                }
                if (alreadyUnlocked) yield true;

                animations.add(new BezierAnimation(lockPick,
                        new Vec2(0, -upMovement * pixelSize), new Vec2(0, -upMovement * pixelSize),
                        new Vec2(0, 0), 5));

                if (curIdx == simulatedLock.getSeriesUnlockIdx(unlockingIdx)) {
                    animations.add(new BezierAnimation(lockCores.get(curIdx),
                            new Vec2(0, -upMovement * pixelSize), new Vec2(0, -upMovement * pixelSize),
                            new Vec2(0, -upMovement * pixelSize), 5));
                    ++unlockingIdx;
                    if (unlockingIdx == LOCK_LENGTH) {
                        // 成功撬开一把锁，加分并重置
                        score += SCORE_PER_LOCK;
                        resetLock();
                    }
                } else {
                    animations.add(new BezierAnimation(lockCores.get(curIdx),
                            new Vec2(0, -upMovement * pixelSize), new Vec2(0, -upMovement * pixelSize),
                            new Vec2(0, 0), 5));
                    if (simulatedLock.getRandom().nextFloat() < simulatedLock.getResistance()) {
                        // 撬锁失败，重置锁芯（不扣分）
                        resetLock();
                    }
                }
                yield true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (curIdx > 0) {
                    animations.add(new BezierAnimation(lockPick,
                            new Vec2(-lockInterval * pixelSize, 0), 10));
                    --curIdx;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_D -> {
                if (curIdx < LOCK_LENGTH - 1) {
                    animations.add(new BezierAnimation(lockPick,
                            new Vec2(lockInterval * pixelSize, 0), 10));
                    ++curIdx;
                }
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    // ══════════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════════

    /** 重置锁芯到初始位置，开始下一把锁 */
    private void resetLock() {
        simulatedLock.resetSeries();
        unlockingIdx = 0;
        curIdx = 0;
        int lockCoreStartY = height / 2 - lockHeight * pixelSize / 2 + 20 + 2 * pixelSize;
        for (TextureWidget core : lockCores) {
            core.setY(lockCoreStartY);
        }
        // 重置撬锁器位置
        int lockStartX = width / 2 - (LOCK_LENGTH * 5 + 1) * pixelSize / 2;
        int lockPickX = lockStartX - (lockPick.getLockPickWidth() + (LOCK_LENGTH - 1) * (lockWidth - 1) - 4) * pixelSize;
        lockPick.setX(lockPickX);
    }

    /** 游戏结束，提交分数 */
    private void endGame() {
        state = GameState.ENDED;
        endTime = System.currentTimeMillis();
        ClientPlayNetworking.send(new ScoreboardSubmitC2SPacket(MINIGAME_ID, score));
    }

    /** 格式化时间为 m:ss */
    private String formatTime(float seconds) {
        int totalSec = (int) Math.ceil(seconds);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
