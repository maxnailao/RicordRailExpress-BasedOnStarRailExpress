package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.client.data.CS2ClientBoxCache;
import org.agmas.noellesroles.cs2.network.OpenBoxC2SPayload;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * CS2 仓库连抽数量选择弹窗。
 * <p>
 * 中间展示要开的箱子图标与名称，下方为数量行：-10 / -1 / 可编辑数量框 / +1 / +10，
 * 数量支持直接键入；上限为 min(持有箱子数, 持有钥匙数, 100)。
 * 确认后按数量发送开箱请求（1 走滚动动画流程，&gt;1 走批量网格流程）。
 * </p>
 */
public class CS2OpenCountScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 178;

    private static final int PANEL_BG = 0xF00C1020;
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int HEADER_TEXT = 0xFF1A1F2E;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF999999;
    private static final int ACCENT = 0xFF4488FF;
    private static final int MAX_LIMIT = 100;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String boxId;
    private final String boxName;
    private final Screen parent;

    /** 当前选择的开箱数量（1 ~ maxCount），输入框与 +/- 按钮共同维护 */
    private int count = 1;
    /** 可开上限：min(箱子数, 钥匙数, 100)；为 0 表示材料不足 */
    private int maxCount = 0;

    private EditBox countBox;
    private Button confirmButton;

    public CS2OpenCountScreen(String boxId, String boxName, Screen parent) {
        super(Component.literal("选择开箱数量"));
        this.boxId = boxId;
        this.boxName = boxName;
        this.parent = parent;
    }

    /** 上限 = min(持有箱子数, 持有钥匙数（如需钥匙）, 100) */
    private int computeMaxCount() {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(p);
        int max = inv.getBoxCount(boxId);
        String keyName = CS2ClientBoxCache.getKeyName(boxId);
        if (keyName != null && !keyName.isEmpty()) {
            max = Math.min(max, inv.getKeyCount(keyName));
        }
        return Math.max(0, Math.min(max, MAX_LIMIT));
    }

    @Override
    protected void init() {
        super.init();
        maxCount = computeMaxCount();
        count = Math.max(1, Math.min(count, Math.max(1, maxCount)));

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;
        int centerX = px + PANEL_W / 2;
        int rowY = py + 108;

        // 数量行：-10 | -1 | [输入框] | +1 | +10
        addRenderableWidget(Button.builder(Component.literal("-10"), b -> stepCount(-10))
                .pos(centerX - 84, rowY).size(32, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-1"), b -> stepCount(-1))
                .pos(centerX - 48, rowY).size(24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+1"), b -> stepCount(1))
                .pos(centerX + 24, rowY).size(24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10"), b -> stepCount(10))
                .pos(centerX + 52, rowY).size(32, 20).build());

        countBox = new EditBox(font, centerX - 20, rowY + 3, 40, 16, Component.literal("开箱数量"));
        countBox.setMaxLength(3);
        // 只允许输入数字，粘贴非法内容会被过滤器拒绝
        countBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        countBox.setHint(Component.literal("1"));
        countBox.setTextColor(TEXT_COLOR);
        countBox.setValue(String.valueOf(count));
        addRenderableWidget(countBox);

        // 底部：确认开箱 / 取消
        confirmButton = Button.builder(Component.literal("开箱"), b -> confirmOpen())
                .pos(px + 12, py + PANEL_H - 30).size(130, 20).build();
        addRenderableWidget(confirmButton);
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .pos(px + PANEL_W - 74, py + PANEL_H - 30).size(62, 20).build());

        updateConfirmLabel();
        setInitialFocus(countBox);
    }

    /** 从输入框吸收最新键入值（项目内 EditBox 均为按需读取，不挂监听器）；非法或空输入保留上一次有效值 */
    private void flushTypedValue() {
        if (countBox == null) return;
        String s = countBox.getValue().trim();
        if (s.isEmpty()) return;
        try {
            int v = Integer.parseInt(s);
            if (v >= 1) count = v;
        } catch (NumberFormatException ignored) {
            // 输入中途：保留上一次有效值，确认/步进时再夹取范围
        }
    }

    /** 加减数量：先吸收输入框中的最新值，再步进并夹到 [1, maxCount] */
    private void stepCount(int delta) {
        if (maxCount < 1) return;
        flushTypedValue();
        count = Math.max(1, Math.min(maxCount, count + delta));
        countBox.setValue(String.valueOf(count));
        updateConfirmLabel();
    }

    /** 确认按钮文案随数量实时变化；材料不足时禁用 */
    private void updateConfirmLabel() {
        if (confirmButton == null) return;
        if (maxCount < 1) {
            confirmButton.setMessage(Component.literal("箱子或钥匙不足"));
            confirmButton.active = false;
        } else {
            int shown = Math.max(1, Math.min(count, maxCount));
            confirmButton.setMessage(Component.literal("确认开箱 x" + shown));
            confirmButton.active = true;
        }
    }

    /** 发送开箱请求：count==1 走单开滚动动画，count>1 走批量聚合网格 */
    private void confirmOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || maxCount < 1) return;
        flushTypedValue();
        int open = Math.max(1, Math.min(count, maxCount));
        CS2WarehouseScreen.isBoxOpening = true;
        ClientPlayNetworking.send(new OpenBoxC2SPayload(boxId, open));
        mc.setScreen(parent);
        if (parent instanceof CS2WarehouseScreen wh) {
            wh.refreshData();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirmOpen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 实时吸收键入值，让“确认开箱 xN”按钮文案跟随输入更新（不在输入中途改写文本）
        flushTypedValue();
        updateConfirmLabel();

        // 半透明压暗底层界面，突出弹窗
        g.fill(0, 0, width, height, 0x90000000);

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;
        int centerX = px + PANEL_W / 2;

        // 面板底 + 白色描边
        g.fill(px, py, px + PANEL_W, py + PANEL_H, PANEL_BG);
        g.fill(px, py, px + PANEL_W, py + 1, BORDER_COLOR);
        g.fill(px, py + PANEL_H - 1, px + PANEL_W, py + PANEL_H, BORDER_COLOR);
        g.fill(px, py, px + 1, py + PANEL_H, BORDER_COLOR);
        g.fill(px + PANEL_W - 1, py, px + PANEL_W, py + PANEL_H, BORDER_COLOR);

        // 白色标题栏：左侧标题 + 右侧实时时间（深色字）
        g.fill(px + 1, py + 1, px + PANEL_W - 1, py + 17, BORDER_COLOR);
        g.drawString(font, "选择开箱数量", px + 5, py + 5, HEADER_TEXT, false);
        String time = LocalTime.now().format(TIME_FMT);
        g.drawString(font, time, px + PANEL_W - 4 - font.width(time), py + 5, HEADER_TEXT, false);

        // 中部：箱子图标（放大 2 倍）与箱子名称
        g.pose().pushPose();
        g.pose().translate(centerX - 16, py + 26, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderFakeItem(new ItemStack(Items.CHEST), 0, 0);
        g.pose().popPose();

        String name = clampText(boxName == null || boxName.isEmpty() ? boxId : boxName, PANEL_W - 20);
        g.drawCenteredString(font, name, centerX, py + 62, TEXT_COLOR);

        // 持有信息与可开上限
        String keyName = CS2ClientBoxCache.getKeyName(boxId);
        var p = Minecraft.getInstance().player;
        if (p != null) {
            CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(p);
            String held = "持有箱子 x" + inv.getBoxCount(boxId);
            if (keyName != null && !keyName.isEmpty()) {
                held += "　钥匙 x" + inv.getKeyCount(keyName);
            }
            g.drawCenteredString(font, held, centerX, py + 76, TEXT_DIM);
            g.drawCenteredString(font, "可开上限: " + maxCount, centerX, py + 88,
                    maxCount > 0 ? ACCENT : 0xFFFF6666);
        }

        g.drawCenteredString(font, "数量", centerX, py + 100, TEXT_DIM);

        super.render(g, mouseX, mouseY, delta);
    }

    /** 过长文本截断（弹窗内居中显示用） */
    private String clampText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (font.width(sb + "..") > maxWidth) {
                sb.deleteCharAt(sb.length() - 1);
                break;
            }
        }
        sb.append("..");
        return sb.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}