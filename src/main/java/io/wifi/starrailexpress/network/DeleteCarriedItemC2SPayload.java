package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.util.TrashCanProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 *
 * <p>哪些物品受保护（武器 / 炸弹客的炸弹 / 食物饮料）由
 * {@link TrashCanProtection} 统一判定 —— 地图里的垃圾桶方块也走同一套规则。
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

    /**
     * 该物品是否受保护（客户端也用它来决定要不要弹确认框）。
     *
     * <p>服务端仍会独立再查一次 —— 客户端判断只是体验优化，不能作为安全边界。
     */
    public static boolean isProtectedItem(ItemStack stack) {
        return TrashCanProtection.isProtected(stack);
    }

    /**
     * 受保护时返回给玩家看的提示文案；可删除时返回 {@code null}。
     *
     * <p>客户端与服务端都调这一个方法，保证两边口径一致。
     */
    public static Component protectedReason(ItemStack stack) {
        return TrashCanProtection.protectedReason(stack);
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
        Component deny = protectedReason(carried);
        if (deny != null) {
            player.displayClientMessage(deny.copy().withStyle(ChatFormatting.RED), true);
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
