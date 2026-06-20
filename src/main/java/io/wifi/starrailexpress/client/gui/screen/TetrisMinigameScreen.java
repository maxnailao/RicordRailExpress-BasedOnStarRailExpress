package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.network.packet.ScoreboardSubmitC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 俄罗斯方块小游戏
 * <p>
 * 单机模式，10x20标准场地，7-bag随机器，含积分榜。
 * 操作: A/D 左右移动, W 软降, Space 硬降, 右键 旋转。
 * </p>
 */
public class TetrisMinigameScreen extends Screen {

    // ── 常量 ──
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int CELL = 20;
    private static final int FIELD_W = COLS * CELL;
    private static final int FIELD_H = ROWS * CELL;
    private static final int SIDE_W = 110;
    private static final int PANEL_W = FIELD_W + SIDE_W + 16;
    private static final int PANEL_H = FIELD_H + 20;

    private static final long LOCK_DELAY = 500;
    private static final long DAS = 150;   // 延迟自动移位
    private static final long ARR = 50;    // 自动移位重复率

    // 消行基础分: index = 消行数 - 1
    private static final int[] LINE_SCORES = {10, 30, 60, 100};

    // 方块贴图（4种颜色，随机分配给每个方块）
    private static final ResourceLocation[] BLOCK_TEX = {
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/eluosifangkuai/cyan_square.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/eluosifangkuai/green_square.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/eluosifangkuai/red_square.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/eluosifangkuai/yellow_square.png"),
    };

    // 7种方块的基础颜色（ARGB，用于 tint 叠加区分类型）
    private static final int[] TYPE_TINTS = {
            0xFF4DD0E1, // I 浅青
            0xFF5C6BC0, // J 靛蓝
            0xFFFF8A65, // L 暖橙
            0xFFFFD54F, // O 柔黄
            0xFF66BB6A, // S 翠绿
            0xFFAB47BC, // T 雅紫
            0xFFEF5350, // Z 玫红
    };

    // 7种方块的初始形状（布尔矩阵）
    private static final boolean[][][] SHAPES = {
            {{false,true,false,false},{false,true,false,false},{false,true,false,false},{false,true,false,false}}, // I
            {{true,false,false},{true,true,true}},             // J
            {{false,false,true},{true,true,true}},             // L
            {{true,true},{true,true}},                         // O
            {{false,true,true},{true,true,false}},             // S
            {{false,true,false},{true,true,true}},             // T
            {{true,true,false},{false,true,true}},             // Z
    };

    // ── 游戏状态 ──
    private enum State { PLAYING, ENDED }
    private State state = State.PLAYING;

    private final int[][] field = new int[ROWS][COLS]; // 0=空, 1-7=颜色类型+1
    private final int[][] fieldTex = new int[ROWS][COLS]; // 贴图索引 0-3

    // 当前方块
    private int curType = -1;
    private boolean[][] curShape;
    private int curRow, curCol;
    private int curTexIdx; // 贴图索引

    // 下一个方块
    private int nextType = -1;
    private boolean[][] nextShape;
    private int nextTexIdx;

    // 7-bag
    private final List<Integer> bag = new ArrayList<>();
    private final Random rng = new Random();

    // 分数 & 等级
    private int score = 0;
    private int level = 1;
    private int totalLines = 0;
    private int combo = 0;

    // 时间
    private long lastDrop;
    private long lockTimer = -1;
    private boolean onGround = false;

    // 按键状态
    private boolean keyLeft, keyRight;
    private long dasTimerL, dasTimerR;
    private boolean dasActiveL, dasActiveR;

    // 布局
    private int panelLeft, panelTop, fieldLeft, fieldTop;

    private final Runnable onSuccess;

