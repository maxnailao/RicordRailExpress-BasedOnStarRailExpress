package org.agmas.noellesroles.game.roles.vigilante.cowboy;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

/**
 * 西部牛仔组件
 *
 * 弹药重置通过 {@code GameUtils.serverTaskQueue} 延迟执行：
 * 购买"弹药重置"后获得 6 秒缓慢 I，6 秒后自动重置德林加弹药（USED → false）。
 */
public class CowboyPlayerComponent implements RoleComponent {

    public static final ComponentKey<CowboyPlayerComponent> KEY = ModComponents.COWBOY;

    private final Player player;

    public CowboyPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /**
     * 重置背包中所有德林加手枪的弹药
     */
    public static void resetDerringerAmmo(Player player) {
        for (var list : player.getInventory().compartments) {
            for (ItemStack stack : list) {
                if (stack.is(TMMItems.DERRINGER)) {
                    stack.set(SREDataComponentTypes.USED, false);
                }
            }
        }
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.niuzai_juedouba.ammo_refilled")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
