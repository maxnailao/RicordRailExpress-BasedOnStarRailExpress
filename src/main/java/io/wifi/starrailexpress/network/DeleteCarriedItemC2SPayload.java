package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
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
        return stack != null && !stack.isEmpty() && isProtected(stack);
    }

    /**
     * 不允许被垃圾桶销毁的物品。
     *
     * <p>都是**局内战斗物品**：误删会直接毁掉一局游戏（警察丢了巡警手枪、
     * 牛仔丢了德林加就没法完成复仇、杀手丢了刀就只能挨打），所以垃圾桶一律拒绝。
     *
     * <p>注意几处容易漏的：
     * <ul>
     * <li>双枪是左右两件独立物品，都要挡；</li>
     * <li>手雷有三种（普通 / 粘性 / 定时），都算手雷；</li>
     * <li>短管霰弹枪在 {@code ModItems}，其余多数在 {@code TMMItems}。</li>
     * </ul>
     */
    private static boolean isProtected(ItemStack stack) {
        // 手枪类
        return stack.is(io.wifi.starrailexpress.index.TMMItems.REVOLVER)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.DERRINGER)
                || stack.is(org.agmas.noellesroles.init.ModItems.PATROLLER_REVOLVER)
                || stack.is(org.agmas.noellesroles.init.ModItems.BANDIT_REVOLVER)
                || stack.is(org.agmas.noellesroles.init.ModItems.SHERIFF_REVOLVER)
                || stack.is(org.agmas.noellesroles.init.ModItems.FAKE_REVOLVER)
                || stack.is(org.agmas.noellesroles.init.ModItems.DUAL_PISTOL_LEFT)
                || stack.is(org.agmas.noellesroles.init.ModItems.DUAL_PISTOL_RIGHT)
                // 近战与投掷物
                || stack.is(io.wifi.starrailexpress.index.TMMItems.KNIFE)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.BAT)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.GRENADE)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.STICKY_GRENADE)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.TIMED_GRENADE)
                // 长枪
                || stack.is(org.agmas.noellesroles.init.ModItems.SHORT_SHOTGUN)
                || stack.is(io.wifi.starrailexpress.index.TMMItems.SNIPER_RIFLE);
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
        if (isProtected(carried)) {
            player.displayClientMessage(Component
                    .literal("§c这件武器不能扔进垃圾桶")
                    .withStyle(ChatFormatting.RED), true);
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
