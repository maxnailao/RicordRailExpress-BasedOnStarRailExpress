package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.event.EventEntry;
import io.wifi.starrailexpress.event.client.EventClient;
import io.wifi.starrailexpress.event.network.EventAdminAddC2SPayload;
import io.wifi.starrailexpress.event.network.EventAdminFeatureC2SPayload;
import io.wifi.starrailexpress.event.network.EventAdminRemoveC2SPayload;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.cs2.CS2SkinInfo;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;
import io.wifi.starrailexpress.api.SRERole;

import javax.imageio.ImageIO;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动配置弹窗（管理员后台进入，服务端做 OP2 校验）。
 * 草稿全程不重建界面；奖励、条件均为可累加的构建器；日期内嵌三列滚轮浮层；皮肤奖励可搜索、可看贴图与品质色。
 */
public class EventConfigScreen extends Screen {

    private static final String[] REWARD_KEYS = {"coin", "shard", "draw"};
    private static final String[] REWARD_LABELS = {"货币", "神话碎片", "抽数", "皮肤"};
    // 条件类型（当前仅击杀；未来在数组追加即可，服务端需为每种类型加计数钩子）
    private static final String[] COND_KEYS = {"kill"};
    private static final String[] COND_LABELS = {"击杀玩家"};
    // 武器类别（与 EventManager.weaponTypeOf / EventClient.weaponDisplay 对齐）
    private static final String[] WEAPON_KEYS = {"knife", "gun", "bat", "grenade", "bow", "trident"};
    private static final String[] WEAPON_LABELS = {"刀", "枪", "棍棒", "手雷", "弓箭", "三叉戟"};
    private static final int YEAR_FROM = 2024, YEAR_TO = 2035;
    private static final int VIS = 6, ROW_H = 14;

    private static final String[] QUALITY_NAMES = {"普通", "罕见", "稀有", "史诗", "传说", "神话", "传承"};
    private static final int[] QUALITY_COLORS = {
            0xFFEEEEEE, 0xFF33FF55, 0xFFAAAAFF, 0xFFAA55FF, 0xFFFFAA55, 0xFFFF3F3F, 0xFF8B7A2E};

    private final Screen parent;
    private EditBox titleBox, contentBox, amountBox, condNumBox, searchBox;
    private Button rewardTypeBtn, condTypeBtn, startBtn, endBtn, browseBtn;

    private final List<String> draftRewards = new ArrayList<>();
    private final List<String> draftConditions = new ArrayList<>();
    private final List<String> packConds = new ArrayList<>();
    private final List<String> packRewards = new ArrayList<>();
    private int rewardType;          // 0 货币 1 碎片 2 抽数 3 皮肤
    private int condType;            // 0 击杀
    private int skinIdx;
    private String start = "", end = "";
    private byte[] imageBytes;
    private String imageId = "", imageName = "";
    private int featIdx, remIdx;
    private String feedback = "";
    private int feedbackColor = 0xFF99BBAA;

    // 日期滚轮浮层
    private int picking;
    private int selY, selM, selD, offY, offM, offD;
    private String overlayErr = "";
    private int pickX, pickY, pickW = 268, pickH = 32 + VIS * ROW_H + 26;

    // 皮肤浏览器浮层
    private boolean browsing;
    private int browseScroll;
    private int brX, brY, brW = 320, brH = 256;
    private final List<String> browseSlice = new ArrayList<>();
    private int browseListTop;

    // 条件选择浮层：0 关闭 / 1 类型列表 / 2 职业列表(可搜索) / 3 武器列表
    private int condPick;
    private int condScroll;
    private int cpX, cpY, cpW = 300, cpH = 240;
    private final List<String> condSlice = new ArrayList<>();
    // 已选条件（构造 token 用）
    private String selCondKind = "any";   // any | role | weapon
    private String selRoleId = "", selRoleName = "";
    private String selWeaponType = "", selWeaponName = "";

    // 布局坐标
    private int panelX, panelY, panelW, panelH;
    private int imgTextX, imgTextY, imgPrevX, imgPrevY;
    private int skinTextX, skinTextY;
    private int rewardListX, rewardListY, condListX, condListY;
    private int featTextX, featTextY, remTextX, remTextY;
    private int statusX, statusY;

    public EventConfigScreen(Screen parent) {
        super(Component.literal("活动配置"));
        this.parent = parent;
        EventClient.requestSync();
    }

