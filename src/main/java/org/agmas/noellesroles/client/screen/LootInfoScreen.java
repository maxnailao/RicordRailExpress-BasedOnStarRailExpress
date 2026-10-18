package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.exmo.sre.EXSREClient;
import net.exmo.sre.loading.FrameAnimationRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.agmas.noellesroles.client.animation.AbstractAnimation;
import org.agmas.noellesroles.client.animation.AnimationTimeLineManager;
import org.agmas.noellesroles.client.animation.BezierAnimation;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.agmas.noellesroles.packet.Loot.LootBuyChanceC2SPacket;
import org.agmas.noellesroles.packet.Loot.LootDataRefreshC2SPacket;
import org.agmas.noellesroles.packet.Loot.LootMultiRequestC2SPacket;
import org.agmas.noellesroles.packet.Loot.LootRequestC2SPacket;
import org.agmas.noellesroles.utils.Pair;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖信息页
 * - 用于显示卡池信息，以及启动抽奖
 * - 左侧选项卡常驻 + 右侧展示图 + 底部操作栏（抽奖、五连抽、预览）
 */
public class LootInfoScreen extends AbstractPixelScreen {
    private static final class LayoutMetrics {
        private final int leftX;
        private final int topY;
        private final int totalWidth;
        private final int totalHeight;
        private final int sidebarWidth;
        private final int sidebarPadding;
        private final int sidebarContentX;
        private final int sketchX;
        private final int sketchY;
        private final int sketchWidth;
        private final int sketchHeight;
        private final int sketchEdge;
        private final int actionBarHeight;
        private final int actionBarY;
        private final int actionBtnSpacing;
        private final int actionStartX;
        private final int actionButtonWidth;
        private final int actionButtonHeight;
        private final int sidebarViewportHeight;
        private final int poolButtonX;
        private final int poolButtonY;
        private final int poolButtonWidth;
        private final int poolButtonHeight;
        private final int poolButtonStep;

        private LayoutMetrics(int screenWidth, int screenHeight, int centerX, int centerY, int poolCount) {
            int availableWidth = Math.max(screenWidth - 32, 320);
            int availableHeight = Math.max(screenHeight - 32, 240);
            float widthScale = (float) availableWidth / (float) BASE_TOTAL_WIDTH;
            float heightScale = (float) availableHeight / (float) BASE_TOTAL_HEIGHT;
            float scale = Math.max(MIN_LAYOUT_SCALE, Math.min(MAX_LAYOUT_SCALE, Math.min(widthScale, heightScale)));

            int sidebarWidthValue = Math.max(76, Math.round(BASE_SIDEBAR_WIDTH * scale));
            int sidebarPaddingValue = Math.max(6, Math.round(BASE_SIDEBAR_PADDING * scale));
            int sketchEdgeValue = Math.max(14, Math.round(BASE_SKETCH_EDGE * scale));
            int actionBarHeightValue = Math.max(30, Math.round(BASE_ACTION_BAR_HEIGHT * scale));
            int actionBtnSpacingValue = Math.max(6, Math.round(BASE_ACTION_BTN_SPACING * scale));

            int maxTotalWidth = Math.max(screenWidth - 16, 260);
            int maxTotalHeight = Math.max(screenHeight - 16, 200);
            int availableSketchWidth = Math.max(160, maxTotalWidth - sidebarWidthValue - sketchEdgeValue);
            int availableSketchHeight = Math.max(96, maxTotalHeight - actionBarHeightValue - sketchEdgeValue);

            float sketchAspect = (float) BASE_SKETCH_WIDTH / (float) BASE_SKETCH_HEIGHT;
            int sketchWidthValue = availableSketchWidth;
            int sketchHeightValue = Math.round(sketchWidthValue / sketchAspect);
            if (sketchHeightValue > availableSketchHeight) {
                sketchHeightValue = availableSketchHeight;
                sketchWidthValue = Math.round(sketchHeightValue * sketchAspect);
            }

            sketchWidthValue = Mth.clamp(sketchWidthValue, 160, availableSketchWidth);
            sketchHeightValue = Mth.clamp(sketchHeightValue, 90, availableSketchHeight);

            int computedTotalWidth = sidebarWidthValue + sketchEdgeValue + sketchWidthValue;
            if (computedTotalWidth > maxTotalWidth) {
                int overflow = computedTotalWidth - maxTotalWidth;
                int shrinkSidebar = Math.min(overflow, Math.max(0, sidebarWidthValue - 72));
                sidebarWidthValue -= shrinkSidebar;
                overflow -= shrinkSidebar;
                if (overflow > 0)
                    sketchEdgeValue = Math.max(10, sketchEdgeValue - overflow);
            }

            sketchWidth = sketchWidthValue;
            sketchHeight = sketchHeightValue;
            sketchEdge = sketchEdgeValue;
            actionBarHeight = actionBarHeightValue;
            sidebarWidth = sidebarWidthValue;
            sidebarPadding = sidebarPaddingValue;
            actionBtnSpacing = actionBtnSpacingValue;

            totalWidth = sidebarWidth + sketchEdge + sketchWidth;
            totalHeight = sketchEdge + sketchHeight + actionBarHeight;
            leftX = centerX - totalWidth / 2;
            topY = centerY - totalHeight / 2;
            sidebarViewportHeight = totalHeight - actionBarHeight;

            sidebarContentX = leftX;
            sketchX = leftX + sidebarWidth + sketchEdge / 2;
            sketchY = topY + sketchEdge / 2;

            int visibleButtonCount = Math.max(1, Math.min(poolCount, MAX_VISIBLE_POOL_BUTTONS));
            poolButtonWidth = sidebarWidth;
            poolButtonHeight = Mth.clamp(Math.round(BASE_POOL_BUTTON_HEIGHT * scale * 0.9f), MIN_POOL_BUTTON_HEIGHT, MAX_POOL_BUTTON_HEIGHT);
            int remainingSpace = Math.max(0, sidebarViewportHeight - visibleButtonCount * poolButtonHeight);
            int buttonGap = Math.min(MAX_POOL_BUTTON_GAP, remainingSpace / Math.max(visibleButtonCount + 1, 1));
            poolButtonStep = poolButtonHeight + buttonGap;
            poolButtonX = sidebarContentX;
            poolButtonY = topY + buttonGap;

            actionButtonHeight = Math.max(20, Math.round(BASE_ACTION_BUTTON_HEIGHT * scale));
            int maxActionButtonWidth = sketchWidth / 3 - actionBtnSpacing;
            actionButtonWidth = Math.max(56, Math.min(poolButtonWidth, maxActionButtonWidth));
            int actionButtonsTotalWidth = actionButtonWidth * 3 + actionBtnSpacing * 2;
            actionBarY = topY + totalHeight - actionBarHeight + (actionBarHeight - actionButtonHeight) / 2;
            actionStartX = leftX + sidebarWidth + Math.max(0, (sketchEdge + sketchWidth - actionButtonsTotalWidth) / 2);
        }
    }

