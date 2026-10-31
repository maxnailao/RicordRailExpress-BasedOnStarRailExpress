package org.agmas.noellesroles.game.roles.neutral.cipher;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 密客无人机驾驶 HUD：绿色边框 + 顶部/左右文字 + finish 点亮 + 驾驶淡白滤镜 + 冲锋红边。
 */
public class CipherHud {

    private static final int GREEN = 0xFF00E676;
    private static final int LIT = 0xFFFFF59D;   // 已点亮：白黄
    private static final int GRAY = 0xFF9E9E9E;  // 未点亮：灰
    private static final int RED = 0xFFFF1744;
    private static final int DRONE_TINT = 0x26FFFFFF; // 驾驶中全屏淡白滤镜（约15%不透明，不能太白）

    public static void register() {
        RoleHudRenderCallback.EVENT.register(
                org.agmas.noellesroles.role.ModRoles.CIPHER_ID,
                (context, tickCounter) -> render(context));
    }

    private static void render(FakeGuiGraphics context) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        CipherPlayerComponent comp = CipherPlayerComponent.KEY.get(player);
        if (!comp.droneActive) {
            renderIdleStatus(context, mc, player);
            renderBlownBanner(context, mc);
            return;
        }

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // 边框（冲锋平滑变红；贴上C4后绿红交替平滑脉冲）
        int border = GREEN;
        float redP = CipherClientState.rushRedProgress();
        if (redP > 0f && comp.rush) {
            border = blend(GREEN, RED, redP);
        }
        boolean c4Attached = comp.c4CarrierId != null;
        if (c4Attached) {
            double gt = mc.level.getGameTime();
            float speed = comp.c4RemainingSeconds <= 5 ? 0.35f : 0.18f;
            float mix = (float) (0.5 + 0.5 * Math.sin(gt * speed));
            border = blend(GREEN, RED, mix);
        }
        drawBorder(context, w, h, border);

        // 顶部居中：无人机驾驶中
        drawCenteredTop(context, mc, "无人机驾驶中", border);

        // 贴上C4：屏幕中央显示剩余时间与预判炸死人数
        if (c4Attached) {
            renderC4Center(context, mc, w, h, comp);
        }

        // 屏幕中央：已装弹冲锋的爆炸引信倒计时
        renderBombCountdown(context, mc, comp);

        // 屏幕右侧（垂直居中偏上，右对齐）：充能 x/3 + 自杀式冲锋状态
        // 从原来的右上角下移，避开右上角通用信息栏遮挡
        String empText = "充能 " + comp.emp + "/3";
        String rushText = comp.rush ? "自杀式冲锋进行中"
                : (comp.emp > 0 ? "自杀式冲锋已就绪" : "无 EMP 充能");
        String bombText = comp.bombLoaded ? "已装载悍匪炸弹" : "未装载炸弹(需悍匪右键装弹)";
        int bombColor = comp.bombLoaded ? LIT : GRAY;
        int midY = h / 2 - 12;
        context.drawString(mc.font, empText, w - 6 - mc.font.width(empText), midY, border, false);
        context.drawString(mc.font, rushText, w - 6 - mc.font.width(rushText),
                midY + mc.font.lineHeight + 2, comp.rush ? RED : border, false);
        context.drawString(mc.font, bombText, w - 6 - mc.font.width(bombText),
                midY + (mc.font.lineHeight + 2) * 2, bombColor, false);

        // 右侧竖排：无人机可用三格（索引 6/7/8）物品预览，选中位加绿框
        renderDroneSlotsPreview(context, mc, w, h, comp);

        // 左侧：finish 拼写 + 防呆提示
        renderFinish(context, mc, h);

        // 左下角：无人机操作说明
        renderControlsLegend(context, mc, w, h);

