package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.agmas.noellesroles.utils.lottery.LotteryManager;
import io.wifi.starrailexpress.client.gui.screen.ingame.AccessoryScreen;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ViewLotteryPoolScreen extends AbstractPixelScreen{
    public static class ItemCardGroup {
        public ItemCardGroup() {
            itemCards = new ArrayList<>();
        }
        public ItemCardGroup(List<List<ItemCard>> itemCards, int height) {
            this.itemCards = itemCards;
            this.height = height;
            minY += height;
        }
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            itemCards.forEach(itemCardGroup -> {
                itemCardGroup.forEach(itemCard -> itemCard.render(guiGraphics, mouseX, mouseY, delta));
            });
        }
        public void setItemCards(List<List<ItemCard>> itemCards) {
            this.itemCards = itemCards;
        }
        public void setHeight(int height) {
            this.height = height;
            minY -= height;
        }
        public void addY(double y) {
            if (y < 0 && this.y <= minY)
                return;
            if (y > 0 && this.y >= maxY)
                return;
            this.y += y;
            itemCards.forEach(itemCardGroup -> {
                itemCardGroup.forEach(itemCard -> itemCard.setY(itemCard.getY() + (int)y));
            });
        }
        public double x = 0, y = 0;
        public double height = 0;
        public double minY = -100, maxY = 100;
        public List<List<ItemCard>> itemCards = null;
    }
    public static class ItemCard extends AbstractWidget {
        public ItemCard(int i, int j, int pixelSize, int quality, String rawName, Button.OnPress onPress) {
            super(i, j,
                    ITEM_PIXELS_SIZE * pixelSize,
                    (ITEM_PIXELS_SIZE + QUALITY_BAR_PIXELS_SIZE) * pixelSize, Component.empty());
            item = new TextureWidget(
                    i, j,
                    ITEM_PIXELS_SIZE * pixelSize, ITEM_PIXELS_SIZE * pixelSize,
                    ITEM_PIXELS_SIZE, ITEM_PIXELS_SIZE,
                    LootScreenUtils.getItemResourceLocation(rawName)
            );
            qualityBar = new TextureWidget(
                    i, j + ITEM_PIXELS_SIZE * pixelSize,
                    ITEM_PIXELS_SIZE * pixelSize, QUALITY_BAR_PIXELS_SIZE * pixelSize,
                    ITEM_PIXELS_SIZE, QUALITY_BAR_PIXELS_SIZE,
                    LotteryManager.getQualityBgResourceLocation(quality)
            );
            button = Button.builder(
                    Component.empty(),
                    onPress)
                    .pos(i, j)
                    .size(ITEM_PIXELS_SIZE * pixelSize, (ITEM_PIXELS_SIZE + QUALITY_BAR_PIXELS_SIZE) * pixelSize)
                    .build();
            button.setAlpha(0f);

            skinName = LotteryManager.LotteryPool.getTrueName(rawName);
            // 设置itemStack
            itemType = LotteryManager.LotteryPool.getSkinItemStack(rawName);
            this.pixelSize = pixelSize;
            this.qualityIndex = quality;
        }
        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
            // render BG
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BG_COLOR.getRGB());
            qualityBar.render(guiGraphics, i, j, f);
            // 传承品质：品质条时不时爆出红色光
            if (qualityIndex == LotteryManager.INHERIT_QUALITY_INDEX) {
                float pulse = LotteryManager.inheritRedPulse();
                if (pulse > 0.01F) {
                    int a = (int) (pulse * 230);
                    guiGraphics.fill(getX(), getY() + ITEM_PIXELS_SIZE * pixelSize,
                            getX() + getWidth(), getY() + getHeight(), (a << 24) | 0xFF2F2F);
                }
            }
            if (itemType != null && !itemType.isEmpty()) {
                LootScreenUtils.renderPixelScaleSkinItem(item.getX(), item.getY(), pixelSize, guiGraphics, itemType, skinName);
            }
            // 渲染金币等非物品
            else if(item != null)
                item.render(guiGraphics, i, j, f);
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

        }
        @Override
        public void onClick(double d, double e) {
            button.onClick(d, e);
        }
        @Override
        public void setX(int x) {
            super.setX(x);
            item.setX(x);
            qualityBar.setX(x);
        }
        @Override
        public void setY(int y) {
            super.setY(y);
            item.setY(y);
            qualityBar.setY(y + ITEM_PIXELS_SIZE * pixelSize);
        }
        public static final Color BG_COLOR = new Color(0x1FCCCCCC, true);
        private int qualityIndex = 0;
        private TextureWidget item = null;
        private TextureWidget qualityBar = null;
        private Button button = null;
        protected ItemStack itemType;
        protected String skinName;
        private int pixelSize;
    }
    protected ViewLotteryPoolScreen(int poolID) {
        this(poolID, null);
    }
    protected ViewLotteryPoolScreen(int poolID, Screen parent) {
        super(Component.empty());
        this.POOL_ID = poolID;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        while ((VIEW_POOL_SCREEN_COL_NUM * (ROW_INTERVAL_PIXELS + ITEM_PIXELS_SIZE) - ROW_INTERVAL_PIXELS) * (pixelSize + 1) < width)
            ++pixelSize;
        int totalPixels = (VIEW_POOL_SCREEN_COL_NUM * (ROW_INTERVAL_PIXELS + ITEM_PIXELS_SIZE) - ROW_INTERVAL_PIXELS) * pixelSize;
        LotteryManager.LotteryPool lotteryPool = LotteryManager.getInstance().getLotteryPool(POOL_ID);
        List<List<ItemCard>> itemCardsGroup = new ArrayList<>();
        // 添加物品展示卡
        int curX = 0, curY = COL_INTERVAL_PIXELS * pixelSize;
        for (int i = 0;
             i < lotteryPool.getQualityListGroupConfigs().size();
             ++i,
             curY += (ITEM_PIXELS_SIZE + QUALITY_BAR_PIXELS_SIZE + COL_INTERVAL_PIXELS) * pixelSize) {
            List<String> itemNames = lotteryPool.getQualityListGroupConfigs().get(i).second;
            curX = (width - totalPixels) / 2;
            itemCardsGroup.add(new ArrayList<>());
            for (int j = itemNames.size() - 1, curCount = 0;
                 j >= 0;
                 --j, ++curCount,
                curX += (ITEM_PIXELS_SIZE + ROW_INTERVAL_PIXELS) * pixelSize)
            {
                if (curCount >= VIEW_POOL_SCREEN_COL_NUM) {
                    curX = (width - totalPixels) / 2;
                    curY += (ITEM_PIXELS_SIZE + QUALITY_BAR_PIXELS_SIZE + COL_INTERVAL_PIXELS) * pixelSize;
                    curCount = 0;
                }
                String itemName = itemNames.get(j);
                
                ItemCard itemCard = new ItemCard(
                        curX, curY,
                        pixelSize,
                        i,
                        itemName,
                        button -> {
                            // 皮肤条目（type/skinName）：进入与主菜单饰品页一致的角色预览（人物持握/佩戴 + 特写取景）
                            if (itemName.contains("/")) {
                                Minecraft minecraft = Minecraft.getInstance();
                                minecraft.setScreen(new AccessoryScreen(this, itemName));
                                return;
                            }
                            ItemStack itemStack = LotteryManager.LotteryPool.getSkinItemStack(itemName);
                            Minecraft minecraft = Minecraft.getInstance();
                            if (itemStack != null) {
                                String trueName = itemName.substring(itemName.indexOf('/') + 1);
                                int idx = trueName.indexOf('/');
                                if (idx != -1)
                                    trueName = trueName.substring(0, idx);
                                itemStack.set(SREDataComponentTypes.SKIN, trueName);
                                minecraft.setScreen(new DisplayItemScreen(itemStack,this));
                            }
                        });
                addRenderableWidget(itemCard);
                itemCardsGroup.getLast().add(itemCard);
            }
        }
        // 设置物品卡片组
        itemCards = new ItemCardGroup();
        itemCards.setItemCards(itemCardsGroup);
        itemCards.setHeight(curY);
        // 预览背景与抽奖主界面同源：优先使用卡池动态视频背景（run/video/lottery_starry）
        if (!LootInfoScreen.POOL_VIDEO.hasFrames())
            LootInfoScreen.POOL_VIDEO.loadFrames();
        // 静态回退图（pool_bg{poolID}.png，若资源缺失则仍为动态背景兜底）
        viewPoolBg = new TextureWidget(
                0,0,
                width, height,
                POOL_BG_WIDTH, POOL_BG_HEIGHT,
                LootInfoScreen.getPoolSketchTexture(POOL_ID)
        );
        // 背景最后添加防止覆盖按钮优先级
        addRenderableWidget(viewPoolBg);
    }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (LootInfoScreen.POOL_VIDEO.hasFrames()) {
            // 全屏动态背景，与主界面播放同一帧序列
            LootInfoScreen.POOL_VIDEO.render(guiGraphics, this.width, this.height, delta);
            // 轻压暗一层，保证物品卡与文字清晰
            guiGraphics.fill(0, 0, this.width, this.height, 0x59000000);
        } else {
            viewPoolBg.render(guiGraphics, mouseX, mouseY, delta);
        }
        itemCards.render(guiGraphics, mouseX, mouseY, delta);
        // 顶部居中显示"预览"标题（最后绘制，保证盖在所有元素之上）
        guiGraphics.drawCenteredString(this.font, Component.literal("预览"),
                this.width / 2, 4, 0xFFFFFFFF);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmound, double verticalAmound) {
        // TODO : 更加灵动的滚动效果
        itemCards.addY(verticalAmound * 10);
        return true;
    }
    @Override
    public void onClose() {
        if (parent != null && minecraft != null)
            minecraft.setScreen(parent);
        else
            super.onClose();
    }
    /** 奖池背景图源尺寸（与 LootInfoScreen 卡池立绘 pool_bg{poolID}.png 一致，16:9） */
    public static final int POOL_BG_WIDTH = 320;
    public static final int POOL_BG_HEIGHT = 180;
    private static final int VIEW_POOL_SCREEN_COL_NUM = 10;
    private static final int ROW_INTERVAL_PIXELS = 5;
    private static final int COL_INTERVAL_PIXELS = 2;
    private static final int QUALITY_BAR_PIXELS_SIZE = 1;
    private static final int ITEM_PIXELS_SIZE = 16;
    private final Screen parent;
    private final int POOL_ID;
    private ItemCardGroup itemCards;
    private TextureWidget viewPoolBg;
}
