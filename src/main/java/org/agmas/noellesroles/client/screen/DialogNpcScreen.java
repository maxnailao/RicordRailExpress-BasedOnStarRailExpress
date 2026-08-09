package org.agmas.noellesroles.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.DialogSelectC2SPacket;
import org.agmas.noellesroles.packet.OpenDialogNpcScreenS2CPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话 NPC 界面：展示 NPC 台词与分支选项。
 * <p>
 * 遵循 docs/ui_style.md 的复古列车风格：深棕渐变面板 + 棕褐描边 +
 * 顶部装饰线，标题金色粗体，选项行带 hover 过渡。
 * <p>
 * 台词采用字幕式逐条播放：节点 text 按换行拆分为多条字幕，
 * 当前条打字机播完后停顿片刻自动播下一条；点击可加速
 * （正在打字 → 补全当前条，播完停顿中 → 立即切下一条），
 * 全部播完后才显示分支选项。
 */
public class DialogNpcScreen extends Screen {

    // UI 风格色板（见 docs/ui_style.md）
    private static final int BG_TOP = 0xD81A1008;
    private static final int BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int TOP_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int TEXT_DIM = 0xFFC8B898;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int HOVER_BG = 0x22FFFFFF;

    private static final int PAD = 12;
    private static final int OPTION_HEIGHT = 20;
    private static final int OPTION_GAP = 4;
    private static final int LINE_HEIGHT = 12;
    /** 每条字幕播完后的停顿 tick 数（约 1.5 秒） */
    private static final int HOLD_TICKS = 30;

    private final int entityId;
    private final String npcName;
    private final JsonObject root;

    private boolean valid = true;
    private String currentNodeId = "";

    /** 当前节点的字幕列表（text 按换行拆分） */
    private List<String> nodeLines = List.of();
    /** 当前播放到第几条字幕 */
    private int currentLineIdx = 0;
    /** 当前字幕已显示的字符数（打字机） */
    private int shownChars = 0;
    /** 当前字幕播完后的停顿计时 */
    private int holdTicks = 0;
    /** 布局用：所有字幕换行后的最大行数 */
    private int maxWrappedLines = 1;

    private List<OptionEntry> options = List.of();
    private int panelX, panelY, panelW, panelH;
    private int hoveredOption = -1;
    private final List<OptionAnim> optionAnims = new ArrayList<>();

    /** 单个选项的运行时数据 */
    private record OptionEntry(String text, String next, boolean end, boolean hasCommand, int x, int y, int w, int h) {
    }

    /** 选项 hover 动画状态 */
    private static final class OptionAnim {
        float hover;
    }

    public DialogNpcScreen(OpenDialogNpcScreenS2CPacket payload) {
        super(Component.literal(payload.npcName()));
        this.entityId = payload.entityId();
        this.npcName = payload.npcName();
        JsonObject parsed = null;
        try {
            JsonElement element = JsonParser.parseString(payload.dialogJson());
            if (element.isJsonObject()) {
                parsed = element.getAsJsonObject();
            }
        } catch (Exception e) {
            Noellesroles.LOGGER.error("解析对话界面数据失败", e);
        }
        this.root = parsed;
        if (this.root == null || !this.root.has("nodes") || !this.root.get("nodes").isJsonObject()) {
            this.valid = false;
        } else {
            String start = this.root.has("start") ? this.root.get("start").getAsString() : "";
            JsonObject nodes = this.root.getAsJsonObject("nodes");
            if (!nodes.has(start)) {
                start = nodes.keySet().stream().findFirst().orElse("");
            }
            this.currentNodeId = start;
        }
    }

    @Override
    protected void init() {
        this.panelW = Math.min(560, this.width - 40);
        if (this.valid) {
            this.switchNode(this.currentNodeId);
        }
    }

    /** 台词是否全部播完（可显示选项） */
    private boolean playbackDone() {
        return this.currentLineIdx >= this.nodeLines.size() - 1
                && this.shownChars >= this.currentLineText().length();
    }

    private String currentLineText() {
        if (this.nodeLines.isEmpty()) {
            return "";
        }
        int idx = Mth.clamp(this.currentLineIdx, 0, this.nodeLines.size() - 1);
        return this.nodeLines.get(idx);
    }