    public static class PoolButton extends AbstractButton {
        public interface OnRelease {
            void onRelease(PoolButton button);
        }
        private int poolID;
        private final OnRelease onRelease;
        private boolean isPressed = false;
        private boolean selected = false;

        public PoolButton(int poolId, int i, int j, int k, int l, Component component, OnRelease onRelease) {
            super(i, j, k, l, component);
            this.poolID = poolId;
            this.onRelease = onRelease;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float f) {
            Minecraft minecraft = Minecraft.getInstance();
            int fillColor = getFillColor();
            int borderColor = selected ? selectedBorderColor.getRGB() : buttonBorderColor.getRGB();
            int textColor = selected ? selectedTextColor.getRGB() : buttonTextColor.getRGB();

            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, applyAlpha(fillColor));
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, applyAlpha(borderColor));
            guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, applyAlpha(borderColor));
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, applyAlpha(borderColor));
            guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, applyAlpha(borderColor));

            String buttonText = minecraft.font.plainSubstrByWidth(getMessage().getString(), Math.max(8, width - 10));
            int textX = getX() + width / 2 - minecraft.font.width(buttonText) / 2;
            int textY = getY() + (height - 8) / 2;
            guiGraphics.drawString(minecraft.font, buttonText, textX, textY, applyAlpha(textColor), false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

        }

        @Override
        public void onPress() {
            isPressed = true;
        }

        @Override
        public void onRelease(double d, double e) {
            isPressed = false;
            if (isHovered) {
                onRelease.onRelease(this);
            }
        }

        public float getAlpha() {
            return this.alpha;
        }

        public void setPoolID(int poolID) {
            this.poolID = poolID;
        }

        public int getPoolID() {
            return poolID;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        private int getFillColor() {
            if (isPressed)
                return selected ? selectedPressedColor.getRGB() : buttonPressedColor.getRGB();
            if (isHovered)
                return selected ? selectedHoverColor.getRGB() : buttonHoverColor.getRGB();
            return selected ? selectedIdleColor.getRGB() : buttonIdleColor.getRGB();
        }

        private int applyAlpha(int color) {
            int alphaBits = Mth.clamp((int) (this.alpha * 255.0f), 0, 255) << 24;
            return alphaBits | (color & 0x00FFFFFF);
        }

        public boolean isOnButton(int mouseX, int mouseY) {
            return mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= getY()
                    && mouseY < getY() + getHeight();
        }
    }

    protected static class AnimationController extends AbstractWidget {
        public AnimationController() {
            super(0, 0, 0, 0, Component.empty());
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {

        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

        }

        private float curBgProcess = 0f;
    }

    public LootInfoScreen() {
        this(0,0,0, null);
    }
    public LootInfoScreen(int initPoolId, Screen parent) {
        this(initPoolId, 0, 0, parent);
    }
    public LootInfoScreen(int coinNumber, int lotteryChance) {
        this(1, coinNumber, lotteryChance, null);
    }
    public LootInfoScreen(int initPoolId, int coinNumber, int lotteryChance) {
        this(initPoolId, coinNumber, lotteryChance, null);
    }
    public LootInfoScreen(int initPoolId, int coinNumber, int lotteryChance, Screen parent) {
        super(Component.empty());
        this.initPoolId = initPoolId;
        this.coinNumber = coinNumber;
        this.lotteryChance = lotteryChance;
        this.parent = parent;
        ClientPlayNetworking.send(new LootDataRefreshC2SPacket());
    }

    @Override
    protected void init() {
        super.init();
        // 重置成员状态
        initialized = false;
        poolButtons = new ArrayList<>();
        animationStack = new ArrayList<>();
        animationController = new AnimationController();
        renderWidgets = new ArrayList<>();

        List<LotteryManager.LotteryPool> lotteryPools = LotteryManager.getInstance().getLotteryPools();
        layoutMetrics = new LayoutMetrics(width, height, centerX, centerY, lotteryPools.size());

        List<Pair<Float, AbstractAnimation>> animations = new ArrayList<>();
        // 新布局计算：左侧选项卡 + 右侧展示区 + 底部操作栏
        int leftX = layoutMetrics.leftX;
        int topY = layoutMetrics.topY;
        this.curPool = LotteryManager.getInstance().getLotteryPool(initPoolId);
        boolean isInit = tryToInitPool();
        if (isInit) {
            // 立绘动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                            poolSketch,
                            new Vec2(0f, (float) -layoutMetrics.sketchEdge / 2),
                            (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .build()));
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                            poolSketch,
                            new Vec2(1f, 0f),
                            (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        poolSketch.setAlpha(poolSketch.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));

            // 按钮动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                            startPoolBtn,
                            new Vec2(1f, 0f),
                            (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        startPoolBtn.setAlpha(startPoolBtn.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                            multiPoolBtn,
                            new Vec2(1f, 0f),
                            (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        multiPoolBtn.setAlpha(multiPoolBtn.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                            previewBtn,
                            new Vec2(1f, 0f),
                            (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        previewBtn.setAlpha(previewBtn.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));
        }

        // 加载卡池动态背景帧序列（run/video/lottery_starry），无帧自动回退静态立绘
        if (!POOL_VIDEO.hasFrames())
            POOL_VIDEO.loadFrames();

        // 添加金币显示 : 金币图标与数量置于操作栏上半行，抽数显示在下半行
        int halfRow = layoutMetrics.actionButtonHeight / 2;
        TextureWidget coinWidget = new TextureWidget(
                layoutMetrics.leftX + layoutMetrics.actionBarHeight / 4, layoutMetrics.actionBarY,
                layoutMetrics.actionButtonHeight / 2, layoutMetrics.actionButtonHeight / 2,
                9,9,
                LootScreenUtils.getCoinResourceLocation()
                );
        // 金币数量文本（上半行）
        AbstractWidget textWidget = new AbstractWidget(
                layoutMetrics.leftX + layoutMetrics.actionButtonHeight, layoutMetrics.actionBarY,
                layoutMetrics.actionButtonWidth / 2, halfRow,
                Component.empty()
                ) {
            @Override
            protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
                RenderSystem.enableBlend();
                RenderSystem.enableDepthTest();
                guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                int k = this.getX();
                int l = this.getX() + this.getWidth();
                renderScrollingString(guiGraphics, font, Component.literal("" + coinNumber),
                        k, this.getY(), l, this.getY() + this.getHeight(),
                        this.active ? 16777215 : 10526880 | Mth.ceil(this.alpha * 255.0F) << 24);
            }
            @Override
            protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

            }
        };
        renderWidgets.add(coinWidget);
        renderWidgets.add(textWidget);

        // 为每个卡池添加卡池按钮
        for (int i = 0; i < lotteryPools.size(); ++i) {
            LotteryManager.LotteryPool curBtnPool = lotteryPools.get(i);
            PoolButton poolBtn = new PoolButton(
                    curBtnPool.getPoolID(),
                    layoutMetrics.poolButtonX,
                    layoutMetrics.poolButtonY,
                    layoutMetrics.poolButtonWidth, layoutMetrics.poolButtonHeight,
                    Component.literal(curBtnPool.getName()),
                    (buttonWidget) -> {
                        switchToPool(curBtnPool.getPoolID());
                    });
            poolBtn.setAlpha(0f);
            // 按钮排列动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    poolBtn,
                    new Vec2(0f, 0f),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .build()));
            // 透明度动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    poolBtn,
                    new Vec2(1f, 0f),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        poolBtn.setAlpha(poolBtn.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));
            poolButtons.add(poolBtn);
            poolBtn.active = false;
            addRenderableWidget(poolBtn);
        }
        if (curPool != null) {
            PoolButton curPoolBtn = getPoolBtnByPoolId(curPool.getPoolID());
            if (curPoolBtn != null)
                curPoolBtn.setSelected(true);
        }

        setSidebarScroll(sidebarScrollOffset);

        animationTimeLineManager = AnimationTimeLineManager.builder()
                .addAnimation(0f, BezierAnimation.builder(
                        animationController,
                        new Vec2(1.0f, 0),
                        (int) (initBgTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            animationController.curBgProcess += vec2.x;
                        })
                        .setIntErrorFix(false)
                        .build())
                .addAnimations(animations)
                .build();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (!initialized) {
            if (animationTimeLineManager.isFinished()) {
                initialized = true;
                if (viewPoolBtn != null)
                   viewPoolBtn.active = true;
                if (startPoolBtn != null)
                    startPoolBtn.active = true;
                if (multiPoolBtn != null)
                    multiPoolBtn.active = true;
                if (previewBtn != null)
                    previewBtn.active = true;
                for (PoolButton poolButton : poolButtons)
                    poolButton.active = true;
            } else
                animationTimeLineManager.renderUpdate(delta);
        }
        animationStack.forEach(animation -> animation.renderUpdate(delta));
        animationStack.removeIf(AbstractAnimation::isFinished);

        int leftX = layoutMetrics.leftX;
        int topY = layoutMetrics.topY;
        int sidebarBottomY = topY + layoutMetrics.sidebarViewportHeight;
        int contentRightX = leftX + layoutMetrics.sidebarWidth
            + Math.round(animationController.curBgProcess * (layoutMetrics.sketchEdge + layoutMetrics.sketchWidth));

        // 绘制左侧选项卡背景（常驻矩形）
        guiGraphics.fill(
            (int) (leftX + (1.0f - animationController.curBgProcess) * layoutMetrics.sidebarWidth),
                topY,
            leftX + layoutMetrics.sidebarWidth,
            sidebarBottomY,
                poolBtnBgColor.getRGB());
        guiGraphics.fill(
            leftX + layoutMetrics.sidebarWidth - 1,
            topY,
            leftX + layoutMetrics.sidebarWidth,
            sidebarBottomY,
            panelDividerColor.getRGB());
        // 绘制右侧展示区背景
        guiGraphics.fill(
            leftX + layoutMetrics.sidebarWidth,
                topY,
            contentRightX,
            sidebarBottomY,
                sketchBgColor.getRGB());

        // 卡池动态背景：有视频帧时替代静态立绘，随开屏动画同步淡入并按展开区域裁剪
        if (POOL_VIDEO.hasFrames() && poolSketch != null) {
            guiGraphics.enableScissor(leftX + layoutMetrics.sidebarWidth, topY,
                    contentRightX, sidebarBottomY);
            POOL_VIDEO.render(guiGraphics, poolSketch.getX(), poolSketch.getY(),
                    poolSketch.getWidth(), poolSketch.getHeight(),
                    Math.min(1.0F, animationController.curBgProcess * 2.0F));
            renderPoolTitleText(guiGraphics, poolSketch);
            guiGraphics.disableScissor();
        } else if (poolSketch != null)
            poolSketch.render(guiGraphics, mouseX, mouseY, delta);
        renderSidebarScrollbarBackground(guiGraphics);
        guiGraphics.enableScissor(leftX, topY, leftX + layoutMetrics.sidebarWidth, sidebarBottomY);
        for (PoolButton poolBtn : poolButtons)
            poolBtn.render(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.disableScissor();

        // 绘制底部操作栏背景，使其覆盖左下角侧栏区域
        guiGraphics.fill(
                leftX,
                sidebarBottomY,
                contentRightX,
                topY + layoutMetrics.totalHeight,
                actionBarBgColor.getRGB());
        guiGraphics.fill(
                leftX,
                sidebarBottomY,
                contentRightX,
                sidebarBottomY + 1,
                actionBarLineColor.getRGB());

        renderSidebarScrollbar(guiGraphics);
        if (startPoolBtn != null)
            startPoolBtn.render(guiGraphics, mouseX, mouseY, delta);
        if (multiPoolBtn != null)
            multiPoolBtn.render(guiGraphics, mouseX, mouseY, delta);
        if (previewBtn != null)
            previewBtn.render(guiGraphics, mouseX, mouseY, delta);
        renderWidgets.forEach(widget -> widget.render(guiGraphics, mouseX, mouseY, delta));
        renderLootPopup(guiGraphics);
        renderPityInfo(guiGraphics);
    }

    @Override
    public void tick() {
        super.tick();
        // 兜底轮询：每 0.5 秒向服务端主动拉取最新硬币/抽数/保底（不依赖服务端推送）
        if (this.minecraft != null && this.minecraft.level != null && ++lootRefreshTick % 10 == 0) {
            ClientPlayNetworking.send(new LootDataRefreshC2SPacket());
        }
        if (org.agmas.noellesroles.client.LootClientDataCache.coinNumber >= 0
                && coinNumber != org.agmas.noellesroles.client.LootClientDataCache.coinNumber) {
            setCoinNumber(org.agmas.noellesroles.client.LootClientDataCache.coinNumber);
        }
        if (org.agmas.noellesroles.client.LootClientDataCache.lootChance >= 0
                && lotteryChance != org.agmas.noellesroles.client.LootClientDataCache.lootChance) {
            setLotteryChance(org.agmas.noellesroles.client.LootClientDataCache.lootChance);
        }
        if (org.agmas.noellesroles.client.LootClientDataCache.pityCount >= 0
                && pityCount != org.agmas.noellesroles.client.LootClientDataCache.pityCount) {
            setPityCount(org.agmas.noellesroles.client.LootClientDataCache.pityCount);
        }
    }

    public void setPityCount(int pityCount) {
        this.pityCount = Math.max(0, pityCount);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layoutMetrics != null && isPointInSidebar(mouseX, mouseY) && getSidebarMaxScroll() > 0) {
            setSidebarScroll(sidebarScrollOffset - (float) verticalAmount * Math.max(16.0f, layoutMetrics.poolButtonHeight * 0.45f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isPointInSidebarScrollbar(mouseX, mouseY)) {
            draggingSidebarScrollbar = true;
            updateSidebarScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSidebarScrollbar && button == 0) {
            updateSidebarScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0)
            draggingSidebarScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override
    public void onClose() {
        if (parent != null && this.minecraft != null) {
            this.minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }
    private boolean tryToInitPool() {
        int sketchX = layoutMetrics.sketchX;
        int sketchY = layoutMetrics.sketchY;
        // 无卡池信息时的处理
        try {
            // 设置预览按钮：隐形按钮点击立绘打开预览,
            viewPoolBtn = Button.builder(
                            Component.empty(),
                            buttonWidget -> {
                                Minecraft minecraft = Minecraft.getInstance();
                                minecraft.setScreen(new ViewLotteryPoolScreen(this.curPool.getPoolID(), this));
                            })
                    .pos(sketchX, sketchY)
                    .size(layoutMetrics.sketchWidth, layoutMetrics.sketchHeight)
                    .build();
            viewPoolBtn.setAlpha(0f);
            viewPoolBtn.active = false;
            // 按钮处理顺序：先加入的优先处理
            addRenderableWidget(viewPoolBtn);
            // 设置卡池立绘
            poolSketch = new TextureWidget(
                    sketchX,
                    sketchY + layoutMetrics.sketchEdge / 2,
                    layoutMetrics.sketchWidth, layoutMetrics.sketchHeight,
                    BASE_SKETCH_WIDTH, BASE_SKETCH_HEIGHT,
                    getPoolSketchTexture(curPool.getPoolID()));
            addRenderableWidget(poolSketch);
            poolSketch.setAlpha(0f);

            // 底部操作栏按钮
            int actionBarY = layoutMetrics.actionBarY;
            int actionStartX = layoutMetrics.actionStartX;

            // 添加开始抽奖按钮（单抽）
            startPoolBtn = new PoolButton(
                    curPool.getPoolID(),
                    actionStartX,
                    actionBarY,
                    layoutMetrics.actionButtonWidth,
                    layoutMetrics.actionButtonHeight,
                    Component.translatable("screen.noellesroles.loot.lootBtn"),
                    poolButton -> {
                        ClientPlayNetworking.send(new LootRequestC2SPacket(this.curPool.getPoolID()));
                    });
            addRenderableWidget(startPoolBtn);
            startPoolBtn.active = false;
            startPoolBtn.setAlpha(0f);

            // 添加十连抽按钮
            multiPoolBtn = new PoolButton(
                    curPool.getPoolID(),
                    actionStartX + layoutMetrics.actionButtonWidth + layoutMetrics.actionBtnSpacing,
                    actionBarY,
                    layoutMetrics.actionButtonWidth,
                    layoutMetrics.actionButtonHeight,
                    Component.translatable("screen.noellesroles.loot.multiLootBtn"),
                    poolButton -> {
                        int lootCount = 10;
                        ClientPlayNetworking.send(new LootMultiRequestC2SPacket(this.curPool.getPoolID(), lootCount));
                    });
            addRenderableWidget(multiPoolBtn);
            multiPoolBtn.active = false;
            multiPoolBtn.setAlpha(0f);

            // 添加预览按钮
            previewBtn = new PoolButton(
                    curPool.getPoolID(),
                    actionStartX + (layoutMetrics.actionButtonWidth + layoutMetrics.actionBtnSpacing) * 2,
                    actionBarY,
                    layoutMetrics.actionButtonWidth,
                    layoutMetrics.actionButtonHeight,
                    Component.translatable("screen.noellesroles.loot.previewBtn"),
                    poolButton -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.setScreen(new ViewLotteryPoolScreen(this.curPool.getPoolID(), this));
                    });
            addRenderableWidget(previewBtn);
            previewBtn.active = false;
            previewBtn.setAlpha(0f);

            // 添加抽卡次数的显示，在try中进行可以为以后不同卡池使用不同抽卡次数

            // 抽卡次数显示（纯展示）：位于货币数量下方的下半行
            int halfRow = layoutMetrics.actionButtonHeight / 2;
            int chanceTextX = layoutMetrics.leftX + layoutMetrics.actionButtonHeight;
            int chanceTextW = font.width("抽卡次数: 9999") + 2;
            AbstractWidget chanceText = new AbstractWidget(
                    chanceTextX, layoutMetrics.actionBarY + halfRow,
                    chanceTextW, halfRow,
                    Component.empty()) {
                @Override
                protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
                    RenderSystem.enableBlend();
                    RenderSystem.enableDepthTest();
                    guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                    int k = this.getX();
                    int l = this.getX() + this.getWidth();
                    renderScrollingString(guiGraphics, font, Component.literal("抽卡次数: " + lotteryChance),
                            k, this.getY(), l, this.getY() + this.getHeight(),
                            Mth.ceil(this.alpha * 255.0F) << 24 | 16777215);
                }
                @Override
                protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
                }
            };
            addRenderableWidget(chanceText);
            renderWidgets.add(chanceText);

            // 购买抽数按钮：紧贴抽卡次数右侧，带方框底衬
            final String buyChanceLabel = "购买抽数(500/抽)";
            Button buyChanceBtn = new Button(
                    chanceTextX + chanceTextW + 6,
                    layoutMetrics.actionBarY,
                    font.width(buyChanceLabel) + 14, layoutMetrics.actionButtonHeight,
                    Component.literal(buyChanceLabel),
                    button -> {
                        ClientPlayNetworking.send(new LootBuyChanceC2SPacket(1));
                    },
                    (supplier) -> (MutableComponent) supplier.get()
            ) {
                @Override
                protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
                    int bg = !this.active ? 0x66304052 : (this.isHovered() ? 0xFFE3C48C : 0xFF243547);
                    int border = this.isHovered() ? 0xFFF7E7BE : 0xFF8D734A;
                    int textColor = !this.active ? 0x88F0E8D7 : (this.isHovered() ? 0xFF1D2734 : 0xFFF0E8D7);
                    // 方框底衬 + 四边描边
                    guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                    guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
                    guiGraphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
                    guiGraphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
                    guiGraphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
                    guiGraphics.drawString(font, buyChanceLabel,
                            getX() + (getWidth() - font.width(buyChanceLabel)) / 2,
                            getY() + (getHeight() - font.lineHeight) / 2 + 1,
                            textColor, false);
                }
            };
            buyChanceBtn.setTooltip(Tooltip.create(Component.literal("花费500硬币购买1次抽数，购买后可直接抽奖")));
            addRenderableWidget(buyChanceBtn);
            renderWidgets.add(buyChanceBtn);
            return true;
        } catch (Exception e) {
            curPool = null;
            poolSketch = null;
            viewPoolBtn = null;
            startPoolBtn = null;
            multiPoolBtn = null;
            previewBtn = null;
            return false;
        }
    }
    public void switchToPool(int poolD) {
        if (curPool != null && poolD == curPool.getPoolID())
            return;
        LotteryManager.LotteryPool nextPool = LotteryManager.getInstance().getLotteryPool(poolD);
        if (nextPool == null)
            return;
        if (poolSketch == null || startPoolBtn == null || multiPoolBtn == null || previewBtn == null) {
            this.curPool = nextPool;
            if (!tryToInitPool())
                return;
            else {
                if (viewPoolBtn != null)
                    viewPoolBtn.active = true;
                if (startPoolBtn != null)
                    startPoolBtn.active = true;
                if (multiPoolBtn != null)
                    multiPoolBtn.active = true;
                if (previewBtn != null)
                    previewBtn.active = true;
            }
        }
        initPoolId = poolD;
        poolSketch.setTEXTURE(getPoolSketchTexture(nextPool.getPoolID()));
        // 添加位移和透明度动画
        poolSketch.setY(layoutMetrics.sketchY + layoutMetrics.sketchEdge / 2);
        animationStack.add(
                BezierAnimation.builder(
                        poolSketch,
                        new Vec2(0f, (float) -layoutMetrics.sketchEdge / 2),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .build());
        poolSketch.setAlpha(0f);
        animationStack.add(
                BezierAnimation.builder(
                        poolSketch,
                        new Vec2(1f, 0f),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            poolSketch.setAlpha(poolSketch.getAlpha() + vec2.x);
                        })
                        .setIntErrorFix(false)
                        .build());
        startPoolBtn.setPoolID(poolD);
        startPoolBtn.setAlpha(0f);
        animationStack.add(
                BezierAnimation.builder(
                        startPoolBtn,
                        new Vec2(1f, 0f),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            startPoolBtn.setAlpha(startPoolBtn.getAlpha() + vec2.x);
                        })
                        .setIntErrorFix(false)
                        .build());
        multiPoolBtn.setPoolID(poolD);
        multiPoolBtn.setAlpha(0f);
        animationStack.add(
                BezierAnimation.builder(
                        multiPoolBtn,
                        new Vec2(1f, 0f),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            multiPoolBtn.setAlpha(multiPoolBtn.getAlpha() + vec2.x);
                        })
                        .setIntErrorFix(false)
                        .build());
        previewBtn.setPoolID(poolD);
        previewBtn.setAlpha(0f);
        animationStack.add(
                BezierAnimation.builder(
                        previewBtn,
                        new Vec2(1f, 0f),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            previewBtn.setAlpha(previewBtn.getAlpha() + vec2.x);
                        })
                        .setIntErrorFix(false)
                        .build());
        switchToPoolBtn(poolD);
        curPool = nextPool;
    }

    public void switchToPoolBtn(int poolD) {
        if (curPool == null)
            return;
        for (PoolButton poolButton : poolButtons) {
            poolButton.setSelected(poolButton.getPoolID() == poolD);
        }
    }

    /** 卡池按钮的索引并不等于卡池id，使用该函数可以获取，不存在则返回null */
    protected PoolButton getPoolBtnByPoolId(int poolId) {
        for (PoolButton poolButton : poolButtons) {
            if (poolButton.getPoolID() == poolId)
                return poolButton;
        }
        return null;
    }

    private void setSidebarScroll(float nextScrollOffset) {
        sidebarScrollOffset = Mth.clamp(nextScrollOffset, 0.0f, (float) getSidebarMaxScroll());
        updatePoolButtonPositions();
    }
    public void setInitPoolId(int poolId) {
        initPoolId = poolId;
    }

    private void updatePoolButtonPositions() {
        if (layoutMetrics == null)
            return;
        int baseX = layoutMetrics.poolButtonX;
        int baseY = layoutMetrics.poolButtonY - Math.round(sidebarScrollOffset);
        for (int i = 0; i < poolButtons.size(); ++i) {
            PoolButton poolButton = poolButtons.get(i);
            poolButton.setX(baseX);
            poolButton.setY(baseY + i * layoutMetrics.poolButtonStep);
            poolButton.setWidth(layoutMetrics.poolButtonWidth);
            poolButton.setHeight(layoutMetrics.poolButtonHeight);
        }
    }

    private int getSidebarContentHeight() {
        if (layoutMetrics == null)
            return 0;
        return Math.max(0, poolButtons.size() * layoutMetrics.poolButtonStep + layoutMetrics.poolButtonHeight - layoutMetrics.poolButtonStep + layoutMetrics.poolButtonY - layoutMetrics.topY);
    }

    private int getSidebarMaxScroll() {
        if (layoutMetrics == null)
            return 0;
        return Math.max(0, getSidebarContentHeight() - layoutMetrics.sidebarViewportHeight);
    }

    private boolean isPointInSidebar(double mouseX, double mouseY) {
        if (layoutMetrics == null)
            return false;
        return mouseX >= layoutMetrics.leftX
                && mouseX < layoutMetrics.leftX + layoutMetrics.sidebarWidth
                && mouseY >= layoutMetrics.topY
            && mouseY < layoutMetrics.topY + layoutMetrics.sidebarViewportHeight;
    }

    private int getScrollbarX() {
        return layoutMetrics.leftX + layoutMetrics.sidebarWidth - SIDEBAR_SCROLLBAR_WIDTH;
    }

    private int getScrollbarThumbHeight() {
        int contentHeight = Math.max(1, getSidebarContentHeight());
        int viewportHeight = Math.max(1, layoutMetrics.sidebarViewportHeight);
        return Math.max(18, viewportHeight * viewportHeight / contentHeight);
    }

    private int getScrollbarThumbY() {
        int maxScroll = getSidebarMaxScroll();
        if (maxScroll <= 0)
            return layoutMetrics.topY;
        int travel = Math.max(0, layoutMetrics.sidebarViewportHeight - getScrollbarThumbHeight());
        return layoutMetrics.topY + Math.round((sidebarScrollOffset / maxScroll) * travel);
    }

    private boolean isPointInSidebarScrollbar(double mouseX, double mouseY) {
        if (layoutMetrics == null || getSidebarMaxScroll() <= 0)
            return false;
        int scrollbarX = getScrollbarX();
        return mouseX >= scrollbarX
                && mouseX < scrollbarX + SIDEBAR_SCROLLBAR_WIDTH
                && mouseY >= layoutMetrics.topY
            && mouseY < layoutMetrics.topY + layoutMetrics.sidebarViewportHeight;
    }

    private void updateSidebarScrollFromMouse(double mouseY) {
        int maxScroll = getSidebarMaxScroll();
        if (maxScroll <= 0)
            return;
        int thumbHeight = getScrollbarThumbHeight();
        int travel = Math.max(1, layoutMetrics.sidebarViewportHeight - thumbHeight);
        float normalized = (float) ((mouseY - layoutMetrics.topY - thumbHeight / 2.0f) / travel);
        setSidebarScroll(Mth.clamp(normalized, 0.0f, 1.0f) * maxScroll);
    }

    private void renderSidebarScrollbarBackground(GuiGraphics guiGraphics) {
        if (layoutMetrics == null)
            return;
        int scrollbarBgX = layoutMetrics.leftX + layoutMetrics.sidebarWidth - SIDEBAR_SCROLLBAR_BG_WIDTH;
        guiGraphics.fill(scrollbarBgX, layoutMetrics.topY,
                layoutMetrics.leftX + layoutMetrics.sidebarWidth,
                layoutMetrics.topY + layoutMetrics.sidebarViewportHeight,
                sidebarScrollAreaColor.getRGB());
    }

    private void renderSidebarScrollbar(GuiGraphics guiGraphics) {
        if (layoutMetrics == null || getSidebarMaxScroll() <= 0)
            return;
        int scrollbarX = getScrollbarX();
        int thumbY = getScrollbarThumbY();
        int thumbHeight = getScrollbarThumbHeight();
        guiGraphics.fill(scrollbarX, layoutMetrics.topY, scrollbarX + SIDEBAR_SCROLLBAR_WIDTH,
                layoutMetrics.topY + layoutMetrics.sidebarViewportHeight, sidebarScrollTrackColor.getRGB());
        guiGraphics.fill(scrollbarX, thumbY, scrollbarX + SIDEBAR_SCROLLBAR_WIDTH,
                thumbY + thumbHeight, sidebarScrollThumbColor.getRGB());
    }

    /** 背景左下角标题：星空曙光 — 横线 — 传承圣剑（整体 2 倍放大） */
    private void renderPoolTitleText(GuiGraphics guiGraphics, TextureWidget sketch) {
        final float titleScale = 2.0F;
        String line1 = "星空曙光";
        String line2 = "传承圣剑";
        // 未缩放坐标系内的块高：上文字 8 + 间隙 2 + 横线 1 + 间隙 2 + 下文字 8 = 21
        final int blockH = 21;
        int baseX = sketch.getX() + 8;
        int baseY = sketch.getY() + sketch.getHeight() - Math.round(blockH * titleScale) - 8;
        int lineW = Math.max(font.width(line1), font.width(line2)) + 8;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(baseX, baseY, 0.0F);
        guiGraphics.pose().scale(titleScale, titleScale, 1.0F);
        // 上行文字
        guiGraphics.drawString(font, line1, 0, 0, 0xFFFFFFFF, true);
        // 中间横线（缩放坐标系内画，粗细随整体放大）
        guiGraphics.fill(0, 10, lineW, 11, 0xFFD0B27A);
        // 下行文字
        guiGraphics.drawString(font, line2, 0, 13, 0xFFE8D8B0, true);
        guiGraphics.pose().popPose();
    }

    public void showLootPopup(String text) {
        popupText = text;
        popupExpireMs = System.currentTimeMillis() + 2500;
    }

    /** 页面内临时提示框：顶部居中，超时自动淡出 */
    private void renderLootPopup(GuiGraphics guiGraphics) {
        if (popupText == null)
            return;
        long remain = popupExpireMs - System.currentTimeMillis();
        if (remain <= 0) {
            popupText = null;
            return;
        }
        int alpha = (int) (Mth.clamp(remain / 500.0F, 0.0F, 1.0F) * 255.0F) << 24;
        Component text = Component.literal(popupText);
        int w = font.width(text) + 28;
        int h = font.lineHeight + 14;
        int x = centerX - w / 2;
        int y = layoutMetrics != null ? Math.max(6, layoutMetrics.topY - h - 8) : 6;
        // 底 + 四边描边
        guiGraphics.fill(x, y, x + w, y + h, alpha | 0x202B39);
        guiGraphics.fill(x, y, x + w, y + 1, alpha | 0xD0B27A);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, alpha | 0xD0B27A);
        guiGraphics.fill(x, y, x + 1, y + h, alpha | 0xD0B27A);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, alpha | 0xD0B27A);
        guiGraphics.drawString(font, text, x + (w - font.width(text)) / 2,
                y + (h - font.lineHeight) / 2 + 1, alpha | 0xFFF0E8D7, false);
    }

    public int getCoinNumber() {
        return coinNumber;
    }
    public void setCoinNumber(int coinNumber) {
        this.coinNumber = coinNumber;
    }
    public int getLotteryChance() {
        return lotteryChance;
    }
    public void setLotteryChance(int lotteryChance) {
        this.lotteryChance = lotteryChance;
    }

    private static final Color poolBtnBgColor = new Color(0xFF202B39, true);
    private static final Color sketchBgColor = new Color(0xFFF3EAD9, true);
    private static final Color actionBarBgColor = new Color(0xFF2C3646, true);
    private static final Color actionBarLineColor = new Color(0xFFD0B27A, true);
    private static final Color panelDividerColor = new Color(0xFFB69A68, true);
    private static final Color buttonIdleColor = new Color(0xFF243547, true);
    private static final Color buttonHoverColor = new Color(0xFF31465E, true);
    private static final Color buttonPressedColor = new Color(0xFF172433, true);
    private static final Color buttonBorderColor = new Color(0xFF8D734A, true);
    private static final Color selectedIdleColor = new Color(0xFFD7B47A, true);
    private static final Color selectedHoverColor = new Color(0xFFE3C48C, true);
    private static final Color selectedPressedColor = new Color(0xFFC29C63, true);
    private static final Color selectedBorderColor = new Color(0xFFF7E7BE, true);
    private static final Color buttonTextColor = new Color(0xFFF0E8D7, true);
    private static final Color selectedTextColor = new Color(0xFF1D2734, true);
    private static final Color sidebarScrollAreaColor = new Color(0x88304052, true);
    private static final Color sidebarScrollTrackColor = new Color(0x55263645, true);
    private static final Color sidebarScrollThumbColor = new Color(0xCCDEC08A, true);
    private static final int BASE_SKETCH_WIDTH = 320;
    private static final int BASE_SKETCH_HEIGHT = 180;
    private static final int BASE_POOL_BUTTON_HEIGHT = (int) (40);
    private static final int BASE_SIDEBAR_WIDTH = 108;
    private static final int BASE_SIDEBAR_PADDING = 12;
    private static final int BASE_SKETCH_EDGE = 36;
    private static final int BASE_ACTION_BAR_HEIGHT = 40;
    private static final int BASE_ACTION_BUTTON_HEIGHT = (int) (18 * 1.5f);
    private static final int BASE_ACTION_BTN_SPACING = 12;
    private static final int BASE_TOTAL_WIDTH = BASE_SKETCH_EDGE + BASE_SKETCH_WIDTH + BASE_SIDEBAR_WIDTH;
    private static final int BASE_TOTAL_HEIGHT = BASE_SKETCH_EDGE + BASE_SKETCH_HEIGHT + BASE_ACTION_BAR_HEIGHT;
    private static final int MAX_VISIBLE_POOL_BUTTONS = 6;
    private static final int MIN_POOL_BUTTON_HEIGHT = 18;
    private static final int MAX_POOL_BUTTON_HEIGHT = 28;
    private static final int MAX_POOL_BUTTON_GAP = 6;
    private static final int SIDEBAR_SCROLLBAR_BG_WIDTH = 10;
    private static final int SIDEBAR_SCROLLBAR_WIDTH = 5;
    private static final float MIN_LAYOUT_SCALE = 0.72f;
    private static final float MAX_LAYOUT_SCALE = 1.28f;
    /** 背景初始化时间 */
    private static final float initBgTime = 0.5f;
    private static final float initWidgetTime = 1.0f;
    /** 卡池动态背景：24fps 播放 run/video/lottery_starry 帧序列，游戏内 GUI 不清空（clearWhenInWorld=false） */
    private static final float POOL_VIDEO_FPS = 16.0F;
    public static final FrameAnimationRenderer POOL_VIDEO =
            new FrameAnimationRenderer(POOL_VIDEO_FPS, EXSREClient.LOTTERY_VIDEO_DIR, false);
    private List<PoolButton> poolButtons = null;
    private List<AbstractWidget> renderWidgets = null;
    /** 动画栈，用于实时管理当前运行的动画 */
    private List<AbstractAnimation> animationStack = null;
    /** 动画控制器，没啥用就是一个纯用来管理动画的widget */
    private AnimationTimeLineManager animationTimeLineManager = null;
    /** 开始抽卡按钮 */
    private PoolButton startPoolBtn = null;
    /** 多抽按钮 */
    private PoolButton multiPoolBtn = null;
    /** 预览按钮 */
    private PoolButton previewBtn = null;
    /** 预览按钮 */
    private Button viewPoolBtn = null;
    /** 动画控制器，没啥用就是一个纯用来管理动画的widget */
    private AnimationController animationController = null;
    private LotteryManager.LotteryPool curPool = null;
    /** 卡池立绘 */
    private TextureWidget poolSketch = null;
    private LayoutMetrics layoutMetrics = null;
    protected Screen parent;
    private boolean draggingSidebarScrollbar = false;
    private boolean initialized;
    private float sidebarScrollOffset = 0.0f;
    private int initPoolId = 1;
    /** 硬币数量 */
    private int coinNumber = 0;
    /** 抽卡次数 */
    private int lotteryChance = 0;
    /** 页面内提示弹窗 */
    private String popupText = null;
    private long popupExpireMs = 0;
    /** 保底计数（服务端同步）：距上次出传承的累计抽数 */
    private int pityCount = 0;
    private int lootRefreshTick = 0;

    /** 保底进度：显示在背景立绘右下角，临近保底时高亮 */
    private void renderPityInfo(GuiGraphics guiGraphics) {
        if (layoutMetrics == null)
            return;
        String text = "保底: " + pityCount + " / " + LotteryManager.PITY_THRESHOLD;
        int x = layoutMetrics.sketchX + layoutMetrics.sketchWidth - font.width(text) - 10;
        int y = layoutMetrics.sketchY + layoutMetrics.sketchHeight - font.lineHeight - 8;
        guiGraphics.drawString(font, text, x + 1, y + 1, 0x99000000, false);
        guiGraphics.drawString(font, text, x, y,
                pityCount + 1 >= LotteryManager.PITY_THRESHOLD ? 0xFFFFE4AF : 0xFFDCE4EE, false);
    }

    public static ResourceLocation getPoolSketchTexture(int poolID) {
        return ResourceLocation.fromNamespaceAndPath(
                "noellesroles", "textures/gui/loot/pool_bg" + poolID + ".png");
    }
}
