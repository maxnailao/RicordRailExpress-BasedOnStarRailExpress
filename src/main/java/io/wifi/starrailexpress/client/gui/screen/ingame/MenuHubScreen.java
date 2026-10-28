package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.client.gui.endfield.EndfieldMenuRenderer;
import io.wifi.starrailexpress.client.gui.endfield.menu.animation.MenuTimeline;
import io.wifi.starrailexpress.client.gui.endfield.menu.animation.SpringValue;
import io.wifi.starrailexpress.client.gui.endfield.menu.input.PointerGesture;
import io.wifi.starrailexpress.client.gui.endfield.menu.input.ScrollMotion;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.Box;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.DiscGeometry;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.MenuLayout;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.MenuScene;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.SceneProjection;
import io.wifi.starrailexpress.client.gui.endfield.menu.model.MenuAction;
import io.wifi.starrailexpress.client.gui.endfield.menu.model.MenuEntry;
import io.wifi.starrailexpress.client.gui.endfield.menu.model.PlayerCard;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapIntroClientCache;
import io.wifi.starrailexpress.client.gui.screen.roster.RoleRosterEditScreen;
import io.wifi.starrailexpress.client.stats.ClientPlayerStatsCache;
import io.wifi.starrailexpress.content.mail.MailboxScreen;
import io.wifi.starrailexpress.network.MapIntroRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import org.lwjgl.glfw.GLFW;
import org.agmas.noellesroles.client.screen.CS2WarehouseScreen;
import org.agmas.noellesroles.client.screen.GameManagementScreen;
import org.agmas.noellesroles.client.screen.LootInfoScreen;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoCheckC2SPacket;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full EndfieldCraft-style lobby menu.  This screen is intentionally separate
 * from LimitedInventoryScreen: the latter remains the match-only inventory.
 */
public final class MenuHubScreen extends Screen {
    private static final String CLOSE = "close";
    private static final String RESUME = "resume";
    private final PointerGesture pointer = new PointerGesture();
    private final ScrollMotion scroll = new ScrollMotion();
    private final SpringValue yaw = new SpringValue();
    private final SpringValue pitch = new SpringValue();
    private final Map<String, SpringValue> hovers = new LinkedHashMap<>();
    private final Map<String, SpringValue> presses = new LinkedHashMap<>();
    private final Map<String, Runnable> actions = new LinkedHashMap<>();
    private List<MenuEntry> entries = List.of();
    private List<MenuScene.Button> buttons = List.of();
    private MenuLayout layout;
    private SceneProjection projection;
    private MenuTimeline timeline;
    private EndfieldMenuRenderer renderer;
    private PlayerCard playerCard;
    private long lastFrame;
    private boolean draggingScroll;
    private boolean reducedMotion;
    private float pointerX;
    private float pointerY;
    private long lastSnapshot;
    private String participationState;
    private String focusedId;
    private String showcaseMapId;

    public MenuHubScreen() {
        super(Component.literal("Ricord Rail Express"));
    }

    @Override
    protected void init() {
        super.init();
        reducedMotion = minecraft.options.screenEffectScale().get() == 0 && !SREClientConfig.menuAnimationsEnabled();
        entries = buildEntries();
        var window = minecraft.getWindow();
        layout = MenuLayout.create(width, height, window.getWidth(), window.getHeight(), entries);
        projection = new SceneProjection(layout, 0, 0);
        scroll.setLimit(layout.maxScroll());
        pointer.cancel();
        focusedId = null;
        draggingScroll = false;
        playerCard = capturePlayer(minecraft);
        participationState = participationLabel();
        requestShowcaseMap();
        showcaseMapId = showcaseMapId();
        try {
            renderer = EndfieldMenuRenderer.acquire(minecraft);
            MenuTimeline.Frame preparationFrame = new MenuTimeline.Frame(9999, -1, false);
            buttons = MenuScene.buttons(layout, preparationFrame, 0);
            renderer.prepare(layout, playerCard, buttons, showcaseMapId);
        } catch (RuntimeException | LinkageError failure) {
            EndfieldMenuRenderer.releaseShared();
            renderer = null;
            buttons = List.of();
        }
        long now = System.nanoTime();
        timeline = new MenuTimeline(now, reducedMotion);
        lastFrame = now;
        lastSnapshot = now;
        buttons = MenuScene.buttons(layout, timeline.sample(now), 0);
    }