    /** 切换到指定对话节点，重算字幕与选项布局 */
    private void switchNode(String nodeId) {
        this.currentNodeId = nodeId;
        JsonObject nodes = this.root.getAsJsonObject("nodes");
        JsonObject node = nodes.has(nodeId) && nodes.get(nodeId).isJsonObject()
                ? nodes.getAsJsonObject(nodeId)
                : null;

        String fullText = node != null && node.has("text") ? node.get("text").getAsString() : "";
        // 按换行拆分为逐条播放的字幕
        List<String> lines = new ArrayList<>();
        for (String line : fullText.split("\n", -1)) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        this.nodeLines = lines;
        this.currentLineIdx = 0;
        this.shownChars = 0;
        this.holdTicks = 0;

        // 解析选项
        List<String[]> rawOptions = new ArrayList<>(); // [text, next, end, hasCommand]
        if (node != null && node.has("options") && node.get("options").isJsonArray()) {
            JsonArray array = node.getAsJsonArray("options");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject option = element.getAsJsonObject();
                String text = option.has("text") ? option.get("text").getAsString() : "";
                String next = option.has("next") ? option.get("next").getAsString() : "";
                boolean end = option.has("end") && option.get("end").getAsBoolean();
                boolean hasCommand = option.has("command");
                rawOptions.add(new String[] { text, next, String.valueOf(end), String.valueOf(hasCommand) });
            }
        }

        // 布局计算：文本区按所有字幕中最长的一条预留高度，避免播放时面板跳动
        int textMaxW = this.panelW - PAD * 2;
        this.maxWrappedLines = 1;
        for (String line : this.nodeLines) {
            this.maxWrappedLines = Math.max(this.maxWrappedLines, this.wrapText(line, textMaxW).size());
        }
        int textAreaH = this.maxWrappedLines * LINE_HEIGHT;
        int contentH = PAD + 14 + 6 + textAreaH + 10
                + (rawOptions.size() + 1) * (OPTION_HEIGHT + OPTION_GAP) + PAD;
        this.panelH = Mth.clamp(contentH, 120, this.height - 60);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = this.height - this.panelH - 34;

        List<OptionEntry> built = new ArrayList<>();
        int optionW = this.panelW - PAD * 2;
        int y = this.panelY + PAD + 14 + 6 + textAreaH + 10;
        for (String[] raw : rawOptions) {
            built.add(new OptionEntry(raw[0], raw[1], Boolean.parseBoolean(raw[2]),
                    Boolean.parseBoolean(raw[3]), this.panelX + PAD, y, optionW, OPTION_HEIGHT));
            y += OPTION_HEIGHT + OPTION_GAP;
        }
        // 兜底的"结束对话"选项
        built.add(new OptionEntry("", "", true, false, this.panelX + PAD, y, optionW, OPTION_HEIGHT));
        this.options = built;

        // 对齐动画状态数量
        while (this.optionAnims.size() < this.options.size()) {
            this.optionAnims.add(new OptionAnim());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        String current = this.currentLineText();
        if (this.shownChars < current.length()) {
            // 打字机推进
            this.shownChars = Math.min(current.length(), this.shownChars + 1);
        } else if (this.currentLineIdx < this.nodeLines.size() - 1) {
            // 当前条播完：停顿后自动播下一条
            this.holdTicks++;
            if (this.holdTicks >= HOLD_TICKS) {
                this.currentLineIdx++;
                this.shownChars = 0;
                this.holdTicks = 0;
            }
        }
        // hover 动画插值
        for (int i = 0; i < this.optionAnims.size(); i++) {
            OptionAnim anim = this.optionAnims.get(i);
            float target = i == this.hoveredOption ? 1.0F : 0.0F;
            anim.hover += (target - anim.hover) * 0.22F;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 全屏暗色遮罩
        g.fill(0, 0, this.width, this.height, 0x66000000);
        if (!this.valid) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.dialog_npc.invalid"),
                    this.width / 2, this.height / 2, MUTED);
            return;
        }

        boolean done = this.playbackDone();

        // 更新 hover 命中（播放未完成时选项不可交互）
        this.hoveredOption = -1;
        if (done) {
            for (int i = 0; i < this.options.size(); i++) {
                OptionEntry option = this.options.get(i);
                if (mouseX >= option.x() && mouseX <= option.x() + option.w()
                        && mouseY >= option.y() && mouseY <= option.y() + option.h()) {
                    this.hoveredOption = i;
                }
            }
        }

        // 面板三步绘制：渐变 + 描边 + 顶部装饰线
        g.fillGradient(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH,
                BG_TOP, BG_BOTTOM);
        g.renderOutline(this.panelX, this.panelY, this.panelW, this.panelH, BORDER);
        g.fill(this.panelX + 1, this.panelY + 1, this.panelX + this.panelW - 1, this.panelY + 2, TOP_LINE);

