package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.network.packet.ScoreboardSubmitC2SPacket;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 打靶小游戏 - 单机积分制（含难度选择）
 */
public class TargetShootingMinigameScreen extends Screen {

    // ══════════════════════════════════════════════
    // 难度定义
    // ══════════════════════════════════════════════

    public enum Difficulty {
        EASY(2000, 5000, 48, 1.0f, 0, 5,
                "screen.starrailexpress.target_shooting.diff_easy", 0x55FF55),
        NORMAL(1300, 4000, 38, 1.5f, 300, 5,
                "screen.starrailexpress.target_shooting.diff_normal", 0xFFAA00),
        HARD(800, 2300, 38, 1.8f, 800, 3,
                "screen.starrailexpress.target_shooting.diff_hard", 0xFF4444);

        public final long spawnInterval;
        public final long targetLifetime;
        public final int targetSize;
        public final float scoreMultiplier;
        public final long missPenalty;
        public final int timeBonusSeconds;
        public final String nameKey;
        public final int color;

        Difficulty(long si, long tl, int ts, float sm, long mp, int tbs, String nk, int c) {
            spawnInterval = si; targetLifetime = tl; targetSize = ts;
            scoreMultiplier = sm; missPenalty = mp; timeBonusSeconds = tbs;
            nameKey = nk; color = c;
        }
    }

    // ══════════════════════════════════════════════
    // 常量
    // ══════════════════════════════════════════════

    private static final int TOTAL_AVATARS = 5;
    private static final int GAME_DURATION = 60;
    private static final float TIME_BONUS_CHANCE = 0.1f;
    private static final int HIT_SCORE = 10;
    private static final int MISS_PENALTY_PTS = 15;
    private static final int TIME_ICON_SIZE = 36;
    private static final int AVATAR_INFO_SIZE = 32;
    private static final int CROSSHAIR_SIZE = 32;
    private static final int MIN_TARGET_GAP = 20;
    private static final int BG_COLOR = 0xCC0A0A14;
    private static final int AREA_COLOR = 0xAA16162A;
    private static final int GRID_COLOR = 0x18FFFFFF;

    private static final ResourceLocation[] AVATAR_TEX = new ResourceLocation[TOTAL_AVATARS];
    private static final ResourceLocation TIME_TEX =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/daba/timeadd.png");
    private static final ResourceLocation CROSSHAIR_TEX =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/daba/zhunxin.png");
    static {
        for (int i = 0; i < TOTAL_AVATARS; i++)
            AVATAR_TEX[i] = ResourceLocation.fromNamespaceAndPath("noellesroles",
                    "textures/gui/daba/player" + (i + 1) + ".png");
    }

    // ══════════════════════════════════════════════
    // 状态
    // ══════════════════════════════════════════════

    private enum State { SELECT_DIFFICULTY, PLAYING, ENDED }
    private State state = State.SELECT_DIFFICULTY;
    private Difficulty difficulty = Difficulty.EASY;
    private int score = 0;
    private float remainingTime = GAME_DURATION;
    private long lastSpawnTime, lastFrameTime, endTime = 0;
    private final List<Integer> killerAvatars = new ArrayList<>();
    private final List<Target> activeTargets = new ArrayList<>();
    private final Random random = new Random();
    private int nextTargetId = 0;
    private long penaltyEndTime = 0;
    private long clickTime = 0;
    private int clickX, clickY;
    private boolean clickHit, clickMiss;
    private int gameLeft, gameTop, gameWidth, gameHeight;
    private final Runnable onSuccess;

    private enum TargetType { KILLER, NORMAL, TIME_BONUS }
    private record Target(int id, int x, int y, int avatarIndex, TargetType type, long spawnTime) {}

    public TargetShootingMinigameScreen(BlockPos pos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.target_shooting.title"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        super.init();
        gameWidth = (int) (this.width * 0.7);
        gameHeight = (int) (this.height * 0.75);
        gameLeft = this.width / 2 - gameWidth / 2 + 60;
        gameTop = this.height / 2 - gameHeight / 2 + 20;
        state = State.SELECT_DIFFICULTY;
    }

