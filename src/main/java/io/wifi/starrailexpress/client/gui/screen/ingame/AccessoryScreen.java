package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.UpdateSkinSelectedPayload;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.client.render.entity.HatFeatureRenderer;
import io.wifi.starrailexpress.client.render.entity.HatSkinRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.cs2.CS2SkinInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 饰品界面（从主菜单"饰品"按钮进入），延续主菜单视觉风格。
 * <p>
 * 打开时：人物预览模块先从主菜单位置平滑滑动到位，随后镜头平滑推近到人物手部特写；
 * 点击右侧网格中的饰品（每行 3 个，可滚动），人物手里会拿着它查看（关闭界面自动还原主手）。
 * 皮肤数据与 CS2 仓库同源（CS2InventoryComponent.getSkins()）。
 * </p>
 */
public class AccessoryScreen extends Screen {

    // 与主菜单同源的配色
    private static final int BG_COLOR = LobbyUiTheme.BACKGROUND;
    private static final int PANEL_COLOR = LobbyUiTheme.PANEL;
    private static final int ACCENT = LobbyUiTheme.ACCENT;
    private static final int TEXT_COLOR = LobbyUiTheme.TEXT;
    private static final int TEXT_DIM = LobbyUiTheme.TEXT_DIM;
    private static final int ROW_BG = LobbyUiTheme.CARD;
    private static final int ROW_HOVER = LobbyUiTheme.CARD_HOVER;

    // 白色标题栏/描边 + 深色标题文字（与主菜单统一）
    private static final int BORDER_COLOR = LobbyUiTheme.BORDER;
    private static final int HEADER_TEXT = LobbyUiTheme.TEXT;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // 品质颜色（与仓库一致）
    private static final int[] QUALITY_COLORS = {
            0xFFEEEEEE, 0xFF33FF55, 0xFFAAAAFF, 0xFFAA55FF, 0xFFFFAA55, 0xFFFF3F3F,
            0xFF8B7A2E,
    };

    // 双形态特别皮肤的内部切换形态（暗星/圣宣/坚守者，非独立皮肤），不在饰品中展示
    private static final Set<String> EXCLUDED_FORM_SKINS = Set.of(
            "knife/knife_anxing_1", "knife/knife_anxing_2",
            "knife/knife_emozhidao_2",
            "revolver/revolver_shengxuan_1", "revolver/revolver_shengxuan_2",
            "revolver/revolver_jianshouzhe_1", "revolver/revolver_jianshouzhe_2");

    // 指定不在饰品预览中展示的玩偶帽（阿琼 / liyu / 凉介 / 皓子：其玩偶方块不在 noellesroles 命名空间，通用玩偶帽过滤未命中）
    private static final Set<String> EXCLUDED_PREVIEW_HATS = Set.of(
            "hat/hat_aqiong", "hat/hat_liyu", "hat/hat_liangjie", "hat/hat_haozi");

    private static final int COLS = 3;
    private static final int CELL_GAP = 6;
    private static final int SCROLLBAR_W = 6;

    // 分类标签（索引 0 = 全部）
    private static final String[] CATEGORY_LABELS = {"全部", "手雷", "刀", "手枪", "帽子", "球棒", "弓"};
    private int category = 0;
    // 竖排分类模式：点"↕ 排序"后，分类按拼音自上而下排列，横向空间不足也能容纳全部分类
    private boolean sortVertical = false;
    private static final int TABCOL_W = 44;
    private int baseListX;
    private static final java.text.Collator CATEGORY_COLLATOR =
            java.text.Collator.getInstance(java.util.Locale.CHINESE);

    private final Screen parent;
    /** 进入界面时要自动选中并预览的皮肤 ID（type/skinName），可为 null；用于抽奖预览等外部入口 */
    private final String initialSkinId;

    // 面板与预览模块布局
    private int panelX, panelY, panelW, panelH;
    private int startPreviewX, startPreviewY;
    private int endPreviewX, endPreviewY, previewW, previewH;

