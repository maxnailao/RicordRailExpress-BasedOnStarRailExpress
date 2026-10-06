package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBox;
import io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.cs2.CS2SkinInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 皮肤预览界面
 * <p>
 * 展示全部武器皮肤 / 刀皮 / 帽子 / 音乐盒（无论是否已拥有均可预览），按品质降序排列。
 * 已拥有的项目左上角标有绿点；点击音乐盒可试听 MVP 片段；右侧详情面板放大预览选中项。
 * </p>
 */
public class SkinPreviewScreen extends Screen {

    private static final int BG_COLOR = 0xE60C1020;
    private static final int PANEL_COLOR = 0xC0161B30;
    private static final int CARD_BG = 0x30FFFFFF;
    private static final int CARD_HOVER = 0x50FFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF999999;

    private static final int[] QUALITY_COLORS = {
            0xFFEEEEEE, 0xFF33FF55, 0xFFAAAAFF, 0xFFAA55FF, 0xFFFFAA55, 0xFFFF3F3F,
    };
    private static final String[] QUALITY_NAMES = {"普通", "罕见", "稀有", "史诗", "传说", "不可思议"};

    /** 预览排除：双形态特别皮肤的内部切换变体（暗星 / 恶魔之刃举刀 / 圣宣，非独立皮肤） */
    private static final Set<String> PREVIEW_EXCLUDED_SKINS = Set.of(
            "knife/knife_anxing_1", "knife/knife_anxing_2",
            "knife/knife_emozhidao_2",
            "revolver/revolver_shengxuan_1", "revolver/revolver_shengxuan_2",
            "revolver/revolver_jianshouzhe_1", "revolver/revolver_jianshouzhe_2");

    private static class Entry {
        String type;    // "skin" | "music"
        String id;      // skinId 或 musicbox id
        String name;
        String desc;
        int quality;    // 音乐盒为 -1
        boolean owned;  // 是否已拥有（仅用于角标标记，不影响预览）
    }

    private final List<Entry> entries = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private SoundInstance currentPreviewSound = null;   // 当前试听音，用于切换/退出时停止，避免重叠

    private int cardSize = 56;
    private int cardGap = 6;
    private int cols;
    private int gridStartX = 16;
    private int gridStartY = 40;
    private int panelX;

    public SkinPreviewScreen() {
        super(Component.literal("皮肤预览"));
    }

    @Override
    protected void init() {
        super.init();
        panelX = width * 2 / 3;
        cols = (panelX - gridStartX - 16) / (cardSize + cardGap);
        if (cols < 1) cols = 1;

        addRenderableWidget(Button.builder(Component.literal("< 返回仓库"), b ->
                minecraft.setScreen(new CS2WarehouseScreen()))
                .pos(8, height - 28).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b ->
                minecraft.setScreen(null))
                .pos(width - 68, height - 28).size(60, 20).build());