    @Override public void removed() { restoreCursor(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }

    private void restoreCursor() {
        if (minecraft != null)
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    private void startGame(Difficulty diff) {
        this.difficulty = diff;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < TOTAL_AVATARS; i++) idx.add(i);
        Collections.shuffle(idx, random);
        killerAvatars.clear();
        killerAvatars.add(idx.get(0));
        killerAvatars.add(idx.get(1));
        Collections.sort(killerAvatars);

        score = 0; remainingTime = GAME_DURATION;
        activeTargets.clear(); nextTargetId = 0;
        penaltyEndTime = 0; clickHit = false; clickMiss = false;
        state = State.PLAYING; endTime = 0;
        long now = System.currentTimeMillis();
        lastSpawnTime = now; lastFrameTime = now;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    // ══════════════════════════════════════════════
    // 主渲染
    // ══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float dt = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        g.fill(0, 0, this.width, this.height, BG_COLOR);
        super.render(g, mouseX, mouseY, partialTick);

        switch (state) {
            case SELECT_DIFFICULTY -> renderDifficultySelection(g, mouseX, mouseY);
            case PLAYING -> renderPlaying(g, mouseX, mouseY, now, dt);
            case ENDED -> renderEnded(g);
        }

        if (state == State.PLAYING) {
            g.blit(CROSSHAIR_TEX, mouseX - CROSSHAIR_SIZE / 2, mouseY - CROSSHAIR_SIZE / 2,
                    0, 0, CROSSHAIR_SIZE, CROSSHAIR_SIZE, CROSSHAIR_SIZE, CROSSHAIR_SIZE);
            if (now < penaltyEndTime) {
                long rem = penaltyEndTime - now;
                int red = ((int) (Math.min(1f, rem / 200f) * 200) << 24) | 0xFF0000;
                g.fill(mouseX - 12, mouseY - 1, mouseX + 12, mouseY + 1, red);
                g.fill(mouseX - 1, mouseY - 12, mouseX + 1, mouseY + 12, red);
                g.drawCenteredString(this.font, String.format("%.1fs", rem / 1000f), mouseX, mouseY + 16, red);
            }
        }
    }

    // ── 难度选择 ──

    private void renderDifficultySelection(GuiGraphics g, int mx, int my) {
        int cx = this.width / 2, cy = this.height / 2;
        g.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.target_shooting.select_difficulty").getString(),
                cx, cy - 120, 0xFFFFFF);

        Difficulty[] diffs = Difficulty.values();
        int pW = 150, pH = 180, gap = 24;
        int startX = cx - (pW * 3 + gap * 2) / 2;
        int py = cy - 70;

        for (int i = 0; i < diffs.length; i++) {
            Difficulty d = diffs[i];
            int px = startX + i * (pW + gap);
            boolean hover = mx >= px && mx <= px + pW && my >= py && my <= py + pH;
            g.fill(px, py, px + pW, py + pH, hover ? 0xEE2A2A4E : 0xEE1A1A2E);
            g.renderOutline(px, py, pW, pH, d.color);
            g.drawCenteredString(this.font, Component.translatable(d.nameKey).getString(), px + pW / 2, py + 14, d.color);

            int ty = py + 40;
            for (String line : getDiffDesc(d)) { g.drawCenteredString(this.font, line, px + pW / 2, ty, 0xBBBBBB); ty += 18; }
            g.drawCenteredString(this.font, String.format("x%.1f", d.scoreMultiplier), px + pW / 2, py + pH - 30, d.color);
        }
    }

    private String[] getDiffDesc(Difficulty d) {
        return switch (d) {
            case EASY -> new String[]{"2s刷新 / 5s存在", "大小: 48px", "时钟: +5s", "无空枪惩罚"};
            case NORMAL -> new String[]{"1.3s刷新 / 4s存在", "大小: 38px", "时钟: +5s", "空枪惩罚: 0.3s"};
            case HARD -> new String[]{"0.8s刷新 / 2.3s存在", "大小: 38px", "时钟: +3s", "空枪惩罚: 0.8s"};
        };
    }