        // NPC 名称 + 字幕进度
        g.drawString(this.font, Component.literal(this.npcName).withStyle(net.minecraft.ChatFormatting.BOLD),
                this.panelX + PAD, this.panelY + PAD, GOLD, false);
        if (this.nodeLines.size() > 1) {
            String progress = (Math.min(this.currentLineIdx + 1, this.nodeLines.size())) + "/" + this.nodeLines.size();
            g.drawString(this.font, progress,
                    this.panelX + this.panelW - PAD - this.font.width(progress), this.panelY + PAD, MUTED, false);
        }

        // 当前字幕（打字机效果）
        String current = this.currentLineText();
        String shown = current.substring(0, Math.min(this.shownChars, current.length()));
        int textY = this.panelY + PAD + 14 + 6;
        List<String> wrapped = this.wrapText(current, this.panelW - PAD * 2);
        int remaining = shown.length();
        String lastVisible = "";
        int lastVisibleY = textY;
        for (String line : wrapped) {
            if (remaining <= 0) {
                break;
            }
            String visible = line.length() <= remaining ? line : line.substring(0, remaining);
            g.drawString(this.font, visible, this.panelX + PAD, textY, TEXT_DIM, false);
            lastVisible = visible;
            lastVisibleY = textY;
            remaining -= line.length();
            textY += LINE_HEIGHT;
        }
        // 打字机未完成时的闪烁光标
        if (this.shownChars < current.length() && (System.currentTimeMillis() / 360) % 2 == 0) {
            g.drawString(this.font, "_", this.panelX + PAD + this.font.width(lastVisible),
                    lastVisibleY, MUTED, false);
        }

        // 选项行（台词全部播完后才显示与交互）
        if (done) {
            for (int i = 0; i < this.options.size(); i++) {
                OptionEntry option = this.options.get(i);
                OptionAnim anim = this.optionAnims.get(i);
                boolean isEnd = option.end() && option.text().isEmpty();
                String label = isEnd
                        ? Component.translatable("screen.noellesroles.dialog_npc.end").getString()
                        : option.text();

                if (anim.hover > 0.02F) {
                    g.fill(option.x(), option.y(), option.x() + option.w(), option.y() + option.h(), HOVER_BG);
                    int borderColor = blendColors(0xFF5A4530, GOLD, anim.hover);
                    g.renderOutline(option.x(), option.y(), option.w(), option.h(), borderColor);
                }
                int textColor = blendColors(isEnd ? MUTED : TEXT, 0xFFF5E8C8, anim.hover);
                g.drawString(this.font, (isEnd ? "" : "» ") + label,
                        option.x() + 6, option.y() + (option.h() - 8) / 2, textColor, false);
            }
        } else {
            // 播放中的"点击继续"呼吸提示
            float pulse = 0.65F + 0.35F * (float) Math.abs(Math.sin(System.currentTimeMillis() / 360.0));
            int hintColor = (MUTED & 0x00FFFFFF) | ((int) (0x99 * pulse) << 24);
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.dialog_npc.playing"),
                    this.width / 2, this.panelY + this.panelH + 8, hintColor);
        }

        // 底部提示
        if (done) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.dialog_npc.hint"),
                    this.width / 2, this.panelY + this.panelH + 8, MUTED);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.valid) {
            this.onClose();
            return true;
        }
        // 正在打字：点击补全当前字幕
        if (this.shownChars < this.currentLineText().length()) {
            this.shownChars = this.currentLineText().length();
            return true;
        }
        // 还有后续字幕：点击立即切到下一条
        if (this.currentLineIdx < this.nodeLines.size() - 1) {
            this.currentLineIdx++;
            this.shownChars = 0;
            this.holdTicks = 0;
            return true;
        }
        // 全部播完：处理选项点击
        for (int i = 0; i < this.options.size(); i++) {
            OptionEntry option = this.options.get(i);
            if (mouseX >= option.x() && mouseX <= option.x() + option.w()
                    && mouseY >= option.y() && mouseY <= option.y() + option.h()) {
                this.selectOption(i, option);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 处理选项点击：执行命令（如有）、跳转节点或结束对话 */
    private void selectOption(int index, OptionEntry option) {
        playClickSound();
        if (option.hasCommand()) {
            ClientPlayNetworking.send(new DialogSelectC2SPacket(this.entityId, this.currentNodeId, index));
        }
        if (!option.next().isEmpty()) {
            this.switchNode(option.next());
        } else if (option.end()) {
            this.onClose();
        }
    }

    private void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /** ARGB 线性插值混色 */
    private static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    /** 按像素宽度手动换行，配合打字机效果使用 */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (current.length() > 0 && this.font.width(current.toString() + c) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            current.append(c);
        }
        lines.add(current.toString());
        return lines;
    }
}