    private List<MenuEntry> buildEntries() {
        actions.clear();
        List<MenuEntry> result = new ArrayList<>();
        add(result, RESUME, "返回游戏", MenuAction.RESUME, true, this::onClose);
        add(result, "accessories", "饰品", MenuAction.ADVANCEMENTS, true,
                () -> minecraft.setScreen(new AccessoryScreen(this)));
        add(result, "shop", "商城", MenuAction.STATISTICS, true,
                () -> minecraft.setScreen(new ShopScreen(this)));
        add(result, "announcements", "公告", MenuAction.FEEDBACK, true,
                () -> minecraft.setScreen(new AnnouncementScreen(this)));
        add(result, "role_cards", "职业卡", MenuAction.OPTIONS, true,
                () -> minecraft.setScreen(new RoleCardScreen(this)));
        add(result, "warehouse", "仓库", MenuAction.WORLD_OPTIONS, true,
                () -> minecraft.setScreen(new CS2WarehouseScreen()));
        add(result, "lottery", "抽奖", MenuAction.FRIENDS, true, this::openLottery);
        add(result, "mail", "邮箱", MenuAction.FEEDBACK, true,
                () -> minecraft.setScreen(new MailboxScreen()));
        add(result, "client_options", "客户端选项", MenuAction.OPTIONS, true,
                () -> minecraft.setScreen(SREClientConfig.HANDLER.generateGui().generateScreen(this)));
        boolean operator = minecraft.player != null && minecraft.player.hasPermissions(2);
        add(result, "game_management", "游戏管理", MenuAction.BUGS, operator,
                operator ? () -> minecraft.setScreen(new GameManagementScreen(this)) : this::tellPermission);
        add(result, "role_rotation", "职业轮换管理", MenuAction.REPORTING, operator,
                operator ? () -> minecraft.setScreen(new RoleRosterEditScreen()) : this::tellPermission);
        add(result, "participation", participationLabel(), MenuAction.OTHER, true,
                () -> toggleParticipation());
        add(result, "disconnect", "离开大厅", MenuAction.DISCONNECT, true, this::onClose);
        return List.copyOf(result);
    }

    private String participationLabel() {
        boolean participating = minecraft.player == null
                || ParticipationComponent.KEY.get(minecraft.player.level()).isParticipating(minecraft.player);
        return participating ? "退出本局" : "参加本局";
    }

