package org.agmas.noellesroles.game.roles.killer.ghoul;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 食尸鬼玩家组件
 * 
 * 杀手阵营
 * 
 * 技能：搜刮尸体
 * - 打开尸体的物品栏
 * - 最多拿取2个物品
 * - 无法拿取保安盾、画板
 * - 德林加和双截棍自动转化为左轮手枪
 * - 拿取后物品放到物品栏
 * - 使用后尸体变为骨架，留下黑色粒子
 * - 获取尸体生前40%的金钱
 * - CD 30秒
 */
public class GhoulPlayerComponent implements RoleComponent, ServerTickingComponent {

    // 食尸鬼已拿取物品数量（用于限制最多拿取2个）
    public int ghoulItemsTaken = 0;
    // 食尸鬼是否正在搜刮此尸体
    public boolean ghoulLooting = false;

    /** 组件键 */
    public static final ComponentKey<GhoulPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ghoul"),
            GhoulPlayerComponent.class);

    /** 技能冷却时间（30秒 = 600 tick） */
    public static final int ABILITY_COOLDOWN = 30 * 20;

    private final Player player;

    /** 技能冷却 */
    public int cooldown = 0;

    /** 已打开过的尸体UUID集合 */
    public Set<UUID> openedCorpses = new HashSet<>();

    public GhoulPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.cooldown = 0;
        this.openedCorpses.clear();
        this.ghoulItemsTaken = 0;
        this.ghoulLooting = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 检查是否可以打开尸体
     */
    public boolean canOpenCorpse(UUID corpseUuid) {
        if (cooldown > 0) {
            return false;
        }
        return !openedCorpses.contains(corpseUuid);
    }

    /**
     * 打开尸体后调用
     */
    public void onCorpseOpened(UUID corpseUuid) {
        openedCorpses.add(corpseUuid);
        cooldown = ABILITY_COOLDOWN;
        this.sync();
    }

    /**
     * 获取剩余冷却时间（秒）
     */
    public int getRemainingCooldown() {
        return (cooldown + 19) / 20;
    }

    /**
     * 检查尸体是否已被打开过
     */
    public boolean hasOpenedCorpse(UUID corpseUuid) {
        return openedCorpses.contains(corpseUuid);
    }

    /**
     * 检查冷却是否结束
     */
    public boolean isCooldownReady() {
        return cooldown <= 0;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.GHOUL)) {
            return;
        }
        if (!gameWorld.isRunning()) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 200 == 0 || cooldown == 0) {
                sync();
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);

        ListTag corpseList = new ListTag();
        for (UUID uuid : openedCorpses) {
            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("uuid", uuid);
            corpseList.add(uuidTag);
        }
        tag.put("openedCorpses", corpseList);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.getInt("cooldown");

        this.openedCorpses.clear();
        if (tag.contains("openedCorpses", Tag.TAG_LIST)) {
            ListTag corpseList = tag.getList("openedCorpses", Tag.TAG_COMPOUND);
            for (int i = 0; i < corpseList.size(); i++) {
                CompoundTag uuidTag = corpseList.getCompound(i);
                UUID uuid = uuidTag.getUUID("uuid");
                this.openedCorpses.add(uuid);
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
