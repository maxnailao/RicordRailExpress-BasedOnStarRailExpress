package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.content.minigame.doudizhu.DoudizhuSession;
import io.wifi.starrailexpress.network.packet.DoudizhuBidC2SPacket;
import io.wifi.starrailexpress.network.packet.DoudizhuJoinC2SPacket;
import io.wifi.starrailexpress.network.packet.DoudizhuPlayC2SPacket;
import io.wifi.starrailexpress.network.packet.DoudizhuStateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 斗地主小游戏客户端界面
 * <p>
 * 多人联网对战，服务端权威。
 * 底部显示玩家手牌，左上/右上显示对手信息，中央显示出牌区域。
 * </p>
 */
public class DoudizhuMinigameScreen extends Screen {

    // ── 常量 ──
    private static final int CARD_W = 40;
    private static final int CARD_H = 56;
    private static final int CARD_SPACING = 20; // 每张牌占用的水平空间
    private static final int SELECT_OFFSET = 15; // 选中牌上移像素
    private static final int SMALL_CARD_W = 18;
    private static final int SMALL_CARD_H = 25;
    private static final int BG_COLOR = 0xFF2D5016;   // 深绿背景
    private static final int PANEL_COLOR = 0xCC1A3A0A; // 面板色
    private static final int SELECTED_TINT = 0xFFFFFFAA; // 选中牌微黄

    // 花色贴图
    private static final ResourceLocation CARD_TEX =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/doudizhu/paidi.png");
    private static final ResourceLocation[] SUIT_TEX = {
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/doudizhu/heitao.png"),    // 0=黑桃
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/doudizhu/hongtao.png"),   // 1=红桃
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/doudizhu/heimeihua.png"), // 2=梅花
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/doudizhu/hongfang.png"),  // 3=方块
    };

    private static final String[] RANK_NAMES = {
        "3","4","5","6","7","8","9","10","J","Q","K","A","2","小","大","大"
    };

    // ── 游戏状态 ──
    private enum Phase { WAITING, BIDDING, PLAYING, ENDED }
    private Phase phase = Phase.WAITING;

    private int playerIndex = 0;
    private int currentTurn = -1;
    private int landlordIndex = -1;
    private int[] myHand = new int[0];
    private int oppCount1 = 0; // (playerIndex+1)%3 的对手牌数
    private int oppCount2 = 0; // (playerIndex+2)%3 的对手牌数
    private int[] bottomCards = new int[0];
    private int[] lastPlayed = new int[0];
    private int lastPlayedBy = -1;
    private int consecutivePasses = 0;
    private int[] bids = {0, 0, 0};
    private String[] playerNames = {"", "", ""};
    private byte winnerSide = -1;
    private int waitCount = 0;

    // ── 选中的牌 ──
    private final Set<Integer> selectedIndices = new LinkedHashSet<>();

    // ── 按钮 ──
    private Button btnBid1, btnBid2, btnBid3, btnBidPass;
    private Button btnPlay, btnPass;
    private Button btnFillAI;

    private final Runnable onSuccess;

    public DoudizhuMinigameScreen(BlockPos pos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.doudizhu.title"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        super.init();
        // 发送加入包
        ClientPlayNetworking.send(new DoudizhuJoinC2SPacket(DoudizhuJoinC2SPacket.ACTION_JOIN));
        createButtons();
    }

    private void createButtons() {
        int cx = width / 2;
        int by = height - 22;

        // 叫地主按钮
        btnBidPass = Button.builder(Component.literal("不叫"), b -> sendBid(0))
                .bounds(cx - 110, by, 50, 18).build();
        btnBid1 = Button.builder(Component.literal("1分"), b -> sendBid(1))
                .bounds(cx - 55, by, 50, 18).build();
        btnBid2 = Button.builder(Component.literal("2分"), b -> sendBid(2))
                .bounds(cx, by, 50, 18).build();
        btnBid3 = Button.builder(Component.literal("3分"), b -> sendBid(3))
                .bounds(cx + 55, by, 50, 18).build();

        // 出牌按钮
        btnPlay = Button.builder(Component.literal("出牌"), b -> sendPlay())
                .bounds(cx - 55, by, 50, 18).build();
        btnPass = Button.builder(Component.literal("不出"), b -> sendPass())
                .bounds(cx + 5, by, 50, 18).build();

        // AI补位按钮
        btnFillAI = Button.builder(Component.literal("AI补位开局"), b ->
                ClientPlayNetworking.send(new DoudizhuJoinC2SPacket(DoudizhuJoinC2SPacket.ACTION_FILL_AI)))
                .bounds(cx - 50, by - 30, 100, 18).build();

        addWidget(btnBidPass); addWidget(btnBid1);
        addWidget(btnBid2); addWidget(btnBid3);
        addWidget(btnPlay); addWidget(btnPass);
        addWidget(btnFillAI);
    }

