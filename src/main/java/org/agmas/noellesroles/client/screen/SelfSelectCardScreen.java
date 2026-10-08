package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
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
    /** 已被他人选走的角标颜色 */
    private static final int TEXT_CLAIMED = 0xFFFFAA55;

    private int selectedFaction = -1;
    /** 当前阵营的**全部**可选职业（不受搜索影响） */
    private final List<SRERole> allFactionRoles = new ArrayList<>();
    /** 应用搜索过滤后、真正显示在网格里的职业 */
    private final List<SRERole> selectableRoles = new ArrayList<>();
    /** 当前列出的职业里，本局被禁用的职业 id */
    private final Set<String> disabledRoleIds = new HashSet<>();
    private int scrollOffset = 0; // 单位：行

    // ===== 搜索框 =====
    private static final int SEARCH_H = 18;
    private static final int SEARCH_TOP = 24;
    private String searchText = "";
    private boolean searchFocused = false;

    public SelfSelectCardScreen() {
        super(Component.literal("自选职业卡"));
    }

    /** 搜索框矩形：宽度取 2 列卡片宽，水平居中 */
    private int searchX() {
        return width / 2 - searchW() / 2;
    }

    private int searchW() {
        return Math.min(320, Math.max(160, gridCols() * (CARD_W + CARD_GAP) - CARD_GAP));
    }

    private boolean isInSearchBox(double mx, double my) {
        return inside(mx, my, searchX(), SEARCH_TOP, searchW(), SEARCH_H);
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

        // ── 搜索框（职业太多时用来快速定位）──
        renderSearchBox(g, mouseX, mouseY);

        if (selectableRoles.isEmpty()) {
            String msg = allFactionRoles.isEmpty() ? "该阵营没有可选职业" : "没有匹配「" + searchText + "」的职业";
            g.drawCenteredString(font, msg, width / 2, 90, 0xFF999999);
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
            // 已被别人占用的职业：显示为不可选（服务端也会拦，这里只是提前告知）
            boolean claimed = isClaimedByOther(role);
            // 人数不到该职业的刷新门槛：同样提前置灰
            boolean tooFewPlayers = isPlayerCountGated(role);
            boolean unusable = disabled || claimed || tooFewPlayers;
            boolean hovered = inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
            int bg = unusable ? BG_DISABLED : (hovered ? BG_HOVER : BG_NORMAL);
            g.fill(x, y, x + CARD_W, y + CARD_H, bg);
            g.drawCenteredString(font, truncate(role.getName().getString(), CARD_W - 12),
                    x + CARD_W / 2, y + (CARD_H - 9) / 2, unusable ? TEXT_DISABLED : TEXT_NORMAL);
            if (claimed) {
                // 右上角标记，一眼看出"这个名额已经满了"
                g.drawString(font, "已满", x + CARD_W - font.width("已满") - 4, y + 4,
                        TEXT_CLAIMED, false);
            } else if (tooFewPlayers) {
                g.drawString(font, "人数不足", x + CARD_W - font.width("人数不足") - 4, y + 4,
                        TEXT_CLAIMED, false);
            }
            if (unusable && hovered) {
                Component tip;
                if (disabled) {
                    tip = Component.literal("该职业已在本局禁用")
                            .append("\n")
                            .append(Component.literal("§7无法使用自选职业卡（卡牌不会被消耗）"));
                } else if (tooFewPlayers) {
                    tip = Component.literal("人数不够，本局不会刷新该职业")
                            .append("\n")
                            .append(Component.literal("§7需要 " + io.wifi.starrailexpress.progression.SelfSelectGate
                                    .describeRequirement(role)))
                            .append("\n")
                            .append(Component.literal("§7（卡牌不会被消耗）"));
                } else {
                    tip = Component.literal("该职业本局名额已满")
                            .append("\n")
                            .append(Component.literal("§7上限 " + claimCapacity(role)
                                    + " 人（卡牌不会被消耗）"));
                }
                g.renderTooltip(font, tip, mouseX, mouseY);
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

    /** 搜索框：点击聚焦后可直接输入，支持中文名 / 职业 id / 拼音 */
    private void renderSearchBox(GuiGraphics g, int mouseX, int mouseY) {
        int sx = searchX();
        int sw = searchW();
        boolean hovered = isInSearchBox(mouseX, mouseY);
        int border = searchFocused ? 0xFF4488FF : (hovered ? 0xFF888888 : 0xFF555555);
        g.fill(sx, SEARCH_TOP, sx + sw, SEARCH_TOP + SEARCH_H, 0xC0101420);
        g.renderOutline(sx, SEARCH_TOP, sw, SEARCH_H, border);

        String shown = searchText.isEmpty() ? "搜索职业…" : searchText;
        int color = searchText.isEmpty() ? 0xFF777777 : 0xFFFFFFFF;
        g.drawString(font, shown, sx + 5, SEARCH_TOP + (SEARCH_H - 8) / 2, color, false);

        // 聚焦时画一个闪烁光标
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = sx + 5 + font.width(searchText);
            g.fill(cx, SEARCH_TOP + 3, cx + 1, SEARCH_TOP + SEARCH_H - 3, 0xFFFFFFFF);
        }

        // 右侧显示命中数量，方便判断是否搜到
        if (!searchText.isEmpty()) {
            String cnt = selectableRoles.size() + " 项";
            g.drawString(font, cnt, sx + sw - 4 - font.width(cnt), SEARCH_TOP + (SEARCH_H - 8) / 2,
                    0xFF88CC88, false);
        }
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // 搜索框最先处理：点它只切换聚焦，不要穿透到下面的技能格
        if (selectedFaction >= 0 && isInSearchBox(mouseX, mouseY)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
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
                    // 本局被禁用 / 已被别人占走 / 人数不够：不发送命令、不消耗卡牌
                    // 地图不在这里判 —— 选卡时地图还没投票，由服务端开局复查
                    if (isDisabled(role) || isClaimedByOther(role) || isPlayerCountGated(role)) {
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

    /** 搜索框聚焦时吞掉字符输入；Esc 先退出搜索，再退出界面 */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused && selectedFaction >= 0 && !Character.isISOControl(codePoint)) {
            searchText += codePoint;
            applySearchFilter();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused && selectedFaction >= 0) {
            switch (keyCode) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!searchText.isEmpty()) {
                        searchText = searchText.substring(0, searchText.length() - 1);
                        applySearchFilter();
                    }
                    return true;
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> {
                    // 第一下先退出搜索框，不直接关界面，避免误触关掉
                    searchFocused = false;
                    return true;
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE -> {
                    searchText = "";
                    applySearchFilter();
                    return true;
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> {
                    searchFocused = false;
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        searchText = "";
        searchFocused = true; // 进阵营后直接把焦点给搜索框，方便立刻输入
        allFactionRoles.clear();
        selectableRoles.clear();
        disabledRoleIds.clear();
        for (SRERole role : TMMRoles.ROLES.values()) {
            // 彩蛋职业（芙兰朵露 / 迪奥…）与特殊地图职业（雪原猎手 / 重刑犯…）都允许直选，
            // 因此这里用 SelfSelectGate 判定，而不是直接用 isSelfSelectableRole。
            // 只用「职业本身是否允许自选」这一层来过滤列表；人数/地图这类**局内条件**
            // 不在这里剔除，而是显示出来并置灰，让玩家知道"这个职业存在但本局用不了"。
            if (denyReason(role) == io.wifi.starrailexpress.progression.SelfSelectGate.Deny.NOT_SELECTABLE) {
                continue;
            }
            if (roleFactionType(role) != factionType) continue;
            allFactionRoles.add(role);
            // 每帧都查会反复扫描禁用列表，这里在打开阵营时缓存一次。
            // 判定复用角色介绍界面同一套 SREDisableManager，保证「显示为禁用」和「不可选」一致。
            if (SREDisableManager.isRoleDisabled(role)) {
                disabledRoleIds.add(role.identifier().toString());
            }
        }
        allFactionRoles.sort((a, b) -> a.getName().getString().compareTo(b.getName().getString()));
        applySearchFilter();
    }

    /**
     * 按 {@link #searchText} 过滤出要显示的职业。
     * <p>匹配中文名、职业 id，以及中文名的拼音（复用角色介绍界面同款 {@code PinYinUtils}），
     * 这样中文名打不出字也能用拼音找。
     */
    private void applySearchFilter() {
        selectableRoles.clear();
        String q = searchText == null ? "" : searchText.trim();
        for (SRERole role : allFactionRoles) {
            if (q.isEmpty()) {
                selectableRoles.add(role);
                continue;
            }
            String name = role.getName().getString();
            if (name.toLowerCase().contains(q.toLowerCase())
                    || role.identifier().toString().toLowerCase().contains(q.toLowerCase())
                    || PinYinUtils.contains(q, name)) {
                selectableRoles.add(role);
            }
        }
        scrollOffset = 0;
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

    /** 该职业本局名额是否已满（按 ROLE_MAX；自己已占的不算冲突） */
    private boolean isClaimedByOther(SRERole role) {
        if (minecraft == null || minecraft.player == null || role == null) {
            return false;
        }
        try {
            return Harpymodloader.isRoleOverflow(role, minecraft.player.getUUID());
        } catch (Throwable t) {
            // 客户端拿不到服务端占位表时不要报错，交给服务端裁决
            return false;
        }
    }

    /** 客户端能拿到的在线玩家列表（人数门槛判定用） */
    private java.util.List<net.minecraft.server.level.ServerPlayer> clientPlayers() {
        // 客户端没有 ServerPlayer 列表，人数改用 knownPlayerCount() 折算，
        // 真实裁决始终在服务端，这里只用于置灰提示。
        return java.util.List.of();
    }

    /**
     * 客户端侧的可选性判断。
     *
     * <p><b>不判地图</b>：流程是「先选职业卡，再投票选地图」，选卡时还不知道地图，
     * 所以特殊地图职业一律显示为可选。地图不匹配的情况由服务端在**开局时**复查，
     * 不生效会自动退还自选卡。
     *
     * <p>人数门槛同样只在"已知人数"时生效，拿不到就不拦，交给服务端裁决。
     */
    private io.wifi.starrailexpress.progression.SelfSelectGate.Deny denyReason(SRERole role) {
        // 「必须由别的职业产生」的职业（蜂后召唤的工蜂/马蜂、教父的家族成员、
        // 猫娘杀手、操纵师…）先拦下 —— 与彩蛋例外无关
        if (io.wifi.starrailexpress.progression.SelfSelectGate.isGeneratedOnly(role)) {
            return io.wifi.starrailexpress.progression.SelfSelectGate.Deny.NOT_SELECTABLE;
        }
        boolean special = role instanceof io.wifi.starrailexpress.api.EggRole || role.isSpecialMapRole();
        if (!special && !TMMRoles.isSelfSelectableRole(role)) {
            return io.wifi.starrailexpress.progression.SelfSelectGate.Deny.NOT_SELECTABLE;
        }
        int min = io.wifi.starrailexpress.progression.SelfSelectGate.minPlayers(role);
        int online = knownPlayerCount();
        if (min > 0 && online > 0 && online < min) {
            return io.wifi.starrailexpress.progression.SelfSelectGate.Deny.PLAYER_COUNT;
        }
        // 地图：选卡阶段还不知道（要等投票），故意不判
        return io.wifi.starrailexpress.progression.SelfSelectGate.Deny.NONE;
    }

    /** 该职业是否因为「人数不够」而此刻不可选 */
    private boolean isPlayerCountGated(SRERole role) {
        return denyReason(role) == io.wifi.starrailexpress.progression.SelfSelectGate.Deny.PLAYER_COUNT;
    }

    /** 已知的在线人数（取 Tab 列表，最接近真实对局人数）；未知返回 0 */
    private int knownPlayerCount() {
        try {
            if (minecraft != null && minecraft.getConnection() != null) {
                return minecraft.getConnection().getOnlinePlayers().size();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** 该职业的本局名额上限（ROLE_MAX，默认 1），用于悬停提示 */
    private int claimCapacity(SRERole role) {
        try {
            return Harpymodloader.getRoleCapacity(role);
        } catch (Throwable t) {
            return 1;
        }
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