    // 滑动动画：anim 0~1，前半段从主菜单位置滑到位
    private float anim = 0F;
    // 取景：三个量各自平滑追踪目标（zoom=放大程度, yOff=垂直取景中心, xOff=水平像素偏移）
    // zoom: 0=全身, 1=特写放大
    private float zoom = 0F;
    private float zoomTarget = 0F;
    // 取景垂直中心：值越大越靠上。0.0625=全身、0.12≈持刀/持雷手臂、0.85≈头部
    // 偏高就每次减 0.05，偏低就每次加 0.05
    private static final float FULL_Y = 0.0625F;
    private static final float HAND_Y = 0.12F;
    private static final float HEAD_Y = 0.85F;
    private float yOff = FULL_Y;
    private float yOffTarget = FULL_Y;
    // 特写时取景水平偏移（像素）：正值=画面整体右移、手（在人物右侧=画面左侧）更居中；头部居中用 0
    private static final int HAND_X = 48;
    private float xOff = 0F;
    private float xOffTarget = 0F;
    // 特写放大倍率（相对 previewH）：手部较大、头部较小；随模式平滑过渡
    // 头部特写想更小就把 HEAD_SCALE 调低，想更大就调高
    private static final float HAND_SCALE = 0.95F;
    private static final float HEAD_SCALE = 0.72F;
    private float closeScaleFactor = HAND_SCALE;
    private float closeScaleFactorTarget = HAND_SCALE;
    // 视角：仅按住鼠标拖动时旋转（虚拟鼠标坐标喂给原版旋转公式）
    private int virtMouseX, virtMouseY;
    private boolean draggingView = false;

    // 展示视角：打开界面/切换武器时，让人物从正对平滑转到左侧约 45°。
    // 原版 renderEntityInInventoryFollowsMouse 的偏航角近似 yaw = atan((mouseX-中心)/40)，
    // 故 45° 对应水平偏移 = 40 * tan(45°) ≈ 40 像素。
    private static final float TARGET_YAW_DEG = 45F;
    // 转向符号：若人物转向的是右边而不是左边，把 1 改成 -1 即可
    private static final int VIEW_SIDE = 1;
    // virtMouseX 的平滑追踪目标
    private int viewTargetX;

    // 饰品网格区
    private int listX, listY, listW, cellW, cellH;
    private int visibleRows = 1;
    // 顶部分类标签行
    private int tabRowY, tabCellW, tabCellH;
    // 滑动条
    private int barX, barTop, barHeight;
    private int rowScroll = 0;
    private boolean draggingScrollbar = false;

    // 手持展示：记录被临时替换的主手物品，关闭时还原
    private int selectedIndex = -1;
    private ItemStack originalMainhand = null;

    private record SkinEntry(String skinId, String name, int quality, boolean owned) {
    }

    private final List<SkinEntry> allSkins = new ArrayList<>();
    private final List<SkinEntry> skins = new ArrayList<>();

    public AccessoryScreen(Screen parent) {
        this(parent, null);
    }

    /** parent + 初始聚焦皮肤：打开后自动滚动到该饰品、选中并应用角色预览取景 */
    public AccessoryScreen(Screen parent, String initialSkinId) {
        super(Component.literal("饰品"));
        this.parent = parent;
        this.initialSkinId = initialSkinId;
    }

