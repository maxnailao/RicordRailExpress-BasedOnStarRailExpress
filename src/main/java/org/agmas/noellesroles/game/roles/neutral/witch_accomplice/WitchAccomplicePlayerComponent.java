package org.agmas.noellesroles.game.roles.neutral.witch_accomplice;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 魔女共犯组件（狼方中立）。
 *
 * <p>职责：
 * <ul>
 * <li>锁定并同步「绑定的预备魔女」UUID 到本人客户端（供 InstinctRenderer 蓝色透视）</li>
 * <li>压力试剂领域：投掷落点为中心展开，领域内真实心情值玩家持续掉理智，预备魔女掉更多</li>
 * <li>被扣心情跟踪：离开领域 / 领域结束后，逐渐恢复被扣量的 50%</li>
 * <li>领域内对受影响玩家发送屏幕上方 actionbar 提示（预备魔女专属文案）</li>
 * </ul>
 */
public class WitchAccomplicePlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<WitchAccomplicePlayerComponent> KEY = ModComponents.WITCH_ACCOMPLICE;

    private final Player player;

    // ===== 领域状态 =====
    private boolean domainActive = false;
    private double domainX, domainY, domainZ;
    private long domainEndTick = 0;

    // ===== 绑定的预备魔女（同步本人客户端，供透视）=====
    private UUID boundPreWitch = null;

    // ===== 心情扣除 / 恢复跟踪 =====
    /** 领域内累计被扣心情 */
    private final Map<UUID, Float> drained = new HashMap<>();
    /** 待恢复量（= 被扣量的 50%），逐渐 addMood 回去 */
    private final Map<UUID, Float> recovering = new HashMap<>();

    public WitchAccomplicePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        domainActive = false;
        domainEndTick = 0;
        boundPreWitch = null;
        drained.clear();
        recovering.clear();
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /** 压力试剂入口：在指定落点为中心展开领域。成功返回 true。 */
    public boolean startDomain(double x, double y, double z) {
        if (!(player instanceof ServerPlayer sp))
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.WITCH_ACCOMPLICE))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return false;

        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        // 已有领域：先结束（原范围内玩家进入恢复），再在新落点重新展开
        if (domainActive)
            endDomain();
        this.domainX = x;
        this.domainY = y;
        this.domainZ = z;
        this.domainEndTick = sp.level().getGameTime() + cfg.witchAccompliceDomainSeconds * 20L;
        this.domainActive = true;
        this.drained.clear();
        ServerLevel level = sp.serverLevel();
        level.playSound(null, domainX, domainY, domainZ,
                SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp))
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.WITCH_ACCOMPLICE)) {
            if (domainActive || !drained.isEmpty() || !recovering.isEmpty() || boundPreWitch != null) {
                init();
            }
            return;
        }

        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();

        // ===== 绑定场上唯一的预备魔女（绑定后即使其转化为魔女也保持）=====
        if (boundPreWitch == null) {
            for (ServerPlayer p : level.players()) {
                if (p == sp)
                    continue;
                if (game.isRole(p, ModRoles.PRE_WITCH) && GameUtils.isPlayerAliveAndSurvival(p)) {
                    boundPreWitch = p.getUUID();
                    sync();
                    break;
                }
            }
        }

        // ===== 领域：持续扣心情 =====
        if (domainActive) {
            if (now >= domainEndTick) {
                endDomain();
            } else {
                double r = cfg.witchAccompliceDomainRadius;
                double rSq = r * r;
                for (ServerPlayer target : level.players()) {
                    if (!GameUtils.isPlayerAliveAndSurvival(target))
                        continue;
                    UUID uid = target.getUUID();
                    if (target.distanceToSqr(domainX, domainY, domainZ) > rSq) {
                        // 离开领域：启动「恢复被扣的 50%」
                        if (drained.containsKey(uid))
                            startRecover(uid);
                        continue;
                    }
                    // 仅对拥有「真实心情值」的玩家（好人与会掉理智的职业）生效与提示
                    SRERole role = game.getRole(target);
                    if (role == null || role.getMoodType() != SRERole.MoodType.REAL)
                        continue;
                    boolean isPreWitch = game.isRole(target, ModRoles.PRE_WITCH);
                    float rate = isPreWitch
                            ? cfg.witchAccomplicePreWitchDrainPerTick
                            : cfg.witchAccompliceDrainPerTick;
                    SREPlayerMoodComponent.KEY.get(target).addMood(-rate);
                    drained.merge(uid, rate, Float::sum);
                    // 屏幕上方 actionbar 提示：预备魔女与其它好人/掉理智职业分别文案
                    target.displayClientMessage(Component.translatable(isPreWitch
                            ? "message.noellesroles.witch_accomplice.domain_prewitch"
                            : "message.noellesroles.witch_accomplice.domain_in"), true);
                }
                // 领域粒子（密集，每 4 tick 一次）
                if (now % 4 == 0)
                    spawnDomainParticles(level, r);
            }
        }

        // ===== 逐渐恢复 =====
        processRecover(level, cfg);
    }

    private void endDomain() {
        domainActive = false;
        for (UUID uid : new HashSet<>(drained.keySet())) {
            startRecover(uid);
        }
        sync();
    }

    private void startRecover(UUID uid) {
        Float d = drained.remove(uid);
        if (d != null && d > 0f) {
            recovering.merge(uid, d * 0.5f, Float::sum);
        }
    }

    private void processRecover(ServerLevel level, NoellesRolesConfig cfg) {
        if (recovering.isEmpty())
            return;
        float perTick = Math.max(0.0001f, cfg.witchAccompliceRecoverPerTick);
        Iterator<Map.Entry<UUID, Float>> it = recovering.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Float> e = it.next();
            Player p = level.getPlayerByUUID(e.getKey());
            if (p == null || !GameUtils.isPlayerAliveAndSurvival(p)) {
                it.remove();
                continue;
            }
            float remaining = e.getValue();
            float add = Math.min(remaining, perTick);
            SREPlayerMoodComponent.KEY.get(p).addMood(add);
            remaining -= add;
            if (remaining <= 0.0001f)
                it.remove();
            else
                e.setValue(remaining);
        }
    }

    private void spawnDomainParticles(ServerLevel level, double radius) {
        // 边缘环（加密）
        int ring = (int) Math.min(120, Math.max(48, radius * 10.0));
        for (int i = 0; i < ring; i++) {
            double angle = (2.0 * Math.PI * i) / ring;
            double px = domainX + Math.cos(angle) * radius;
            double pz = domainZ + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.SOUL, px, domainY + 0.4, pz, 1, 0.15, 0.2, 0.15, 0.0);
        }
        // 内部填充：在整个圆盘内随机密集撒上升粒子
        int fill = 80;
        for (int i = 0; i < fill; i++) {
            double ang = level.random.nextDouble() * 2.0 * Math.PI;
            double rr = Math.sqrt(level.random.nextDouble()) * radius;
            double px = domainX + Math.cos(ang) * rr;
            double pz = domainZ + Math.sin(ang) * rr;
            double py = domainY + 0.2 + level.random.nextDouble() * 2.2;
            SimpleParticleType type = level.random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.SOUL;
            level.sendParticles(type, px, py, pz, 1, 0.05, 0.3, 0.05, 0.01);
        }
    }

    /** 供客户端 InstinctRenderer 调用：目标是否为绑定的预备魔女 */
    public boolean isBoundPreWitch(UUID uuid) {
        return uuid != null && boundPreWitch != null && boundPreWitch.equals(uuid);
    }

    public boolean isDomainActive() {
        return domainActive;
    }

    // ===== NBT 同步 =====

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        if (boundPreWitch != null)
            tag.putUUID("boundPreWitch", boundPreWitch);
        tag.putBoolean("domainActive", domainActive);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        boundPreWitch = tag.hasUUID("boundPreWitch") ? tag.getUUID("boundPreWitch") : null;
        domainActive = tag.getBoolean("domainActive");
        SREClient.cachedHighLightMap.clear();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}