    private boolean handleDifficultyClick(int mx, int my) {
        int cx = this.width / 2, cy = this.height / 2;
        int pW = 150, pH = 180, gap = 24;
        int startX = cx - (pW * 3 + gap * 2) / 2;
        int py = cy - 70;
        for (Difficulty d : Difficulty.values()) {
            int px = startX + d.ordinal() * (pW + gap);
            if (mx >= px && mx <= px + pW && my >= py && my <= py + pH) { startGame(d); return true; }
        }
        return false;
    }

    // ── 游戏中渲染 ──

    private void renderPlaying(GuiGraphics g, int mx, int my, long now, float dt) {
        remainingTime -= dt;
        if (remainingTime <= 0) { remainingTime = 0; endGame(); return; }
        if (now - lastSpawnTime >= difficulty.spawnInterval) { spawnTarget(now); lastSpawnTime = now; }
        activeTargets.removeIf(t -> now - t.spawnTime() >= difficulty.targetLifetime);

        g.fill(gameLeft, gameTop, gameLeft + gameWidth, gameTop + gameHeight, AREA_COLOR);
        g.renderOutline(gameLeft, gameTop, gameWidth, gameHeight, 0xFF3A3A5A);
        int cW = gameWidth / 8, cH = gameHeight / 5;
        for (int i = 1; i < 8; i++) g.fill(gameLeft + i * cW, gameTop, gameLeft + i * cW + 1, gameTop + gameHeight, GRID_COLOR);
        for (int j = 1; j < 5; j++) g.fill(gameLeft, gameTop + j * cH, gameLeft + gameWidth, gameTop + j * cH + 1, GRID_COLOR);

        int tSize = difficulty.targetSize;
        for (Target t : activeTargets) {
            int sz = t.type() == TargetType.TIME_BONUS ? TIME_ICON_SIZE : tSize;
            ResourceLocation tex = t.type() == TargetType.TIME_BONUS ? TIME_TEX : AVATAR_TEX[t.avatarIndex()];
            long rem = difficulty.targetLifetime - (now - t.spawnTime());
            if (rem >= 1500 || (now / 150) % 2 == 0)
                g.blit(tex, t.x() - sz / 2, t.y() - sz / 2, 0, 0, sz, sz, sz, sz);
        }
        renderClickFeedback(g, now);
        renderHUD(g);
        renderKillerInfo(g);
    }

    private void renderHUD(GuiGraphics g) {
        int hudY = gameTop - 35;
        g.fill(gameLeft, hudY, gameLeft + gameWidth, hudY + 28, 0xEE1A1A2E);
        g.renderOutline(gameLeft, hudY, gameWidth, 28, 0xFF4A4A6A);
        g.drawString(this.font, Component.translatable("screen.starrailexpress.target_shooting.score", score).getString(), gameLeft + 12, hudY + 9, 0x55FF55);
        String dl = Component.translatable(difficulty.nameKey).getString() + " x" + difficulty.scoreMultiplier;
        g.drawString(this.font, dl, gameLeft + gameWidth / 2 - this.font.width(dl) / 2, hudY + 9, difficulty.color);
        int tc = remainingTime <= 10 ? (System.currentTimeMillis() / 300 % 2 == 0 ? 0xFF4444 : 0xFF0000) : 0xFFFFFF;
        String tt = Component.translatable("screen.starrailexpress.target_shooting.time", String.format("%.1f", remainingTime)).getString();
        g.drawString(this.font, tt, gameLeft + gameWidth - this.font.width(tt) - 12, hudY + 9, tc);
        int bW = gameWidth - 160, bX = gameLeft + gameWidth / 2 - bW / 2;
        float p = Math.max(0, remainingTime / GAME_DURATION);
        g.fill(bX, hudY + 22, bX + bW, hudY + 25, 0xFF333333);
        g.fill(bX, hudY + 22, bX + (int) (bW * p), hudY + 25, remainingTime <= 10 ? 0xFFFF4444 : 0xFF44AAFF);
    }

