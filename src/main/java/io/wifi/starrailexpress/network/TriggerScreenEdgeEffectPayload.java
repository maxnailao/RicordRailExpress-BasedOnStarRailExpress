package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 屏幕边缘效果包（击杀红光 / 低血量等）。
 *
 * <p><b>本类必须保持"纯数据"</b>：不允许出现任何客户端类型
 * （{@code net.minecraft.client.*}、{@code LocalPlayer}、{@code Minecraft}，
 * 也不要调用 {@code io.wifi.starrailexpress.client.*} 里的方法）。
 *
 * <p>原因：JVM 在链接/验证本类时会解析方法体里出现的类型。之前这里带着客户端
 * 接收逻辑、访问了 {@code context.client().player}（类型是 {@code LocalPlayer}），
 * 而专用服务端的 Minecraft 没有这个类，于是服务端启动时崩在
 * {@code SREPayloadRegister} 注册这一行：
 * {@code NoClassDefFoundError: net/minecraft/class_746}。
 *
 * <p>接收逻辑已挪到 {@code io.wifi.starrailexpress.client.network.ScreenEdgeEffectClient}。
 */
public record TriggerScreenEdgeEffectPayload(int color, long durationMs, float intensity)
        implements CustomPacketPayload {
    public static final Type<TriggerScreenEdgeEffectPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "trigger_screen_edge_effect"));
    public static final StreamCodec<FriendlyByteBuf, TriggerScreenEdgeEffectPayload> CODEC = StreamCodec
            .ofMember(TriggerScreenEdgeEffectPayload::encode, TriggerScreenEdgeEffectPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(color);
        buf.writeLong(durationMs);
        buf.writeFloat(intensity);
    }

    public static TriggerScreenEdgeEffectPayload decode(FriendlyByteBuf buf) {
        int color = buf.readInt();
        long durationMs = buf.readLong();
        float intensity = buf.readFloat();
        return new TriggerScreenEdgeEffectPayload(color, durationMs, intensity);
    }
}
