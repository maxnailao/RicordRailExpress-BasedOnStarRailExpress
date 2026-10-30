package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.event.network.EventAdminAddC2SPayload;
import io.wifi.starrailexpress.event.network.EventAdminFeatureC2SPayload;
import io.wifi.starrailexpress.event.network.EventAdminRemoveC2SPayload;
import io.wifi.starrailexpress.event.network.EventClaimC2SPayload;
import io.wifi.starrailexpress.event.network.EventImageRequestC2SPayload;
import io.wifi.starrailexpress.event.network.EventImageS2CPayload;
import io.wifi.starrailexpress.event.network.EventRequestC2SPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDate;

/** 活动系统服务端接线：列表请求、图片按需下发、管理员增删/置顶（OP2 校验）、击杀进度。 */
public final class EventServerNetwork {

    private EventServerNetwork() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EventRequestC2SPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> EventManager.getInstance().syncToPlayer(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(EventImageRequestC2SPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    byte[] data = EventManager.getInstance().readImage(payload.imageId());
                    ServerPlayNetworking.send(context.player(),
                            new EventImageS2CPayload(payload.imageId(), data == null ? new byte[0] : data));
                }));

        ServerPlayNetworking.registerGlobalReceiver(EventAdminAddC2SPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!player.hasPermissions(2)) {
                        player.displayClientMessage(
                                Component.literal("没有权限添加活动").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    String start = payload.startDate().trim();
                    String end = payload.endDate().trim();
                    if (!start.isEmpty() && !end.isEmpty()) {
                        try {
                            if (!LocalDate.parse(end).isAfter(LocalDate.parse(start))) {
                                player.displayClientMessage(Component.literal("结束日期必须晚于开始日期")
                                        .withStyle(ChatFormatting.RED), true);
                                return;
                            }
                        } catch (Exception e) {
                            player.displayClientMessage(Component.literal("日期格式须为 yyyy-MM-dd")
                                    .withStyle(ChatFormatting.RED), true);
                            return;
                        }
                    }
                    String imageId = "";
                    if (payload.imageData() != null && payload.imageData().length > 0
                            && EventManager.getInstance().writeImage(payload.imageId(), payload.imageData())) {
                        imageId = payload.imageId();
                    }
                    EventManager.getInstance().add(payload.title().trim(), payload.content(),
                            payload.rewards().trim(), start, end,
                            payload.condition().trim(), imageId, player.server);
                    player.displayClientMessage(
                            Component.literal("§a已添加活动：" + payload.title().trim()), true);
                }));

        ServerPlayNetworking.registerGlobalReceiver(EventAdminFeatureC2SPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!player.hasPermissions(2)) {
                        player.displayClientMessage(
                                Component.literal("没有权限设置置顶活动").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    EventManager.getInstance().setFeatured(payload.eventId(), player.server);
                    player.displayClientMessage(Component.literal(
                            payload.eventId().isEmpty() ? "§a已取消大活动置顶" : "§a已指定大活动").withStyle(ChatFormatting.WHITE), true);
                }));

        ServerPlayNetworking.registerGlobalReceiver(EventAdminRemoveC2SPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!player.hasPermissions(2)) {
                        player.displayClientMessage(
                                Component.literal("没有权限删除活动").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    boolean ok = EventManager.getInstance()
                            .remove(payload.eventId(), player.server);
                    player.displayClientMessage(Component.literal(
                            ok ? "§a已删除活动" : "§c未找到该活动").withStyle(ChatFormatting.WHITE), true);
                }));

        ServerPlayNetworking.registerGlobalReceiver(EventClaimC2SPayload.ID,
                (payload, context) -> context.server().execute(() ->
                        EventManager.getInstance().claim(context.player(), payload.packKey())));

        // 击杀进度：任何阵营的玩家击杀都计入所有进行中的 kill 条件活动
        OnPlayerKilledPlayer.EVENT.register((victim, killer, reason) -> {
            if (killer != null) EventManager.getInstance().onKill(killer, reason);
        });

        // 登录时推送一次完整列表 + 进度
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> EventManager.getInstance().syncToPlayer(handler.getPlayer())));
    }
}