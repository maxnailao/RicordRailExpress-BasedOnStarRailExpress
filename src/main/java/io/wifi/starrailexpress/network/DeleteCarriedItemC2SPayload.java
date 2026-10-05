package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端→服务端：把鼠标当前「拿在手上」的物品丢进垃圾桶（永久删除）。
 *
 * <p>玩家在物品栏界面把物品拖到右侧垃圾槽并确认删除时发送。服务端以
 * {@code containerMenu.getCarried()} 为准（而不是客户端传物品 id），
 * 这样客户端无法任意指定要删什么，也不会出现两边不一致。
 */
public record DeleteCarriedItemC2SPayload() implements CustomPacketPayload {

    public static final Type<DeleteCarriedItemC2SPayload> ID = new Type<>(SRE.id("delete_carried_item"));
    public static final StreamCodec<FriendlyByteBuf, DeleteCarriedItemC2SPayload> CODEC = StreamCodec
            .ofMember(
                    (payload, buf) -> {
                    },
                    buf -> new DeleteCarriedItemC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 服务端处理：清掉光标上拿着的那叠物品。 */
    public static void handle(ServerPlayer player) {
        if (player == null) {
            return;
        }
        var menu = player.containerMenu;
        if (menu == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        // 关键：先清空 carried，再广播，客户端光标上的物品会立刻消失
        menu.setCarried(ItemStack.EMPTY);
        menu.broadcastChanges();
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
        // 兜底同步一次背包，避免本地预测残留
        player.containerMenu.slotsChanged(player.getInventory());
    }
}
