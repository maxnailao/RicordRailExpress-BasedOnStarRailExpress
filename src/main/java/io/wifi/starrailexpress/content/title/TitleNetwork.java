package io.wifi.starrailexpress.content.title;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.title.network.EquipTitleC2SPayload;
import io.wifi.starrailexpress.content.title.network.TitleCatalogS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 称号系统的网络接线：处理装备请求、推送定义表。
 */
public final class TitleNetwork {

    private TitleNetwork() {
    }

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(EquipTitleC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                TitleSavedData data = TitleSavedData.get(context.server());
                TitlePlayerComponent comp = TitlePlayerComponent.KEY.get(player);
                String id = payload.titleId();

                if (id == null || id.isEmpty()) {
                    comp.clearEquipped();
                    TitleManager.apply(context.server(), player, data);
                    player.displayClientMessage(Component.literal("§e已卸下称号"), true);
                    return;
                }
                if (!comp.owns(id)) {
                    player.displayClientMessage(Component.literal("§c你没有这个称号"), true);
                    return;
                }
                Title title = data.get(id);
                if (title == null) {
                    player.displayClientMessage(Component.literal("§c这个称号已被删除"), true);
                    return;
                }
                boolean equipped = comp.toggleEquip(id);
                TitleManager.apply(context.server(), player, data);
                player.displayClientMessage(
                        Component.literal(equipped ? "§a已装备称号：" : "§e已卸下称号：")
                                .append(title.component()),
                        true);
            });
        });
    }

    /** 把称号定义表推给某个玩家（登录时 / 定义变化时） */
    public static void sendCatalog(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        try {
            TitleSavedData data = TitleSavedData.get(player.getServer());
            ServerPlayNetworking.send(player,
                    new TitleCatalogS2CPayload(new java.util.ArrayList<>(data.all())));
        } catch (Exception e) {
            SRE.LOGGER.warn("[Title] 推送称号表失败: {}", player.getName().getString(), e);
        }
    }

    /** 把称号定义表推给所有在线玩家（定义变化时用） */
    public static void broadcastCatalog(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendCatalog(p);
        }
    }

    /**
     * 玩家登录 / 换世界后：同步定义表 + 重新应用称号到记分板。
     */
    public static void onPlayerJoin(ServerPlayer player) {
        sendCatalog(player);
        TitleManager.reapplyOnJoin(player);
    }
}
