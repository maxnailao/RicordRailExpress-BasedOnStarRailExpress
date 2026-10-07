package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.DianxueMasterClickC2SPacket;
import org.agmas.noellesroles.packet.DianxueMasterSyncS2CPacket;

public class DianxueMasterScreen extends Screen {

    public static DianxueMasterScreen CURRENT;

    private static final int POINT_COUNT = 5;
    private static final int BG = 0xCC0A0A14;
    private static final int BODY = 0xFF3A3A5A;
    private static final int DIM = 0xFF555555;
    private static final int RED = 0xFFFF3030;
    private static final int HIT_COLOR = 0xFF55FF55;
    private static final int R = 15;

    private int phase;
    private int activeIndex;
    private String targetName;
    private long endAtMillis;
    private long maxMillis;

    public DianxueMasterScreen(DianxueMasterSyncS2CPacket p) {
        super(Component.translatable("screen.noellesroles.dianxue_master.title",
                p.targetName() == null || p.targetName().isEmpty() ? "?" : p.targetName()));
        this.phase = p.phase();
        this.activeIndex = p.activeIndex();
        this.targetName = p.targetName();
        applyTiming(p);
    }

    private void applyTiming(DianxueMasterSyncS2CPacket p) {
        this.phase = p.phase();
        this.activeIndex = p.activeIndex();
        if (p.targetName() != null && !p.targetName().isEmpty()) this.targetName = p.targetName();
        this.maxMillis = p.maxTicks() * 50L;
        this.endAtMillis = System.currentTimeMillis() + p.remainingTicks() * 50L;
    }

    @Override protected void init() { super.init(); CURRENT = this; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { if (CURRENT == this) CURRENT = null; super.removed(); }

    public static void handleSync(DianxueMasterSyncS2CPacket p) {
        Minecraft client = Minecraft.getInstance();
        switch (p.action()) {
            case 0 -> client.setScreen(new DianxueMasterScreen(p));
            case 1 -> { if (CURRENT != null) CURRENT.applyTiming(p); }
            case 2 -> { if (CURRENT != null) client.setScreen(null); }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BG);
        super.render(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int top = this.height / 2 - 100;
        drawBody(g, cx, top);

        long now = System.currentTimeMillis();
        boolean blink = (now / 180L) % 2 == 0;
        for (int i = 0; i < POINT_COUNT; i++) {
            int[] xy = pointPos(cx, top, i);
            boolean active = i == activeIndex;
            int col = active ? (blink ? RED : 0xFFAA0000) : DIM;
            g.fill(xy[0] - R, xy[1] - R, xy[0] + R, xy[1] + R, col);
            if (active) g.renderOutline(xy[0] - R - 2, xy[1] - R - 2, (R + 2) * 2, (R + 2) * 2, col);
        }

        renderTimerBar(g);
        String hintKey = phase == 0 ? "screen.noellesroles.dianxue_master.gate_hint"
                : "screen.noellesroles.dianxue_master.hint";
        Component hint = phase == 0
                ? Component.translatable(hintKey)
                : Component.translatable(hintKey, activeIndex + 1, POINT_COUNT);
        g.drawCenteredString(this.font, hint.getString(), cx, top + 210, 0xFFFFDD00);
    }

    private void renderTimerBar(GuiGraphics g) {
        int cx = this.width / 2;
        int bw = 200;
        int bx = cx - bw / 2;
        int by = this.height / 2 + 130;
        float frac = maxMillis > 0 ? Math.max(0f, Math.min(1f, (endAtMillis - System.currentTimeMillis()) / (float) maxMillis)) : 0f;
        g.fill(bx, by, bx + bw, by + 8, 0xFF333333);
        g.fill(bx, by, bx + (int) (bw * frac), by + 8, frac < 0.25f ? RED : HIT_COLOR);
    }

    private void drawBody(GuiGraphics g, int cx, int top) {
        g.fill(cx - 22, top, cx + 22, top + 40, BODY);
        g.fill(cx - 22, top + 45, cx + 22, top + 115, BODY);
        g.fill(cx - 22, top + 115, cx - 4, top + 185, BODY);
        g.fill(cx + 4, top + 115, cx + 22, top + 185, BODY);
        g.fill(cx - 40, top + 50, cx - 24, top + 55, BODY);
        g.fill(cx + 24, top + 50, cx + 40, top + 55, BODY);
    }

    private int[] pointPos(int cx, int top, int i) {
        return switch (i) {
            case 0 -> new int[]{cx, top + 20};
            case 1 -> new int[]{cx, top + 70};
            case 2 -> new int[]{cx, top + 105};
            case 3 -> new int[]{cx - 13, top + 155};
            default -> new int[]{cx + 13, top + 155};
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && activeIndex >= 0 && activeIndex < POINT_COUNT) {
            int cx = this.width / 2;
            int top = this.height / 2 - 100;
            int[] xy = pointPos(cx, top, activeIndex);
            if ((mx - xy[0]) * (mx - xy[0]) + (my - xy[1]) * (my - xy[1]) <= R * R) {
                ClientPlayNetworking.send(new DianxueMasterClickC2SPacket(activeIndex));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}