package org.agmas.noellesroles.game.roles.innocence.taopiaozhe;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 逃票者组件
 *
 * 被动技能：其他玩家在逃票者半径6.5格内累计存在45秒后，
 * 逃票者即可在背包内知晓该玩家的阵营归属（显示玩家头像与阵营）。
 *
 * 被动豁免：逃票者被平民阵营玩家击杀时不触发小脑惩罚
 * （豁免逻辑见 XiaoNaoHandler）。
 */
public class TaopiaozhePlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<TaopiaozhePlayerComponent> KEY = ModComponents.TAOPIAOZHE;

    // ==================== 常量定义 ====================

    /** 侦测半径（格） */
    public static final double DETECT_RADIUS = 6.5;

    /** 侦测半径平方 */
    private static final double DETECT_RADIUS_SQR = DETECT_RADIUS * DETECT_RADIUS;

    /** 累计存在时长（45秒 = 900 tick） */
    public static final int REQUIRED_TICKS = 45 * 20;

    /** 阵营编码：平民阵营 */
    public static final byte CAMP_INNOCENT = 0;
    /** 阵营编码：杀手阵营 */
    public static final byte CAMP_KILLER = 1;
    /** 阵营编码：中立阵营 */
    public static final byte CAMP_NEUTRAL = 2;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 各玩家在自己半径内的累计 tick 数（仅服务端） */
    private final Map<UUID, Integer> accumulatedTicks = new HashMap<>();

    /** 已知晓阵营的玩家：UUID -> 阵营编码（同步给客户端） */
    private final Map<UUID, Byte> revealedCamps = new LinkedHashMap<>();

    // ==================== 构造函数 ====================

    public TaopiaozhePlayerComponent(Player player) {
        this.player = player;
    }

    // ==================== 初始化/清理 ====================

    /**
     * 开局初始化：框架在 onStartGame/onEndGame 会先调用 clear() 彻底清理，
     * 且 init() 可能因角色分配分波被多次回调，这里只做同步，不清空持续状态，
     * 避免 mid-round 误杀已累计的进度。
     */
    @Override
    public void init() {
        this.sync();
    }

    @Override
    public void clear() {
        this.accumulatedTicks.clear();
        this.revealedCamps.clear();
        this.sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    // ==================== 查询接口 ====================

    /**
     * 获取已知晓阵营的玩家列表（客户端用于背包头像展示）
     */
    public Map<UUID, Byte> getRevealedCamps() {
        return revealedCamps;
    }

    /**
     * 根据目标玩家的角色计算阵营编码
     */
    public static byte campOf(SREGameWorldComponent gameWorld, Player target) {
        SRERole role = gameWorld.getRole(target);
        if (role == null) {
            return CAMP_INNOCENT;
        }
        if (role.isInnocent()) {
            return CAMP_INNOCENT;
        }
        if (role.isKillerTeam()) {
            return CAMP_KILLER;
        }
        return CAMP_NEUTRAL;
    }

    // ==================== Tick 处理 ====================

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void serverTick() {
        // 实体状态守卫：死亡/旁观不参与累计
        if (player.isSpectator() || player.isDeadOrDying()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRunning()) return;
        if (!gameWorld.isRole(player, ModRoles.TAOPIAOZHE)) return;

        ServerLevel serverLevel = serverPlayer.serverLevel();
        boolean changed = false;

        for (ServerPlayer other : serverLevel.getPlayers(p -> true)) {
            if (other == serverPlayer) continue;
            UUID uuid = other.getUUID();
            if (revealedCamps.containsKey(uuid)) continue;
            // 仅累计存活且处于冒险模式的玩家
            if (!GameUtils.isPlayerAliveAndSurvival(other)) continue;
            if (other.distanceToSqr(serverPlayer) > DETECT_RADIUS_SQR) continue;

            int ticks = accumulatedTicks.getOrDefault(uuid, 0) + 1;
            if (ticks >= REQUIRED_TICKS) {
                // 累计满45秒：记录阵营并提示
                revealedCamps.put(uuid, campOf(gameWorld, other));
                accumulatedTicks.remove(uuid);
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.taopiaozhe.revealed", other.getName())
                                .withStyle(ChatFormatting.AQUA),
                        false);
                serverLevel.playSound(null,
                        serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
                changed = true;
            } else {
                accumulatedTicks.put(uuid, ticks);
            }
        }

        if (changed) {
            this.sync();
        }
    }

    // ==================== 同步 ====================

    public void sync() {
        ModComponents.TAOPIAOZHE.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Byte> entry : revealedCamps.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("uuid", entry.getKey());
            item.putByte("camp", entry.getValue());
            list.add(item);
        }
        tag.put("revealedCamps", list);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        revealedCamps.clear();
        ListTag list = tag.getList("revealedCamps", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            revealedCamps.put(item.getUUID("uuid"), item.getByte("camp"));
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : accumulatedTicks.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("uuid", entry.getKey());
            item.putInt("ticks", entry.getValue());
            list.add(item);
        }
        tag.put("accumulatedTicks", list);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
        accumulatedTicks.clear();
        ListTag list = tag.getList("accumulatedTicks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            accumulatedTicks.put(item.getUUID("uuid"), item.getInt("ticks"));
        }
    }
}
