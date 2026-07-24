package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.agmas.noellesroles.cs2.CS2SkinInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * CS2 风格开箱动画界面
 * <p>
 * 横向滚动卡片条 + 中央红色指针。
 * 加速→匀速→贝塞尔减速→停止，然后展示结果。
 * </p>
 */
public class CS2CaseOpeningScreen extends Screen {

    // 品质发光颜色
    private static final int[] QUALITY_GLOW_COLORS = {
            0x40AAAAAA, 0x5000CC00, 0x600066FF, 0x70AA00FF, 0x80FFAA00, 0x90FF3333,
    };
    private static final int[] QUALITY_GLOW_INNER_COLORS = {
            0x30CCCCCC, 0x4000FF00, 0x500088FF, 0x60CC44FF, 0x70FFCC00, 0x80FF6666,
    };
    private static final String[] QUALITY_NAMES = {
            "普通", "罕见", "稀有", "史诗", "传说", "不可思议"
    };
    private static final int[] QUALITY_TEXT_COLORS = {
            0xFFEEEEEE, 0xFF33FF55, 0xFFAAAAFF, 0xFFAA55FF, 0xFFFFAA55, 0xFFFF3F3F,
    };

    // 卡片数据
    private final List<CardData> cards = new ArrayList<>();
    private final int endCardIdx;
    private final int resultQuality;
    private final String resultSkinId;
    private final boolean isDuplicate;

    // 动画状态
    private float scrollPosition = 0f;
    private float scrollSpeed = 0f;
    private float maxSpeed = 25f;
    private float accelerationRate = 0.6f;
    private boolean phase_accel = true;
    private boolean phase_cruise = false;
    private boolean phase_decel = false;
    private boolean phase_done = false;
    private int cruiseTicks = 50;
    private int cruiseCounter = 0;
    private float decelTarget = 0f; // 减速阶段的目标位置
    private int lastPassedCardIdx = -1;

    // 结果展示
    private float resultAnimProgress = 0f;
    private int ticksSinceStop = 0;

    // 布局
    private int cardW = 56;
    private int cardH = 56;
    private int cardGap = 8;
    private int pointerX;

    public CS2CaseOpeningScreen(List<Integer> cardQualities, List<String> cardSkinIds,
                                 int endCardIdx, int resultQuality, String resultSkinId,
                                 boolean isDuplicate) {
        super(Component.literal("开箱"));
        this.endCardIdx = endCardIdx;
        this.resultQuality = resultQuality;
        this.resultSkinId = resultSkinId;
        this.isDuplicate = isDuplicate;

        for (int i = 0; i < cardQualities.size(); i++) {
            cards.add(new CardData(cardQualities.get(i), cardSkinIds.get(i)));
        }
    }

    @Override
    protected void init() {
        super.init();
        pointerX = width / 2;
        // 计算目标滚动位置
        decelTarget = endCardIdx * (cardW + cardGap) + cardW / 2f;
    }

