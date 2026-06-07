package org.agmas.noellesroles.game.roles.neutral.corruptcop;

import io.wifi.starrailexpress.api.GameMode;
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

import static pro.fazeclan.river.stupid_express.role.avaricious.AvariciousGoldHandler.gameStartTime;

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
        // 只有活跃的黑警才需要检测
        if (!isActiveCorruptCop())
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        // 亡命徒时刻不触发黑警时刻
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
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