    private void updateButtonVisibility() {
        btnBidPass.visible = false; btnBid1.visible = false;
        btnBid2.visible = false; btnBid3.visible = false;
        btnPlay.visible = false; btnPass.visible = false;
        btnFillAI.visible = false;

        if (phase == Phase.WAITING) {
            btnFillAI.visible = true;
        } else if (phase == Phase.BIDDING && currentTurn == playerIndex) {
            btnBidPass.visible = true; btnBid1.visible = true;
            btnBid2.visible = bids[playerIndex] < 2 || true; // 总是可见
            btnBid3.visible = true;
            // 禁用低于当前最高叫分的选项
        } else if (phase == Phase.PLAYING && currentTurn == playerIndex) {
            btnPlay.visible = true;
            boolean mustPlay = (lastPlayed.length == 0 || lastPlayedBy == playerIndex || consecutivePasses >= 2);
            btnPass.visible = !mustPlay;
        }
    }

    // ── 状态接收 ──

    public void onStateReceived(DoudizhuStateS2CPacket pkt) {
        this.playerIndex = pkt.playerIndex();
        this.currentTurn = pkt.currentTurn();
        this.landlordIndex = pkt.landlordIndex();
        this.myHand = pkt.myHand() != null ? pkt.myHand() : new int[0];
        sortClientHand(this.myHand);
        this.oppCount1 = pkt.oppCount1();
        this.oppCount2 = pkt.oppCount2();
        this.bottomCards = pkt.bottomCards() != null ? pkt.bottomCards() : new int[0];
        this.lastPlayed = pkt.lastPlayed() != null ? pkt.lastPlayed() : new int[0];
        this.lastPlayedBy = pkt.lastPlayedBy();
        this.consecutivePasses = pkt.consecutivePasses();
        this.bids = pkt.bids() != null ? pkt.bids() : new int[]{0, 0, 0};
        this.playerNames = pkt.playerNames() != null ? pkt.playerNames() : new String[]{"", "", ""};
        this.winnerSide = pkt.winnerSide();
        this.selectedIndices.clear();

        switch (pkt.action()) {
            case DoudizhuStateS2CPacket.WAITING:
                phase = Phase.WAITING;
                this.waitCount = 0; // 根据名字数量推断
                for (String n : playerNames) if (!n.isEmpty()) waitCount++;
                break;
            case DoudizhuStateS2CPacket.BIDDING:
                phase = Phase.BIDDING;
                break;
            case DoudizhuStateS2CPacket.PLAY:
            case DoudizhuStateS2CPacket.PASS:
                phase = Phase.PLAYING;
                break;
            case DoudizhuStateS2CPacket.WIN:
                phase = Phase.ENDED;
                break;
            case DoudizhuStateS2CPacket.OPPONENT_LEFT:
                phase = Phase.ENDED;
                break;
        }
    }

    // ── 卡牌排序 ──

    private void sortClientHand(int[] hand) {
        Integer[] boxed = new Integer[hand.length];
        for (int i = 0; i < hand.length; i++) boxed[i] = hand[i];
        Arrays.sort(boxed, (a, b) -> {
            int ra = DoudizhuSession.getRank(a), rb = DoudizhuSession.getRank(b);
            if (ra != rb) return Integer.compare(rb, ra);
            return Integer.compare(suitPri(a), suitPri(b));
        });
        for (int i = 0; i < hand.length; i++) hand[i] = boxed[i];
    }

    private static int suitPri(int cardId) {
        int s = DoudizhuSession.getSuit(cardId);
        if (s == 0) return 3; if (s == 1) return 2; if (s == 3) return 1; return 0;
    }

    // ── 操作发送 ──

    private void sendBid(int score) {
        ClientPlayNetworking.send(new DoudizhuBidC2SPacket(score));
    }

    private void sendPlay() {
        if (selectedIndices.isEmpty()) return;
        int[] cards = new int[selectedIndices.size()];
        int i = 0;
        for (int idx : selectedIndices) cards[i++] = myHand[idx];
        ClientPlayNetworking.send(new DoudizhuPlayC2SPacket(cards));
        selectedIndices.clear();
    }

