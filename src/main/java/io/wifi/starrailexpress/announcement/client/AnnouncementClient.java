package io.wifi.starrailexpress.announcement.client;

import io.wifi.starrailexpress.announcement.network.AnnouncementNewS2CPayload;
import io.wifi.starrailexpress.announcement.network.AnnouncementSyncS2CPayload;
import io.wifi.starrailexpress.client.gui.screen.ingame.AnnouncementScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 公告系统客户端：注册 S2C 接收器 + 屏幕顶部横幅 */
@Environment(EnvType.CLIENT)
public final class AnnouncementClient {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AnnouncementSyncS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    AnnouncementClientCache.setFromJson(payload.listJson());
                    if (context.client().screen instanceof AnnouncementScreen screen) {
                        screen.refresh();
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(AnnouncementNewS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> AnnouncementClientCache.showBanner(payload.title())));
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> renderBanner(graphics));
    }

    private static void renderBanner(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        String title = AnnouncementClientCache.bannerTitle();
        if (title == null || System.currentTimeMillis() > AnnouncementClientCache.bannerExpireAt()) return;

        Font font = mc.font;
        String text = "新公告：" + title;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int padX = 8, padY = 4;
        int boxW = font.width(text) + padX * 2;
        int boxH = font.lineHeight + padY * 2;
        int boxX = (screenW - boxW) / 2;
        int boxY = 12;
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xE0101818);
        graphics.renderOutline(boxX, boxY, boxW, boxH, 0xCCFFD24A);
        graphics.drawString(font, text, boxX + padX, boxY + padY + 1, 0xFFFFD24A, false);
    }
}