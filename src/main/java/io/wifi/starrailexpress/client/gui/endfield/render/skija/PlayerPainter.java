package io.wifi.starrailexpress.client.gui.endfield.render.skija;

import io.wifi.starrailexpress.client.gui.endfield.menu.model.PlayerCard;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.RRect;

import static io.wifi.starrailexpress.client.gui.endfield.render.skija.ArtCanvas.*;

public final class PlayerPainter {
    private final SkijaResources resources;

    public PlayerPainter(SkijaResources resources) {
        this.resources = resources;
    }

    public void profile(Canvas canvas, PlayerCard player, float width, float height) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.round(7, 7, width - 10, height - 10, 14, 0x304e5d51);
            art.gradient(1, 1, width - 12, height - 12, 13, 0xf52b3434, 0xfa101416);
            art.rim(1, 1, width - 12, height - 12, 13, 0xbbc6b784, 1.5f);
            art.rect(17, 17, 76, 76, 0xff1a2525);
            art.line(12, 34, 12, 12, MINT, 2);
            art.line(12, 12, 34, 12, MINT, 2);
            art.line(98, 80, 98, 98, MINT, 2);
            art.line(98, 98, 80, 98, MINT, 2);
            art.text(player.name(), 113, 36, 24, PAPER, false, width - 146);
            art.text(player.daysPlayedLabel(), 114, 56, 11, 0xffb4b4a4, false, 130);
            art.text(Long.toString(player.daysPlayed()), 113, 91, 40, PAPER, true, 100);
            art.text(player.mode(), width - 118, 80, 15, 0xffd5bf8a, false, 90);
            art.round(114, height - 25, width - 148, 4, 2, 0xff41483e);
            art.round(114, height - 25, (width - 148) * Math.clamp(player.dayProgress(), 0, 1), 4, 2, 0xffd5bf8a);
        }
    }

    public void compactProfile(Canvas canvas, PlayerCard player) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.text(player.name(), 80, 30, 23, PAPER, false, 360);
            art.text(player.daysPlayedLabel() + "  " + player.daysPlayed(), 80, 64, 18, MINT, false, 350);
            art.rim(0, 0, 66, 66, 4, MINT, 1);
            art.round(380, 51, 230, 4, 2, 0xff41483e);
            art.round(380, 51, 230 * Math.clamp(player.dayProgress(), 0, 1), 4, 2, YELLOW);
        }
    }

    public void center(Canvas canvas, PlayerCard player) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.text(player.world(), 28, 36, 18, PAPER, false, 300);
            art.text(player.status(), 28, 58, 13, MINT, false, 270);
            art.text(player.position(), 430, 36, 13, 0xffb8c8ba, true, 240);
            art.text(player.mode(), 430, 58, 13, 0xffb8c8ba, false, 240);
        }
    }

    public void readout(Canvas canvas, PlayerCard player) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.round(0, 0, 340, 48, 24, 0xf12a302c);
            art.rim(0, 0, 340, 48, 24, 0x767a8771, 1);
            art.icon("radar", 14, 10, 27, PAPER);
            art.text(player.daysPlayedLabel(), 52, 32, 18, PAPER, false, 150);
            art.text(Long.toString(player.daysPlayed()), 234, 35, 37, PAPER, true, 85);
            art.rect(1, 14, 4, 20, YELLOW);
        }
    }

    public void world(Canvas canvas, PlayerCard player) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.gradient(4, 6, 370, 124, 12, 0xee2c3434, 0xee141e22);
            art.rim(4, 6, 370, 124, 12, YELLOW, 2);
            canvas.save();
            canvas.clipRRect(RRect.makeXYWH(4, 6, 370, 124, 12), true);
            for (int x = -100; x < 480; x += 12) {
                art.line(x, 130, x + 120, 6, 0x1028acb9, 5);
            }
            canvas.restore();
            art.rect(8, 0, 124, 19, 0xff9a3e39);
            art.text("MINECRAFT / 1.21.1", 16, 15, 13, PAPER, true, 155);
            art.text(player.world(), 25, 58, 22, PAPER, false, 320);
            art.text(player.connection(), 26, 95, 16, 0xffbec9b6, false, 320);
            art.line(10, 140, 90, 140, PAPER, 3);
            art.line(102, 140, 182, 140, YELLOW, 3);
            art.line(194, 140, 274, 140, 0xff697260, 3);
        }
    }

    public void footer(Canvas canvas, PlayerCard player, float width) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.icon("signal", 0, 6, 17, PAPER);
            art.text(player.connection(), 26, 22, 14, 0xffbec6b6, false, width / 2);
            art.text(player.hint(), width - 405, 22, 14, 0xffbec6b6, false, 400);
        }
    }
}