    @Override
    protected void init() {
        super.init();

        computeLayout();

        rebuildSkins();

        // 进入饰品界面：先去掉玩偶自带的帽子，只有点击帽子饰品时才戴回头顶
        HatFeatureRenderer.setPreviewHat("");

        // 起始正对人物（虚拟鼠标停在预览区中心），随后在 render 中平滑转到左 45°
        virtMouseX = endPreviewX + previewW / 2;
        virtMouseY = endPreviewY + previewH / 2;
        viewTargetX = leftYawVirtX();

        addRenderableWidget(Button.builder(Component.literal("返回"), b ->
                minecraft.setScreen(parent != null ? parent : new MenuHubScreen()))
                .pos(panelX + 6, panelY + panelH - 26).size(50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↕ 排序"), b -> toggleSort())
                .pos(panelX + 60, panelY + panelH - 26).size(52, 20).build());

        if (initialSkinId != null) {
            focusInitialSkin(initialSkinId);
        }
    }

    /** 依据是否竖排计算面板/预览/网格/标签布局；切换排序或首开时调用 */
    private void computeLayout() {
        panelW = Math.min(width - 16, 440);
        panelH = Math.min(height - 48, 190);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2 + 14;

        int pad = 8;
        previewH = panelH - pad * 2;
        previewW = (int) (previewH * 0.62);
        endPreviewX = panelX + pad;
        endPreviewY = panelY + pad;

        int menuPanelW = Math.min(width - 16, 360);
        int menuPanelH = Math.min(height - 48, 190);
        int menuPanelX = (width - menuPanelW) / 2;
        int menuPanelY = (height - menuPanelH) / 2 + 14;
        startPreviewX = menuPanelX + pad;
        startPreviewY = menuPanelY + pad;

        barX = panelX + panelW - pad - SCROLLBAR_W;
        baseListX = endPreviewX + previewW + 12;
        int totalW = barX - 6 - baseListX;

        int gridAvailH;
        if (sortVertical) {
            // 竖排：左侧一列标签，网格整体右移并占满高度（无顶部标签行）
            tabCellH = 12;
            tabCellW = TABCOL_W;
            listX = baseListX + TABCOL_W + 4;
            listW = totalW - TABCOL_W - 4;
            listY = panelY + pad;
            gridAvailH = previewH;
        } else {
            // 横排：顶部一行标签，网格在其下方
            tabRowY = panelY + pad;
            tabCellH = 12;
            tabCellW = totalW / CATEGORY_LABELS.length;
            listX = baseListX;
            listW = totalW;
            listY = tabRowY + tabCellH + 3;
            gridAvailH = previewH - (tabCellH + 3);
        }
        cellH = 48;
        visibleRows = Math.max(1, (gridAvailH + CELL_GAP) / (cellH + CELL_GAP));
        cellW = (listW - CELL_GAP * (COLS - 1)) / COLS;
        barTop = listY;
        barHeight = gridAvailH;
    }

    /** 切换横排/竖排分类，并重算布局、收敛滚动 */
    private void toggleSort() {
        sortVertical = !sortVertical;
        computeLayout();
        rowScroll = clampRowScroll(rowScroll);
    }

    /** 分类标签按中文拼音排序后的索引顺序 */
    private List<Integer> sortedCategoryOrder() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < CATEGORY_LABELS.length; i++) order.add(i);
        order.sort((a, b) -> CATEGORY_COLLATOR.compare(CATEGORY_LABELS[a], CATEGORY_LABELS[b]));
        return order;
    }

    /** 读取全部饰品目录（含未拥有）并标记拥有状态：已拥有优先、再按品质降序 */
    private void rebuildSkins() {
        allSkins.clear();
        if (minecraft.player == null) {
            applyFilter();
            return;
        }
        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(minecraft.player);

        // 已拥有集合（数量 > 0）
        Set<String> ownedIds = new HashSet<>();
        for (Map.Entry<String, Integer> entry : inv.getSkins().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) ownedIds.add(entry.getKey());
        }

        // 全量目录：遍历皮肤注册表（含帽子），未拥有也列出；
        // 帽子统一归入"帽子"分类浏览，"全部"页只显示已拥有的帽子（见 applyFilter）
        Set<String> ids = new LinkedHashSet<>();
        for (var typeEntry : ItemSkinManager.getSkins().entrySet()) {
            String itemType = typeEntry.getKey();
            for (String skinName : typeEntry.getValue().keySet()) {
                if ("default".equalsIgnoreCase(skinName)) continue;
                ids.add(itemType + "/" + skinName);
            }
        }
        ids.addAll(ownedIds);

        for (String skinId : ids) {
            if (EXCLUDED_FORM_SKINS.contains(skinId)) continue;
            if (EXCLUDED_PREVIEW_HATS.contains(skinId)) continue;
            String[] parts = skinId.split("/");
            if (parts.length < 2) continue;
            // 饰品界面不展示"玩偶帽"类别（如"残月 玩偶帽"），只保留饰品帽
            if ("hat".equals(parts[0]) && HatSkinRenderer.isPlushHatSkin(parts[1])) continue;
            allSkins.add(new SkinEntry(skinId, CS2SkinInfo.getName(skinId),
                    skinQuality(parts[0], parts[1]), ownedIds.contains(skinId)));
        }
        allSkins.sort(Comparator.comparingInt((SkinEntry s) -> s.owned() ? 0 : 1)
                .thenComparing(Comparator.comparingInt(SkinEntry::quality).reversed()));
        applyFilter();
    }

    /** 皮肤所属分类索引：1=手雷,2=刀,3=手枪,4=帽子,5=球棒,6=弓；未知前缀返回 -1（仅在"全部"显示） */
    private int categoryOf(String skinId) {
        if (skinId.startsWith("grenade/")) return 1;
        if (skinId.startsWith("knife/")) return 2;
        if (skinId.startsWith("gun/") || skinId.startsWith("revolver/")) return 3;
        if (skinId.startsWith("hat/")) return 4;
        if (skinId.startsWith("bat/")) return 5;
        if (skinId.startsWith("bow/")) return 6;
        return -1;
    }

    /** 依据当前分类从 allSkins 重建展示列表 skins */
    private void applyFilter() {
        skins.clear();
        for (SkinEntry e : allSkins) {
            int cat = categoryOf(e.skinId());
            if (category == 0) {
                // 全部：沿用原设计，未拥有的帽子不混入总览（避免刷屏），可在"帽子"分类查看
                if (cat == 4 && !e.owned()) continue;
            } else if (cat != category) {
                continue;
            }
            skins.add(e);
        }
        selectedIndex = -1; // 过滤后索引失效，清除选中高亮（手持预览保持不变）
        rowScroll = clampRowScroll(rowScroll);
    }

    private int totalRows() {
        return (skins.size() + COLS - 1) / COLS;
    }

    private int maxRowScroll() {
        return Math.max(0, totalRows() - visibleRows);
    }

    private int clampRowScroll(int v) {
        return Mth.clamp(v, 0, maxRowScroll());
    }

    private int skinQuality(String itemType, String skinName) {
        ItemSkinManager.Skin skin = ItemSkinManager.getSkinFromName(itemType, skinName);
        if (skin == null) return 0;
        int color = skin.getColor();
        for (int i = 0; i < QUALITY_COLORS.length; i++) {
            if (QUALITY_COLORS[i] == (color | 0xFF000000)) return i;
        }
        return 0;
    }

    /** 皮肤对应的基础物品图标（与仓库同款映射） */
    private ItemStack skinIcon(String skinId) {
        if (skinId.startsWith("knife/")) return TMMItems.KNIFE.getDefaultInstance();
        if (skinId.startsWith("gun/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("revolver/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("bat/")) return TMMItems.BAT.getDefaultInstance();
        if (skinId.startsWith("grenade/")) return TMMItems.GRENADE.getDefaultInstance();
        if (skinId.startsWith("bow/")) return TMMItems.BOW.getDefaultInstance();
        if (skinId.startsWith("crossbow/")) return TMMItems.CROSSBOW.getDefaultInstance();
        if (skinId.startsWith("hat/")) {
            ItemStack icon = CS2SkinInfo.getIconStack(skinId);
            return icon != null ? icon : new ItemStack(Items.LEATHER_HELMET);
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 动画推进：前半段滑动到位；取景（放大/垂直中心/水平偏移）各自平滑追踪目标
        anim = Math.min(1F, anim + 0.04F);
        float slideT = easeOut(Mth.clamp(anim / 0.5F, 0F, 1F));
        zoom += (zoomTarget - zoom) * 0.12F;
        yOff += (yOffTarget - yOff) * 0.12F;
        xOff += (xOffTarget - xOff) * 0.12F;
        closeScaleFactor += (closeScaleFactorTarget - closeScaleFactor) * 0.12F;
        if (Math.abs(zoomTarget - zoom) < 0.001F) zoom = zoomTarget;
        if (Math.abs(yOffTarget - yOff) < 0.001F) yOff = yOffTarget;
        if (Math.abs(xOffTarget - xOff) < 0.001F) xOff = xOffTarget;
        if (Math.abs(closeScaleFactorTarget - closeScaleFactor) < 0.001F) closeScaleFactor = closeScaleFactorTarget;
        float zoomT = easeOut(zoom);

        // 视角平滑：非拖拽时让 virtMouseX 追向目标（打开/切换武器会重置目标）
        if (!draggingView) {
            int dv = viewTargetX - virtMouseX;
            virtMouseX = Math.abs(dv) <= 1 ? viewTargetX : virtMouseX + Math.round(dv * 0.12F);
        }

        int px = Mth.lerpInt(slideT, startPreviewX, endPreviewX);
        int py = Mth.lerpInt(slideT, startPreviewY, endPreviewY);

        LobbySubscreenRenderer.background(graphics, width, height);

        int headerH = 24;
        LobbySubscreenRenderer.panel(graphics, panelX, panelY - headerH, panelW, panelH + headerH);
        LobbySubscreenRenderer.header(graphics, font, panelX, panelY - headerH, panelW, "饰品工坊", null);
        String timeStr = LocalTime.now().format(TIME_FMT);
        graphics.drawString(font, timeStr, panelX + panelW - font.width(timeStr) - 12, panelY - headerH + 10, TEXT_DIM);

        LobbySubscreenRenderer.section(graphics, font, panelX + 8, panelY + 36, previewW, "装备预览");
        LobbySubscreenRenderer.section(graphics, font, listX, panelY + 36, listW, "饰品目录");

        // 预览区使用无贴图深色卡片，避免旧的蓝白场景感压过角色模型。
        LobbySubscreenRenderer.card(graphics, px, py, previewW, previewH, true, false);
        graphics.fill(px + 3, py + 3, px + previewW - 3, py + 4, 0xAA8B6914);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 人物模型：取景平滑追踪目标（刀/手雷=手部特写，手枪=头部特写，其他=全身）
        // 渲染前后锁定头部独立旋转，防止头跟着任何外部视角自己转
        if (minecraft.player != null) {
            var player = minecraft.player;
            float savedHeadYaw = player.yHeadRot;
            float savedHeadYawO = player.yHeadRotO;
            player.yHeadRot = player.yBodyRot;
            player.yHeadRotO = player.yBodyRotO;

            graphics.pose().pushPose();
            graphics.pose().translate(xOff, 0, 300F);
            int fullScale = Math.max(previewH / 5, 20);
            int closeScale = Math.max((int) (previewH * closeScaleFactor), fullScale);
            int modelScale = Mth.lerpInt(zoomT, fullScale, closeScale);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, px, py, px + previewW, py + previewH,
                    modelScale, yOff, virtMouseX, virtMouseY, player);
            graphics.pose().popPose();

            player.yHeadRot = savedHeadYaw;
            player.yHeadRotO = savedHeadYawO;
        }

        renderTabs(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);
        renderScrollbar(graphics, mouseX, mouseY);
    }

    // 自绘界面已铺满整屏底色；屏蔽原版 renderBackground，避免背景模糊与泥土遮罩盖在上面发糊。
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private static float easeOut(float v) {
        float inv = 1F - v;
        return 1F - inv * inv * inv;
    }

    /** 预览区中心 + 使偏航角约等于 TARGET_YAW_DEG 的水平像素偏移（yaw≈atan(offset/40)）。 */
    private int leftYawVirtX() {
        int centerX = endPreviewX + previewW / 2;
        int offset = (int) Math.round(40.0D * Math.tan(Math.toRadians(TARGET_YAW_DEG)));
        return centerX + VIEW_SIDE * offset;
    }

    /** 顶部分类标签：横排一行 或 竖排一列（按拼音排序，点"↕ 排序"切换） */
    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sortVertical) {
            List<Integer> order = sortedCategoryOrder();
            for (int k = 0; k < order.size(); k++) {
                int idx = order.get(k);
                int y = listY + k * (tabCellH + 1);
                boolean sel = category == idx;
                boolean hov = mouseX >= baseListX && mouseX < baseListX + TABCOL_W
                        && mouseY >= y && mouseY < y + tabCellH;
                LobbySubscreenRenderer.card(graphics, baseListX, y, TABCOL_W, tabCellH, sel, hov);
                graphics.drawCenteredString(font, CATEGORY_LABELS[idx],
                        baseListX + TABCOL_W / 2, y + (tabCellH - font.lineHeight) / 2, TEXT_COLOR);
            }
            return;
        }
        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            int x = listX + i * tabCellW;
            int w = (i == CATEGORY_LABELS.length - 1) ? listW - i * tabCellW : tabCellW - 1;
            boolean sel = category == i;
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= tabRowY && mouseY < tabRowY + tabCellH;
            LobbySubscreenRenderer.card(graphics, x, tabRowY, w, tabCellH, sel, hov);
            graphics.drawCenteredString(font, CATEGORY_LABELS[i],
                    x + w / 2, tabRowY + (tabCellH - font.lineHeight) / 2, TEXT_COLOR);
        }
    }

    /** 预览模块右边：每行 3 个饰品，可滚动多行 */
    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        if (skins.isEmpty()) {
            graphics.drawCenteredString(font, "暂无饰品", listX + listW / 2, listY + cellH, TEXT_DIM);
            return;
        }
        for (int r = 0; r < visibleRows; r++) {
            int row = rowScroll + r;
            for (int c = 0; c < COLS; c++) {
                int idx = row * COLS + c;
                if (idx >= skins.size()) break;
                SkinEntry e = skins.get(idx);
                int x = listX + c * (cellW + CELL_GAP);
                int y = listY + r * (cellH + CELL_GAP);
                int qColor = QUALITY_COLORS[Math.min(e.quality(), QUALITY_COLORS.length - 1)];

                boolean hovered = mouseX >= x && mouseX < x + cellW
                        && mouseY >= y && mouseY < y + cellH;
                LobbySubscreenRenderer.card(graphics, x, y, cellW, cellH, idx == selectedIndex, hovered);
                graphics.fill(x, y, x + 2, y + cellH, qColor);
                graphics.fill(x + 2, y, x + 3, y + cellH, qColor);

                // 物品图标（顶部居中）
                ItemStack icon = skinIcon(e.skinId());
                if (icon != null) {
                    String[] parts = e.skinId().split("/");
                    if (parts.length >= 2) {
                        icon.set(SREDataComponentTypes.SKIN, parts[1]);
                    }
                    graphics.renderFakeItem(icon, x + cellW / 2 - 8, y + 6);
                }

                // 名称（底部居中，超长截断）
                String name = e.name();
                while (name.length() > 1 && font.width(name + "..") > cellW - 6) {
                    name = name.substring(0, name.length() - 1);
                }
                boolean truncated = !name.equals(e.name());
                boolean equipped = isEquipped(e);
                // 已装备：名称上移一行，底部留出绿色"已装备"文字
                int nameY = equipped ? y + cellH - 23 : y + cellH - 14;
                graphics.drawCenteredString(font, truncated ? name + ".." : name,
                        x + cellW / 2, nameY, qColor);
                if (equipped) {
                    graphics.drawCenteredString(font, "已装备",
                            x + cellW / 2, y + cellH - 12, 0xFF55FF55);
                }

                // 未拥有：整格压暗变灰，右下角红字"未拥有"
                if (!e.owned()) {
                    graphics.fill(x, y, x + cellW, y + cellH, 0x80202830);
                    String lockText = "未拥有";
                    graphics.drawString(font, lockText,
                            x + cellW - font.width(lockText) - 2,
                            y + cellH - font.lineHeight - 2, 0xFFFF5555);
                }
            }
        }
    }

    /** 最右侧滑动条（按行滚动） */
    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (maxRowScroll() <= 0) return;
        graphics.fill(barX, barTop, barX + SCROLLBAR_W, barTop + barHeight, 0x60000000);
        int thumbH = Math.max(16, barHeight * visibleRows / Math.max(1, totalRows()));
        int travel = barHeight - thumbH;
        int thumbY = barTop + travel * rowScroll / maxRowScroll();
        boolean hover = mouseX >= barX && mouseX <= barX + SCROLLBAR_W + 2
                && mouseY >= barTop && mouseY <= barTop + barHeight;
        graphics.fill(barX, thumbY, barX + SCROLLBAR_W, thumbY + thumbH,
                draggingScrollbar || hover ? ACCENT : 0xFF8899BB);
    }

    private void dragBarTo(double mouseY) {
        int thumbH = Math.max(16, barHeight * visibleRows / Math.max(1, totalRows()));
        int travel = Math.max(1, barHeight - thumbH);
        double ratio = (mouseY - barTop) / (double) travel;
        rowScroll = clampRowScroll((int) Math.round(ratio * maxRowScroll()));
    }

    private boolean overThumb(double mouseX, double mouseY) {
        if (maxRowScroll() <= 0) return false;
        int thumbH = Math.max(16, barHeight * visibleRows / Math.max(1, totalRows()));
        int travel = barHeight - thumbH;
        int thumbY = barTop + travel * rowScroll / maxRowScroll();
        return mouseX >= barX - 2 && mouseX <= barX + SCROLLBAR_W + 2
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overThumb(mouseX, mouseY)) {
            draggingScrollbar = true;
            return true;
        }
        // 点击分类标签
        if (sortVertical) {
            if (mouseX >= baseListX && mouseX < baseListX + TABCOL_W && mouseY >= listY) {
                List<Integer> order = sortedCategoryOrder();
                for (int k = 0; k < order.size(); k++) {
                    int y = listY + k * (tabCellH + 1);
                    if (mouseY >= y && mouseY < y + tabCellH) {
                        int idx = order.get(k);
                        if (category != idx) {
                            category = idx;
                            applyFilter();
                        }
                        return true;
                    }
                }
            }
        } else if (mouseY >= tabRowY && mouseY < tabRowY + tabCellH) {
            for (int i = 0; i < CATEGORY_LABELS.length; i++) {
                int x = listX + i * tabCellW;
                int w = (i == CATEGORY_LABELS.length - 1) ? listW - i * tabCellW : tabCellW - 1;
                if (mouseX >= x && mouseX < x + w) {
                    if (category != i) {
                        category = i;
                        applyFilter();
                    }
                    return true;
                }
            }
        }
        // 点击饰品格：左键临时预览（拿到手上查看），右键真正装备到身上
        for (int r = 0; r < visibleRows; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (rowScroll + r) * COLS + c;
                if (idx >= skins.size()) break;
                int x = listX + c * (cellW + CELL_GAP);
                int y = listY + r * (cellH + CELL_GAP);
                if (mouseX >= x && mouseX < x + cellW && mouseY >= y && mouseY < y + cellH) {
                    SkinEntry entry = skins.get(idx);
                    if (button == 1) {
                        // 右键：已拥有的皮肤真正装备（持久化），同时预览
                        if (entry.owned()) {
                            equipSkin(entry);
                        }
                        selectSkin(idx);
                    } else if (button == 0) {
                        // 左键：仅临时预览，不涉及持久化
                        selectSkin(idx);
                    }
                    return true;
                }
            }
        }
        // 在预览区内按下：开始拖转视角（避开左下角"返回"按钮）
        boolean inPreview = mouseX >= endPreviewX && mouseX < endPreviewX + previewW
                && mouseY >= endPreviewY && mouseY < endPreviewY + previewH;
        boolean inBackBtn = mouseX >= panelX + 6 && mouseX <= panelX + 56
                && mouseY >= panelY + panelH - 26 && mouseY <= panelY + panelH - 6;
        if (button == 0 && inPreview && !inBackBtn) {
            draggingView = true;
            virtMouseX = (int) mouseX;
            virtMouseY = (int) mouseY;
            viewTargetX = virtMouseX;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 真正装备某个皮肤：本地客户端组件立即生效并发送网络包到服务端持久化（与皮肤管理界面同款模式） */
    private void equipSkin(SkinEntry entry) {
        if (minecraft.player == null) return;
        String[] parts = entry.skinId().split("/");
        if (parts.length < 2) return;
        String itemType = parts[0];
        String skinName = parts[1];
        SREPlayerSkinsComponent.KEY.get(minecraft.player).setEquippedSkinForItemType(itemType, skinName);
        ClientPlayNetworking.send(new UpdateSkinSelectedPayload(itemType, skinName));
    }

    /** 判断某饰品是否为玩家当前已装备的皮肤（按物品类型读取装备记录比对皮肤名） */
    private boolean isEquipped(SkinEntry e) {
        if (minecraft.player == null) return false;
        String[] parts = e.skinId().split("/");
        if (parts.length < 2) return false;
        String equipped = SREPlayerSkinsComponent.KEY.get(minecraft.player).getEquippedSkin(parts[0]);
        return equipped != null && equipped.equalsIgnoreCase(parts[1]);
    }

    /** 选中某个饰品：帽子→戴在头上预览，其他→临时替换主手物品让人物拿着查看，并按类型平滑切换取景
     *  （刀/手雷=手部特写，手枪/球棒=头部特写，其他=全身）。 */
    private void selectSkin(int idx) {
        if (minecraft.player == null || idx < 0 || idx >= skins.size()) return;
        SkinEntry entry = skins.get(idx);
        // 未拥有的饰品也允许在人物模型处"试用"：仅本地临时预览，
        // 关闭界面即还原，不涉及真正的装备/持久化。
        selectedIndex = idx;
        if (originalMainhand == null) {
            originalMainhand = minecraft.player.getMainHandItem().copy();
        }
        String skinId = entry.skinId();
        String[] parts = skinId.split("/");
        // 取景目标：刀/手雷→手部特写；手枪/球棒→头部特写；其他（含帽子）→全身
        if (skinId.startsWith("knife/") || skinId.startsWith("grenade/")) {
            zoomTarget = 1F;
            yOffTarget = HAND_Y;
            xOffTarget = HAND_X;
            closeScaleFactorTarget = HAND_SCALE;
        } else if (skinId.startsWith("gun/") || skinId.startsWith("revolver/") || skinId.startsWith("bat/")) {
            zoomTarget = 1F;
            yOffTarget = HEAD_Y;
            xOffTarget = 0F;
            closeScaleFactorTarget = HEAD_SCALE;
        } else {
            zoomTarget = 0F;
            yOffTarget = FULL_Y;
            xOffTarget = 0F;
            closeScaleFactorTarget = HAND_SCALE;
        }
        // 切换展示时：视角重新从正对平滑转到左 45°
        virtMouseX = endPreviewX + previewW / 2;
        viewTargetX = leftYawVirtX();

        if (skinId.startsWith("hat/")) {
            // 帽子：戴在头上（本地预览覆盖），手里还原为原本物品，不在手中展示
            HatFeatureRenderer.setPreviewHat(parts.length >= 2 ? parts[1] : "");
            minecraft.player.setItemSlot(EquipmentSlot.MAINHAND, originalMainhand.copy());
            return;
        }
        // 非帽子：取消帽子预览覆盖（保留玩家真实帽子），把饰品拿到手上展示
        HatFeatureRenderer.setPreviewHat(null);
        ItemStack icon = skinIcon(skinId);
        if (icon != null) {
            if (parts.length >= 2) {
                icon.set(SREDataComponentTypes.SKIN, parts[1]);
            }
            minecraft.player.setItemSlot(EquipmentSlot.MAINHAND, icon);
        } else if (originalMainhand != null) {
            minecraft.player.setItemSlot(EquipmentSlot.MAINHAND, originalMainhand.copy());
        }
    }

    /** 还原被临时替换的主手物品（关闭/切换界面时都会触发） */
    private void restoreHeldItem() {
        if (minecraft.player != null && originalMainhand != null) {
            minecraft.player.setItemSlot(EquipmentSlot.MAINHAND, originalMainhand);
        }
        originalMainhand = null;
    }

    @Override
    public void removed() {
        restoreHeldItem();
        HatFeatureRenderer.setPreviewHat(null); // 取消帽子预览覆盖，恢复玩家真实帽子
        super.removed();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        draggingView = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingView) {
            virtMouseX = (int) mouseX;
            virtMouseY = (int) mouseY;
            viewTargetX = virtMouseX;
            return true;
        }
        if (draggingScrollbar) {
            dragBarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxRowScroll() > 0) {
            rowScroll = clampRowScroll(rowScroll + (verticalAmount > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 定位到指定皮肤：切到"全部"分类，滚动到其所在行并选中预览 */
    private void focusInitialSkin(String skinId) {
        category = 0;
        applyFilter();
        for (int i = 0; i < skins.size(); i++) {
            if (skins.get(i).skinId().equals(skinId)) {
                rowScroll = clampRowScroll(i / COLS - visibleRows / 2);
                selectSkin(i);
                return;
            }
        }
    }
}
