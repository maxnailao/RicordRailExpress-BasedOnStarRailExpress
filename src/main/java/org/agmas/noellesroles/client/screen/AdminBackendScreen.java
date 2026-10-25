package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.content.title.Title;
import io.wifi.starrailexpress.content.title.TitleClientCache;
import io.wifi.starrailexpress.client.data.AdminInspectCache;
import io.wifi.starrailexpress.network.AdminInspectRequestC2SPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import io.wifi.starrailexpress.announcement.AnnouncementEntry;
import io.wifi.starrailexpress.announcement.client.AnnouncementClientCache;
import io.wifi.starrailexpress.announcement.network.AnnouncementRequestC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 管理员后台：发放货币 / 称号（全色渐变调色板 + 实时预览）/ 阵营选择卡（含自选卡）。
 *
 * <p>不新增网络包：所有操作组装为已有服务端命令，权限由服务端校验（均要求权限 2）：
 * <ul>
 * <li>货币：{@code tmm:money add|set <数量> <目标>}（MoneyCommand）</li>
 * <li>称号：{@code sre:title grant <玩家> #RRGGBB <pre|suf> <文本>}（TitleCommand）</li>
 * <li>卡牌：{@code sre:backpack grant <类型> <数量> <目标>}（BackpackGrantCommand）</li>
 * </ul>
 */
public class AdminBackendScreen extends Screen {

    private final Screen parent;

    private EditBox targetBox;
    private EditBox moneyBox;
    private EditBox titleTextBox;
    private EditBox cardCountBox;
    private EditBox announceTitleBox;
    private EditBox announceContentBox;
    private EditBox announceDeleteBox;
    private EditBox skinTypeBox;
    private EditBox skinIdBox;

    /** 称号显示位置（true=名字后）与当前取色（HSV，覆盖全部颜色） */
    private boolean titleSuffix = false;
    private float hue = 0.118f;
    private float sat = 0.769f;
    private float bri = 0.867f;

    /** 角色卡发放/扣除模式切换（true=扣除） */
    private boolean cardTakeMode = false;

    // 面板布局
    private int panelX, panelY, panelW, panelH;
    // 称号预览 / 渐变色板 / 十六进制文本 的绘制位置（init 计算）
    private int previewX, previewY;
    private int colorX, colorY, colorW, colorH;
    private int hexLabelX, hexLabelY;
    // 快捷目标下拉
    private boolean showTargets = false;
    private int dropX, dropY;
    private int listScroll = 0;
    private static final int DROP_W = 160;
    private static final int DROP_ROW_H = 12;
    private static final int DROP_MAX_ROWS = 8;

    private static final class UiButton {
        final int x, y, w, h;
        final Supplier<Component> label;
        final Supplier<Component> sub;
        final Runnable action;
        final BooleanSupplier enabled;
        final int color;

        UiButton(int x, int y, int w, int h, Supplier<Component> label,
                Runnable action, BooleanSupplier enabled, int color) {
            this(x, y, w, h, label, null, action, enabled, color);
        }

        UiButton(int x, int y, int w, int h, Supplier<Component> label, Supplier<Component> sub,
                Runnable action, BooleanSupplier enabled, int color) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label; this.sub = sub; this.action = action;
            this.enabled = enabled; this.color = color;
        }
    }

    private record StaticLabel(int x, int y, Component text, int color) {}

    private final List<UiButton> buttons = new ArrayList<>();
    private final List<StaticLabel> labels = new ArrayList<>();

    private static final int BLUE = 0xFF5577CC;
    private static final int GOLD = 0xFFDDAA33;
    private static final int GREEN = 0xFF44BB66;
    private static final int RED = 0xFFBB5533;

    // 屏幕中央弹窗提示（已发送 / 已删除），到期自动消失
    private String toastText = "";
    private long toastUntil = 0L;

    // 删除公告的"选择公告"快捷下拉
    private boolean showAnnList = false;
    private int annDropX, annDropY;
    private int annListScroll = 0;
    private static final int ANN_DROP_W = 300;
    private static final int ANN_DROP_ROW_H = 12;
    private static final int ANN_DROP_MAX_ROWS = 8;

    // 给予皮肤的"选皮肤"下拉
    private boolean showSkinList = false;
    private int skinDropX, skinDropY;
    private int skinListScroll = 0;
    private static final int SKIN_DROP_W = 260;
    private static final int SKIN_DROP_ROW_H = 12;
    private static final int SKIN_DROP_MAX_ROWS = 10;

    // 目标玩家数据快照（AdminInspectCache）与动作后待回填弹窗
    private String inspectDraft = "";
    private int inspectDebounce = 0;
    private String lastInspectRequested = "";
    private long appliedSnapshotVersion = -1L;
    private String pendingVerb = null;
    private boolean pendingWantBalance = false;

    // 删除称号的"选择称号"下拉
    private boolean showTitleList = false;
    private int titleDropX, titleDropY;
    private int titleListScroll = 0;
    private static final int TITLE_DROP_W = 200;
    private static final int TITLE_DROP_ROW_H = 12;
    private static final int TITLE_DROP_MAX_ROWS = 8;

    public AdminBackendScreen(Screen parent) {
        super(Component.literal("管理员后台"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // 进入时二次校验：无管理权限则提示并退回上级界面，不构建后台 UI
        if (this.minecraft == null || this.minecraft.player == null
                || !this.minecraft.player.hasPermissions(2)) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.literal("需要管理权限才能进入管理员后台")
                                .withStyle(ChatFormatting.RED), false);
            }
            this.onClose();
            return;
        }

        buttons.clear();
        labels.clear();

        // resize 后重建时保留已输入内容
        String keepTarget = targetBox == null ? "" : targetBox.getValue();
        String keepMoney = moneyBox == null ? "100" : moneyBox.getValue();
        String keepTitle = titleTextBox == null ? "" : titleTextBox.getValue();
        String keepCount = cardCountBox == null ? "1" : cardCountBox.getValue();
        String keepAnnTitle = announceTitleBox == null ? "" : announceTitleBox.getValue();
        String keepAnnContent = announceContentBox == null ? "" : announceContentBox.getValue();
        String keepAnnDel = announceDeleteBox == null ? "" : announceDeleteBox.getValue();
        String keepSkinType = skinTypeBox == null ? "" : skinTypeBox.getValue();
        String keepSkinId = skinIdBox == null ? "" : skinIdBox.getValue();

        panelW = Math.min(430, this.width - 30);
        panelH = Math.min(448, this.height - 24);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        int cx = panelX + 10;
        int cw = panelW - 20;
        int y = panelY + 24;

        // ── 目标玩家：输入框 + 快捷选择下拉 ──
        labels.add(new StaticLabel(cx, y, Component.literal("目标玩家（名字，或 @a/@p）"), 0xFFCCDDFF));
        targetBox = makeBox(cx, y + 11, cw - 68, keepTarget, "留空则命令不生效", 40, null);
        buttons.add(new UiButton(cx + cw - 64, y + 11, 64, 16,
                () -> Component.literal(showTargets ? "收起 ▴" : "快捷选择 ▾"),
                () -> { showTargets = !showTargets; showAnnList = false; showSkinList = false; showTitleList = false; listScroll = 0; },
                () -> true, BLUE));
        dropX = cx;
        dropY = y + 28;
        y += 32;

        // ── 发放/扣除货币 ──
        labels.add(new StaticLabel(cx, y, Component.literal("发放货币").withStyle(ChatFormatting.BOLD), GOLD));
        moneyBox = makeBox(cx, y + 12, 90, keepMoney, "数量", 8, s -> s.matches("\\d{0,8}"));
        buttons.add(new UiButton(cx + 96, y + 12, 80, 16,
                () -> Component.literal("发放货币"),
                () -> performAction("tmm:money add " + parseInt(moneyBox.getValue(), 100, 0, 10_000_000)
                        + " " + targetArg(), "已发放", true),
                this::moneyValid, GREEN));
        buttons.add(new UiButton(cx + 182, y + 12, 80, 16,
                () -> Component.literal("设定货币"),
                () -> performAction("tmm:money set " + parseInt(moneyBox.getValue(), 100, 0, 10_000_000)
                        + " " + targetArg(), "已设定", true),
                this::moneyValid, RED));
        buttons.add(new UiButton(cx + 268, y + 12, 80, 16,
                () -> Component.literal("扣除货币"),
                () -> performAction("tmm:money add -" + parseInt(moneyBox.getValue(), 100, 0, 10_000_000)
                        + " " + targetArg(), "已扣除", true),
                this::moneyValid, GOLD));
        y += 34;

        // ── 给予皮肤（指定玩家：类型 + 皮肤ID，可"选皮肤"快捷回填） ──
        labels.add(new StaticLabel(cx, y, Component.literal("给予皮肤").withStyle(ChatFormatting.BOLD), GOLD));
        skinTypeBox = makeBox(cx, y + 12, 80, keepSkinType, "类型 如knife", 24, null);
        skinIdBox = makeBox(cx + 86, y + 12, 140, keepSkinId, "皮肤ID 如xingguang", 48, null);
        buttons.add(new UiButton(cx + 232, y + 12, 80, 16,
                () -> Component.literal("给予皮肤"),
                () -> send("tmm:skins give " + targetArg() + " "
                        + skinTypeBox.getValue().trim() + " " + skinIdBox.getValue().trim()),
                this::skinValid, BLUE));
        buttons.add(new UiButton(cx + 318, y + 12, 72, 16,
                () -> Component.literal(showSkinList ? "收起 ▴" : "选皮肤 ▾"),
                () -> { showSkinList = !showSkinList; showTargets = false; showAnnList = false; showTitleList = false; skinListScroll = 0; },
                () -> true, BLUE));
        skinDropX = cx + 86;
        skinDropY = y + 30;
        y += 34;

        // ── 发放称号 ──
        labels.add(new StaticLabel(cx, y, Component.literal("发放称号").withStyle(ChatFormatting.BOLD), GOLD));
        titleTextBox = makeBox(cx, y + 11, 200, keepTitle, "称号文本（≤32字符）", 32, null);
        buttons.add(new UiButton(cx + 206, y + 11, 96, 16,
                () -> Component.literal(titleSuffix ? "位置: 名字后" : "位置: 名字前"),
                () -> titleSuffix = !titleSuffix,
                () -> true, BLUE));
        buttons.add(new UiButton(cx + 308, y + 11, 96, 16,
                () -> Component.literal(showTitleList ? "收起 ▴" : "选择称号 ▾"),
                () -> { showTitleList = !showTitleList; showTargets = false; showAnnList = false; showSkinList = false; titleListScroll = 0; },
                () -> targetArg() != null, RED));
        titleDropX = cx;
        titleDropY = y + 28;
        y += 29;

        // ── 全色渐变色板：横向色相，上半 白→纯色，下半 纯色→黑 ──
        colorX = cx;
        colorY = y;
        colorW = cw;
        colorH = 52;
        y += colorH + 6;

        // 称号预览（色板右侧下方区域）
        previewX = cx + 200;
        previewY = y + 4;

        buttons.add(new UiButton(cx, y, 90, 16,
                () -> Component.literal("发放称号"),
                () -> performAction("sre:title grant " + targetArg() + " " + titleColorHex() + " "
                        + (titleSuffix ? "suf" : "pre") + " " + titleTextBox.getValue().trim(),
                        "已发送", false),
                this::titleValid, GOLD));
        hexLabelX = cx + 98;
        hexLabelY = y + 4;
        y += 24;

        // ── 发放/扣除角色卡：自动检测所有阵营选择卡 + 自选卡（右上角切换模式） ──
        labels.add(new StaticLabel(cx, y, Component.literal("发放角色卡").withStyle(ChatFormatting.BOLD), GOLD));
        buttons.add(new UiButton(cx + cw - 70, y - 2, 70, 15,
                () -> Component.literal(cardTakeMode ? "模式: 扣除" : "模式: 发放"),
                () -> cardTakeMode = !cardTakeMode,
                () -> true, GOLD));
        cardCountBox = makeBox(cx, y + 12, 40, keepCount, "张", 2, s -> s.matches("\\d{0,2}"));
        List<String[]> cards = new ArrayList<>();
        for (FactionCardType t : FactionCardType.values()) {
            if (t != FactionCardType.NONE) {
                cards.add(new String[]{t.questKey,
                        Component.translatable(t.displayName).getString() + "卡"});
            }
        }
        cards.add(new String[]{"selfselect", "自选卡"});
        int gap = 4;
        int btnW = (cw - 48 - gap * (cards.size() - 1)) / cards.size();
        for (int i = 0; i < cards.size(); i++) {
            String key = cards.get(i)[0];
            String label = cards.get(i)[1];
            int bx = cx + 48 + i * (btnW + gap);
            buttons.add(new UiButton(bx, y + 12, btnW, 22,
                    () -> Component.literal(label),
                    () -> Component.literal(ownedCardCountText(key)),
                    () -> performAction("sre:backpack " + (cardTakeMode ? "take " : "grant ") + key + " "
                            + parseInt(cardCountBox.getValue(), 1, 1, 99) + " " + targetArg(),
                            cardTakeMode ? "已扣除" : "已发放", false),
                    () -> targetArg() != null, BLUE));
        }
        y += 40;

        // ── 发布公告（无需选择玩家，仅需标题与内容） ──
        labels.add(new StaticLabel(cx, y, Component.literal("发布公告").withStyle(ChatFormatting.BOLD), GOLD));
        announceTitleBox = makeBox(cx, y + 12, 200, keepAnnTitle, "公告标题（≤40字符）", 40, null);
        buttons.add(new UiButton(cx + 206, y + 12, 96, 16,
                () -> Component.literal("发布公告"),
                this::publishAnnouncement,
                this::announceValid, GREEN));
        y += 30;
        announceContentBox = makeBox(cx, y, cw, keepAnnContent, "公告内容（Enter 换行；发布后所有在线玩家屏幕顶部提示标题）", 256, null);
        y += 22;

        // ── 删除公告（按序号，或"选择公告"快捷下拉） ──
        labels.add(new StaticLabel(cx, y, Component.literal("删除公告").withStyle(ChatFormatting.BOLD), GOLD));
        announceDeleteBox = makeBox(cx + 56, y - 2, 40, keepAnnDel, "序号", 3, s -> s.matches("\\d{0,3}"));
        buttons.add(new UiButton(cx + 102, y - 3, 96, 16,
                () -> Component.literal("删除公告"),
                this::deleteAnnouncement,
                this::deleteValid, RED));
        buttons.add(new UiButton(cx + 204, y - 3, 80, 16,
                () -> Component.literal(showAnnList ? "收起 ▴" : "选择公告 ▾"),
                () -> { showAnnList = !showAnnList; showTargets = false; showSkinList = false; showTitleList = false; annListScroll = 0; },
                () -> true, BLUE));
        annDropX = cx;
        annDropY = y + 16;
        y += 24;

        // 拉取当前公告列表，供"选择公告"下拉展示（服务端会把完整列表同步回缓存）
        ClientPlayNetworking.send(new AnnouncementRequestC2SPayload());

        // 目标玩家数据快照：记录已应用版本，若已填目标则先拉取一次（用于货币/卡牌/称号显示与回填）
        appliedSnapshotVersion = AdminInspectCache.version();
        String initTarget = targetBox == null ? "" : targetBox.getValue().trim();
        if (!initTarget.isEmpty() && !initTarget.startsWith("@") && !initTarget.startsWith("#")) {
            lastInspectRequested = initTarget;
            inspectDraft = initTarget;
            ClientPlayNetworking.send(new AdminInspectRequestC2SPayload(initTarget));
        }

        labels.add(new StaticLabel(cx, y,
                Component.literal("命令由服务端做权限与参数校验；目标支持选择器（@a 全员）")
                        .withStyle(ChatFormatting.DARK_GRAY), 0xFF778899));
    }

    /**
     * 空实现：从源头屏蔽原版背景链。Screen#render（super.render）内部会调用
     * renderBackground，其模糊采样会把本界面先绘制的文字糊掉；本界面背景
     * 由 render() 中的不透明 fillGradient 自行绘制。
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        reconcileSnapshot();
        // 不透明底色：完全遮蔽主菜单的上一帧画面
        g.fillGradient(0, 0, this.width, this.height, 0xFF0C1020, 0xFF101828);
        // 面板背景（与 GameManagementScreen 同风格）
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, 0xD80C1020, 0xD8101828);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFF1E3060);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, 0x22FFFFFF);
        // 顶部标题条
        g.fill(panelX + 1, panelY + 1, panelX + 4, panelY + 22, GOLD | 0xFF000000);
        g.drawCenteredString(this.font, this.title, panelX + panelW / 2, panelY + 6, 0xEEEEFF);

        for (StaticLabel l : labels) {
            g.drawString(this.font, l.text(), l.x(), l.y(), l.color(), false);
        }

        // 控件（EditBox）
        super.render(g, mouseX, mouseY, partialTick);

        // 全色渐变色板
        drawColorPanel(g, mouseX, mouseY);
        // 当前色号（动态刷新）
        g.drawString(this.font, "#" + titleColorHex(), hexLabelX, hexLabelY, 0xFF8899BB, false);
        // 称号实时预览
        g.drawString(this.font, titlePreview(), previewX, previewY, 0xFFFFFF, false);

        for (UiButton b : buttons) {
            drawUiButton(g, b, mouseX, mouseY);
        }
        // 快捷目标下拉（最上层）
        if (showTargets) {
            drawTargetDropdown(g, mouseX, mouseY);
        }
        // 删除公告的"选择公告"下拉（最上层）
        if (showAnnList) {
            drawAnnDropdown(g, mouseX, mouseY);
        }
        // 给予皮肤的"选皮肤"下拉（最上层）
        if (showSkinList) {
            drawSkinDropdown(g, mouseX, mouseY);
        }
        // 删除称号的"选择称号"下拉（最上层）
        if (showTitleList) {
            drawTitleDropdown(g, mouseX, mouseY);
        }
        // 屏幕中央弹窗提示（最顶层）
        renderToast(g);
    }

    // ══════════════════════ 渐变色板 ══════════════════════

    private void drawColorPanel(GuiGraphics g, int mx, int my) {
        int cols = colorW / 4 + 1;
        int half = colorH / 2;
        for (int i = 0; i < cols; i++) {
            int x0 = colorX + i * 4;
            int wpx = Math.min(4, colorX + colorW - x0);
            int base = 0xFF000000
                    | (java.awt.Color.HSBtoRGB((float) i / (cols - 1), 1f, 1f) & 0xFFFFFF);
            g.fillGradient(x0, colorY, x0 + wpx, colorY + half, 0xFFFFFFFF, base);
            g.fillGradient(x0, colorY + half, x0 + wpx, colorY + colorH, base, 0xFF000000);
        }
        g.renderOutline(colorX, colorY, colorW, colorH, 0xFF334466);
        // 当前选中色标记（白框 + 黑描边）
        float t = bri >= sat ? (1f - sat) / 2f : 0.5f + (1f - bri) / 2f;
        int px = colorX + (int) (hue * (colorW - 1));
        int py = colorY + (int) (t * (colorH - 1));
        g.renderOutline(px - 3, py - 3, 7, 7, 0xFF000000);
        g.renderOutline(px - 2, py - 2, 5, 5, 0xFFFFFFFF);
        // 悬停提示
        if (isIn(mx, my, colorX, colorY, colorW, colorH)) {
            g.renderTooltip(this.font, Component.literal("点击取色：横向选颜色，纵向选明暗")
                    .withStyle(ChatFormatting.GRAY), mx, my);
        }
    }

    private boolean pickColorAt(int mx, int my) {
        if (mx < colorX || mx >= colorX + colorW || my < colorY || my >= colorY + colorH) {
            return false;
        }
        hue = Mth.clamp((mx - colorX) / (float) (colorW - 1), 0f, 1f);
        float t = Mth.clamp((my - colorY) / (float) (colorH - 1), 0f, 1f);
        if (t < 0.5f) {
            sat = 1f - 2f * t;
            bri = 1f;
        } else {
            sat = 1f;
            bri = 2f - 2f * t;
        }
        return true;
    }

    /** 当前 HSV → 0xAARRGGBB */
    private int hsvColor() {
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF);
    }

    // ══════════════════════ 快捷目标下拉 ══════════════════════

    private List<String> targetOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("@a");
        opts.add("@p");
        opts.add("@s");
        var conn = this.minecraft.getConnection();
        if (conn != null) {
            for (PlayerInfo info : conn.getOnlinePlayers()) {
                String n = info.getProfile().getName();
                if (!opts.contains(n)) opts.add(n);
            }
        }
        return opts;
    }

    private static String displayOf(String opt) {
        return switch (opt) {
            case "@a" -> "@a 所有玩家";
            case "@p" -> "@p 最近玩家";
            case "@s" -> "@s 自己";
            default -> opt;
        };
    }

    private void drawTargetDropdown(GuiGraphics g, int mx, int my) {
        List<String> opts = targetOptions();
        int rows = Math.min(opts.size() - listScroll, DROP_MAX_ROWS);
        int h = Math.max(rows, 1) * DROP_ROW_H + 6;
        g.fill(dropX, dropY, dropX + DROP_W, dropY + h, 0xFF0C1020);
        g.renderOutline(dropX, dropY, DROP_W, h, 0xFF334466);
        for (int i = 0; i < rows; i++) {
            int idx = listScroll + i;
            int ry = dropY + 3 + i * DROP_ROW_H;
            boolean hov = mx >= dropX && mx < dropX + DROP_W && my >= ry && my < ry + DROP_ROW_H;
            if (hov) g.fill(dropX + 1, ry, dropX + DROP_W - 1, ry + DROP_ROW_H, 0x33FFFFFF);
            g.drawString(this.font, displayOf(opts.get(idx)), dropX + 4, ry + 2,
                    hov ? 0xFFFFFFFF : 0xFFCCDDFF, false);
        }
    }

    /** 命中下拉列表项则返回对应目标，否则 null */
    private String dropdownPick(int mx, int my) {
        if (!showTargets) return null;
        List<String> opts = targetOptions();
        int rows = Math.min(opts.size() - listScroll, DROP_MAX_ROWS);
        int h = Math.max(rows, 1) * DROP_ROW_H + 6;
        if (mx < dropX || mx >= dropX + DROP_W || my < dropY || my >= dropY + h) return null;
        int idx = listScroll + (my - dropY - 3) / DROP_ROW_H;
        if (idx < 0 || idx >= opts.size()) return null;
        return opts.get(idx);
    }

    // ══════════════════════ 输入处理 ══════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = (int) mx, y = (int) my;
            // -2) 删除称号的"选择称号"下拉优先
            if (showTitleList) {
                String id = titleDropdownPick(x, y);
                if (id != null) {
                    if (targetArg() != null)
                        performAction("sre:title revoke " + targetArg() + " " + id, "已删除", false);
                    showTitleList = false;
                    return true;
                }
            }
            // -1) 给予皮肤的"选皮肤"下拉优先
            if (showSkinList) {
                String[] pick = skinDropdownPick(x, y);
                if (pick != null) {
                    skinTypeBox.setValue(pick[0]);
                    skinIdBox.setValue(pick[1]);
                    showSkinList = false;
                    playClick();
                    return true;
                }
            }
            // 0) 删除公告的"选择公告"下拉优先（覆盖在其他控件之上）
            if (showAnnList) {
                String pick = annDropdownPick(x, y);
                if (pick != null) {
                    announceDeleteBox.setValue(pick);
                    showAnnList = false;
                    playClick();
                    return true;
                }
            }
            // 1) 下拉列表优先（覆盖在其他控件之上）
            if (showTargets) {
                String pick = dropdownPick(x, y);
                if (pick != null) {
                    targetBox.setValue(pick);
                    showTargets = false;
                    playClick();
                    return true;
                }
            }
            // 2) 色板取色
            if (pickColorAt(x, y)) {
                playClick();
                return true;
            }
            // 3) 普通按钮
            for (UiButton b : buttons) {
                if (isIn(x, y, b.x, b.y, b.w, b.h)) {
                    if (b.enabled.getAsBoolean()) {
                        b.action.run();
                        playClick();
                        return true;
                    }
                }
            }
            // 4) 点击其他区域收起下拉
            if (showTargets) showTargets = false;
            if (showAnnList) showAnnList = false;
            if (showSkinList) showSkinList = false;
            if (showTitleList) showTitleList = false;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (showTitleList) {
            List<String[]> opts = titleOptions();
            int rows = Math.min(Math.max(opts.size() - titleListScroll, 1), TITLE_DROP_MAX_ROWS);
            int h = rows * TITLE_DROP_ROW_H + 6;
            if (isIn((int) mx, (int) my, titleDropX, titleDropY, TITLE_DROP_W, h)) {
                int maxScroll = Math.max(0, opts.size() - TITLE_DROP_MAX_ROWS);
                titleListScroll = Mth.clamp(titleListScroll - (int) Math.signum(scrollY), 0, maxScroll);
                return true;
            }
        }
        if (showSkinList) {
            List<String[]> opts = skinOptions();
            int rows = Math.min(Math.max(opts.size() - skinListScroll, 1), SKIN_DROP_MAX_ROWS);
            int h = rows * SKIN_DROP_ROW_H + 6;
            if (isIn((int) mx, (int) my, skinDropX, skinDropY, SKIN_DROP_W, h)) {
                int maxScroll = Math.max(0, opts.size() - SKIN_DROP_MAX_ROWS);
                skinListScroll = Mth.clamp(skinListScroll - (int) Math.signum(scrollY), 0, maxScroll);
                return true;
            }
        }
        if (showAnnList) {
            List<String[]> opts = annDeleteOptions();
            int rows = Math.min(Math.max(opts.size() - annListScroll, 1), ANN_DROP_MAX_ROWS);
            int h = rows * ANN_DROP_ROW_H + 6;
            if (isIn((int) mx, (int) my, annDropX, annDropY, ANN_DROP_W, h)) {
                int maxScroll = Math.max(0, opts.size() - ANN_DROP_MAX_ROWS);
                annListScroll = Mth.clamp(annListScroll - (int) Math.signum(scrollY), 0, maxScroll);
                return true;
            }
        }
        if (showTargets) {
            int h = DROP_MAX_ROWS * DROP_ROW_H + 6;
            if (isIn((int) mx, (int) my, dropX, dropY, DROP_W, h)) {
                int maxScroll = Math.max(0, targetOptions().size() - DROP_MAX_ROWS);
                listScroll = Mth.clamp(listScroll - (int) Math.signum(scrollY), 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 公告内容框聚焦时，回车插入换行标记 \n（EditBox 为单行控件，公告界面渲染时还原为真正换行）
        if (announceContentBox != null && announceContentBox.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            announceContentBox.insertText("\\n");
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════ 命令组装与校验 ══════════════════════

    /** 目标参数：名字含空格自动加引号；选择器原样透传；空则视为无效 */
    private String targetArg() {
        if (targetBox == null) return null;
        String raw = targetBox.getValue().trim();
        if (raw.isEmpty()) return null;
        if (raw.startsWith("@") || raw.startsWith("#")) return raw;
        if (raw.contains(" ")) return "\"" + raw.replace("\"", "") + "\"";
        return raw;
    }

    private boolean moneyValid() {
        return targetArg() != null && moneyBox != null && !moneyBox.getValue().trim().isEmpty();
    }

    private boolean titleValid() {
        if (targetArg() == null || titleTextBox == null) return false;
        String txt = titleTextBox.getValue().trim();
        return !txt.isEmpty() && txt.length() <= 32;
    }

    /** 发布公告：标题与内容都非空才可提交 */
    private boolean announceValid() {
        return announceTitleBox != null && announceContentBox != null
                && !announceTitleBox.getValue().trim().isEmpty()
                && !announceContentBox.getValue().trim().isEmpty();
    }

    /** 命令参数安全加引号：去换行、去双引号，整体用双引号包裹（供 sre:announce 标题使用） */
    private static String quoted(String raw) {
        String s = raw == null ? "" : raw.trim().replace("\r", " ").replace("\n", " ").replace("\"", "");
        return "\"" + s + "\"";
    }

    /** 给予皮肤：目标 + 类型 + 皮肤ID 都非空才可提交 */
    private boolean skinValid() {
        return targetArg() != null && skinTypeBox != null && skinIdBox != null
                && !skinTypeBox.getValue().trim().isEmpty()
                && !skinIdBox.getValue().trim().isEmpty();
    }

    /** 皮肤候选：枚举所有已注册 类型/皮肤（排除 default） */
    private List<String[]> skinOptions() {
        List<String[]> opts = new ArrayList<>();
        try {
            for (var entry : ItemSkinManager.getSkins().entrySet()) {
                String type = entry.getKey();
                for (String skin : entry.getValue().keySet()) {
                    if ("default".equals(skin)) continue;
                    opts.add(new String[]{type, skin, type + "/" + skin});
                }
            }
        } catch (Exception ignored) {}
        return opts;
    }

    private String titleColorHex() {
        return String.format("%06X", hsvColor() & 0xFFFFFF);
    }

    /** 称号预览：按位置开关拼出【鱼仁】玩家名 或 玩家名【鱼仁】，颜色即当前取色 */
    private Component titlePreview() {
        String txt = titleTextBox == null ? "" : titleTextBox.getValue().trim();
        if (txt.length() > 32) txt = txt.substring(0, 32);
        if (txt.isEmpty()) txt = "鱼仁";
        String name = targetArg();
        String shownName = (name == null || name.startsWith("@") || name.startsWith("#"))
                ? "玩家名" : name;
        MutableComponent title = Component.literal("【" + txt + "】")
                .withStyle(s -> s.withColor(TextColor.fromRgb(hsvColor())));
        MutableComponent nameComp = Component.literal(shownName)
                .withStyle(s -> s.withColor(TextColor.fromRgb(0xFFDDEEFF)));
        return titleSuffix
                ? Component.empty().append(nameComp).append(title)
                : Component.empty().append(title).append(nameComp);
    }

    private void send(String command) {
        if (this.minecraft.player == null) return;
        this.minecraft.player.connection.sendCommand(command);
        this.minecraft.player.displayClientMessage(
                Component.literal("已发送: /" + command).withStyle(ChatFormatting.GREEN), true);
    }

    // ══════════════════════ 目标数据快照与动作回填 ══════════════════════

    /** 输入稳定后（防抖约 0.4s）向服务端拉取目标玩家数据快照 */
    @Override
    public void tick() {
        super.tick();
        if (targetBox == null) return;
        String cur = targetBox.getValue().trim();
        if (!cur.equals(inspectDraft)) {
            inspectDraft = cur;
            inspectDebounce = 8;
            return;
        }
        if (inspectDebounce > 0) {
            inspectDebounce--;
            return;
        }
        boolean valid = !cur.isEmpty() && !cur.startsWith("@") && !cur.startsWith("#");
        if (valid && !cur.equals(lastInspectRequested)) {
            lastInspectRequested = cur;
            ClientPlayNetworking.send(new AdminInspectRequestC2SPayload(cur));
        }
    }

    /**
     * 执行管理命令：命令先行、随后请求目标快照。快照回执到达时（reconcileSnapshot）把待回填
     * 动词（可选带最新余额）作为弹窗显示；选择器/空目标则立即弹窗（无数值）。
     */
    private void performAction(String command, String verb, boolean wantBalance) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        this.minecraft.player.connection.sendCommand(command);
        String t = targetBox == null ? "" : targetBox.getValue().trim();
        boolean named = !t.isEmpty() && !t.startsWith("@") && !t.startsWith("#");
        if (named) {
            pendingVerb = verb;
            pendingWantBalance = wantBalance;
            lastInspectRequested = t;
            inspectDraft = t;
            inspectDebounce = 0;
            ClientPlayNetworking.send(new AdminInspectRequestC2SPayload(t));
        } else {
            showToast(verb);
        }
        playClick();
    }

    /** 每次渲染检查：若服务端快照有新回执，结算待回填弹窗 */
    private void reconcileSnapshot() {
        long v = AdminInspectCache.version();
        if (v == appliedSnapshotVersion) return;
        appliedSnapshotVersion = v;
        if (pendingVerb != null) {
            String txt = pendingVerb;
            if (pendingWantBalance) {
                AdminInspectCache.Snapshot s = AdminInspectCache.snapshot();
                txt = s.found ? txt + " " + s.balance : txt + "（目标不在线）";
            }
            showToast(txt);
            pendingVerb = null;
            pendingWantBalance = false;
        }
    }

    /** 目标持有某张阵营卡的张数（快照查询） */
    private static int cardCount(AdminInspectCache.Snapshot s, String key) {
        for (int i = 0; i < s.cardKeys.size(); i++) {
            if (s.cardKeys.get(i).equals(key)) return s.cardCounts.get(i);
        }
        return 0;
    }

    /** 卡按钮第二行：目标在线时显示持有数量 "×N"（有）或 "未拥有"（无）；未选/离线目标为空串 */
    private String ownedCardCountText(String key) {
        AdminInspectCache.Snapshot s = AdminInspectCache.snapshot();
        if (s == null || !s.found) return "";
        int c = "selfselect".equals(key) ? s.selfSelect : cardCount(s, key);
        return c > 0 ? "×" + c : "未拥有";
    }

    /** 删除称号下拉候选：目标拥有称号（id + 显示名，来自 TitleClientCache） */
    private List<String[]> titleOptions() {
        List<String[]> opts = new ArrayList<>();
        AdminInspectCache.Snapshot s = AdminInspectCache.snapshot();
        if (s == null) return opts;
        for (String id : s.titleIds) {
            Title t = TitleClientCache.get(id);
            opts.add(new String[]{id, t != null ? t.displayText() : ("(未知称号 " + id + ")")});
        }
        return opts;
    }

    private void drawTitleDropdown(GuiGraphics g, int mx, int my) {
        List<String[]> opts = titleOptions();
        if (opts.isEmpty()) {
            g.fill(titleDropX, titleDropY, titleDropX + TITLE_DROP_W, titleDropY + 18, 0xFF0C1020);
            g.renderOutline(titleDropX, titleDropY, TITLE_DROP_W, 18, 0xFF334466);
            g.drawString(this.font, "（该玩家没有称号）", titleDropX + 4, titleDropY + 5, 0xFF8899BB, false);
            return;
        }
        int rows = Math.min(Math.max(opts.size() - titleListScroll, 1), TITLE_DROP_MAX_ROWS);
        int h = rows * TITLE_DROP_ROW_H + 6;
        g.fill(titleDropX, titleDropY, titleDropX + TITLE_DROP_W, titleDropY + h, 0xFF0C1020);
        g.renderOutline(titleDropX, titleDropY, TITLE_DROP_W, h, 0xFF334466);
        for (int i = 0; i < rows; i++) {
            int idx = titleListScroll + i;
            int ry = titleDropY + 3 + i * TITLE_DROP_ROW_H;
            boolean hov = isIn(mx, my, titleDropX, ry, TITLE_DROP_W, TITLE_DROP_ROW_H);
            if (hov) g.fill(titleDropX + 1, ry, titleDropX + TITLE_DROP_W - 1, ry + TITLE_DROP_ROW_H, 0x33FFFFFF);
            String disp = this.font.plainSubstrByWidth(opts.get(idx)[1], TITLE_DROP_W - 8);
            g.drawString(this.font, disp, titleDropX + 4, ry + 2, hov ? 0xFFFFFFFF : 0xFFCCDDFF, false);
        }
    }

    /** 命中"选择称号"下拉项则返回称号 id，否则 null */
    private String titleDropdownPick(int mx, int my) {
        if (!showTitleList) return null;
        List<String[]> opts = titleOptions();
        if (opts.isEmpty()) return null;
        int rows = Math.min(Math.max(opts.size() - titleListScroll, 1), TITLE_DROP_MAX_ROWS);
        int h = rows * TITLE_DROP_ROW_H + 6;
        if (!isIn(mx, my, titleDropX, titleDropY, TITLE_DROP_W, h)) return null;
        int idx = titleListScroll + (my - titleDropY - 3) / TITLE_DROP_ROW_H;
        if (idx < 0 || idx >= opts.size()) return null;
        return opts.get(idx)[0];
    }

    /** 发布公告：发送命令 → 清空标题与内容输入框 → 屏幕中央弹窗"已发送" */
    private void publishAnnouncement() {
        if (this.minecraft.player == null || !announceValid()) return;
        String content = announceContentBox.getValue().trim().replace("\r", " ").replace("\n", " ");
        this.minecraft.player.connection.sendCommand(
                "sre:announce add " + quoted(announceTitleBox.getValue()) + " " + content);
        announceTitleBox.setValue("");
        announceContentBox.setValue("");
        showToast("已发送");
        playClick();
    }

    /** 删除公告：按序号发送 sre:announce remove → 清空序号框 → 屏幕中央弹窗 */
    private void deleteAnnouncement() {
        if (this.minecraft.player == null || !deleteValid()) return;
        int idx = parseInt(announceDeleteBox.getValue(), 1, 1, 999);
        this.minecraft.player.connection.sendCommand("sre:announce remove " + idx);
        announceDeleteBox.setValue("");
        showToast("已删除第 " + idx + " 条公告");
        playClick();
    }

    /** 删除按钮可用：序号框为从 1 起的正整数 */
    private boolean deleteValid() {
        if (announceDeleteBox == null) return false;
        String s = announceDeleteBox.getValue().trim();
        if (s.isEmpty()) return false;
        try {
            return Integer.parseInt(s) >= 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** 触发屏幕中央弹窗提示，约 1.8 秒后自动消失 */
    private void showToast(String text) {
        toastText = text;
        toastUntil = System.currentTimeMillis() + 1800L;
    }

    private void renderToast(GuiGraphics g) {
        if (toastText == null || toastText.isEmpty()) return;
        if (System.currentTimeMillis() >= toastUntil) return;
        int w = this.font.width(toastText) + 28;
        int h = this.font.lineHeight + 18;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        g.fill(x, y, x + w, y + h, 0xF00C1A12);
        g.renderOutline(x, y, w, h, GREEN | 0xFF000000);
        g.drawCenteredString(this.font, Component.literal(toastText).withStyle(ChatFormatting.GREEN),
                x + w / 2, y + 9, 0xFF66EE88);
    }

    /** 删除下拉的候选：最新在前，序号取 remove 命令所需的插入序（1-based） */
    private List<String[]> annDeleteOptions() {
        List<String[]> opts = new ArrayList<>();
        List<AnnouncementEntry> list = AnnouncementClientCache.get();
        for (int i = list.size() - 1; i >= 0; i--) {
            AnnouncementEntry e = list.get(i);
            int removeIndex = i + 1;
            String disp = "#" + removeIndex + "  " + e.title() + "   (" + e.publisher() + ")";
            opts.add(new String[]{String.valueOf(removeIndex), disp});
        }
        return opts;
    }

    private void drawAnnDropdown(GuiGraphics g, int mx, int my) {
        List<String[]> opts = annDeleteOptions();
        if (opts.isEmpty()) {
            g.fill(annDropX, annDropY, annDropX + ANN_DROP_W, annDropY + 18, 0xFF0C1020);
            g.renderOutline(annDropX, annDropY, ANN_DROP_W, 18, 0xFF334466);
            g.drawString(this.font, "（当前没有公告）", annDropX + 4, annDropY + 5, 0xFF8899BB, false);
            return;
        }
        int rows = Math.min(Math.max(opts.size() - annListScroll, 1), ANN_DROP_MAX_ROWS);
        int h = rows * ANN_DROP_ROW_H + 6;
        g.fill(annDropX, annDropY, annDropX + ANN_DROP_W, annDropY + h, 0xFF0C1020);
        g.renderOutline(annDropX, annDropY, ANN_DROP_W, h, 0xFF334466);
        for (int i = 0; i < rows; i++) {
            int idx = annListScroll + i;
            int ry = annDropY + 3 + i * ANN_DROP_ROW_H;
            boolean hov = isIn(mx, my, annDropX, ry, ANN_DROP_W, ANN_DROP_ROW_H);
            if (hov) g.fill(annDropX + 1, ry, annDropX + ANN_DROP_W - 1, ry + ANN_DROP_ROW_H, 0x33FFFFFF);
            String disp = this.font.plainSubstrByWidth(opts.get(idx)[1], ANN_DROP_W - 8);
            g.drawString(this.font, disp, annDropX + 4, ry + 2, hov ? 0xFFFFFFFF : 0xFFCCDDFF, false);
        }
    }

    /** 命中"选择公告"下拉项则返回其序号字符串，否则 null */
    private String annDropdownPick(int mx, int my) {
        if (!showAnnList) return null;
        List<String[]> opts = annDeleteOptions();
        if (opts.isEmpty()) return null;
        int rows = Math.min(Math.max(opts.size() - annListScroll, 1), ANN_DROP_MAX_ROWS);
        int h = rows * ANN_DROP_ROW_H + 6;
        if (!isIn(mx, my, annDropX, annDropY, ANN_DROP_W, h)) return null;
        int idx = annListScroll + (my - annDropY - 3) / ANN_DROP_ROW_H;
        if (idx < 0 || idx >= opts.size()) return null;
        return opts.get(idx)[0];
    }

    private void drawSkinDropdown(GuiGraphics g, int mx, int my) {
        List<String[]> opts = skinOptions();
        if (opts.isEmpty()) {
            g.fill(skinDropX, skinDropY, skinDropX + SKIN_DROP_W, skinDropY + 18, 0xFF0C1020);
            g.renderOutline(skinDropX, skinDropY, SKIN_DROP_W, 18, 0xFF334466);
            g.drawString(this.font, "（无可用皮肤）", skinDropX + 4, skinDropY + 5, 0xFF8899BB, false);
            return;
        }
        int rows = Math.min(Math.max(opts.size() - skinListScroll, 1), SKIN_DROP_MAX_ROWS);
        int h = rows * SKIN_DROP_ROW_H + 6;
        g.fill(skinDropX, skinDropY, skinDropX + SKIN_DROP_W, skinDropY + h, 0xFF0C1020);
        g.renderOutline(skinDropX, skinDropY, SKIN_DROP_W, h, 0xFF334466);
        for (int i = 0; i < rows; i++) {
            int idx = skinListScroll + i;
            int ry = skinDropY + 3 + i * SKIN_DROP_ROW_H;
            boolean hov = isIn(mx, my, skinDropX, ry, SKIN_DROP_W, SKIN_DROP_ROW_H);
            if (hov) g.fill(skinDropX + 1, ry, skinDropX + SKIN_DROP_W - 1, ry + SKIN_DROP_ROW_H, 0x33FFFFFF);
            String disp = this.font.plainSubstrByWidth(opts.get(idx)[2], SKIN_DROP_W - 8);
            g.drawString(this.font, disp, skinDropX + 4, ry + 2, hov ? 0xFFFFFFFF : 0xFFCCDDFF, false);
        }
    }

    /** 命中"选皮肤"下拉项则返回 [类型, 皮肤ID, 显示名]，否则 null */
    private String[] skinDropdownPick(int mx, int my) {
        if (!showSkinList) return null;
        List<String[]> opts = skinOptions();
        if (opts.isEmpty()) return null;
        int rows = Math.min(Math.max(opts.size() - skinListScroll, 1), SKIN_DROP_MAX_ROWS);
        int h = rows * SKIN_DROP_ROW_H + 6;
        if (!isIn(mx, my, skinDropX, skinDropY, SKIN_DROP_W, h)) return null;
        int idx = skinListScroll + (my - skinDropY - 3) / SKIN_DROP_ROW_H;
        if (idx < 0 || idx >= opts.size()) return null;
        return opts.get(idx);
    }

    private static int parseInt(String s, int def, int min, int max) {
        try {
            return Mth.clamp(Integer.parseInt(s.trim()), min, max);
        } catch (Exception e) {
            return def;
        }
    }

    private EditBox makeBox(int x, int y, int w, String value, String hint, int maxLen,
            Predicate<String> filter) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.literal(hint));
        box.setValue(value);
        box.setMaxLength(maxLen);
        if (filter != null) box.setFilter(filter);
        this.addRenderableWidget(box);
        return box;
    }

    private void playClick() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void drawUiButton(GuiGraphics g, UiButton b, int mx, int my) {
        boolean en = b.enabled.getAsBoolean();
        boolean hov = en && isIn(mx, my, b.x, b.y, b.w, b.h);
        int full = b.color | 0xFF000000;
        g.renderOutline(b.x, b.y, b.w, b.h, hov ? full : 0xFF334466);
        g.fillGradient(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1,
                hov ? blend(full, 0xFF223380, 0.45f) : 0xFF141828,
                hov ? blend(full, 0xFF162060, 0.30f) : 0xFF0E1020);
        int col = !en ? 0xFF556677 : hov ? 0xFFFFFFFF : 0xFFCCDDFF;
        int fh = this.font.lineHeight;
        if (b.sub != null) {
            String name = this.font.plainSubstrByWidth(b.label.get().getString(), b.w - 4);
            String count = this.font.plainSubstrByWidth(b.sub.get().getString(), b.w - 4);
            int topY = b.y + (b.h - (fh * 2 + 1)) / 2;
            g.drawCenteredString(this.font, Component.literal(name), b.x + b.w / 2, topY, col);
            int countCol = !en ? 0xFF556677 : (hov ? 0xFFFFE08A : 0xFFDDAA55);
            g.drawCenteredString(this.font, Component.literal(count), b.x + b.w / 2, topY + fh + 1, countCol);
        } else {
            String plain = this.font.plainSubstrByWidth(b.label.get().getString(), b.w - 4);
            g.drawCenteredString(this.font, Component.literal(plain), b.x + b.w / 2,
                    b.y + (b.h - fh) / 2, col);
        }
    }

    private static boolean isIn(int pxv, int pyv, int x, int y, int w, int h) {
        return pxv >= x && pxv < x + w && pyv >= y && pyv < y + h;
    }

    private static int blend(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int gg = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int bb = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }
}