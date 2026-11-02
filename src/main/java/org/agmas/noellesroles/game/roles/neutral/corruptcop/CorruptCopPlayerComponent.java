package org.agmas.noellesroles.game.roles.neutral.corruptcop;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class CorruptCopPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<CorruptCopPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "corrupt_cop"),
            CorruptCopPlayerComponent.class
    );

    private final Player player;
    private int killCount = 0;

    // 黑警时刻管理器
    private final CorruptCopTime corruptCopTime;

    // 检查间隔计时器（每秒检查一次）
    private int checkTimer = 0;

    public CorruptCopPlayerComponent(Player player) {
        this.player = player;
        this.corruptCopTime = new CorruptCopTime(player);
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {
        this.killCount = 0;
        this.corruptCopTime.reset();
        this.checkTimer = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.killCount = 0;
        this.corruptCopTime.reset();
        this.checkTimer = 0;
        this.sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public int getKillCount() {
        return this.killCount;
    }

    public void incrementKillCount() {
        this.killCount++;
        this.sync();
    }

    public void reset() {
        this.killCount = 0;
        this.corruptCopTime.reset();
        this.sync();
    }

    /**
     * 检查是否是活跃的黑警
     */
    public boolean isActiveCorruptCop() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.CORRUPT_COP);
    }

    /**
     * 检查场上是否有存活的杀手阵营
     *
     * @return true 表示没有杀手，false 表示还有杀手
     */
    /**
     * 检查场上是否有存活的杀手阵营
     *
     * @return true 表示没有杀手，false 表示还有杀手
     */
    /**
     * 检查场上是否有存活的杀手阵营
     *
     * @return true 表示没有杀手，false 表示还有杀手
     */
    /**
     * 检查场上是否有存活的杀手阵营
     */
    public boolean isNoKillerAlive() {
        if (player.level().isClientSide())
            return false;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        ServerLevel serverWorld = (ServerLevel) player.level();


        // 黑警自己必须存活
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        // 检查是否还有杀手存活
        for (Player p : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;

            SRERole role = gameWorld.getRole(p);
            if (role == null)
                continue;

            if (role.canUseKiller()) {
                return false;  // 还有杀手，不触发
            }
        }

        // 没有杀手了，且黑警存活，触发黑警时刻
        return true;
    }

    /**
     * 获取黑警时刻管理器
     */
    public CorruptCopTime getCorruptCopTime() {
        return corruptCopTime;
    }

    /**
     * 获取黑警时刻进度 (0~1)
     */
    public float getProgress() {
        return corruptCopTime.getProgress(player.level().getGameTime());
    }

    /**
     * 是否处于黑警时刻
     */
    public boolean isBlackoutActive() {
        return corruptCopTime.isActive();
    }

    @Override
    public void clientTick() {
    }

    @Override
    public void serverTick() {
        // ★ 黑警死亡 → 立刻结束黑警时刻。
        //
        // 这一条必须放在"死亡直接 return"**之前**：以前下面那个
        // `if (!isPlayerAliveAndSurvival) return;` 会让 tick 永远走不到，
        // 结果黑警一死，corruptCopTime 的 active 一直为 true ——
        // 全局标志不清、背景音乐不停、屏幕上的倒计时也一直挂着，直到本局结束。
        //
        // endOnDeath() 只做收尾（清全局状态 + 收进度条），不会像 end() 那样
        // 再走一遍"判负击杀"的逻辑（人都已经死了）。
        if ((corruptCopTime.isActive() || corruptCopTime.isSuspendedForLooseEnd())
                && !GameUtils.isPlayerAliveAndSurvival(player)) {
            corruptCopTime.endOnDeath();
            this.sync();
            return;
        }

        // ★ 与亡命徒时刻的互斥（必须放在下面 isSkillAvailable 早退之前）。
        //
        // 亡命徒时刻开始时会把 isSkillAvailable 置 false，而下面那行会直接 return，
        // 于是黑警的 tick 在亡命徒时刻期间完全停摆 —— 既观察不到"亡命徒时刻开始"，
        // 也观察不到"亡命徒失败回溯结束"，clock 还会被白白吃掉。
        //
        // 现在的语义：
        //   亡命徒时刻开启 → 处决时刻挂起（收音乐/读条/HUD，记下剩余时间）；
        //   亡命徒失败回溯（isAnyRevivals 回到 false）→ 按剩余时间重新触发处决时刻。
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (isActiveCorruptCop()) {
            var refugee = pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent.KEY
                    .get(player.level());
            boolean looseEndActive = refugee != null && refugee.isAnyRevivals;
            if (looseEndActive && corruptCopTime.isActive()) {
                corruptCopTime.suspendForLooseEnd();
                this.sync();
            } else if (!looseEndActive && corruptCopTime.isSuspendedForLooseEnd()) {
                if (gameWorld.isRunning()) {
                    corruptCopTime.resumeAfterLooseEnd(player.level().getGameTime());
                } else {
                    // 局已经结束了（RebugeeComponent 清场也会把 isAnyRevivals 置 false），
                    // 这时不能"重新触发"，静默丢弃挂起即可。
                    corruptCopTime.dropSuspended();
                }
                this.sync();
            }
        }

        // 只有活跃的黑警才需要检测
        if (!isActiveCorruptCop())
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        // 亡命徒时刻不触发黑警时刻
        if (!gameWorld.isSkillAvailable) {
            return;
        }

        long currentTime = player.level().getGameTime();

        // 如果已激活，更新黑警时刻
        if (corruptCopTime.isActive()) {
            corruptCopTime.tick(currentTime);
            // 状态变化时同步
            if (!corruptCopTime.isActive()) {
                this.sync();
            }
            return;
        }

        // 未激活时，每秒检查一次是否有杀手存活
        checkTimer++;
        if (checkTimer % 20 == 0) {
            if (isNoKillerAlive()) {
                // 触发黑警时刻，记录开始时间
                corruptCopTime.activate(currentTime);
                this.sync();
            }
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return true;/*player == this.player*/
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("killCount", this.killCount);
        tag.putBoolean("blackoutActive", corruptCopTime.isActive());
        tag.putLong("blackoutStartTime", corruptCopTime.getStartTime());
        tag.putBoolean("blackoutHasBeenTriggered", corruptCopTime.isHasBeenTriggered());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.killCount = tag.contains("killCount") ? tag.getInt("killCount") : 0;

        boolean active = tag.contains("blackoutActive") && tag.getBoolean("blackoutActive");
        long startTime = tag.getLong("blackoutStartTime");
        boolean hasBeenTriggered = tag.contains("blackoutHasBeenTriggered") && tag.getBoolean("blackoutHasBeenTriggered");

        corruptCopTime.setActive(active);
        corruptCopTime.setStartTime(startTime);
        corruptCopTime.setHasBeenTriggered(hasBeenTriggered);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("killCount", this.killCount);
        tag.putBoolean("blackoutActive", corruptCopTime.isActive());
        tag.putLong("blackoutStartTime", corruptCopTime.getStartTime());
        tag.putBoolean("blackoutHasBeenTriggered", corruptCopTime.isHasBeenTriggered());
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.killCount = tag.contains("killCount") ? tag.getInt("killCount") : 0;

        boolean active = tag.contains("blackoutActive") && tag.getBoolean("blackoutActive");
        long startTime = tag.getLong("blackoutStartTime");
        boolean hasBeenTriggered = tag.contains("blackoutHasBeenTriggered") && tag.getBoolean("blackoutHasBeenTriggered");

        corruptCopTime.setActive(active);
        corruptCopTime.setStartTime(startTime);
        corruptCopTime.setHasBeenTriggered(hasBeenTriggered);
    }
}