package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.SREDisableManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自选职业卡 GUI：第一步选阵营（杀手/平民/中立/杀手中立/警长），第二步在网格中选该阵营的具体职业。
 * 只展示谋杀模式职业：原版基础职业、修理逃脱模式、其他模式、特殊地图限定、不会自然刷新的职业不展示
 * （彩蛋职业与警长阵营除外）。选择后发送 {@code sre:pass selfselect <roleId>}。
 *
 * <p>本局被禁用的职业仍会列出，但以灰色显示且无法点击（服务端也会二次拦截，
 * 见 {@code ProgressionCommand#selfSelect}）。
 */
public class SelfSelectCardScreen extends Screen {

    private static final int[] FACTION_TYPES = { 4, 1, 2, 3, 5 };
    private static final String[] FACTION_NAMES = { "杀手", "平民", "中立", "杀手中立", "警长" };

    /** 职业卡片网格尺寸 */
    private static final int CARD_W = 150;
    private static final int CARD_H = 26;
    private static final int CARD_GAP = 6;
    private static final int GRID_TOP = 70;

    // ===== 配色 =====
    private static final int BG_NORMAL = 0x30161B30;
    private static final int BG_HOVER = 0x504488FF;
    private static final int BG_DISABLED = 0x40000000;
    private static final int TEXT_NORMAL = 0xFFFFFFFF;
    private static final int TEXT_DISABLED = 0xFF9A9A9A;

    private int selectedFaction = -1;
    private final List<SRERole> selectableRoles = new ArrayList<>();
    /** 当前列出的职业里，本局被禁用的职业 id */
    private final Set<String> disabledRoleIds = new HashSet<>();
    private int scrollOffset = 0; // 单位：行

    public SelfSelectCardScreen() {
        super(Component.literal("自选职业卡"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xE60C1020);
        g.drawCenteredString(font, "自选职业卡", width / 2, 8, 0xFFFFFFFF);
        if (selectedFaction < 0) {
            renderFactionSelection(g, mouseX, mouseY);
        } else {
            renderRoleSelection(g, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, delta);
    }

    private void renderFactionSelection(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, "选择阵营", width / 2, 40, 0xFFCCCCCC);
        for (int i = 0; i < FACTION_TYPES.length; i++) {
            int y = 70 + i * 32;
            int x = width / 2 - 110;
            boolean hovered = inside(mouseX, mouseY, x, y, 220, 26);
            g.fill(x, y, x + 220, y + 26, hovered ? 0x504488FF : 0x30161B30);
            g.drawCenteredString(font, FACTION_NAMES[i], width / 2, y + 9, 0xFFFFFFFF);
        }
    }

    private void renderRoleSelection(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, "选择职业", width / 2, 40, 0xFFCCCCCC);
        if (selectableRoles.isEmpty()) {
            g.drawCenteredString(font, "该阵营没有可选职业", width / 2, 90, 0xFF999999);
        }

        int cols = gridCols();
        int startX = gridStartX();
        for (int i = scrollOffset * cols; i < selectableRoles.size(); i++) {
            int idx = i - scrollOffset * cols;
            int col = idx % cols;
            int row = idx / cols;
            int x = startX + col * (CARD_W + CARD_GAP);
            int y = GRID_TOP + row * (CARD_H + CARD_GAP);
            if (y + CARD_H > height - 40) break;

            SRERole role = selectableRoles.get(i);
            boolean disabled = isDisabled(role);
            boolean hovered = inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
            int bg = disabled ? BG_DISABLED : (hovered ? BG_HOVER : BG_NORMAL);
            g.fill(x, y, x + CARD_W, y + CARD_H, bg);
            g.drawCenteredString(font, truncate(role.getName().getString(), CARD_W - 12),
                    x + CARD_W / 2, y + (CARD_H - 9) / 2, disabled ? TEXT_DISABLED : TEXT_NORMAL);
            if (disabled && hovered) {
                g.renderTooltip(font, Component.literal("该职业已在本局禁用")
                        .append("\n")
                        .append(Component.literal("§7无法使用自选职业卡（卡牌不会被消耗）")), mouseX, mouseY);
            }
        }

        // 返回按钮
        int by = height - 30;
        boolean backHovered = inside(mouseX, mouseY, width / 2 - 100, by, 200, 20);
        g.fill(width / 2 - 100, by, width / 2 + 100, by + 20, backHovered ? 0x50FFFFFF : 0x30FFFFFF);
        g.drawCenteredString(font, "返回", width / 2, by + 6, 0xFFFFFFFF);

        // 右侧滚轮提示
        String hint = "滚轮滚动";
        g.drawString(font, hint, width - font.width(hint) - 10, by + 6, 0xFFAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (selectedFaction < 0) {
            for (int i = 0; i < FACTION_TYPES.length; i++) {
                if (inside(mouseX, mouseY, width / 2 - 110, 70 + i * 32, 220, 26)) {
                    selectFaction(FACTION_TYPES[i]);
                    return true;
                }
            }
        } else {
            if (inside(mouseX, mouseY, width / 2 - 100, height - 30, 200, 20)) {
                selectedFaction = -1;
                scrollOffset = 0;
                return true;
            }
            int cols = gridCols();
            int startX = gridStartX();
            for (int i = scrollOffset * cols; i < selectableRoles.size(); i++) {
                int idx = i - scrollOffset * cols;
                int col = idx % cols;
                int row = idx / cols;
                int x = startX + col * (CARD_W + CARD_GAP);
                int y = GRID_TOP + row * (CARD_H + CARD_GAP);
                if (inside(mouseX, mouseY, x, y, CARD_W, CARD_H)) {
                    SRERole role = selectableRoles.get(i);
                    // 本局被禁用的职业不可选：不发送命令、不消耗卡牌
                    if (isDisabled(role)) {
                        return true;
                    }
                    sendCommand("sre:pass selfselect " + role.identifier());
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (selectedFaction >= 0) {
            int visibleRows = Math.max(1, (height - GRID_TOP - 40) / (CARD_H + CARD_GAP));
            int maxRow = Math.max(0, gridRows() - visibleRows);
            scrollOffset = Math.max(0, Math.min(maxRow, scrollOffset - (int) deltaY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void selectFaction(int factionType) {
        selectedFaction = factionType;
        scrollOffset = 0;
        selectableRoles.clear();
        disabledRoleIds.clear();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (!TMMRoles.isSelfSelectableRole(role)) continue;
            if (roleFactionType(role) != factionType) continue;
            selectableRoles.add(role);
            // 每帧都查会反复扫描禁用列表，这里在打开阵营时缓存一次。
            // 判定复用角色介绍界面同一套 SREDisableManager，保证「显示为禁用」和「不可选」一致。
            if (SREDisableManager.isRoleDisabled(role)) {
                disabledRoleIds.add(role.identifier().toString());
            }
        }
        selectableRoles.sort((a, b) -> a.getName().getString().compareTo(b.getName().getString()));
    }

    private boolean isDisabled(SRERole role) {
        return role != null && disabledRoleIds.contains(role.identifier().toString());
    }

    private int gridCols() {
        return Math.max(1, (width - 40) / (CARD_W + CARD_GAP));
    }

    private int gridStartX() {
        int cols = gridCols();
        int totalW = cols * CARD_W + (cols - 1) * CARD_GAP;
        return (width - totalW) / 2;
    }

    private int gridRows() {
        int cols = gridCols();
        return (selectableRoles.size() + cols - 1) / cols;
    }

    private String truncate(String name, int maxWidth) {
        if (font.width(name) <= maxWidth) return name;
        String ellipsis = "…";
        String out = name;
        while (!out.isEmpty() && font.width(out + ellipsis) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + ellipsis;
    }

    private static int roleFactionType(SRERole role) {
        if (role.isVigilanteTeam()) return 5;
        if (role.isInnocent()) return 1;
        if (role.isNeutrals() && !role.isNeutralForKiller()) return 2;
        if (role.isNeutrals() && role.isNeutralForKiller()) return 3;
        if (role.canUseKiller()) return 4;
        return -1;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