    private void renderKillerInfo(GuiGraphics g) {
        int ix = 12, iy = gameTop - 10;
        g.fill(ix, iy, ix + 120, iy + 100, 0xDD1A1A2E);
        g.renderOutline(ix, iy, 120, 100, 0xFF4A4A6A);
        g.drawString(this.font, Component.translatable("screen.starrailexpress.target_shooting.killer_is").getString(), ix + 6, iy + 6, 0xFF4444);
        for (int i = 0; i < killerAvatars.size(); i++) {
            int ax = ix + 10 + i * (AVATAR_INFO_SIZE + 10), ay = iy + 24;
            g.fill(ax - 2, ay - 2, ax + AVATAR_INFO_SIZE + 2, ay + AVATAR_INFO_SIZE + 2, 0xFFFF2222);
            g.blit(AVATAR_TEX[killerAvatars.get(i)], ax, ay, 0, 0, AVATAR_INFO_SIZE, AVATAR_INFO_SIZE, AVATAR_INFO_SIZE, AVATAR_INFO_SIZE);
        }
        g.drawString(this.font, "+10 / -15", ix + 6, iy + 66, 0xAAAAAA);
        g.drawString(this.font, Component.translatable("screen.starrailexpress.target_shooting.hint").getString(), ix + 6, iy + 80, 0x666666);
    }

    private void renderClickFeedback(GuiGraphics g, long now) {
        if (clickTime > 0 && now - clickTime < 400) {
            float a = 1f - (now - clickTime) / 400f;
            int c; String t;
            if (clickHit) { c = ((int) (a * 255) << 24) | 0x00FF44; t = "+" + HIT_SCORE; }
            else if (clickMiss) { c = ((int) (a * 255) << 24) | 0xFF4444; t = "-" + MISS_PENALTY_PTS; }
            else { c = ((int) (a * 255) << 24) | 0x888888; t = "MISS"; }
            g.drawCenteredString(this.font, t, clickX, clickY - 16, c);
        } else clickTime = 0;
    }

    // ── 结束画面 ──

    private void renderEnded(GuiGraphics g) {
        int cx = this.width / 2, cy = this.height / 2;
        g.fill(cx - 170, cy - 90, cx + 170, cy + 90, 0xEE1A1A2E);
        g.renderOutline(cx - 170, cy - 90, 340, 180, 0xFF4A4A6A);
        g.drawCenteredString(this.font, Component.translatable("screen.starrailexpress.target_shooting.game_over").getString(), cx, cy - 70, 0xFFFF4444);
        g.drawCenteredString(this.font, Component.translatable("screen.starrailexpress.target_shooting.score", score).getString(), cx, cy - 40, 0xFFFFFF);
        int fs = (int) (score * difficulty.scoreMultiplier);
        g.drawCenteredString(this.font, Component.translatable(difficulty.nameKey).getString() + String.format(" x%.1f \u2192 %d", difficulty.scoreMultiplier, fs), cx, cy - 15, difficulty.color);
        String grade = getGrade(fs);
        g.drawCenteredString(this.font, grade, cx, cy + 15, fs >= 80 ? 0xFFFFD700 : fs >= 40 ? 0x55FF55 : 0xAAAAAA);
        g.drawCenteredString(this.font, Component.translatable("screen.starrailexpress.target_shooting.close_hint").getString(), cx, cy + 55, 0x666666);
        if (endTime > 0 && System.currentTimeMillis() - endTime > 3000) {
            if (onSuccess != null) onSuccess.run();
            restoreCursor(); onClose();
        }
    }

    private String getGrade(int fs) {
        if (fs >= 100) return "S - " + Component.translatable("screen.starrailexpress.target_shooting.grade_s").getString();
        if (fs >= 60) return "A - " + Component.translatable("screen.starrailexpress.target_shooting.grade_a").getString();
        if (fs >= 30) return "B - " + Component.translatable("screen.starrailexpress.target_shooting.grade_b").getString();
        if (fs >= 0) return "C - " + Component.translatable("screen.starrailexpress.target_shooting.grade_c").getString();
        return "D - " + Component.translatable("screen.starrailexpress.target_shooting.grade_d").getString();
    }

