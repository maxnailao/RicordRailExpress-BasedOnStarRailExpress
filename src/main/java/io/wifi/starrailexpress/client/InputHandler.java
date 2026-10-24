package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomCameraDirector;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.client.gui.screen.CommandMacroScreen;
import io.wifi.starrailexpress.client.gui.screen.MapVoteScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomBattleScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomPeekDeckScreen;
import io.wifi.starrailexpress.content.item.SniperRifleItem;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.content.vote.client.ClientVoteCache;
import io.wifi.starrailexpress.content.vote.client.GameModeVoteScreen;
import io.wifi.starrailexpress.content.vote.client.VoteScreen;
import net.exmo.sre.meeting.client.MeetingClientHandler;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.content.item.SniperRifleItem;
import org.agmas.noellesroles.content.item.DesertEagleItem;
import org.agmas.noellesroles.init.ModItems;
import io.wifi.starrailexpress.network.RequestOpenClueArchivePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class InputHandler {
    private static boolean wasRightDown = false;

    public static KeyMapping openVotingScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_voting_screen",
            GLFW.GLFW_KEY_M,
            "category.starrailexpress.keybinds"));
    public static KeyMapping openFourthRoomScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_fourth_room_screen",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.starrailexpress.keybinds"));
    public static KeyMapping openFourthRoomPeekScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_fourth_room_peek_screen",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.starrailexpress.keybinds"));
    public static KeyMapping openCommandMacroScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_command_macro_screen",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.starrailexpress.keybinds"));
    public static KeyMapping openClueArchiveKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.open_clue_archive",
            GLFW.GLFW_KEY_UNKNOWN,
            "category.starrailexpress.keybinds"));
    public static KeyMapping sniperReloadKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.sniper_reload",
            GLFW.GLFW_KEY_R,
            "category.starrailexpress.keybinds"));
    // 检视键（Inspect）：默认 H，仅传承品质刀响应
    public static KeyMapping inspectKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.inspect",
            GLFW.GLFW_KEY_H,
            "category.starrailexpress.keybinds"));

    public static void initialize() {

        ClientTickEvents.END_CLIENT_TICK.register(InputHandler::onClientTick);
    }

    public static KeyMapping getOpenVotingScreenKeybind() {
        return openVotingScreenKeybind;
    }

    public static KeyMapping getOpenClueArchiveKeybind() {
        return openClueArchiveKeybind;
    }

    /** 按投票类型选界面：模式投票用专用界面，其余用通用投票界面。 */
    private static Screen createVoteScreen() {
        return ClientVoteCache.isGameModeVote() ? new GameModeVoteScreen() : new VoteScreen();
    }

    private static boolean isVoteScreenOpen(Screen screen) {
        return screen instanceof VoteScreen || screen instanceof GameModeVoteScreen;
    }

    private static boolean canOpenFourthRoomTableUi(Minecraft client) {
        var lookedTable = FourthRoomCameraDirector.getLookedTable(client);
        return lookedTable != null && lookedTable.linkedRoomId() == FourthRoomClientState.snapshot().viewer().roomId();
    }

    public static void onClientTick(Minecraft client) {
        if (client == null)
            return;
        if (client.level == null)
            return;

        // 检查玩家是否持有狙击枪，如果不持有则关闭瞄准镜
        if (ScopeOverlayRenderer.isInScopeView() && client.player != null) {
            ItemStack mainHandItem = client.player.getMainHandItem();
            if (!mainHandItem.is(TMMItems.SNIPER_RIFLE)) {
                ScopeOverlayRenderer.setInScopeView(false);
            }
        }

        // 狙击枪操作
        if (client.player != null && client.player.getMainHandItem().is(TMMItems.SNIPER_RIFLE)) {
            ItemStack mainHandItem = client.player.getMainHandItem();
            // 右键开镜/关镜（兜底检测，防止 use() 未触发）
            if (SniperRifleItem.hasScopeAttached(mainHandItem)) {
                boolean rightDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                        client.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
                if (rightDown && !wasRightDown) {
                    ScopeOverlayRenderer.setInScopeView(!ScopeOverlayRenderer.isInScopeView());
                }
                wasRightDown = rightDown;
            }
            if (sniperReloadKeybind.consumeClick()) {
                SniperRifleItem.tryReloadFromKeybind(client.player);
            }
        } else {
            wasRightDown = false;
        }

        // 沙漠之鹰 R键换弹
        if (client.player != null && client.player.getMainHandItem().is(ModItems.DESERT_EAGLE)) {
            if (sniperReloadKeybind.consumeClick()) {
                DesertEagleItem.tryReloadFromClient(client.player);
            }
        }

        // 检视中的切手校验：换槽位/换物品即打断（advance 只在渲染星光刀时运行，必须在 tick 侧兜底）
        XingguangPoseManager.validateInspectAgainstHand(client.player);

        if (inspectKeybind.consumeClick()) {
            if (client.player != null && client.screen == null
                    && isLegacyQualityKnife(client.player, client.player.getMainHandItem())) {
                if (XingguangPoseManager.isXinghongClaw(client.player.getMainHandItem())) {
                    // 爪子检视：再按一次 H 退出；动画接入前窗口标志已可打断切刀/被空挥与蓄力打断
                    if (ClawFlipManager.isInspecting()) {
                        ClawFlipManager.notifyInspectEnded();
                    } else {
                        ClawFlipManager.notifyInspectStarted();
                    }
                } else {
                    XingguangPoseManager.startInspect();
                }
            }
        }

        if (openVotingScreenKeybind.consumeClick()) {
            // 会议投票阶段：打开玩家投票界面
            if (MeetingClientHandler.phase == 3 && ClientVoteCache.isActive()) {
                client.setScreen(createVoteScreen());
                return;
            }
            // 检查是否处于投票阶段
            final MapVotingComponent mapVotingComponent = MapVotingComponent.KEY.get(client.level);
            if (mapVotingComponent.isVotingActive()) {
                // 打开投票界面
                client.setScreen(MapVoteScreen.create());
            } else if (ClientVoteCache.canReOpen() && !isVoteScreenOpen(client.screen)) {
                client.setScreen(createVoteScreen());
            }
        }

        if (openClueArchiveKeybind.consumeClick()) {

            if (client.screen != null) {
                return;
            }
            if (client.getConnection() != null) {
                ClientPlayNetworking.send(RequestOpenClueArchivePayload.INSTANCE);
            }
        }

        if (openFourthRoomScreenKeybind.consumeClick()) {
            if (client.screen instanceof FourthRoomPeekDeckScreen peekScreen) {
                peekScreen.onClose();
                return;
            }
            if (client.screen instanceof FourthRoomBattleScreen) {
                client.setScreen(null);
                return;
            }
            if (FourthRoomClientState.snapshot().active()) {
                if (canOpenFourthRoomTableUi(client)) {
                    client.setScreen(new FourthRoomBattleScreen());
                } else if (client.player != null) {
                    client.player.displayClientMessage(Component.literal("请先看向自己房间的牌桌"), true);
                }
            }
        }


        if (openCommandMacroScreenKeybind.consumeClick()) {
            if (!(client.screen instanceof CommandMacroScreen)) {
                client.setScreen(new CommandMacroScreen());
            }
        }

        if (openFourthRoomPeekScreenKeybind.consumeClick()) {
            if (client.screen instanceof FourthRoomPeekDeckScreen peekScreen) {
                peekScreen.onClose();
                return;
            }
            if (!FourthRoomClientState.snapshot().active()) {
                return;
            }
            if (FourthRoomClientState.snapshot().viewer().peekCards().isEmpty()) {
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal("当前没有可查看的窥视牌堆"), true);
                }
                return;
            }
            if (canOpenFourthRoomTableUi(client) || client.screen instanceof FourthRoomBattleScreen) {
                client.setScreen(new FourthRoomPeekDeckScreen(client.screen));
            } else if (client.player != null) {
                client.player.displayClientMessage(Component.literal("请先看向自己房间的牌桌"), true);
            }
        }
    }

    /** 主手是否为传承品质（LEGACY）的刀：检视仅对传承品质开放，非传承按 H 无任何反应 */
    private static boolean isLegacyQualityKnife(Player player, ItemStack stack) {
        if (player == null || !(stack.getItem() instanceof KnifeItem)) {
            return false;
        }
        String skinId = ItemSkinManager.getEquippedSkin(player, stack);
        if (skinId == null) {
            return false;
        }
        int color = ItemSkinManager.getColorFromName(ItemSkinManager.SkinTypes.KNIFE, skinId);
        int qualityIndex = ItemSkinManager.qualityFromColor(color);
        ItemSkinManager.QualityColor[] values = ItemSkinManager.QualityColor.values();
        if (qualityIndex < 0 || qualityIndex >= values.length) {
            return false;
        }
        return values[qualityIndex] == ItemSkinManager.QualityColor.LEGACY;
    }
}