        // 快捷栏：高亮当前选中的"最后三格"之一
        renderDroneHotbarHighlight(context, mc, w, h);
    }

    /** 屏幕中央：C4 倒计时 + 预判炸死人数 */
    private static void renderC4Center(FakeGuiGraphics context, Minecraft mc, int w, int h,
            CipherPlayerComponent comp) {
        String timeLine = String.format("C4 剩余 %d 秒", comp.c4RemainingSeconds);
        String killLine = "预计炸死 " + comp.c4PredictedKills + " 人";
        int y = h / 2 - 34;
        context.drawCenteredString(mc.font, timeLine, w / 2, y, RED);
        context.drawCenteredString(mc.font, killLine, w / 2, y + mc.font.lineHeight + 2, LIT);
    }

    /** 高亮最后三格(索引6/7/8)中当前选中的槽位（原版已渲染物品，这里只描边） */
    private static void renderDroneHotbarHighlight(FakeGuiGraphics context, Minecraft mc, int w, int h) {
        var inv = mc.player.getInventory();
        int slot = inv.selected;
        if (slot < 6 || slot > 8) return;
        int size = 20;
        int t = 2;
        int topY = h - size - 2;
        int left = w / 2 + (slot - 4) * size - size / 2;
        context.fill(left - t, topY - t, left + size + t, topY, GREEN);
        context.fill(left - t, topY + size, left + size + t, topY + size + t, GREEN);
        context.fill(left - t, topY, left, topY + size, GREEN);
        context.fill(left + size, topY, left + size + t, topY + size, GREEN);
    }

    /** 右侧竖排：显示无人机可用三格（6/7/8）的物品图标 + 数量 + 名称，选中位高亮 */
    private static void renderDroneSlotsPreview(FakeGuiGraphics context, Minecraft mc, int w, int h,
            CipherPlayerComponent comp) {
        var inv = mc.player.getInventory();
        int slot = inv.selected;
        int size = 26;
        int gap = 4;
        int totalH = 3 * size + 2 * gap;
        int startX = w - 8 - size;
        int startY = h / 2 - totalH / 2 + 40; // 位于冲锋状态文字下方
        for (int i = 6; i <= 8; i++) {
            int y = startY + (i - 6) * (size + gap);
            // 半透明背景
            context.fill(startX - 2, y - 2, startX + size + 2, y + size + 2, 0xB0101810);
            // 选中位外框（绿色），非选中位灰框
            int frame = (i == slot) ? GREEN : 0xFF4A4A4A;
            context.fill(startX - 2, y - 2, startX + size + 2, y - 1, frame);
            context.fill(startX - 2, y + size + 1, startX + size + 2, y + size + 2, frame);
            context.fill(startX - 2, y - 1, startX - 1, y + size + 1, frame);
            context.fill(startX + size + 1, y - 1, startX + size + 2, y + size + 1, frame);
            var stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                context.renderItem(stack, startX + 3, y + 3, 0);
                // 数量
                if (stack.getCount() > 1) {
                    String count = String.valueOf(stack.getCount());
                    int cx = startX + size - mc.font.width(count) - 1;
                    context.drawString(mc.font, count, cx, y + size - 8, LIT, true);
                }
            }
        }
    }

    /** 左下角：无人机操作说明 */
    private static void renderControlsLegend(FakeGuiGraphics context, Minecraft mc, int w, int h) {
        String[] lines = {
                "w-前进",
                "a-左走",
                "s-后退",
                "d-右走",
                "shift-下降",
                "空格-上升"
        };
        int lineH = mc.font.lineHeight;
        int x = 6;
        int y = h - 6 - lines.length * lineH;
        for (String line : lines) {
            context.drawString(mc.font, line, x, y, GRAY, false);
            y += lineH;
        }
    }

    /** 右下角：技能就绪 / 冷却倒计时（参考影隼 / 葬仪布局） */
    private static void renderIdleStatus(FakeGuiGraphics context, Minecraft mc, Player player) {
        var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(player);
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        var font = mc.font;
        int xOffset = w - 10;
        int yOffset = h - 10 - font.lineHeight;

        // 右下角：EMP 充能 x/3（有充能高亮青色，无则灰色）
        var comp = CipherPlayerComponent.KEY.get(player);
        String empText = "EMP充能 " + comp.emp + " / " + CipherPlayerComponent.MAX_EMP;
        int empColor = comp.emp > 0 ? 0x55FFCC : 0x999999;
        context.drawString(font, empText, xOffset - font.width(empText),
                yOffset - (font.lineHeight + 2), empColor, false);

        Component text;
        if (ability.cooldown > 0) {
            int secondsLeft = (ability.cooldown + 19) / 20;
            text = Component.translatable("hud.noellesroles.cipher.cooldown", secondsLeft)
                    .withStyle(ChatFormatting.RED);
        } else {
            var abilityKey = org.agmas.noellesroles.client.NoellesrolesClient.abilityBind
                    .getTranslatedKeyMessage();
            text = Component.translatable("hud.noellesroles.cipher.ready", abilityKey)
                    .withStyle(ChatFormatting.GREEN);
        }
        context.drawString(font, text, xOffset - font.width(text), yOffset, 0xFFFFFF, false);
    }

    /** 物品栏上方："炸飞了 X 人！"（引爆后 3 秒内显示） */
    private static void renderBlownBanner(FakeGuiGraphics context, Minecraft mc) {
        if (!CipherClientState.blownActive()) return;
        int n = CipherClientState.blownCount();
        if (n <= 0) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        String text = "炸飞了 " + n + " 人！";
        int tw = mc.font.width(text);
        context.drawString(mc.font, text, w / 2 - tw / 2, h - 58, LIT, false);
    }

    /** 屏幕中央：自杀冲锋装弹后的爆炸倒计时；未装载炸弹则不显示；最后 3 秒红黄闪烁 */
    private static void renderBombCountdown(FakeGuiGraphics context, Minecraft mc, CipherPlayerComponent comp) {
        if (!comp.rush || !comp.bombLoaded || comp.bombRemainingTicks <= 0) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float secs = comp.bombRemainingTicks / 20f;
        String num = secs < 5
                ? String.format(java.util.Locale.ROOT, "%.1f", secs)
                : String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(secs));
        String text = "炸弹 " + num + " 秒后爆炸";
        int color = RED;
        if (secs < 3 && (System.currentTimeMillis() / 200L) % 2 == 0) color = LIT;
        int tw = mc.font.width(text);
        context.drawString(mc.font, text, w / 2 - tw / 2, h / 2 + 26, color, false);
    }

    private static void renderFinish(FakeGuiGraphics context, Minecraft mc, int h) {
        int x = 12;
        int y = h / 2 - 8;
        String[] letters = CipherClientState.letters();
        int idx = CipherClientState.finishIndex();
        // 先防呆绿字说明
        context.drawString(mc.font, "防呆关闭方式", x, y - 14, GREEN, false);
        // 逐字母：点亮用白黄，未点亮用灰
        int cx = x;
        for (int i = 0; i < letters.length; i++) {
            int color = i < idx ? LIT : GRAY;
            context.drawString(mc.font, letters[i], cx, y, color, false);
            cx += mc.font.width(letters[i]) + 4;
        }
    }

    private static void drawCenteredTop(FakeGuiGraphics context, Minecraft mc, String text, int color) {
        int w = mc.getWindow().getGuiScaledWidth();
        int tw = mc.font.width(text);
        context.drawString(mc.font, text, w / 2 - tw / 2, 12, color, false);
    }

    private static void drawBorder(FakeGuiGraphics context, int w, int h, int color) {
        int t = 3; // 边厚
        context.fill(0, 0, w, t, color);
        context.fill(0, h - t, w, h, color);
        context.fill(0, 0, t, h, color);
        context.fill(w - t, 0, w, h, color);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}