    @Override
    public void tick() {
        super.tick();

        if (phase_done) {
            ticksSinceStop++;
            if (resultAnimProgress < 1f) {
                resultAnimProgress = Mth.clamp(resultAnimProgress + 0.04f, 0f, 1f);
            }
            return;
        }

        // 动画阶段
        if (phase_accel) {
            scrollSpeed = Mth.clamp(scrollSpeed + accelerationRate, 0, maxSpeed);
            if (scrollSpeed >= maxSpeed) {
                phase_accel = false;
                phase_cruise = true;
            }
        } else if (phase_cruise) {
            cruiseCounter++;
            if (cruiseCounter >= cruiseTicks) {
                phase_cruise = false;
                phase_decel = true;
            }
        } else if (phase_decel) {
            // 使用 lerp 向目标位置收敛，保证精确停止
            float remaining = decelTarget - scrollPosition;
            if (Math.abs(remaining) < 0.5f) {
                scrollPosition = decelTarget;
                scrollSpeed = 0;
                phase_done = true;
                // 播放结果音效
                if (resultQuality >= 4) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f));
                } else {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f));
                }
            } else {
                // 以剩余距离的 4% 速度逼近（平缓指数衰减曲线，保证精确停止）
                scrollSpeed = remaining * 0.04f;
                // 限制最大减速速度，避免减速初期速度突变
                if (scrollSpeed > maxSpeed) scrollSpeed = maxSpeed;
                scrollPosition += scrollSpeed;
            }
        } else {
            scrollPosition += scrollSpeed;
        }

        // Tick 音效：每当经过一张新卡片
        int currentCardIdx = (int) (scrollPosition / (cardW + cardGap));
        if (currentCardIdx > lastPassedCardIdx && currentCardIdx < cards.size()) {
            lastPassedCardIdx = currentCardIdx;
            float pitch = 0.8f + Mth.clamp((float) currentCardIdx / cards.size(), 0f, 0.5f);
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // 暗色背景
        guiGraphics.fill(0, 0, width, height, 0xDD080B18);
        super.render(guiGraphics, mouseX, mouseY, delta);

        int centerY = height / 2;

        // 绘制卡片条
        renderCardStrip(guiGraphics, centerY, mouseX, mouseY, delta);

        // 绘制中央红色指针
        renderPointer(guiGraphics, centerY);

        // 结果展示
        if (phase_done) {
            renderResult(guiGraphics);
        }

        // 标题
        guiGraphics.drawCenteredString(font, "正在开启箱子...", width / 2, 12, 0xFFE8D6A8);
    }

    private void renderCardStrip(GuiGraphics guiGraphics, int centerY, int mouseX, int mouseY, float delta) {
        int stripY = centerY - cardH / 2;

        // 目标停止位置: endCardIdx 卡片中心对齐 pointerX
        float currentScroll;

        if (phase_done) {
            currentScroll = decelTarget;
        } else {
            currentScroll = scrollPosition;
        }

        // 渲染可见卡片
        for (int i = 0; i < cards.size(); i++) {
            float cardCenterX = pointerX + (i * (cardW + cardGap) + cardW / 2f) - currentScroll;
            int cardX = (int) (cardCenterX - cardW / 2f);

            // 跳过屏幕外的卡片
            if (cardX + cardW < -50 || cardX > width + 50) continue;

            CardData card = cards.get(i);
            boolean isTarget = (i == endCardIdx && phase_done);

            // 品质发光
            if (card.quality >= 2) {
                int glowSize = Math.max(2, card.quality);
                int glowColor = QUALITY_GLOW_COLORS[Math.min(card.quality, 5)];
                guiGraphics.fill(cardX - glowSize, stripY - glowSize,
                        cardX + cardW + glowSize, stripY + cardH + glowSize, glowColor);
                int innerGlow = QUALITY_GLOW_INNER_COLORS[Math.min(card.quality, 5)];
                guiGraphics.fill(cardX - 1, stripY - 1,
                        cardX + cardW + 1, stripY + cardH + 1, innerGlow);
            }

            // 卡片背景
            int bgColor = isTarget ? 0x60FFD700 : 0x30FFFFFF;
            guiGraphics.fill(cardX, stripY, cardX + cardW, stripY + cardH, bgColor);

            // 品质边框
            int borderColor = QUALITY_TEXT_COLORS[Math.min(card.quality, 5)];
            guiGraphics.fill(cardX - 1, stripY - 1, cardX + cardW + 1, stripY, borderColor);
            guiGraphics.fill(cardX - 1, stripY + cardH, cardX + cardW + 1, stripY + cardH + 1, borderColor);
            guiGraphics.fill(cardX - 1, stripY, cardX, stripY + cardH, borderColor);
            guiGraphics.fill(cardX + cardW, stripY, cardX + cardW + 1, stripY + cardH, borderColor);

            // 皮肤物品渲染（使用 renderFakeItem 通过 GeneralModel 系统）
            renderSkinCard(guiGraphics, card.skinId, cardX + 8, stripY + 6, 40);
        }
    }

    private void renderPointer(GuiGraphics guiGraphics, int centerY) {
        int pointerH = cardH + 20;
        int pointerTop = centerY - pointerH / 2;
        // 红色指针线
        guiGraphics.fill(pointerX - 1, pointerTop, pointerX + 2, pointerTop + pointerH, 0xFFFF3333);
        // 三角箭头（上）
        guiGraphics.fill(pointerX - 5, pointerTop - 6, pointerX + 6, pointerTop, 0xFFFF3333);
        // 三角箭头（下）
        guiGraphics.fill(pointerX - 5, pointerTop + pointerH, pointerX + 6, pointerTop + pointerH + 6, 0xFFFF3333);
    }

    private void renderResult(GuiGraphics guiGraphics) {
        float alpha = Mth.clamp(resultAnimProgress * 2f, 0f, 1f);
        if (alpha <= 0) return;

        int alphaByte = (int) (alpha * 255);
        int overlayColor = (alphaByte << 24) | 0x202020;
        guiGraphics.fill(0, 0, width, height, overlayColor);

        // 结果卡片放大
        float scale = 1f + resultAnimProgress * 0.5f;
        int centerX = width / 2;
        int centerY = height / 2 + 60;
        int size = (int) (80 * scale);

        int x = centerX - size / 2;
        int y = centerY - size / 2;

        // 发光
        int glowColor = QUALITY_GLOW_COLORS[Math.min(resultQuality, 5)];
        int glowSize = (int) (8 * scale);
        guiGraphics.fill(x - glowSize, y - glowSize, x + size + glowSize, y + size + glowSize, glowColor);

        // 卡片背景
        guiGraphics.fill(x, y, x + size, y + size, 0x40FFFFFF);

        // 品质边框
        int borderColor = QUALITY_TEXT_COLORS[Math.min(resultQuality, 5)];
        guiGraphics.fill(x - 2, y - 2, x + size + 2, y, borderColor);
        guiGraphics.fill(x - 2, y + size, x + size + 2, y + size + 2, borderColor);
        guiGraphics.fill(x - 2, y, x, y + size, borderColor);
        guiGraphics.fill(x + size, y, x + size + 2, y + size, borderColor);

        // 皮肤物品渲染（使用指针所指卡片的真实数据）
        String actualResultSkinId2 = resultSkinId;
        if (endCardIdx >= 0 && endCardIdx < cards.size()) {
            actualResultSkinId2 = cards.get(endCardIdx).skinId;
        }
        renderSkinCard(guiGraphics, actualResultSkinId2, x + 10, y + 8, size - 20);

        // 结果文本（使用指针所指卡片的真实数据）
        String actualResultSkinId = resultSkinId;
        int actualResultQuality = resultQuality;
        if (endCardIdx >= 0 && endCardIdx < cards.size()) {
            actualResultSkinId = cards.get(endCardIdx).skinId;
            actualResultQuality = cards.get(endCardIdx).quality;
        }

        if (resultAnimProgress > 0.5f) {
            float textAlpha = Mth.clamp((resultAnimProgress - 0.5f) * 2f, 0f, 1f);
            int textColor = (int) (textAlpha * 255) << 24 | 0xFFFFFF;
            String skinName = CS2SkinInfo.getName(actualResultSkinId);

            guiGraphics.drawCenteredString(font, skinName, centerX, y + size + 10, textColor);

            int qIdx = Math.min(actualResultQuality, QUALITY_NAMES.length - 1);
            int qualityColor = QUALITY_TEXT_COLORS[qIdx];
            guiGraphics.drawCenteredString(font, QUALITY_NAMES[qIdx], centerX, y + size + 24, qualityColor);

            if (isDuplicate) {
                guiGraphics.drawCenteredString(font, "(\u91cd\u590d)",
                        centerX, y + size + 38, 0xFFAAAAAA);
            }

            if (resultAnimProgress >= 1f) {
                guiGraphics.drawCenteredString(font, "点击任意位置关闭",
                        width / 2, height - 30, 0x80FFFFFF);
            }
        }
    }

    /**
     * 在指定位置渲染皮肤物品图标（通过 renderFakeItem + GeneralModel 系统）
     */
    private void renderSkinCard(GuiGraphics guiGraphics, String skinId, int x, int y, int size) {
        ItemStack stack = getSkinItemStack(skinId);
        if (stack != null && !stack.isEmpty()) {
            String[] parts = skinId.split("/");
            if (parts.length >= 2) {
                stack.set(SREDataComponentTypes.SKIN, parts[1]);
            }
            float scale = size / 16f;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0);
            guiGraphics.pose().scale(scale, scale, 1f);
            guiGraphics.renderFakeItem(stack, 0, 0);
            guiGraphics.pose().popPose();
        } else {
            // 无法获取物品时回退到贴图渲染
            net.minecraft.resources.ResourceLocation skinTex = LootScreenUtils.getItemResourceLocation(skinId);
            guiGraphics.blit(skinTex, x, y, size, size, 0, 0, 16, 16, 16, 16);
        }
    }

    private static ItemStack getSkinItemStack(String skinId) {
        if (skinId == null) return null;
        if (skinId.startsWith("knife/")) return TMMItems.KNIFE.getDefaultInstance();
        if (skinId.startsWith("revolver/") || skinId.startsWith("gun/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("bat/")) return TMMItems.BAT.getDefaultInstance();
        if (skinId.startsWith("grenade/")) return TMMItems.GRENADE.getDefaultInstance();
        if (skinId.startsWith("hat/")) return new ItemStack(Items.LEATHER_HELMET);
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (phase_done && resultAnimProgress >= 0.9f) {
            CS2WarehouseScreen.isBoxOpening = false; // 重置开箱锁
            minecraft.setScreen(null);
            return true;
        }
        // 允许跳过动画
        if (!phase_done) {
            phase_done = true;
            scrollPosition = decelTarget; // 直接跳转到目标位置
            scrollSpeed = 0;
            resultAnimProgress = 0f;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 卡片数据 */
    private static class CardData {
        final int quality;
        final String skinId;

        CardData(int quality, String skinId) {
            this.quality = quality;
            this.skinId = skinId;
        }
    }
}