    // ══════════════════════════════════════════════
    // 输入
    // ══════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (state == State.SELECT_DIFFICULTY) { handleDifficultyClick((int) mouseX, (int) mouseY); return true; }
        if (state != State.PLAYING) return super.mouseClicked(mouseX, mouseY, button);

        long now = System.currentTimeMillis();
        clickX = (int) mouseX; clickY = (int) mouseY;

        // Miss 惩罚锁定
        if (now < penaltyEndTime) return true;

        clickTime = now; clickHit = false; clickMiss = false;
        playGunSound();

        int tSize = difficulty.targetSize;
        boolean hitAny = false;
        for (int i = activeTargets.size() - 1; i >= 0; i--) {
            Target t = activeTargets.get(i);
            int sz = t.type() == TargetType.TIME_BONUS ? TIME_ICON_SIZE : tSize;
            int half = sz / 2;
            if (mouseX >= t.x() - half && mouseX <= t.x() + half && mouseY >= t.y() - half && mouseY <= t.y() + half) {
                activeTargets.remove(i); hitAny = true;
                switch (t.type()) {
                    case KILLER -> { score += HIT_SCORE; clickHit = true; }
                    case NORMAL -> { score -= MISS_PENALTY_PTS; clickMiss = true; }
                    case TIME_BONUS -> { remainingTime = Math.min(remainingTime + difficulty.timeBonusSeconds, 120); clickHit = true; }
                }
                break;
            }
        }
        if (!hitAny && difficulty.missPenalty > 0) penaltyEndTime = now + difficulty.missPenalty;
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ESCAPE) { restoreCursor(); onClose(); return true; }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public void onClose() { restoreCursor(); super.onClose(); }

    // ══════════════════════════════════════════════
    // 游戏逻辑
    // ══════════════════════════════════════════════

    private void spawnTarget(long now) {
        int cW = gameWidth / 8, cH = gameHeight / 5, tSize = difficulty.targetSize;
        for (int att = 0; att < 30; att++) {
            int col = 1 + random.nextInt(6), row = random.nextInt(5);
            int x = gameLeft + col * cW + random.nextInt(cW) - cW / 2;
            int y = gameTop + row * cH + random.nextInt(cH) - cH / 2;
            if (x - tSize / 2 < gameLeft + 8 || x + tSize / 2 > gameLeft + gameWidth - 8) continue;
            if (y - tSize / 2 < gameTop + 8 || y + tSize / 2 > gameTop + gameHeight - 8) continue;
            boolean overlap = false;
            for (Target e : activeTargets) {
                if (Math.abs(x - e.x()) < tSize + MIN_TARGET_GAP && Math.abs(y - e.y()) < tSize + MIN_TARGET_GAP) { overlap = true; break; }
            }
            if (overlap) continue;
            if (random.nextFloat() < TIME_BONUS_CHANCE)
                activeTargets.add(new Target(nextTargetId++, x, y, -1, TargetType.TIME_BONUS, now));
            else {
                int ai = random.nextInt(TOTAL_AVATARS);
                activeTargets.add(new Target(nextTargetId++, x, y, ai,
                        killerAvatars.contains(ai) ? TargetType.KILLER : TargetType.NORMAL, now));
            }
            return;
        }
    }

    private void endGame() {
        state = State.ENDED; endTime = System.currentTimeMillis();
        int finalScore = (int) (score * difficulty.scoreMultiplier);
        // 通过 C2S 网络包将分数提交到服务端，防止客户端篡改
        ClientPlayNetworking.send(new ScoreboardSubmitC2SPacket("target_shooting", finalScore));
    }

    private void playGunSound() {
        if (minecraft == null || minecraft.player == null) return;
        try { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(TMMSounds.ITEM_REVOLVER_SHOOT, 0.6f, 1.0f)); }
        catch (Exception ignored) {}
    }
}
