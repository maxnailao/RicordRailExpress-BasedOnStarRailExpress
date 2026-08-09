package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateSkinSelectedPayload(String id, String name) implements CustomPacketPayload {
    public static final Type<UpdateSkinSelectedPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "update_skin_selected"));
    public static final StreamCodec<FriendlyByteBuf, UpdateSkinSelectedPayload> CODEC = StreamCodec.ofMember(UpdateSkinSelectedPayload::encode, UpdateSkinSelectedPayload::decode);


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id, 128);
        buf.writeUtf(name, 128);
    }

    public static UpdateSkinSelectedPayload decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(128); // 限制字符串长度，防止恶意客户端发送超长字符串导致OOM
        String name = buf.readUtf(128);
        return new UpdateSkinSelectedPayload(id,name);
    }
    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                boolean unlocked = PlayerEconomyManager.isSkinUnlockedForItemType(player, payload.id, payload.name);
                if (!unlocked) {
                    // 回退：命令/黑市等途径获得的皮肤可能仅存在于 CS2 仓库而未写入旧解锁表，
                    // 以仓库持有为准并补录解锁，避免装备请求被静默拒绝
                    var cs2Inv = io.wifi.starrailexpress.cca.CS2InventoryComponent.KEY.get(player);
                    if (cs2Inv.hasSkin(payload.id + "/" + payload.name)) {
                        unlocked = true;
                        ItemSkinManager.unlockSkinForItemType(player, payload.id, payload.name);
                    }
                }
                if (!unlocked) {
                    io.wifi.starrailexpress.SRE.LOGGER.info("[SkinSelect] rejected {}/{} for {} (not unlocked)",
                            payload.id, payload.name, player.getName().getString());
                    return;
                }
                io.wifi.starrailexpress.SRE.LOGGER.info("[SkinSelect] accepted {}/{} for {}",
                        payload.id, payload.name, player.getName().getString());
                // 同时更新 EconomyManager 和 CCA 组件，确保 NBT 持久化数据一致；
                // 帽子皮肤会额外写入实体数据供渲染同步
                ItemSkinManager.setEquippedSkinForItemType(player, payload.id, payload.name);
                ItemSkinManager.sync(player);
            });
        });
    }
    }
