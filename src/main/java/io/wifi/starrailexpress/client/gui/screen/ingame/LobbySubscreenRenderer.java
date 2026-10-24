package io.wifi.starrailexpress.client.gui.screen.ingame;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Lightweight shared chrome for the lobby sub-screens. */
public final class LobbySubscreenRenderer {
    private LobbySubscreenRenderer() {
    }

    public static void background(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, LobbyUiTheme.BACKGROUND);
        // A quiet technical grid gives every page the same depth without a texture or per-frame allocations.
        int grid = 0x142B3A36;
        for (int x = 0; x < width; x += 32) g.fill(x, 0, x + 1, height, grid);
        for (int y = 0; y < height; y += 32) g.fill(0, y, width, y + 1, grid);
        g.fill(0, 0, width, 2, LobbyUiTheme.ACCENT);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x55000000);
        g.fill(x, y, x + w, y + h, LobbyUiTheme.PANEL);
        g.renderOutline(x, y, w, h, LobbyUiTheme.BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x668F7A3E);
    }

    public static void header(GuiGraphics g, Font font, int x, int y, int w, String title, String status) {
        g.fill(x, y, x + w, y + 30, 0xE9141D1D);
        g.fill(x, y + 29, x + w, y + 30, LobbyUiTheme.ACCENT);
        g.drawString(font, title, x + 12, y + 10, LobbyUiTheme.TEXT);
        if (status != null && !status.isEmpty()) {
            g.drawString(font, status, x + w - font.width(status) - 12, y + 10, LobbyUiTheme.TEXT_DIM);
        }
    }

    public static void section(GuiGraphics g, Font font, int x, int y, int w, String title) {
        g.fill(x, y, x + w, y + 1, 0xAA8B6914);
        g.drawString(font, title, x + 4, y + 6, LobbyUiTheme.TEXT_DIM);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered) {
        int color = active ? 0x884B6E64 : (hovered ? 0x704B6E64 : LobbyUiTheme.CARD);
        g.fill(x, y, x + w, y + h, color);
        g.renderOutline(x, y, w, h, active ? LobbyUiTheme.ACCENT_BRIGHT : 0x668B9A8A);
        if (active) g.fill(x, y, x + w, y + 2, LobbyUiTheme.ACCENT);
    }

    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0x668B6914);
    }

    public static void footer(GuiGraphics g, Font font, int x, int y, int w, String hint) {
        divider(g, x, y, w);
        if (hint != null && !hint.isEmpty()) g.drawString(font, Component.literal(hint), x + 8, y + 9, LobbyUiTheme.TEXT_FAINT);
    }
}
