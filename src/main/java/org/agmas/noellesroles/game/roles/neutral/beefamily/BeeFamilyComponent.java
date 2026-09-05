package org.agmas.noellesroles.game.roles.neutral.beefamily;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.UUID;

/**
 * 蜜蜂家族（蜂后 / 马蜂 / 工蜂）共用的玩家组件。
 *
 * <p>
 * 组件在 {@link io.wifi.starrailexpress.api.RoleMethodDispatcher#onInit} 中被 {@code init()}，
 * 并在每局开始 / 结束时被 {@code clear()}，因此所有字段都必须能在 {@link #init()} 中回到初值。
 */
public class BeeFamilyComponent implements RoleComponent {
    public static final ComponentKey<BeeFamilyComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "bee_family"),
            BeeFamilyComponent.class);

    private final Player player;

    /** 是否处于蜜蜂频道：开启后发言只对蜜蜂家族（与已淘汰玩家）可见。 */
    public boolean beeChannel = true;
    /** 蜂后标记的继承者：蜂后死亡时该玩家复活并接任蜂后。 */
    @Nullable
    public UUID markTarget = null;
    /**
     * 服务端专用：转职为蜜蜂家族之前的职业。
     * 蜜蜂家族全灭时用它把成员还原回原职业，因此不参与同步。
     */
    @Nullable
    public SRERole beforeRole = null;

    public BeeFamilyComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        beeChannel = true;
        markTarget = null;
        beforeRole = null;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("channel", beeChannel);
        if (markTarget != null) {
            tag.putUUID("markTarget", markTarget);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        beeChannel = tag.contains("channel") && tag.getBoolean("channel");
        markTarget = tag.contains("markTarget") ? tag.getUUID("markTarget") : null;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    public void changeChannel(boolean beeChannel) {
        this.beeChannel = beeChannel;
        sync();
    }

    /** 切换频道，并把切换后的频道以 actionbar 形式回显给自己。 */
    public void turnChannel() {
        changeChannel(!beeChannel);
        player.displayClientMessage(BeeFamilyRole.getChannelText(player), true);
    }

    public void markSuccessor(UUID target) {
        this.markTarget = target;
        sync();
    }

    /**
     * 取组件，玩家为 null 或组件缺失时返回 null。
     * 本项目使用的 CCA 版本没有 {@code ComponentKey#getOrNull}，统一走 {@code maybeGet}。
     */
    @Nullable
    public static BeeFamilyComponent getNullable(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        return KEY.maybeGet(player).orElse(null);
    }
}
