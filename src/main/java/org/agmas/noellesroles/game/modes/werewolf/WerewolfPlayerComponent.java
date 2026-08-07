package org.agmas.noellesroles.game.modes.werewolf;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * 狼人杀玩家 CCA 组件
 * 存储玩家在狼人杀模式中的状态
 * Author: jiale
 */
public class WerewolfPlayerComponent implements AutoSyncedComponent {
    /** 玩家编号（1~N） */
    public int seatNumber = -1;
    /** 角色ID（WerewolfRoleDef.id） */
    public String roleId = "";
    /** 是否存活 */
    public boolean alive = true;
    /** 炼药师是否已使用毒药 */
    public boolean usedPoison = false;
    /** 炼药师是否已使用解药 */
    public boolean usedAntidote = false;
    /** 猎人/白狼王是否已使用死后技能 */
    public boolean usedDeathShot = false;
    /** 守护者上轮守护目标编号（-1表示无） */
    public int lastGuardTarget = -1;
    /** 是否被毒杀（用于猎人判断能否开枪） */
    public boolean killedByPoison = false;

    private final Player player;

    public WerewolfPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 初始化/重置组件
     */
    public void init() {
        this.seatNumber = -1;
        this.roleId = "";
        this.alive = true;
        this.usedPoison = false;
        this.usedAntidote = false;
        this.usedDeathShot = false;
        this.lastGuardTarget = -1;
        this.killedByPoison = false;
    }

    /**
     * 获取角色定义
     */
    public WerewolfRoleDef getRoleDef() {
        return WerewolfRoleDef.byId(roleId);
    }

    /**
     * 是否是狼方
     */
    public boolean isWolf() {
        return getRoleDef().isWolf();
    }

    /**
     * 是否是好人
     */
    public boolean isGood() {
        return getRoleDef().isGood();
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        org.agmas.noellesroles.component.ModComponents.WEREWOLF.sync(this.player);
    }

    // === NBT 序列化 ===

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        this.seatNumber = tag.getInt("seatNumber");
        this.roleId = tag.getString("roleId");
        this.alive = tag.getBoolean("alive");
        this.usedPoison = tag.getBoolean("usedPoison");
        this.usedAntidote = tag.getBoolean("usedAntidote");
        this.usedDeathShot = tag.getBoolean("usedDeathShot");
        this.lastGuardTarget = tag.getInt("lastGuardTarget");
        this.killedByPoison = tag.getBoolean("killedByPoison");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putInt("seatNumber", this.seatNumber);
        tag.putString("roleId", this.roleId);
        tag.putBoolean("alive", this.alive);
        tag.putBoolean("usedPoison", this.usedPoison);
        tag.putBoolean("usedAntidote", this.usedAntidote);
        tag.putBoolean("usedDeathShot", this.usedDeathShot);
        tag.putInt("lastGuardTarget", this.lastGuardTarget);
        tag.putBoolean("killedByPoison", this.killedByPoison);
    }

    // === 网络同步 ===

    @Override
    public void writeSyncPacket(@NotNull RegistryFriendlyByteBuf buf, @NotNull net.minecraft.server.level.ServerPlayer recipient) {
        buf.writeInt(this.seatNumber);
        buf.writeUtf(this.roleId);
        buf.writeBoolean(this.alive);
        buf.writeBoolean(this.usedPoison);
        buf.writeBoolean(this.usedAntidote);
        buf.writeBoolean(this.usedDeathShot);
        buf.writeInt(this.lastGuardTarget);
        buf.writeBoolean(this.killedByPoison);
    }

    @Override
    public void applySyncPacket(@NotNull RegistryFriendlyByteBuf buf) {
        this.seatNumber = buf.readInt();
        this.roleId = buf.readUtf();
        this.alive = buf.readBoolean();
        this.usedPoison = buf.readBoolean();
        this.usedAntidote = buf.readBoolean();
        this.usedDeathShot = buf.readBoolean();
        this.lastGuardTarget = buf.readInt();
        this.killedByPoison = buf.readBoolean();
        
        // 同步到客户端状态缓存：仅限本地玩家自己的组件！
        // 修复：附近其他玩家的组件同步也会触发 applySyncPacket，
        // 若不判断会导致自己的编号/身份被其他玩家数据覆盖（编号莫名变化问题）
        if (this.player.level().isClientSide) {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player == this.player) {
                    org.agmas.noellesroles.game.modes.werewolf.client.WerewolfClientState.updateMyInfo(
                            this.seatNumber, this.roleId, this.alive, this.usedAntidote, this.usedPoison);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