    private void toggleParticipation() {
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand("tmm:participate");
        }
    }

    private void add(List<MenuEntry> result, String id, String label, MenuAction action,
                     boolean enabled, Runnable callback) {
        result.add(new MenuEntry(id, label, action, enabled));
        actions.put(id, callback);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (timeline != null) {
            int alpha = Math.round(205 * timeline.sample(System.nanoTime()).backdrop());
            graphics.fill(0, 0, width, height, alpha << 24 | 0x0b1415);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long entryNow = System.nanoTime();
        if (timeline != null && !reducedMotion) {
            applyBackgroundBlur(timeline.sample(entryNow).blur());
        }
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        long now = entryNow;
        float dt = Math.clamp((now - lastFrame) / 1_000_000_000f, 0, 0.05f);
        lastFrame = now;
        pointerX = mouseX;
        pointerY = mouseY;
        scroll.update(dt, draggingScroll && pointer.down());
        if (!pointer.down()) {
            yaw.update(reducedMotion || layout.compact() ? 0 : -(mouseX / (float) Math.max(width, 1) * 2 - 1) * 4, dt, 12);
            pitch.update(reducedMotion || layout.compact() ? 0 : (mouseY / (float) Math.max(height, 1) * 2 - 1) * 2.5f, dt, 12);
        }
        projection = new SceneProjection(layout, yaw.value(), pitch.value());
        MenuTimeline.Frame frame = timeline.sample(now);
        buttons = MenuScene.buttons(layout, frame, scroll.position());
        String hovered = hit(mouseX, mouseY);
        Map<String, EndfieldMenuRenderer.Feedback> feedback = new LinkedHashMap<>();
        for (MenuEntry entry : entries) {
            float over = hovers.computeIfAbsent(entry.id(), ignored -> new SpringValue())
                    .update(entry.id().equals(hovered) && entry.enabled() ? 1 : 0, dt, 22);
            float press = presses.computeIfAbsent(entry.id(), ignored -> new SpringValue())
                    .update(pointer.pressed(entry.id()) ? 1 : 0, dt, 35);
            feedback.put(entry.id(), new EndfieldMenuRenderer.Feedback(over, press,
                    entry.id().equals(focusedId)));
        }
        if (renderer != null) {
            renderer.render(graphics, layout, frame, playerCard, buttons, feedback, scroll.position(), yaw.value(), pitch.value());
        } else {
            renderFallback(graphics, frame, hovered);
        }
        int blackout = Math.round(255f * frame.blackout());
        if (blackout > 0) {
            graphics.fill(0, 0, width, height, blackout << 24);
        }
    }

    /** 按时间轴进度将背景模糊 0–3 遍，模拟清晰到 14px 模糊的渐变（关闭时逆向解除）。 */
    private void applyBackgroundBlur(float progress) {
        int passes = Math.max(1, Math.round(progress * 3f));
        for (int i = 0; i < passes; i++) {
            renderBlurredBackground(1.0f);
        }
    }

    private void renderFallback(GuiGraphics graphics, MenuTimeline.Frame frame, String hovered) {
        graphics.fill(0, 0, width, height, Math.round(205 * frame.backdrop()) << 24 | 0x0b1415);
        graphics.drawCenteredString(font, Component.literal("Ricord Rail Express"), width / 2, height / 2 - 10, 0xfff1ed48);
        graphics.drawCenteredString(font, Component.literal("Endfield menu renderer unavailable"), width / 2, height / 2 + 10, 0xffb4b4a4);
    }

    private String hit(double x, double y) {
        if (timeline == null || timeline.closing()) {
            return null;
        }
        float localX = layout.localX(x);
        float localY = layout.localY(y);
        if (timeline.sample(System.nanoTime()).controlsAlpha() > 0.8f && layout.close().contains(localX, localY)) {
            return CLOSE;
        }
        for (MenuScene.Button button : buttons.reversed()) {
            if (button.alpha() < 0.8f) {
                continue;
            }
            if (button.slot().scrollable() && !layout.scrollArea().contains(localX, localY)) {
                continue;
            }
            Box area = button.slot().style() == MenuLayout.Style.RESUME_ARC
                    ? button.box() : new Box(button.box().x(), button.box().y(),
                    Math.max(1, button.box().width() - 8), Math.max(1, button.box().height() - 10));
            boolean inside = button.slot().style() == MenuLayout.Style.RESUME_ARC
                    ? projection.containsResume(button.plane(), x, y)
                    : projection.contains(area, button.plane(), x, y);
            if (inside) {
                return button.slot().entry().id();
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || timeline.closing()) {
            return false;
        }
        String id = hit(mouseX, mouseY);
        if (id != null) {
            MenuEntry entry = entries.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
            if (entry != null && !entry.enabled()) {
                return true;
            }
        }
        pointer.begin(id, mouseX, mouseY);
        if (id != null) {
            focusedId = id;
        }
        draggingScroll = layout.maxScroll() > 0 && layout.scrollArea().contains(layout.localX(mouseX), layout.localY(mouseY));
        if (draggingScroll) {
            scroll.stop();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || !pointer.down()) {
            return false;
        }
        if (draggingScroll) {
            scroll.drag((float) dragY / Math.max(layout.scale(), 0.01f), 1 / 60f);
        }
        pointer.move(mouseX, mouseY, 6);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !pointer.down()) {
            return false;
        }
        String id = pointer.release(hit(mouseX, mouseY));
        draggingScroll = false;
        if (id != null) {
            activate(id);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout.maxScroll() > 0 && layout.scrollArea().contains(layout.localX(mouseX), layout.localY(mouseY))) {
            scroll.wheel(scrollY);
            return true;
        }
        return false;
    }

    private void activate(String id) {
        if (CLOSE.equals(id) || RESUME.equals(id)) {
            onClose();
            return;
        }
        MenuEntry entry = entries.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (entry == null || !entry.enabled()) {
            return;
        }
        if ("participation".equals(id)) {
            playClick();
            toggleParticipation();
            return;
        }
        timeline.transitionToDetail(System.nanoTime());
        playClick();
        // Keep the action until the original scene has completed its close animation.
        pendingAction = actions.get(id);
    }

    private void playClick() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private Runnable pendingAction;

    @Override
    public void tick() {
        super.tick();
        long now = System.nanoTime();
        if (timeline != null && !timeline.closing() && now - lastSnapshot >= 500_000_000L) {
            lastSnapshot = now;
            String currentParticipation = participationLabel();
            if (!currentParticipation.equals(participationState)) {
                participationState = currentParticipation;
                entries = buildEntries();
                layout = MenuLayout.create(width, height, minecraft.getWindow().getWidth(),
                        minecraft.getWindow().getHeight(), entries);
                scroll.setLimit(layout.maxScroll());
                if (renderer != null) {
                    renderer.invalidateButton("participation");
                }
            }
            PlayerCard current = capturePlayer(minecraft);
            if (!sameSnapshot(playerCard, current)) {
                playerCard = current;
                if (renderer != null) {
                    renderer.invalidatePlayerLayers();
                }
            }
            String currentMapId = showcaseMapId();
            if (!java.util.Objects.equals(showcaseMapId, currentMapId)) {
                showcaseMapId = currentMapId;
                if (renderer != null) {
                    renderer.setShowcaseMap(currentMapId);
                }
            }
        }
        if (timeline != null && timeline.finished(now)) {
            Runnable action = pendingAction;
            pendingAction = null;
            if (action != null) {
                action.run();
            }
        }
    }

    private static boolean sameSnapshot(PlayerCard first, PlayerCard second) {
        return first != null && second != null
                && first.name().equals(second.name())
                && first.daysPlayed() == second.daysPlayed()
                && Float.compare(first.dayProgress(), second.dayProgress()) == 0
                && first.world().equals(second.world())
                && first.status().equals(second.status())
                && first.mode().equals(second.mode())
                && first.connection().equals(second.connection())
                && first.heading().equals(second.heading())
                && first.daysPlayedLabel().equals(second.daysPlayedLabel())
                && first.hint().equals(second.hint());
    }

    @Override
    public void onClose() {
        if (timeline == null || timeline.closing()) {
            minecraft.setScreen(null);
        } else {
            timeline.close(System.nanoTime());
            pendingAction = () -> minecraft.setScreen(null);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (timeline == null || timeline.closing()) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_RIGHT) {
            focusNext(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_LEFT) {
            focusNext(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (focusedId != null) {
                activate(focusedId);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void focusNext(boolean backwards) {
        List<MenuEntry> focusable = entries.stream().filter(MenuEntry::enabled).toList();
        if (focusable.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < focusable.size(); i++) {
            if (focusable.get(i).id().equals(focusedId)) {
                index = i;
                break;
            }
        }
        int next = (index + (backwards ? -1 : 1) + focusable.size()) % focusable.size();
        focusedId = focusable.get(next).id();
        MenuEntry entry = focusable.get(next);
        layout.slots().stream().filter(slot -> slot.entry().id().equals(entry.id()) && slot.scrollable())
                .findFirst().ifPresent(slot -> scroll.reveal(slot.box().y(), slot.box().bottom(),
                        layout.scrollArea().y(), layout.scrollArea().height()));
    }

    @Override
    public void removed() {
        pointer.cancel();
        renderer = null;
        super.removed();
    }

    private void openLottery() {
        if (LotteryManager.getInstance().getLotteryPools().isEmpty()) {
            ClientPlayNetworking.send(new LootPoolsInfoCheckC2SPacket());
        }
        minecraft.setScreen(new LootInfoScreen(0, 0, 0, this));
    }

    private void tellPermission() {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("需要管理权限才能打开该功能"), true);
        }
    }

    private static PlayerCard capturePlayer(Minecraft client) {
        var player = client.player;
        var server = client.getSingleplayerServer();
        String world = server != null ? server.getWorldData().getLevelName()
                : client.getCurrentServer() != null ? client.getCurrentServer().name : I18n.get("efc.multiplayer");
        PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
        String connection = client.isLocalServer() ? I18n.get("efc.local")
                : info != null ? info.getLatency() + " ms" : I18n.get("efc.multiplayer");
        boolean paused = server != null && !server.isPublished();
        long cachedPlayTicks = ClientPlayerStatsCache.getOrEmpty(player.getUUID()).getTotalPlayTime();
        long vanillaPlayTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        long totalPlayTicks = Math.max(cachedPlayTicks, vanillaPlayTicks);
        long daysPlayed = totalPlayTicks / 24_000L;
        float dayProgress = (totalPlayTicks % 24_000L) / 24_000f;
        return new PlayerCard(player.getName().getString(), daysPlayed, dayProgress,
                world, I18n.get(paused ? "efc.paused" : "efc.running"),
                String.format(java.util.Locale.ROOT, "X %d  /  Y %d  /  Z %d", player.getBlockX(), player.getBlockY(), player.getBlockZ()),
                client.gameMode == null ? "SURVIVAL" : client.gameMode.getPlayerMode().getShortDisplayName().getString(),
                connection, I18n.get("menu.game"), I18n.get("efc.days_played"), I18n.get("efc.hint"));
    }

    private void requestShowcaseMap() {
        if (MapIntroClientCache.isEmpty() && !MapIntroClientCache.isRefreshPending()
                && minecraft.getConnection() != null) {
            MapIntroClientCache.beginRefresh(false);
            ClientPlayNetworking.send(new MapIntroRequestPayload(false));
        }
    }

    private static String showcaseMapId() {
        var map = MapIntroClientCache.highestWeightConfiguredMap();
        return map == null ? null : map.id();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