        rebuildEntries();
    }

    private void rebuildEntries() {
        entries.clear();
        selectedIndex = -1;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);

        // 已拥有集合（仅用于角标标记，未拥有同样可预览）
        Set<String> ownedSkins = new HashSet<>();
        for (Map.Entry<String, Integer> e : inv.getSkins().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) ownedSkins.add(e.getKey());
        }
        Set<String> ownedMusic = new HashSet<>();
        for (Map.Entry<String, Integer> e : inv.getMusicBoxes().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) ownedMusic.add(e.getKey());
        }

        // 全量皮肤：遍历注册表，无论是否拥有都列出（玩偶帽、暗星切换形态不预览）
        for (var typeEntry : ItemSkinManager.getSkins().entrySet()) {
            String itemType = typeEntry.getKey();
            // 玩家玩偶帽整个类别不参与预览
            if ("hat".equalsIgnoreCase(itemType)) continue;
            for (var skinEntry : typeEntry.getValue().entrySet()) {
                String skinName = skinEntry.getKey();
                if ("default".equalsIgnoreCase(skinName)) continue;
                String skinId = itemType + "/" + skinName;
                // 双形态切换变体（暗星/圣宣，非独立皮肤）不预览
                if (PREVIEW_EXCLUDED_SKINS.contains(skinId)) continue;
                Entry entry = new Entry();
                entry.type = "skin";
                entry.id = skinId;
                entry.name = CS2SkinInfo.getName(skinId);
                entry.desc = CS2SkinInfo.getDescription(skinId);
                entry.quality = colorToQuality(skinEntry.getValue().getColor());
                entry.owned = ownedSkins.contains(skinId);
                entries.add(entry);
            }
        }

        // 全量音乐盒：遍历注册表，无论是否拥有都列出
        for (MusicBox box : MusicBoxRegistry.getAll()) {
            Entry entry = new Entry();
            entry.type = "music";
            entry.id = box.id();
            entry.name = box.displayName().getString();
            entry.desc = "点击试听 MVP 片段";
            entry.quality = -1;
            entry.owned = ownedMusic.contains(box.id());
            entries.add(entry);
        }

        // 按品质降序（音乐盒排在最后），同品质按类型、名称稳定排序
        entries.sort(Comparator.comparingInt((Entry en) -> en.quality).reversed()
                .thenComparing(en -> en.type)
                .thenComparing(en -> en.name));
    }

    private int colorToQuality(int rawColor) {
        int color = rawColor | 0xFF000000;
        for (int i = 0; i < QUALITY_COLORS.length; i++) {
            if (QUALITY_COLORS[i] == color) return i;
        }
        return 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG_COLOR);
        g.drawCenteredString(font, "皮肤预览（按品质排序）", width / 2, 10, TEXT_COLOR);

        renderGrid(g, mouseX, mouseY);
        renderDetailPanel(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        if (entries.isEmpty()) {
            g.drawCenteredString(font, "暂无可预览内容", gridStartX + (panelX - gridStartX) / 2,
                    height / 2, TEXT_DIM);
            return;
        }
        int hoveredIdx = -1;
        for (int i = 0; i < entries.size(); i++) {
            int idx = i - scrollOffset;
            if (idx < 0) continue;
            int row = idx / cols;
            int col = idx % cols;
            int x = gridStartX + col * (cardSize + cardGap);
            int y = gridStartY + row * (cardSize + cardGap);
            if (y + cardSize > height - 36) continue;

            boolean hovered = mouseX >= x && mouseX < x + cardSize && mouseY >= y && mouseY < y + cardSize;
            if (hovered) hoveredIdx = i;

            g.fill(x, y, x + cardSize, y + cardSize, hovered ? CARD_HOVER : CARD_BG);

            Entry en = entries.get(i);
            if (en.quality > 0 && en.quality < QUALITY_COLORS.length) {
                int c = QUALITY_COLORS[en.quality];
                g.fill(x - 1, y - 1, x + cardSize + 1, y, c);
                g.fill(x - 1, y + cardSize, x + cardSize + 1, y + cardSize + 1, c);
                g.fill(x - 1, y, x, y + cardSize, c);
                g.fill(x + cardSize, y, x + cardSize + 1, y + cardSize, c);
            }
            if (selectedIndex == i) {
                g.fill(x - 2, y - 2, x + cardSize + 2, y + cardSize + 2, 0x80FFFFFF);
            }

            renderIcon(g, en, x + (cardSize - 16) / 2, y + 8, 1.0f);

            String name = en.name;
            int maxChars = (cardSize - 8) / 6;
            if (maxChars > 3 && name.length() > maxChars) name = name.substring(0, maxChars - 2) + "..";
            g.drawCenteredString(font, name, x + cardSize / 2, y + cardSize - 12,
                    en.owned ? 0xFFDDDDDD : TEXT_DIM);
            if ("music".equals(en.type)) {
                g.drawCenteredString(font, "♪", x + cardSize - 8, y + 2, 0xFF66CCFF);
            }
            if (en.owned) {
                g.fill(x + 2, y + 2, x + 6, y + 6, 0xFF33FF55);
            }
        }
    }

    private void renderDetailPanel(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(panelX, 40, width - 8, height - 36, PANEL_COLOR);
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            g.drawCenteredString(font, "选择左侧物品预览", panelX + (width - 8 - panelX) / 2, 60, TEXT_DIM);
            return;
        }
        Entry en = entries.get(selectedIndex);
        int cx = panelX + (width - 8 - panelX) / 2;

        // 放大图标
        renderIcon(g, en, cx - 24, 70, 3.0f);

        g.drawCenteredString(font, en.name, cx, 130, TEXT_COLOR);
        String typeLabel = "music".equals(en.type) ? "音乐盒"
                : CS2SkinInfo.getItemTypeName(en.id.split("/")[0]);
        g.drawCenteredString(font, "类型: " + typeLabel, cx, 145, TEXT_DIM);
        if (en.quality >= 0 && en.quality < QUALITY_NAMES.length) {
            g.drawCenteredString(font, "品质: " + QUALITY_NAMES[en.quality], cx, 160,
                    QUALITY_COLORS[en.quality]);
        }
        g.drawCenteredString(font, en.owned ? "已拥有" : "未拥有（仅预览）", cx, 175,
                en.owned ? 0xFF33FF55 : 0xFFFF8855);
        if (en.desc != null && !en.desc.isEmpty()) {
            g.drawCenteredString(font, en.desc, cx, 190, 0xFF777777);
        }

        if ("music".equals(en.type)) {
            int btnX = cx - 50, btnY = 205;
            boolean hovered = mouseX >= btnX && mouseX < btnX + 100 && mouseY >= btnY && mouseY < btnY + 20;
            g.fill(btnX, btnY, btnX + 100, btnY + 20, hovered ? 0x804488FF : 0x604488FF);
            g.drawCenteredString(font, "♪ 试听 MVP 片段", cx, btnY + 6, TEXT_COLOR);
            previewButtonBounds = new int[]{btnX, btnY, btnX + 100, btnY + 20};
        } else {
            previewButtonBounds = null;
        }
    }

    private int[] previewButtonBounds = null;

    private void renderIcon(GuiGraphics g, Entry en, int x, int y, float scale) {
        ItemStack stack;
        if ("music".equals(en.type)) {
            stack = new ItemStack(Items.MUSIC_DISC_13);
        } else {
            stack = getSkinItemStack(en.id);
            if (stack != null && en.id.contains("/")) {
                stack.set(SREDataComponentTypes.SKIN, en.id.split("/")[1]);
            }
        }
        if (stack == null) {
            g.drawString(font, "?", x, y, TEXT_DIM, false);
            return;
        }
        if (scale == 1.0f) {
            g.renderFakeItem(stack, x, y);
        } else {
            g.pose().pushPose();
            g.pose().translate(x, y, 100);
            g.pose().scale(scale, scale, scale);
            g.renderFakeItem(stack, 0, 0);
            g.pose().popPose();
        }
    }

    private ItemStack getSkinItemStack(String skinId) {
        if (skinId == null) return null;
        if (skinId.startsWith("knife/")) return TMMItems.KNIFE.getDefaultInstance();
        if (skinId.startsWith("gun/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("revolver/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("bat/")) return TMMItems.BAT.getDefaultInstance();
        if (skinId.startsWith("grenade/")) return TMMItems.GRENADE.getDefaultInstance();
        if (skinId.startsWith("hat/")) {
            ItemStack icon = CS2SkinInfo.getIconStack(skinId);
            return icon != null ? icon : new ItemStack(Items.LEATHER_HELMET);
        }
        return null;
    }

    private void playClip(Entry en) {
        if (!"music".equals(en.type)) return;
        MusicBox box = MusicBoxRegistry.get(en.id);
        if (box == null) return;
        var mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        // 先停掉上一段试听，避免切换音乐盒时声音重叠
        stopPreview();
        currentPreviewSound = SimpleSoundInstance.forUI(box.soundEvent(), 1.0f, box.volume());
        mc.getSoundManager().play(currentPreviewSound);
    }

    /** 停止当前正在播放的试听音乐（若有）。 */
    private void stopPreview() {
        if (currentPreviewSound == null) return;
        var mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(currentPreviewSound);
        }
        currentPreviewSound = null;
    }

    @Override
    public void removed() {
        // 退出预览界面（返回仓库 / 关闭 / ESC / 打开其它界面）时停止仍在播放的试听音乐
        stopPreview();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 详情面板试听按钮
        if (previewButtonBounds != null && selectedIndex >= 0 && selectedIndex < entries.size()) {
            int[] b = previewButtonBounds;
            if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                playClip(entries.get(selectedIndex));
                return true;
            }
        }

        // 网格选择
        if (mouseX < panelX) {
            for (int i = 0; i < entries.size(); i++) {
                int idx = i - scrollOffset;
                if (idx < 0) continue;
                int row = idx / cols;
                int col = idx % cols;
                int x = gridStartX + col * (cardSize + cardGap);
                int y = gridStartY + row * (cardSize + cardGap);
                if (y + cardSize > height - 36) continue;
                if (mouseX >= x && mouseX < x + cardSize && mouseY >= y && mouseY < y + cardSize) {
                    selectedIndex = i;
                    Entry en = entries.get(i);
                    if ("music".equals(en.type)) playClip(en);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollOffset -= (int) deltaY * 2;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, entries.size())));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