    @Override
    protected void init() {
        super.init();
        // 面板跟随屏幕尺寸（与公告页同款），不再封顶 454x330
        int w = this.width - 16;
        int h = this.height - 40;
        int x = (this.width - w) / 2;
        int y = Math.max(4, (this.height - h) / 2 + 12);
        panelX = x; panelY = y; panelW = w; panelH = h;
        int cx = x + 10, cw = w - 20;
        int rowY = y + 24;

        titleBox = makeBox(cx, rowY, cw, "活动标题（≤30字符）", 30);
        rowY += 19;
        addRenderableWidget(Button.builder(Component.literal("导入图片 png/jpg"), b -> pickImage())
                .bounds(cx, rowY, 132, 16).build());
        imgTextX = cx + 138; imgTextY = rowY + 4;
        imgPrevX = panelX + panelW - 54; imgPrevY = rowY - 2;
        rowY += 19;
        contentBox = makeBox(cx, rowY, cw, "活动内容", 200);
        rowY += 19;
        startBtn = addRenderableWidget(Button.builder(Component.literal("开始:未设置 ▾"), b -> openPicker(1))
                .bounds(cx, rowY, 150, 16).build());
        endBtn = addRenderableWidget(Button.builder(Component.literal("结束:未设置 ▾"), b -> openPicker(2))
                .bounds(cx + 156, rowY, 150, 16).build());
        updateDateButtons();
        rowY += 19;
        rewardTypeBtn = addRenderableWidget(Button.builder(Component.literal(""), b -> {
                rewardType = (rewardType + 1) % REWARD_LABELS.length;
                updateRewardTypeBtn();
        }).bounds(cx, rowY, 104, 16).build());
        amountBox = makeBox(cx + 110, rowY, 48, "数量", 6);
        skinTextX = cx + 110; skinTextY = rowY + 4;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> stepSkin(-1))
                .bounds(cx + 266, rowY, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> stepSkin(1))
                .bounds(cx + 288, rowY, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("加入"), b -> addReward())
                .bounds(cx + 312, rowY, 48, 16).build());
        browseBtn = addRenderableWidget(Button.builder(Component.literal("浏览"), b -> openBrowser())
                .bounds(cx + 364, rowY, 46, 16).build());
        updateRewardTypeBtn();
        rowY += 19;
        rewardListX = cx; rewardListY = rowY + 2;
        addRenderableWidget(Button.builder(Component.literal("清空奖励"), b -> draftRewards.clear())
                .bounds(cx + cw - 60, rowY, 60, 16).build());
        rowY += 40;
        condTypeBtn = addRenderableWidget(Button.builder(Component.literal(""), b -> openCondType())
                .bounds(cx, rowY, 150, 16).build());
        condNumBox = makeBox(cx + 156, rowY, 40, "数值", 4);
        addRenderableWidget(Button.builder(Component.literal("加入条件"), b -> addCondition())
                .bounds(cx + 200, rowY, 60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("清空草稿"), b -> {
                draftRewards.clear();
                draftConditions.clear();
                packConds.clear();
                packRewards.clear();
        }).bounds(cx + cw - 244, rowY, 60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("加入最终条件与奖励"), b -> packNow())
                .bounds(cx + cw - 180, rowY, 116, 16).build());
        addRenderableWidget(Button.builder(Component.literal("添加活动"), b -> submitAdd())
                .bounds(cx + cw - 60, rowY, 60, 16).build());
        updateCondBtn();
        rowY += 19;
        condListX = cx; condListY = rowY + 2;
        rowY += 40;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> step(true, -1))
                .bounds(cx, rowY, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> step(true, 1))
                .bounds(cx + 22, rowY, 20, 16).build());
        featTextX = cx + 48; featTextY = rowY + 4;
        addRenderableWidget(Button.builder(Component.literal("设为大活动"), b -> sendFeature(false))
                .bounds(cx + cw - 186, rowY, 88, 16).build());
        addRenderableWidget(Button.builder(Component.literal("清空置顶"), b -> sendFeature(true))
                .bounds(cx + cw - 94, rowY, 94, 16).build());
        rowY += 19;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> step(false, -1))
                .bounds(cx, rowY, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> step(false, 1))
                .bounds(cx + 22, rowY, 20, 16).build());
        remTextX = cx + 48; remTextY = rowY + 4;
        addRenderableWidget(Button.builder(Component.literal("删除选中活动"), b -> submitRemove())
                .bounds(cx + cw - 124, rowY, 124, 16).build());
        rowY += 20;
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                .bounds(cx, rowY, 80, 16).build());
        statusX = cx + 90; statusY = rowY + 4;

        searchBox = new EditBox(this.font, 0, 0, brW - 20, 16, Component.literal("搜索皮肤"));
        searchBox.setHint(Component.literal("输入皮肤名 / id 关键词过滤").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(24);
        searchBox.setVisible(false);
        addRenderableWidget(searchBox);
    }

    private EditBox makeBox(int x, int y, int w, String hint, int maxLen) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.literal(hint));
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        box.setMaxLength(maxLen);
        addRenderableWidget(box);
        return box;
    }

    // ── 动态文案 ──

    private void updateRewardTypeBtn() {
        rewardTypeBtn.setMessage(Component.literal("奖励:" + REWARD_LABELS[rewardType] + " ▾"));
        amountBox.setVisible(rewardType != 3);
        browseBtn.visible = rewardType == 3;
    }

    private void updateCondBtn() {
        String label;
        if (selCondKind.equals("role")) label = "以" + (selRoleName.isEmpty() ? "?" : selRoleName) + "击杀";
        else if (selCondKind.equals("weapon")) label = "以" + (selWeaponName.isEmpty() ? "?" : selWeaponName) + "击杀";
        else label = "击杀任意玩家";
        condTypeBtn.setMessage(Component.literal("条件：" + label + " ▾"));
        condNumBox.setVisible(true);
    }

    private void updateDateButtons() {
        startBtn.setMessage(Component.literal("开始:" + (start.isEmpty() ? "未设置" : start) + " ▾"));
        endBtn.setMessage(Component.literal("结束:" + (end.isEmpty() ? "未设置" : end) + " ▾"));
    }

    // ── 皮肤选择 ──

    private List<String> filteredSkins() {
        String kw = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase();
        List<String> out = new ArrayList<>();
        for (String id : CS2SkinInfo.allSkinIds()) {
            if (kw.isEmpty() || id.toLowerCase().contains(kw)
                    || CS2SkinInfo.getName(id).toLowerCase().contains(kw)) {
                out.add(id);
            }
        }
        return out;
    }

    private String currentSkin() {
        List<String> f = filteredSkins();
        if (f.isEmpty()) return "";
        return f.get(Math.floorMod(skinIdx, f.size()));
    }

    private void stepSkin(int d) {
        List<String> f = filteredSkins();
        if (f.isEmpty()) return;
        skinIdx = Math.floorMod(skinIdx + d, f.size());
    }

    private static int qualityIdx(String skinId) {
        String[] p = skinId.split("/", 2);
        if (p.length < 2) return -1;
        int color = ItemSkinManager.getColorFromName(p[0], p[1]);
        if (color < 0) return -1;
        return ItemSkinManager.qualityFromColor(color);
    }

    /** 皮肤贴图：基础物品 + SKIN 组件 → 缩放渲染（与神话商店同款链路） */
    private void drawSkinIcon(GuiGraphics g, String skinId, int x, int y, int size) {
        ItemStack stack = skinBaseStack(skinId);
        if (stack == null) return;
        String[] parts = skinId.split("/", 2);
        if (parts.length >= 2) stack.set(SREDataComponentTypes.SKIN, parts[1]);
        float scale = size / 16f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderFakeItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack skinBaseStack(String skinId) {
        if (skinId == null) return null;
        if (skinId.startsWith("knife/")) return TMMItems.KNIFE.getDefaultInstance();
        if (skinId.startsWith("revolver/") || skinId.startsWith("gun/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("bat/")) return TMMItems.BAT.getDefaultInstance();
        if (skinId.startsWith("grenade/")) return TMMItems.GRENADE.getDefaultInstance();
        if (skinId.startsWith("hat/")) {
            ItemStack icon = CS2SkinInfo.getIconStack(skinId);
            return icon != null ? icon : new ItemStack(Items.LEATHER_HELMET);
        }
        if (skinId.startsWith("bow/")) return new ItemStack(Items.BOW);
        if (skinId.startsWith("crossbow/")) return new ItemStack(Items.CROSSBOW);
        return null;
    }

    private void openBrowser() {
        browsing = true;
        browseScroll = 0;
        brX = (this.width - brW) / 2;
        brY = (this.height - brH) / 2;
        searchBox.setX(brX + 10);
        searchBox.setY(brY + 28);
        searchBox.setWidth(brW - 20);
        searchBox.setVisible(true);
        this.setInitialFocus(searchBox);
        this.setFocused(searchBox);
    }

    private void closeBrowser() {
        browsing = false;
        searchBox.setVisible(false);
    }

    // ── 条件选择浮层 ──

    private void openCondType() {
        browsing = false;
        picking = 0;
        condPick = 1;
        condScroll = 0;
        searchBox.setVisible(false);
        cpX = (this.width - cpW) / 2;
        cpY = (this.height - cpH) / 2;
    }

    private void openRolePicker() {
        condPick = 2;
        condScroll = 0;
        cpX = (this.width - cpW) / 2;
        cpY = (this.height - cpH) / 2;
        searchBox.setX(cpX + 10);
        searchBox.setY(cpY + 26);
        searchBox.setWidth(cpW - 20);
        searchBox.setValue("");
        searchBox.setHint(Component.literal("输入职业名 / id 关键词过滤").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setVisible(true);
        this.setFocused(searchBox);
    }

    private void openWeaponPicker() {
        condPick = 3;
        condScroll = 0;
        searchBox.setVisible(false);
    }

    private void closeCond() {
        condPick = 0;
        searchBox.setVisible(false);
    }

    private static List<SRERole> allRoles() {
        try {
            List<SRERole> l = Noellesroles.getAllRolesSorted();
            return l == null ? new ArrayList<>() : l;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static String roleName(SRERole role) {
        try {
            String n = RoleUtils.getRoleName(role.identifier()).getString();
            return (n == null || n.isEmpty()) ? role.identifier().toString() : n;
        } catch (Throwable t) {
            try {
                return role.identifier().toString();
            } catch (Throwable t2) {
                return "?";
            }
        }
    }

    private String roleNameOfId(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                String n = RoleUtils.getRoleName(rl).getString();
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Throwable ignored) {}
        return id;
    }

    private List<SRERole> filteredRoles() {
        String kw = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase();
        List<SRERole> out = new ArrayList<>();
        for (SRERole role : allRoles()) {
            if (kw.isEmpty()
                    || role.identifier().toString().toLowerCase().contains(kw)
                    || roleName(role).toLowerCase().contains(kw)) {
                out.add(role);
            }
        }
        return out;
    }

    private void renderCondLayer(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, height, 0xF4050A12);
        cpX = (width - cpW) / 2;
        cpY = (height - cpH) / 2;
        g.fill(cpX, cpY, cpX + cpW, cpY + cpH, 0xFF18242E);
        g.renderOutline(cpX, cpY, cpW, cpH, 0xFF2E4A6E);
        condSlice.clear();

        if (condPick == 1) {
            g.drawCenteredString(font, "选择条件类型", cpX + cpW / 2, cpY + 8, 0xFFFFFFFF);
            String[] names = {"击杀任意玩家", "以职业击杀（选职业）", "以武器击杀（选武器）"};
            String[] keys = {"any", "role", "weapon"};
            for (int r = 0; r < 3; r++) {
                int ry = cpY + 30 + r * 22;
                boolean hov = my >= ry && my < ry + 20 && mx >= cpX + 6 && mx < cpX + cpW - 6;
                g.fill(cpX + 6, ry, cpX + cpW - 6, ry + 20, hov ? 0xFF203040 : 0xFF1A2430);
                g.drawString(font, names[r], cpX + 14, ry + 6, 0xFFFFFFFF, false);
                condSlice.add(keys[r]);
            }
        } else if (condPick == 3) {
            g.drawCenteredString(font, "选择武器类别", cpX + cpW / 2, cpY + 8, 0xFFFFFFFF);
            for (int r = 0; r < WEAPON_KEYS.length; r++) {
                int ry = cpY + 30 + r * 20;
                boolean hov = my >= ry && my < ry + 18 && mx >= cpX + 6 && mx < cpX + cpW - 6;
                g.fill(cpX + 6, ry, cpX + cpW - 6, ry + 18, hov ? 0xFF203040 : 0xFF1A2430);
                g.drawString(font, WEAPON_LABELS[r] + " 击杀", cpX + 14, ry + 4, 0xFFFFFFFF, false);
                condSlice.add(WEAPON_KEYS[r]);
            }
        } else {
            g.drawCenteredString(font, "选择职业（点击行选中）", cpX + cpW / 2, cpY + 8, 0xFFFFFFFF);
            searchBox.render(g, mx, my, 0);
            List<SRERole> f = filteredRoles();
            int visRows = 8, rowH = 18;
            condScroll = clamp(condScroll, 0, Math.max(0, f.size() - visRows));
            for (int r = 0; r < visRows; r++) {
                int i = condScroll + r;
                if (i >= f.size()) break;
                SRERole role = f.get(i);
                int ry = cpY + 48 + r * rowH;
                boolean hov = my >= ry && my < ry + rowH - 1 && mx >= cpX + 6 && mx < cpX + cpW - 6;
                boolean cur = role.identifier().toString().equals(selRoleId);
                g.fill(cpX + 6, ry, cpX + cpW - 6, ry + rowH - 1,
                        cur ? 0xFF2E4A6E : (hov ? 0xFF203040 : 0xFF1A2430));
                g.drawString(font, clip(roleName(role), cpW - 40), cpX + 14, ry + 4,
                        cur || hov ? 0xFFFFFFFF : 0xFFCFE8DC, false);
                condSlice.add(role.identifier().toString());
            }
            if (f.isEmpty()) g.drawString(font, "无匹配职业", cpX + 14, cpY + 54, 0xFF8899AA, false);
        }

        int btY = cpY + cpH - 20;
        int clX = cpX + cpW - 62;
        g.fill(clX, btY, clX + 52, btY + 15, 0xFF4A3030);
        g.drawCenteredString(font, "关闭", clX + 26, btY + 4, 0xFFFFFFFF);
    }

    private void condLayerClick(int mx, int my) {
        int btY = cpY + cpH - 20;
        if (my >= btY && my < btY + 15 && mx >= cpX + cpW - 62 && mx < cpX + cpW - 10) {
            closeCond();
            return;
        }
        if (condPick == 1) {
            for (int r = 0; r < 3; r++) {
                int ry = cpY + 30 + r * 22;
                if (my >= ry && my < ry + 20 && mx >= cpX + 6 && mx < cpX + cpW - 6) {
                    String k = condSlice.get(r);
                    if (k.equals("any")) { selCondKind = "any"; updateCondBtn(); closeCond(); }
                    else if (k.equals("role")) openRolePicker();
                    else openWeaponPicker();
                    return;
                }
            }
        } else if (condPick == 3) {
            for (int r = 0; r < WEAPON_KEYS.length; r++) {
                int ry = cpY + 30 + r * 20;
                if (my >= ry && my < ry + 18 && mx >= cpX + 6 && mx < cpX + cpW - 6) {
                    selCondKind = "weapon";
                    selWeaponType = WEAPON_KEYS[r];
                    selWeaponName = WEAPON_LABELS[r];
                    updateCondBtn();
                    closeCond();
                    return;
                }
            }
        } else {
            int top = cpY + 48, rowH = 18;
            int r = (my - top) / rowH;
            if (r >= 0 && r < condSlice.size() && mx >= cpX + 6 && mx < cpX + cpW - 6) {
                String id = condSlice.get(r);
                selCondKind = "role";
                selRoleId = id;
                selRoleName = roleNameOfId(id);
                updateCondBtn();
                closeCond();
            }
        }
    }

    private void step(boolean feat, int d) {
        int n = EventClient.entries().size();
        if (n == 0) return;
        if (feat) featIdx = Math.floorMod(featIdx + d, n);
        else remIdx = Math.floorMod(remIdx + d, n);
    }

    // ── 奖励构建 ──

    private void addReward() {
        if (rewardType == 3) {
            String id = currentSkin();
            if (id.isEmpty()) {
                setFeedback("没有可选皮肤", 0xFFCC4444);
                return;
            }
            draftRewards.add("skin:" + id);
        } else {
            int n = parseInt(amountBox.getValue());
            if (n < 1 || n > 99999) {
                setFeedback("请先填写正确的数量(1-99999)", 0xFFCC4444);
                return;
            }
            draftRewards.add(REWARD_KEYS[rewardType] + ":" + n);
        }
        setFeedback("已加入奖励列表（" + draftRewards.size() + " 项）", 0xFF55DD88);
    }

    // ── 条件构建（可多个，全部达成才发奖；留空 = 无条件） ──

    private void addCondition() {
        int n = parseInt(condNumBox.getValue());
        if (n < 1 || n > 999) {
            setFeedback("击杀数值须在 1-999", 0xFFCC4444);
            return;
        }
        String token;
        if (selCondKind.equals("role")) {
            if (selRoleId.isEmpty()) { setFeedback("请先点击「条件」选择职业", 0xFFCC4444); return; }
            token = "kill_role:" + selRoleId + ":" + n;
        } else if (selCondKind.equals("weapon")) {
            if (selWeaponType.isEmpty()) { setFeedback("请先点击「条件」选择武器", 0xFFCC4444); return; }
            token = "kill_weapon:" + selWeaponType + ":" + n;
        } else {
            token = "kill:" + n;
        }
        draftConditions.add(token);
        setFeedback("已加入条件：" + EventClient.displayCondition(token) + "（共 " + draftConditions.size() + " 项，需全部完成）", 0xFF55DD88);
    }

    private void packNow() {
        if (draftConditions.isEmpty() && draftRewards.isEmpty()) {
            setFeedback("当前没有可打包的条件与奖励", 0xFFCC4444);
            return;
        }
        if (draftConditions.isEmpty()) {
            setFeedback("已加入可手动领取的无条件奖励（活动详情页点“领取”）", 0xFF55DD88);
        }
        packConds.add(String.join(";", draftConditions));
        packRewards.add(String.join(";", draftRewards));
        draftConditions.clear();
        draftRewards.clear();
        setFeedback("已加入任务 " + packConds.size() + "（全部条件达成才发放该任务奖励）", 0xFF55DD88);
    }

    // ── 提交/置顶/删除 ──

    private void submitAdd() {
        String title = titleBox.getValue().trim();
        if (title.isEmpty()) {
            setFeedback("请先填写活动标题", 0xFFCC4444);
            return;
        }
        if (!start.isEmpty() && !end.isEmpty() && end.compareTo(start) <= 0) {
            setFeedback("结束日期必须晚于开始日期", 0xFFCC4444);
            return;
        }
        int nPack = Math.max(packConds.size(), packRewards.size());
        for (int i = 0; i < packConds.size(); i++) {
            if ((packConds.get(i) + "|" + packRewards.get(i)).length() > 400) {
                setFeedback("任务 " + (i + 1) + " 内容过长", 0xFFCC4444);
                return;
            }
        }
        String cond = String.join("|", packConds);
        String rewards = String.join("|", packRewards);
        if (cond.length() > 900 || rewards.length() > 1800) {
            setFeedback("任务总长过大，请精简", 0xFFCC4444);
            return;
        }
        ClientPlayNetworking.send(new EventAdminAddC2SPayload(title,
                contentBox.getValue().trim(), rewards, start, end, cond,
                imageBytes == null ? "" : imageId,
                imageBytes == null ? new byte[0] : imageBytes));
        setFeedback("已提交添加：" + title + "（" + nPack + " 个任务）", 0xFF55DD88);
        titleBox.setValue("");
        contentBox.setValue("");
        amountBox.setValue("");
        condNumBox.setValue("");
        draftRewards.clear();
        draftConditions.clear();
        packConds.clear();
        packRewards.clear();
        start = "";
        end = "";
        updateDateButtons();
        imageBytes = null;
        imageId = "";
        imageName = "";
    }

    private void sendFeature(boolean clear) {
        List<EventEntry> evs = EventClient.entries();
        if (!clear && evs.isEmpty()) {
            setFeedback("当前没有可选活动", 0xFFCC4444);
            return;
        }
        String id = clear ? "" : evs.get(Math.floorMod(featIdx, evs.size())).id();
        ClientPlayNetworking.send(new EventAdminFeatureC2SPayload(id));
        setFeedback(clear ? "已提交：清空置顶" : "已提交：设为大活动", 0xFF55DD88);
    }

    private void submitRemove() {
        List<EventEntry> evs = EventClient.entries();
        if (evs.isEmpty()) {
            setFeedback("当前没有可删除的活动", 0xFFCC4444);
            return;
        }
        EventEntry target = evs.get(Math.floorMod(remIdx, evs.size()));
        ClientPlayNetworking.send(new EventAdminRemoveC2SPayload(target.id()));
        remIdx = 0;
        setFeedback("已提交删除：" + target.title(), 0xFF55DD88);
    }

    // ── 图片导入 + 本地预览 ──

    private void pickImage() {
        try {
            File dir = getInboxDir();
            File newest = newestImage(dir);
            openInFolder(dir);
            if (newest == null) {
                setFeedback("已打开图片文件夹：把 png/jpg 放进去后，再点一次「导入图片」", 0xFFE0B060);
                return;
            }
            final File f = newest;
            final String nm = f.getName();
            Thread t = new Thread(() -> encodeAndApply(f, nm), "sre-event-image-encode");
            t.setDaemon(true);
            t.start();
        } catch (Throwable e) {
            setFeedback("导入失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()), 0xFFCC4444);
        }
    }

    /** 图片收件夹：运行目录/config/sre_event_images_inbox，不存在则创建 */
    private static File getInboxDir() {
        File dir = new File("config/sre_event_images_inbox");
        try {
            dir.mkdirs();
        } catch (Throwable ignored) {
        }
        return dir.getAbsoluteFile();
    }

    /** 收件夹内最近修改的 png/jpg（无则返回 null） */
    private static File newestImage(File dir) {
        File[] files = dir.listFiles((d, n) -> {
            String s = n.toLowerCase();
            return s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg");
        });
        if (files == null || files.length == 0) return null;
        File best = files[0];
        for (File f : files) {
            if (f.lastModified() > best.lastModified()) best = f;
        }
        return best;
    }

    /** 用系统文件管理器打开文件夹（Windows: explorer.exe） */
    private static void openInFolder(File dir) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            String path = dir.getAbsolutePath();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", path).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", path).start();
            } else {
                new ProcessBuilder("xdg-open", path).start();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 后台线程读取并压缩图片，切回客户端线程写缓存 + 预览 */
    private void encodeAndApply(File file, String chosen) {
        try {
            BufferedImage src = ImageIO.read(file);
            if (src == null) {
                runClient(() -> setFeedback("无法读取该图片（仅支持 png/jpg）", 0xFFCC4444));
                return;
            }
            int max = Math.max(src.getWidth(), src.getHeight());
            if (max > 1024) {
                double s = 1024.0 / max;
                int nw = Math.max(1, (int) (src.getWidth() * s));
                int nh = Math.max(1, (int) (src.getHeight() * s));
                BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gg = out.createGraphics();
                gg.drawImage(src.getScaledInstance(nw, nh, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
                gg.dispose();
                src = out;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(src, "png", bos);
            byte[] png = bos.toByteArray();
            String id = "evt_" + System.currentTimeMillis();
            String name = chosen + "（" + (png.length / 1024) + "KB）";
            runClient(() -> {
                imageBytes = png;
                imageId = id;
                imageName = name;
                EventClient.putLocalImage(id, png);
                setFeedback("图片已导入，右侧为预览", 0xFF55DD88);
            });
        } catch (Throwable t) {
            final String msg = t.getMessage() == null ? t.toString() : t.getMessage();
            runClient(() -> setFeedback("图片导入失败：" + msg, 0xFFCC4444));
        }
    }

    private void runClient(Runnable r) {
        if (this.minecraft != null) this.minecraft.execute(r);
    }

    // ── 日期滚轮浮层 ──

    private void openPicker(int which) {
        picking = which;
        overlayErr = "";
        browsing = false;
        searchBox.setVisible(false);
        LocalDate def = which == 1 ? LocalDate.now() : minEnd();
        LocalDate cur = parseOr(which == 1 ? start : end, def);
        if (cur == null) cur = def;
        selY = Math.max(YEAR_FROM, Math.min(YEAR_TO, cur.getYear()));
        selM = cur.getMonthValue();
        selD = Math.min(cur.getDayOfMonth(), daysIn(selY, selM));
        offY = clamp(selY - YEAR_FROM, 0, Math.max(0, (YEAR_TO - YEAR_FROM + 1) - VIS));
        offM = clamp(selM - 1, 0, Math.max(0, 12 - VIS));
        offD = clamp(selD - 1, 0, Math.max(0, daysIn(selY, selM) - VIS));
    }

    private LocalDate minEnd() {
        LocalDate s = parseOr(start, null);
        return s == null ? LocalDate.now() : s.plusDays(1);
    }

    private void applyPick() {
        selD = Math.min(selD, daysIn(selY, selM));
        String ds = String.format("%04d-%02d-%02d", selY, selM, selD);
        if (picking == 2 && !start.isEmpty() && ds.compareTo(start) <= 0) {
            overlayErr = "必须晚于开始日期";
            return;
        }
        if (picking == 1) {
            start = ds;
            if (!end.isEmpty() && end.compareTo(ds) <= 0) end = "";
        } else {
            end = ds;
        }
        updateDateButtons();
        picking = 0;
        overlayErr = "";
    }

    private int columnAt(int mx) {
        int[] xs = {pickX + 10, pickX + 104, pickX + 168};
        int[] ws = {86, 58, 58};
        for (int c = 0; c < 3; c++) {
            if (mx >= xs[c] && mx < xs[c] + ws[c]) return c;
        }
        return -1;
    }

    private void renderPicker(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xB0000000);
        pickX = (width - pickW) / 2;
        pickY = (height - pickH) / 2;
        g.fill(pickX, pickY, pickX + pickW, pickY + pickH, 0xFF18242E);
        g.renderOutline(pickX, pickY, pickW, pickH, 0xFF2E4A6E);
        g.drawCenteredString(font, picking == 1 ? "选择开始日期" : "选择结束日期",
                pickX + pickW / 2, pickY + 8, 0xFFFFFFFF);
        int top = pickY + 32;
        int[] xs = {pickX + 10, pickX + 104, pickX + 168};
        int[] ws = {86, 58, 58};
        int[] offs = {offY, offM, offD};
        int[] sels = {selY, selM, selD};
        int[] starts = {YEAR_FROM, 1, 1};
        int[] sizes = {YEAR_TO - YEAR_FROM + 1, 12, daysIn(selY, selM)};
        String[] heads = {"年", "月", "日"};
        for (int c = 0; c < 3; c++) {
            g.drawString(font, heads[c], xs[c] + ws[c] / 2 - font.width(heads[c]) / 2,
                    top - 11, 0xFF8899BB, false);
            for (int r = 0; r < VIS; r++) {
                int idx = offs[c] + r;
                if (idx >= sizes[c]) break;
                int v = starts[c] + idx;
                int ry = top + r * ROW_H;
                boolean sel = v == sels[c];
                if (sel) g.fill(xs[c], ry, xs[c] + ws[c], ry + ROW_H - 1, 0xFF2E4A6E);
                g.drawString(font, String.valueOf(v), xs[c] + 6, ry + 2,
                        sel ? 0xFFFFFFFF : 0xFF8899AA, false);
            }
        }
        g.drawString(font, String.format("%04d-%02d-%02d", selY, selM, selD),
                pickX + 10, pickY + pickH - 16, 0xFFAECBE8, false);
        if (!overlayErr.isEmpty())
            g.drawString(font, overlayErr, pickX + 90, pickY + pickH - 16, 0xFFCC4444, false);
        int btY = pickY + pickH - 20;
        int okX = pickX + pickW - 128;
        g.fill(okX, btY, okX + 60, btY + 15, 0xFF3A5A50);
        g.drawCenteredString(font, "选好了", okX + 30, btY + 4, 0xFFFFFFFF);
        int caX = pickX + pickW - 62;
        g.fill(caX, btY, caX + 52, btY + 15, 0xFF4A3030);
        g.drawCenteredString(font, "取消", caX + 26, btY + 4, 0xFFFFFFFF);
    }

    // ── 皮肤浏览器浮层 ──

    private void renderBrowser(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, height, 0xF4050A12);
        g.fill(brX, brY, brX + brW, brY + brH, 0xFF18242E);
        g.renderOutline(brX, brY, brW, brH, 0xFF2E4A6E);
        g.drawCenteredString(font, "选择皮肤（点击行选中）", brX + brW / 2, brY + 8, 0xFFFFFFFF);

        browseSlice.clear();
        browseListTop = brY + 50;
        List<String> f = filteredSkins();
        int visRows = 7, rowH = 26;
        browseScroll = clamp(browseScroll, 0, Math.max(0, f.size() - visRows));
        for (int r = 0; r < visRows; r++) {
            int i = browseScroll + r;
            if (i >= f.size()) break;
            String id = f.get(i);
            int ry = browseListTop + r * rowH;
            boolean hov = my >= ry && my < ry + rowH - 2
                    && mx >= brX + 6 && mx < brX + brW - 6;
            boolean cur = i == Math.floorMod(skinIdx, Math.max(1, f.size()));
            g.fill(brX + 6, ry, brX + brW - 6, ry + rowH - 2,
                    cur ? 0xFF2E4A6E : (hov ? 0xFF203040 : 0xFF1A2430));
            drawSkinIcon(g, id, brX + 10, ry + 3, 20);
            String name = clip(CS2SkinInfo.getName(id), brW - 150);
            g.drawString(font, name, brX + 36, ry + 2, 0xFFFFFFFF, false);
            int q = qualityIdx(id);
            String qn = (q >= 0 && q < QUALITY_NAMES.length) ? "「" + QUALITY_NAMES[q] + "」" : "「未知」";
            g.drawString(font, qn, brX + 36 + font.width(name) + 4, ry + 2,
                    q >= 0 ? QUALITY_COLORS[q] : 0xFF8899AA, false);
            g.drawString(font, clip(id, brW - 30), brX + 36, ry + 12, 0xFF7788AA, false);
            browseSlice.add(id);
        }
        if (f.isEmpty()) {
            g.drawString(font, "无匹配皮肤", brX + 14, browseListTop + 6, 0xFF8899AA, false);
        }
        g.drawString(font, "共 " + f.size() + " 款", brX + 10, brY + brH - 34, 0xFF8899AA, false);
        int btY = brY + brH - 20;
        int clX = brX + brW - 62;
        g.fill(clX, btY, clX + 52, btY + 15, 0xFF4A3030);
        g.drawCenteredString(font, "关闭", clX + 26, btY + 4, 0xFFFFFFFF);
        searchBox.render(g, mx, my, 0);
    }

    // ── 输入事件：浮层优先 ──

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (browsing) {
            if (searchBox.getX() <= mx && mx < searchBox.getX() + searchBox.getWidth()
                    && searchBox.getY() <= my && my < searchBox.getY() + searchBox.getHeight()) {
                return super.mouseClicked(mx, my, btn);   // 交给搜索框聚焦
            }
            browserClick((int) mx, (int) my);
            return true;
        }
        if (condPick > 0) {
            if (condPick == 2 && searchBox.getX() <= mx && mx < searchBox.getX() + searchBox.getWidth()
                    && searchBox.getY() <= my && my < searchBox.getY() + searchBox.getHeight()) {
                return super.mouseClicked(mx, my, btn);   // 职业搜索框聚焦
            }
            condLayerClick((int) mx, (int) my);
            return true;
        }
        if (picking > 0) {
            pickerClick((int) mx, (int) my);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void browserClick(int mx, int my) {
        int rowH = 26, visRows = 7;
        int btY = brY + brH - 20;
        if (my >= btY && my < btY + 15 && mx >= brX + brW - 62 && mx < brX + brW - 10) {
            closeBrowser();
            return;
        }
        int r = (my - browseListTop) / rowH;
        if (r >= 0 && r < browseSlice.size() && mx >= brX + 6 && mx < brX + brW - 6) {
            String id = browseSlice.get(r);
            List<String> f = filteredSkins();
            skinIdx = Math.max(0, f.indexOf(id));
            closeBrowser();
            setFeedback("已选择皮肤：" + CS2SkinInfo.getName(id), 0xFF55DD88);
        }
    }

    private void pickerClick(int mx, int my) {
        int btY = pickY + pickH - 20;
        int okX = pickX + pickW - 128;
        if (my >= btY && my < btY + 15) {
            if (mx >= okX && mx < okX + 60) {
                applyPick();
                return;
            }
            int caX = pickX + pickW - 62;
            if (mx >= caX && mx < caX + 52) {
                picking = 0;
                overlayErr = "";
                return;
            }
        }
        int top = pickY + 32;
        int r = (my - top) / ROW_H;
        if (r < 0 || r >= VIS) return;
        int c = columnAt(mx);
        if (c == 0) {
            int idx = offY + r;
            if (idx < YEAR_TO - YEAR_FROM + 1) {
                selY = YEAR_FROM + idx;
                selD = Math.min(selD, daysIn(selY, selM));
                offD = clamp(selD - 1, 0, Math.max(0, daysIn(selY, selM) - VIS));
            }
        } else if (c == 1) {
            int v = 1 + offM + r;
            if (v <= 12) {
                selM = v;
                selD = Math.min(selD, daysIn(selY, selM));
                offD = clamp(selD - 1, 0, Math.max(0, daysIn(selY, selM) - VIS));
            }
        } else if (c == 2) {
            int v = 1 + offD + r;
            if (v <= daysIn(selY, selM)) selD = v;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (browsing) {
            browseScroll += vy > 0 ? -1 : 1;
            return true;
        }
        if (condPick == 2) {
            condScroll += vy > 0 ? -1 : 1;
            return true;
        }
        if (picking > 0) {
            int dir = vy > 0 ? -1 : 1;
            int c = columnAt((int) mx);
            if (c == 0) offY = clamp(offY + dir, 0, Math.max(0, (YEAR_TO - YEAR_FROM + 1) - VIS));
            else if (c == 1) offM = clamp(offM + dir, 0, Math.max(0, 12 - VIS));
            else if (c == 2) offD = clamp(offD + dir, 0, Math.max(0, daysIn(selY, selM) - VIS));
            return true;
        }
        return super.mouseScrolled(mx, my, hx, vy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 261) {
            if (browsing) {
                closeBrowser();
                return true;
            }
            if (condPick > 0) {
                closeCond();
                return true;
            }
            if (picking > 0) {
                picking = 0;
                overlayErr = "";
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── 渲染 ──

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 空实现：阻断原版模糊采样链（与 AdminBackendScreen 同款）
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, this.width, this.height, 0xFF0C1020, 0xFF101828);
        if (browsing) {
            renderBrowser(g, mx, my);
            return;
        }
        if (condPick > 0) {
            renderCondLayer(g, mx, my);
            return;
        }
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, 0xD80C1020, 0xD8101828);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFF1E3060);
        g.drawCenteredString(this.font, this.title, panelX + panelW / 2, panelY + 6, 0xEEEEFF);

        // 当前皮肤选择（品质色文字）
        if (rewardType == 3) {
            String cur = currentSkin();
            if (cur.isEmpty()) {
                g.drawString(this.font, "无匹配皮肤", skinTextX, skinTextY, 0xFF667788, false);
            } else {
                int q = qualityIdx(cur);
                String qn = (q >= 0 && q < QUALITY_NAMES.length) ? "「" + QUALITY_NAMES[q] + "」" : "";
                String nm = clip(CS2SkinInfo.getName(cur), 120);
                g.drawString(this.font, nm, skinTextX, skinTextY,
                        q >= 0 ? QUALITY_COLORS[q] : 0xFFFFFFFF, false);
                g.drawString(this.font, qn, skinTextX + font.width(nm) + 2, skinTextY,
                        q >= 0 ? QUALITY_COLORS[q] : 0xFF8899AA, false);
            }
        }

        for (int i = 0; i < draftRewards.size() && i < 3; i++) {
            g.drawString(this.font, "· " + clip(EventClient.displayRewards(draftRewards.get(i)), panelW - 70),
                    rewardListX, rewardListY + i * 11, 0xFFCFE8DC, false);
        }
        if (draftRewards.size() > 3) {
            g.drawString(this.font, "… 共 " + draftRewards.size() + " 项",
                    rewardListX, rewardListY + 33, 0xFF8899AA, false);
        }

        // 条件预览列表
        int np = Math.max(packConds.size(), packRewards.size());
        if (np == 0) {
            g.drawString(this.font, "任务: 先加入条件/奖励，再点“加入最终条件与奖励”打包（可多个任务）",
                    condListX, condListY, 0xFF8899AA, false);
        }
        for (int i = 0; i < np && i < 3; i++) {
            String cs = i < packConds.size() ? EventClient.displayCondition(packConds.get(i)) : "无条件";
            String rs = i < packRewards.size() ? EventClient.packRewardDisplayOf(packRewards.get(i)) : "无";
            g.drawString(this.font, "· " + clip("任务" + (i + 1) + ": [" + cs + "] → " + rs, panelW - 70),
                    condListX, condListY + i * 11, 0xFFFFE0B0, false);
        }
        if (np > 3) {
            g.drawString(this.font, "… 共 " + np + " 个任务",
                    condListX, condListY + 33, 0xFF8899AA, false);
        }

        // 图片预览框
        if (!imageId.isEmpty() && imageBytes != null) {
            g.renderOutline(imgPrevX - 1, imgPrevY - 1, 46, 28, 0xFF2E4A6E);
            EventClient.drawImage(g, imageId, imgPrevX, imgPrevY, 44, 26);
        }

        List<EventEntry> evs = EventClient.entries();
        int n = evs.size();
        String featTxt = n == 0 ? "（暂无活动，等待服务端同步）"
                : "★ " + clip(evs.get(Math.floorMod(featIdx, n)).title(), Math.max(40, panelW - 270));
        g.drawString(this.font, featTxt, featTextX, featTextY,
                n == 0 ? 0xFF667788 : 0xFFFFD75E, false);
        String remTxt = n == 0 ? "（暂无活动）"
                : clip(evs.get(Math.floorMod(remIdx, n)).title(), Math.max(40, panelW - 200));
        g.drawString(this.font, remTxt, remTextX, remTextY,
                n == 0 ? 0xFF667788 : 0xFFFFFFFF, false);

        g.drawString(this.font, imageName.isEmpty() ? "未导入图片" : "图片：" + imageName,
                imgTextX, imgTextY, imageName.isEmpty() ? 0xFF667788 : 0xFF55DD88, false);
        if (!feedback.isEmpty()) {
            g.drawString(this.font, clip(feedback, panelW - 100), statusX, statusY, feedbackColor, false);
        }

        super.render(g, mx, my, pt);
        if (picking > 0) renderPicker(g);
    }

    private void setFeedback(String msg, int color) {
        feedback = msg;
        feedbackColor = color;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static LocalDate parseOr(String s, LocalDate fallback) {
        try {
            if (s == null || s.isEmpty()) return fallback;
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int daysIn(int y, int m) {
        return YearMonth.of(y, m).lengthOfMonth();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private String clip(String s, int w) {
        return this.font.plainSubstrByWidth(s, Math.max(10, w));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}