    public TetrisMinigameScreen(BlockPos pos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.tetris.title"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        super.init();
        panelLeft = (this.width - PANEL_W) / 2;
        panelTop = (this.height - PANEL_H) / 2;
        fieldLeft = panelLeft + 8;
        fieldTop = panelTop + 10;
        lastDrop = System.currentTimeMillis();
        spawnPiece();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ══════════════════════════════════════════════
    // 方块生成（7-bag）
    // ══════════════════════════════════════════════

    private int nextFromBag() {
        if (bag.isEmpty()) {
            for (int i = 0; i < 7; i++) bag.add(i);
            Collections.shuffle(bag, rng);
        }
        return bag.remove(bag.size() - 1);
    }

    private void spawnPiece() {
        if (nextType >= 0) {
            curType = nextType;
            curShape = nextShape;
            curTexIdx = nextTexIdx;
        } else {
            curType = nextFromBag();
            curShape = copyShape(SHAPES[curType]);
            curTexIdx = rng.nextInt(BLOCK_TEX.length);
        }
        nextType = nextFromBag();
        nextShape = copyShape(SHAPES[nextType]);
        nextTexIdx = rng.nextInt(BLOCK_TEX.length);

        curRow = 0;
        curCol = (COLS - curShape[0].length) / 2;
        onGround = false;
        lockTimer = -1;

        if (collides(curRow, curCol, curShape)) {
            state = State.ENDED;
            ClientPlayNetworking.send(new ScoreboardSubmitC2SPacket("tetris", score));
        }
    }

    // ══════════════════════════════════════════════
    // 方块旋转
    // ══════════════════════════════════════════════

    private static boolean[][] copyShape(boolean[][] s) {
        boolean[][] c = new boolean[s.length][s[0].length];
        for (int r = 0; r < s.length; r++)
            System.arraycopy(s[r], 0, c[r], 0, s[0].length);
        return c;
    }

    private boolean[][] rotateCW(boolean[][] shape) {
        int h = shape.length, w = shape[0].length;
        boolean[][] rotated = new boolean[w][h];
        for (int r = 0; r < h; r++)
            for (int c = 0; c < w; c++)
                rotated[c][h - 1 - r] = shape[r][c];
        return rotated;
    }

    // ══════════════════════════════════════════════
    // 碰撞检测
    // ══════════════════════════════════════════════

    private boolean collides(int row, int col, boolean[][] shape) {
        for (int r = 0; r < shape.length; r++)
            for (int c = 0; c < shape[0].length; c++)
                if (shape[r][c]) {
                    int fr = row + r, fc = col + c;
                    if (fc < 0 || fc >= COLS || fr >= ROWS) return true;
                    if (fr >= 0 && field[fr][fc] != 0) return true;
                }
        return false;
    }

    // ══════════════════════════════════════════════
    // 锁定 & 消行
    // ══════════════════════════════════════════════

    private void lockPiece() {
        for (int r = 0; r < curShape.length; r++)
            for (int c = 0; c < curShape[0].length; c++)
                if (curShape[r][c]) {
                    int fr = curRow + r, fc = curCol + c;
                    if (fr >= 0 && fr < ROWS && fc >= 0 && fc < COLS) {
                        field[fr][fc] = curType + 1;     // 1-7 标识颜色类型
                        fieldTex[fr][fc] = curTexIdx;     // 贴图索引
                    }
                }
        clearLines();
        spawnPiece();
    }

    private void clearLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (field[r][c] == 0) { full = false; break; }
            }
            if (full) {
                cleared++;
                // 上方所有行下移一行
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(field[rr - 1], 0, field[rr], 0, COLS);
                    System.arraycopy(fieldTex[rr - 1], 0, fieldTex[rr], 0, COLS);
                }
                field[0] = new int[COLS];
                fieldTex[0] = new int[COLS];
                r++; // 重新检查当前行
            }
        }
        if (cleared > 0) {
            combo++;
            int pts = LINE_SCORES[Math.min(cleared, 4) - 1] * combo;
            score += pts;
            totalLines += cleared;
            level = score / 100 + 1;
        } else {
            combo = 0;
        }
    }

    // ══════════════════════════════════════════════
    // 幽灵方块（落点预览）
    // ══════════════════════════════════════════════

    private int ghostRow() {
        int gr = curRow;
        while (!collides(gr + 1, curCol, curShape)) gr++;
        return gr;
    }

    // ══════════════════════════════════════════════
    // 下落速度
    // ══════════════════════════════════════════════

    private long dropInterval() {
        return Math.max(50, 1000 - (level - 1) * 80L);
    }

    // ══════════════════════════════════════════════
    // 每帧更新
    // ══════════════════════════════════════════════

    @Override
    public void tick() {
        super.tick();
        if (state != State.PLAYING) return;

        long now = System.currentTimeMillis();

        // DAS 自动重复
        if (keyLeft) {
            if (!dasActiveL) {
                if (now - dasTimerL >= DAS) { dasActiveL = true; dasTimerL = now; tryMove(-1); }
            } else if (now - dasTimerL >= ARR) { dasTimerL = now; tryMove(-1); }
        }
        if (keyRight) {
            if (!dasActiveR) {
                if (now - dasTimerR >= DAS) { dasActiveR = true; dasTimerR = now; tryMove(1); }
            } else if (now - dasTimerR >= ARR) { dasTimerR = now; tryMove(1); }
        }

        // 自然下落
        long interval = keyLeft || keyRight ? dropInterval() : dropInterval();
        // W 键软降加速
        // (tick 中无法直接检测 W 持续按下，改用 keyPressed 单次触发)
        if (now - lastDrop >= interval) {
            lastDrop = now;
            if (!collides(curRow + 1, curCol, curShape)) {
                curRow++;
                onGround = false;
                lockTimer = -1;
            } else {
                onGround = true;
                if (lockTimer < 0) lockTimer = now;
                else if (now - lockTimer >= LOCK_DELAY) {
                    lockPiece();
                }
            }
        }
    }

    private void tryMove(int dx) {
        if (!collides(curRow, curCol + dx, curShape)) {
            curCol += dx;
            if (onGround) lockTimer = System.currentTimeMillis(); // 重置锁定
        }
    }

    private void tryRotate() {
        boolean[][] rotated = rotateCW(curShape);
        if (!collides(curRow, curCol, rotated)) {
            curShape = rotated;
            if (onGround) lockTimer = System.currentTimeMillis();
        }
    }

    private void hardDrop() {
        while (!collides(curRow + 1, curCol, curShape)) {
            curRow++;
            score += 1; // 硬降加分
        }
        lockPiece();
    }

    private void softDrop() {
        if (!collides(curRow + 1, curCol, curShape)) {
            curRow++;
            score += 1;
            lastDrop = System.currentTimeMillis();
        }
    }

    // ══════════════════════════════════════════════
    // 输入
    // ══════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state != State.PLAYING) return super.keyPressed(keyCode, scanCode, modifiers);

        if (keyCode == GLFW.GLFW_KEY_A) {
            keyLeft = true;
            dasTimerL = System.currentTimeMillis();
            dasActiveL = false;
            tryMove(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D) {
            keyRight = true;
            dasTimerR = System.currentTimeMillis();
            dasActiveR = false;
            tryMove(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_W) {
            softDrop();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            hardDrop();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_A) { keyLeft = false; dasActiveL = false; }
        if (keyCode == GLFW.GLFW_KEY_D) { keyRight = false; dasActiveR = false; }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state == State.PLAYING && button == 1) { // 右键旋转
            tryRotate();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ══════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        // 面板背景
        g.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + PANEL_H, 0xEE1A1A2E);
        g.renderOutline(panelLeft, panelTop, PANEL_W, PANEL_H, 0xFF4A4A6A);

        // 场地背景
        g.fill(fieldLeft, fieldTop, fieldLeft + FIELD_W, fieldTop + FIELD_H, 0xFF0A0A14);

        // 网格线
        drawGrid(g);

        // 已锁定方块
        drawField(g);

        // 幽灵方块
        if (state == State.PLAYING) {
            int gr = ghostRow();
            drawShape(g, gr, curCol, curShape, curTexIdx, 0x40);
        }

        // 当前方块
        if (state == State.PLAYING && curType >= 0) {
            drawShape(g, curRow, curCol, curShape, curTexIdx, 0xFF);
        }

        // 侧栏
        drawSidebar(g);

        // 游戏结束覆盖层
        if (state == State.ENDED) {
            g.fill(fieldLeft, fieldTop, fieldLeft + FIELD_W, fieldTop + FIELD_H, 0xCC000000);
            g.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.tetris.game_over").getString(),
                    fieldLeft + FIELD_W / 2, fieldTop + FIELD_H / 2 - 12, 0xFFFF4444);
            g.drawCenteredString(this.font,
                    String.format(Component.translatable("screen.starrailexpress.tetris.final_score").getString(), score),
                    fieldLeft + FIELD_W / 2, fieldTop + FIELD_H / 2 + 6, 0xFFFFFF);
        }
    }

    // ── 网格线 ──
    private void drawGrid(GuiGraphics g) {
        int lineColor = 0xFF222233;
        for (int r = 0; r <= ROWS; r++)
            g.fill(fieldLeft, fieldTop + r * CELL, fieldLeft + FIELD_W, fieldTop + r * CELL + 1, lineColor);
        for (int c = 0; c <= COLS; c++)
            g.fill(fieldLeft + c * CELL, fieldTop, fieldLeft + c * CELL + 1, fieldTop + FIELD_H, lineColor);
    }

    // ── 已锁定方块 ──
    private void drawField(GuiGraphics g) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (field[r][c] != 0)
                    drawCell(g, fieldLeft + c * CELL, fieldTop + r * CELL,
                            fieldTex[r][c], TYPE_TINTS[field[r][c] - 1], 0xFF);
    }

    // ── 方块形状 ──
    private void drawShape(GuiGraphics g, int row, int col, boolean[][] shape, int texIdx, int alpha) {
        int tint = TYPE_TINTS[curType >= 0 ? curType : 0];
        for (int r = 0; r < shape.length; r++)
            for (int c = 0; c < shape[0].length; c++)
                if (shape[r][c]) {
                    int fr = row + r, fc = col + c;
                    if (fr >= 0 && fr < ROWS && fc >= 0 && fc < COLS)
                        drawCell(g, fieldLeft + fc * CELL, fieldTop + fr * CELL, texIdx, tint, alpha);
                }
    }

    // ── 单个 cell 渲染（带 tint 的 blit） ──
    private void drawCell(GuiGraphics g, int x, int y, int texIdx, int tint, int alpha) {
        float tr = ((tint >> 16) & 0xFF) / 255f;
        float tg = ((tint >> 8) & 0xFF) / 255f;
        float tb = (tint & 0xFF) / 255f;
        float ta = alpha / 255f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(tr, tg, tb, ta);
        g.blit(BLOCK_TEX[texIdx], x, y, 0, 0, CELL, CELL, CELL, CELL);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ── 侧栏 ──
    private void drawSidebar(GuiGraphics g) {
        int sx = fieldLeft + FIELD_W + 10;
        int sy = fieldTop;

        // "下一个" 标题
        g.drawString(this.font,
                Component.translatable("screen.starrailexpress.tetris.next").getString(),
                sx, sy, 0xBBBBBB);
        sy += 12;

        // 下一个方块预览（4x4 格子）
        int previewSize = 14;
        g.fill(sx, sy, sx + 4 * previewSize, sy + 4 * previewSize, 0xFF151520);
        if (nextShape != null) {
            int offR = (4 - nextShape.length) / 2;
            int offC = (4 - nextShape[0].length) / 2;
            for (int r = 0; r < nextShape.length; r++)
                for (int c = 0; c < nextShape[0].length; c++)
                    if (nextShape[r][c]) {
                        int px = sx + (offC + c) * previewSize;
                        int py = sy + (offR + r) * previewSize;
                        float tr2 = ((TYPE_TINTS[nextType] >> 16) & 0xFF) / 255f;
                        float tg2 = ((TYPE_TINTS[nextType] >> 8) & 0xFF) / 255f;
                        float tb2 = (TYPE_TINTS[nextType] & 0xFF) / 255f;
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(tr2, tg2, tb2, 1f);
                        g.blit(BLOCK_TEX[nextTexIdx], px, py, 0, 0, previewSize, previewSize, previewSize, previewSize);
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                        RenderSystem.disableBlend();
                    }
        }
        sy += 4 * previewSize + 14;

        // 分数
        g.drawString(this.font,
                String.format(Component.translatable("screen.starrailexpress.tetris.score").getString(), score),
                sx, sy, 0xFFFFFF);
        sy += 12;
        // 等级
        g.drawString(this.font,
                String.format(Component.translatable("screen.starrailexpress.tetris.level").getString(), level),
                sx, sy, 0xAAFFAA);
        sy += 12;
        // 消行数
        g.drawString(this.font,
                String.format(Component.translatable("screen.starrailexpress.tetris.lines").getString(), totalLines),
                sx, sy, 0xFFCCAA);
        sy += 12;
        // 连消
        if (combo > 1) {
            g.drawString(this.font, "Combo x" + combo, sx, sy, 0xFFFF55);
            sy += 12;
        }

        // 键位提示（底部）
        sy = fieldTop + FIELD_H - 60;
        String[] hints = {
                "A/D: " + "\u79FB\u52A8",
                "W: " + "\u52A0\u901F",
                "Space: " + "\u786C\u964D",
                "\u53F3\u952E: " + "\u65CB\u8F6C"
        };
        for (String h : hints) {
            g.drawString(this.font, h, sx, sy, 0x888888);
            sy += 11;
        }
    }
}
