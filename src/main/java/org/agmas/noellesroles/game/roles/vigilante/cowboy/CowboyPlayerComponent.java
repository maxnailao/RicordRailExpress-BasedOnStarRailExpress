package org.agmas.noellesroles.game.roles.vigilante.cowboy;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

/**
 * 西部牛仔的每玩家状态。
 *
 * <p>只保留决斗相关字段（{@link #duelCooldownTicks}）。
 * 原先的「德林加弹药重置」已随手枪一起移除 —— 牛仔现在起手是左轮手枪，没有弹药重置机制。
 */
public class CowboyPlayerComponent implements RoleComponent {

    public static final ComponentKey<CowboyPlayerComponent> KEY = ModComponents.COWBOY;

    /** 决斗冷却剩余 tick；> 0 期间不能再发起决斗。 */
    public int duelCooldownTicks = 0;

    /** 抽雪茄冷却剩余 tick；> 0 期间不能再抽。 */
    public int cigarCooldownTicks = 0;

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

    public boolean isDuelReady() {
        return duelCooldownTicks <= 0;
    }

    public void setDuelCooldown(int ticks) {
        this.duelCooldownTicks = Math.max(0, ticks);
        sync();
    }

    // ==================== 雪茄冷却 ====================

    /** 抽雪茄的冷却时长（tick）：30 秒。 */
    public static final int CIGAR_COOLDOWN_TICKS = 20 * 30;

    public boolean isCigarReady() {
        return cigarCooldownTicks <= 0;
    }

    public void startCigarCooldown() {
        this.cigarCooldownTicks = CIGAR_COOLDOWN_TICKS;
        sync();
    }

    /** 每秒同步一次冷却，让界面上的秒数能逐秒跳动（每 tick 同步太费包）。 */
    private static final int SYNC_EVERY_TICKS = 20;

    /**
     * 每 tick 递减冷却（由 {@code CowboyDuelManager.tickAll} 驱动）。
     *
     * <p>同步节奏：每秒（20 tick）同步一次，归零时立刻再同步一次。
     * 只在整个冷却结束时同步的话，界面会一直停在"60s"然后突然跳到 0 —— 那是显示 bug，
     * 不是冷却真的没走。
     */
    public void tickDuelCooldown() {
        boolean duelRunning = duelCooldownTicks > 0;
        boolean cigarRunning = cigarCooldownTicks > 0;
        if (!duelRunning && !cigarRunning) {
            return;
        }
        if (duelRunning) {
            duelCooldownTicks--;
        }
        if (cigarRunning) {
            cigarCooldownTicks--;
        }

        // 整秒边界（只看真正在倒计时的那一条）
        boolean onBoundary = (duelRunning && duelCooldownTicks % SYNC_EVERY_TICKS == 0)
                || (cigarRunning && cigarCooldownTicks % SYNC_EVERY_TICKS == 0);
        // 刚归零那一下要立刻同步，否则界面会停在"1 秒"
        boolean justEnded = (duelRunning && duelCooldownTicks == 0)
                || (cigarRunning && cigarCooldownTicks == 0);

        if (onBoundary || justEnded) {
            sync();
        }
    }

    @Override
    public void init() {
        duelCooldownTicks = 0;
        cigarCooldownTicks = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("duelCooldownTicks", duelCooldownTicks);
        tag.putInt("cigarCooldownTicks", cigarCooldownTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        this.duelCooldownTicks = tag.getInt("duelCooldownTicks");
        this.cigarCooldownTicks = tag.getInt("cigarCooldownTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("duelCooldownTicks", duelCooldownTicks);
        tag.putInt("cigarCooldownTicks", cigarCooldownTicks);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        this.duelCooldownTicks = tag.getInt("duelCooldownTicks");
        this.cigarCooldownTicks = tag.getInt("cigarCooldownTicks");
    }
}
