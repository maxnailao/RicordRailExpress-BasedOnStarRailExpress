package org.agmas.noellesroles.game.roles.vigilante.cowboy;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
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
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 西部牛仔组件
 *
 * 管理弹药重置的延迟计时器：
 * - 购买"弹药重置"后，获得 6 秒缓慢 I 效果
 * - 6 秒（120 tick）结束后，重置德林加手枪弹药（USED → false）
 */
public class CowboyPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<CowboyPlayerComponent> KEY = ModComponents.COWBOY;

    private final Player player;

    /** 弹药重置倒计时（tick），0 表示无进行中的重置 */
    public int ammoResetTimer = 0;

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

    /**
     * 启动弹药重置：设置 120 tick（6 秒）倒计时
     */
    public void startAmmoReset() {
        this.ammoResetTimer = 120;
        sync();
    }

    @Override
    public void init() {
        this.ammoResetTimer = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public void serverTick() {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.NIUZAI_JUEDOUBA))
            return;

        if (ammoResetTimer > 0) {
            ammoResetTimer--;
            if (ammoResetTimer <= 0) {
                // 计时结束，重置德林加弹药
                resetDerringerAmmo();
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.niuzai_juedouba.ammo_refilled")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }
                sync();
            }
        }
    }

    /**
     * 重置背包中所有德林加手枪的弹药
     */
    private void resetDerringerAmmo() {
        for (var list : player.getInventory().compartments) {
            for (ItemStack stack : list) {
                if (stack.is(TMMItems.DERRINGER)) {
                    stack.set(SREDataComponentTypes.USED, false);
                }
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("ammoResetTimer", ammoResetTimer);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        ammoResetTimer = tag.getInt("ammoResetTimer");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
