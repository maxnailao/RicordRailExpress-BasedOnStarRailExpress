package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.client.XingguangClientEffects;
import io.wifi.starrailexpress.network.TriggerScreenEdgeEffectPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 屏幕边缘效果（击杀红光 / 低血量提示等）的**客户端**接收逻辑。
 *
 * <h2>为什么必须单独放在这里</h2>
 * 这段逻辑原来写在 {@link TriggerScreenEdgeEffectPayload} 里（一个通用的包记录类）。
 * 但它在接收回调里访问了 {@code context.client().player} —— 那个字段的类型是
 * {@code net.minecraft.client.player.LocalPlayer}。
 *
 * <p>JVM 在**链接/验证**该方法时，必须解析调用点描述符里出现的类型，
 * 于是会去找 {@code LocalPlayer}；而专用服务端的 Minecraft 里**根本没有这个类**，
 * 结果就是服务端在 {@code SREPayloadRegister} 注册这个包时直接
 * {@code NoClassDefFoundError: net/minecraft/class_746}，启动失败。
 *
 * <p>把整段逻辑挪到这个只在客户端加载的类后，通用的
 * {@link TriggerScreenEdgeEffectPayload} 里不再出现任何客户端类型，
 * 服务端注册它时不会再触发解析，客户端行为完全不变。
 */
@Environment(EnvType.CLIENT)
public final class ScreenEdgeEffectClient {

    private ScreenEdgeEffectClient() {
    }

    /** 注册接收器（仅在客户端初始化时调用）。 */
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(TriggerScreenEdgeEffectPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    int color = payload.color();
                    // 击杀边缘红光是唯一发送 Color.RED 的来源(GameMode.killPlayer)；
                    // 星光刀持刀者把这份红边改成黑灰，与击杀全屏滤镜(0x1B1B1F)一致。
                    var player = context.client().player;
                    if (color == java.awt.Color.RED.getRGB()
                            && player != null
                            && XingguangClientEffects.hasXingguangEquipped(player)) {
                        color = 0x1B1B1F;
                    }
                    StaminaRenderer.triggerScreenEdgeEffect(color, payload.durationMs(), payload.intensity());
                }));
    }
}
