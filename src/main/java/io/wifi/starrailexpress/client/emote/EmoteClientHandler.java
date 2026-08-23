package io.wifi.starrailexpress.client.emote;

import com.mojang.blaze3d.platform.InputConstants;
import io.wifi.starrailexpress.client.gui.screen.EmoteCompassScreen;
import io.wifi.starrailexpress.emote.EmotePlayS2CPayload;
import io.wifi.starrailexpress.emote.EmoteStopC2SPayload;
import io.wifi.starrailexpress.emote.EmoteStopS2CPayload;
import io.wifi.starrailexpress.emote.EmoteType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * 表情系统客户端入口：注册表情罗盘热键（默认 B）、S2C 接收器，
 * 并在本地玩家播放表情期间检测移动输入以立即打断。
 */
public final class EmoteClientHandler {

    /** 表情罗盘热键，默认 B */
    public static KeyMapping emoteCompassKeybind;

    /** 本地玩家上一 tick 的位置，用于移动打断检测 */
    private static double prevX = Double.NaN;
    private static double prevZ = Double.NaN;

    private EmoteClientHandler() {
    }

    public static void register() {
        emoteCompassKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.starrailexpress.emote_compass",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.starrailexpress.keybinds"));

        ClientPlayNetworking.registerGlobalReceiver(EmotePlayS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                var client = context.client();
                if (client.level == null) {
                    return;
                }
                EmoteType emote = EmoteType.byId(payload.emoteId());
                if (emote != null) {
                    EmoteClientState.start(payload.entityId(), emote, client.level.getGameTime());
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(EmoteStopS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> EmoteClientState.stop(payload.entityId()));
        });

        ClientTickEvents.END_CLIENT_TICK.register(EmoteClientHandler::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            EmoteClientState.clear();
            prevX = Double.NaN;
            prevZ = Double.NaN;
        });
    }

    /**
     * 发送播放请求（由罗盘界面调用）
     */
    public static void requestPlay(EmoteType emote) {
        if (emote == null || !ClientPlayNetworking.canSend(EmoteStopC2SPayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(new io.wifi.starrailexpress.emote.EmotePlayC2SPayload(emote.id()));
    }

    private static void onClientTick(Minecraft client) {
        if (client.level == null) {
            prevX = Double.NaN;
            prevZ = Double.NaN;
            return;
        }
        EmoteClientState.tick(client.level);

        // 打开表情罗盘（仅在无其他界面时响应）
        if (emoteCompassKeybind.consumeClick() && client.player != null && client.screen == null) {
            client.setScreen(new EmoteCompassScreen());
        }

        LocalPlayer player = client.player;
        if (player == null) {
            prevX = Double.NaN;
            prevZ = Double.NaN;
            return;
        }

        // 本地玩家表情播放期间的移动打断：按下移动按键或位置实际变化时立即停止（打开界面时玩家静止，不判定）
        if (client.screen == null && EmoteClientState.get(player.getId()) != null) {
            boolean movingInput = client.options.keyUp.isDown()
                    || client.options.keyDown.isDown()
                    || client.options.keyLeft.isDown()
                    || client.options.keyRight.isDown()
                    || client.options.keyJump.isDown()
                    || client.options.keySprint.isDown();
            double dx = Double.isNaN(prevX) ? 0 : player.getX() - prevX;
            double dz = Double.isNaN(prevZ) ? 0 : player.getZ() - prevZ;
            boolean moved = dx * dx + dz * dz > 1.0E-6D;
            if (movingInput || moved) {
                EmoteClientState.stop(player.getId());
                if (ClientPlayNetworking.canSend(EmoteStopC2SPayload.ID)) {
                    ClientPlayNetworking.send(new EmoteStopC2SPayload());
                }
            }
        }
        prevX = player.getX();
        prevZ = player.getZ();
    }
}