    private void sendPass() {
        ClientPlayNetworking.send(new DoudizhuPlayC2SPacket(new int[0]));
        selectedIndices.clear();
    }

    // ── 渲染 ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        // 深绿背景
        g.fill(0, 0, width, height, BG_COLOR);

        updateButtonVisibility();

        switch (phase) {
            case WAITING -> renderWaiting(g);
            case BIDDING -> renderBidding(g);
            case PLAYING -> renderPlaying(g);
            case ENDED -> renderEnded(g);
        }

        // 渲染按钮
        renderButtons(g, mouseX, mouseY, partialTick);
    }

    private void renderButtons(GuiGraphics g, int mx, int my, float pt) {
        if (btnBidPass.visible) btnBidPass.render(g, mx, my, pt);
        if (btnBid1.visible) btnBid1.render(g, mx, my, pt);
        if (btnBid2.visible) btnBid2.render(g, mx, my, pt);
        if (btnBid3.visible) btnBid3.render(g, mx, my, pt);
        if (btnPlay.visible) btnPlay.render(g, mx, my, pt);
        if (btnPass.visible) btnPass.render(g, mx, my, pt);
        if (btnFillAI.visible) btnFillAI.render(g, mx, my, pt);
    }

    private void renderWaiting(GuiGraphics g) {
        int cx = width / 2, cy = height / 2 - 20;
        g.drawString(font, "等待玩家加入...", cx - 45, cy, 0xFFFFFFFF, true);
        g.drawString(font, "当前: " + waitCount + "/3", cx - 25, cy + 16, 0xFFAAAAAA, true);
        // 已等待玩家名字
        for (int i = 0; i < 3; i++) {
            if (playerNames[i] != null && !playerNames[i].isEmpty()) {
                g.drawString(font, playerNames[i], cx - 30, cy + 36 + i * 12, 0xFF00FF00, true);
            }
        }
    }

    private void renderBidding(GuiGraphics g) {
        // 手牌
        renderMyHand(g);
        // 对手信息
        renderOpponents(g);
        // 叫地主信息
        int cx = width / 2;
        if (currentTurn == playerIndex) {
            g.drawString(font, "轮到你叫分", cx - 30, 10, 0xFFFFFF00, true);
        } else {
            String name = getPlayerName(currentTurn);
            g.drawString(font, name + " 正在叫分...", cx - 40, 10, 0xFFCCCCCC, true);
        }
        // 显示已叫分
        for (int i = 0; i < 3; i++) {
            if (bids[i] > 0) {
                String txt = getPlayerName(i) + ": " + bids[i] + "分";
                g.drawString(font, txt, cx - 30, 26 + i * 12, 0xFFAAAAFF, true);
            }
        }
        // 底牌预览（叫地主阶段不显示，或显示牌背）
        g.drawString(font, "[ 底牌 ]", cx - 20, 60, 0xFF888888, true);
    }

    private void renderPlaying(GuiGraphics g) {
        // 手牌
        renderMyHand(g);
        // 对手信息
        renderOpponents(g);
        // 出牌区域
        renderPlayedArea(g);
        // 地主标识
        renderLandlordBadge(g);
        // 当前回合提示
        if (currentTurn == playerIndex) {
            g.drawString(font, "轮到你出牌", width / 2 - 33, 4, 0xFFFFFF00, true);
        } else {
            g.drawString(font, getPlayerName(currentTurn) + " 出牌中...", width / 2 - 40, 4, 0xFFCCCCCC, true);
        }
        // 底牌（地主确定后显示）
        if (landlordIndex >= 0 && bottomCards.length == 3) {
            int cx = width / 2;
            g.drawString(font, "底牌:", cx - 50, 18, 0xFF888888, true);
            for (int i = 0; i < 3; i++) {
                drawCardFace(g, cx - 42 + i * 28, 30, bottomCards[i], 28, 40);
            }
        }
    }

    private void renderEnded(GuiGraphics g) {
        renderMyHand(g);
        renderOpponents(g);
        renderPlayedArea(g);
        renderLandlordBadge(g);

        int cx = width / 2, cy = height / 2 - 30;
        String msg;
        if (winnerSide == -1) {
            msg = "对手离开，游戏结束";
        } else if (winnerSide == 0) {
            msg = (landlordIndex == playerIndex) ? "地主胜利！" : "地主胜利";
        } else {
            msg = (landlordIndex != playerIndex) ? "农民胜利！" : "农民胜利";
        }
        g.fill(cx - 80, cy - 10, cx + 80, cy + 30, 0xCC000000);
        g.drawString(font, msg, cx - font.width(msg) / 2, cy, 0xFFFFD700, true);
        g.drawString(font, "按 ESC 退出", cx - 25, cy + 16, 0xFFAAAAAA, true);
    }

    // ── 手牌渲染 ──

    private void renderMyHand(GuiGraphics g) {
        if (myHand.length == 0) return;
        int totalW = (myHand.length - 1) * CARD_SPACING + CARD_W;
        int startX = (width - totalW) / 2;
        int baseY = height - CARD_H - 28; // 按钮上方

        for (int i = 0; i < myHand.length; i++) {
            int x = startX + i * CARD_SPACING;
            int y = selectedIndices.contains(i) ? baseY - SELECT_OFFSET : baseY;
            drawCardFace(g, x, y, myHand[i], CARD_W, CARD_H);
            // 选中高亮
            if (selectedIndices.contains(i)) {
                g.fill(x, y, x + CARD_W, y + CARD_H, 0x30FFFF00);
            }
        }
    }

    private void drawCardFace(GuiGraphics g, int x, int y, int cardId, int w, int h) {
        // 牌底
        RenderSystem.enableBlend();
        g.blit(CARD_TEX, x, y, 0, 0, w, h, w, h);

        int rank = DoudizhuSession.getRank(cardId);
        int suit = DoudizhuSession.getSuit(cardId);

        // 颜色
        int textColor;
        if (rank == 15) textColor = 0xFFFF0000;      // 大王-红
        else if (rank == 14) textColor = 0xFF00AA00;  // 小王-绿
        else if (suit == 1 || suit == 3) textColor = 0xFFDD0000; // 红桃/方块-红
        else textColor = 0xFF111111;                   // 黑桃/梅花-黑

        // 数字/字母（左上角）
        String name = RANK_NAMES[rank];
        g.drawString(font, name, x + 2, y + 2, textColor, false);

        // 花色贴图（数字下方）
        int suitSize = Math.max(8, w / 3);
        if (suit >= 0 && suit < 4) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 0.85f);
            g.blit(SUIT_TEX[suit], x + 2, y + 11, 0, 0, suitSize, suitSize, suitSize, suitSize);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else if (rank == 14) {
            // 小王：显示“小”字
            g.drawString(font, "王", x + 2, y + 12, 0xFF00AA00, false);
        } else if (rank == 15) {
            // 大王：显示“大”字
            g.drawString(font, "王", x + 2, y + 12, 0xFFFF0000, false);
        }

        // 右下角镜像显示
        if (h >= 48) {
            int nameW = font.width(name);
            int bx = x + w - nameW - 2;
            int by2 = y + h - 10;
            g.drawString(font, name, bx, by2, textColor, false);
            if (suit >= 0 && suit < 4) {
                g.blit(SUIT_TEX[suit], x + w - suitSize - 2, y + h - suitSize - 12,
                        0, 0, suitSize, suitSize, suitSize, suitSize);
            }
        }

        RenderSystem.disableBlend();
    }

    // ── 对手渲染 ──

    private void renderOpponents(GuiGraphics g) {
        // 对手1: (playerIndex+1)%3 → 左上
        int opp1Idx = (playerIndex + 1) % 3;
        int opp2Idx = (playerIndex + 2) % 3;
        renderOpponent(g, 10, 10, oppCount1, getPlayerName(opp1Idx),
                opp1Idx == landlordIndex, opp1Idx == currentTurn);
        // 对手2: (playerIndex+2)%3 → 右上
        int opp2X = width - 10 - SMALL_CARD_W * Math.min(oppCount2, 5);
        renderOpponent(g, opp2X, 10, oppCount2, getPlayerName(opp2Idx),
                opp2Idx == landlordIndex, opp2Idx == currentTurn);
    }

    private void renderOpponent(GuiGraphics g, int x, int y, int count, String name,
                                boolean isLandlord, boolean isCurrentTurn) {
        // 名字
        int color = isCurrentTurn ? 0xFFFFFF00 : 0xFFFFFFFF;
        g.drawString(font, name + " (" + count + "张)", x, y, color, true);
        // 地主标识
        if (isLandlord) {
            g.fill(x, y + 12, x + 30, y + 24, 0xFFCC0000);
            g.drawString(font, "地主", x + 2, y + 13, 0xFFFFFFFF, true);
        }
        // 牌背（最多显示5张小牌背）
        int showCount = Math.min(count, 5);
        int cardY = y + (isLandlord ? 26 : 14);
        for (int i = 0; i < showCount; i++) {
            RenderSystem.setShaderColor(0.6f, 0.55f, 0.5f, 1f);
            g.blit(CARD_TEX, x + i * 10, cardY, 0, 0, SMALL_CARD_W, SMALL_CARD_H, SMALL_CARD_W, SMALL_CARD_H);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ── 出牌区域 ──

    private void renderPlayedArea(GuiGraphics g) {
        if (lastPlayedBy < 0) return;

        int cx = width / 2;
        int cy = height / 2 - 20;

        // 确定出牌者的屏幕位置
        int playX, playY;
        if (lastPlayedBy == playerIndex) {
            playX = cx - (lastPlayed.length * 18) / 2;
            playY = cy + 30;
        } else if (lastPlayedBy == (playerIndex + 1) % 3) {
            playX = 60;
            playY = cy - 10;
        } else {
            playX = width - 60 - lastPlayed.length * 18;
            playY = cy - 10;
        }

        // 出牌者名字
        String who = getPlayerName(lastPlayedBy);
        g.drawString(font, who + ":", playX, playY - 12, 0xFFCCCCCC, true);

        // 如果是连续pass（空），显示"不出"
        if (lastPlayed.length == 0) {
            g.drawString(font, "不出", playX + 20, playY + 8, 0xFF888888, true);
            return;
        }

        // 排序后显示
        int[] sorted = Arrays.copyOf(lastPlayed, lastPlayed.length);
        sortClientHand(sorted);
        for (int i = 0; i < sorted.length; i++) {
            drawCardFace(g, playX + i * 18, playY, sorted[i], 28, 40);
        }
    }

    // ── 地主标识 ──

    private void renderLandlordBadge(GuiGraphics g) {
        if (landlordIndex < 0) return;
        if (landlordIndex == playerIndex) {
            int handW = (myHand.length - 1) * CARD_SPACING + CARD_W;
            int sx = (width - handW) / 2;
            int sy = height - CARD_H - 42;
            g.fill(sx, sy - 2, sx + 30, sy + 10, 0xFFCC0000);
            g.drawString(font, "地主", sx + 2, sy, 0xFFFFFFFF, true);
        }
    }

    // ── 鼠标交互 ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 按钮优先处理
        if (phase == Phase.BIDDING && currentTurn == playerIndex) {
            if (btnBidPass.mouseClicked(mouseX, mouseY, button)) return true;
            if (btnBid1.mouseClicked(mouseX, mouseY, button)) return true;
            if (btnBid2.mouseClicked(mouseX, mouseY, button)) return true;
            if (btnBid3.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (phase == Phase.PLAYING && currentTurn == playerIndex) {
            if (btnPlay.mouseClicked(mouseX, mouseY, button)) return true;
            if (btnPass.visible && btnPass.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (phase == Phase.WAITING && btnFillAI.mouseClicked(mouseX, mouseY, button)) return true;

        // 手牌点击
        if ((phase == Phase.BIDDING || phase == Phase.PLAYING) && myHand.length > 0) {
            int totalW = (myHand.length - 1) * CARD_SPACING + CARD_W;
            int startX = (width - totalW) / 2;
            int baseY = height - CARD_H - 28;

            // 从右到左检查（后绘制的在上面）
            for (int i = myHand.length - 1; i >= 0; i--) {
                int x = startX + i * CARD_SPACING;
                int y = selectedIndices.contains(i) ? baseY - SELECT_OFFSET : baseY;
                if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H) {
                    if (selectedIndices.contains(i)) selectedIndices.remove(i);
                    else selectedIndices.add(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── 工具方法 ──

    private String getPlayerName(int index) {
        if (index < 0 || index >= 3) return "?";
        if (playerNames[index] != null && !playerNames[index].isEmpty()) return playerNames[index];
        return "玩家" + (index + 1);
    }

    @Override
    public void onClose() {
        ClientPlayNetworking.send(new DoudizhuJoinC2SPacket(DoudizhuJoinC2SPacket.ACTION_LEAVE));
        if (onSuccess != null) onSuccess.run();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
