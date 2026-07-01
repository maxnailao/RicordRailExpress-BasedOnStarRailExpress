package org.agmas.noellesroles.game.roles.innocence.intelligence;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.ArrayList;
import java.util.List;

public class IntelligencePlayerComponent implements RoleComponent {
    public static final ComponentKey<IntelligencePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "intelligence"),
            IntelligencePlayerComponent.class
    );

    private final Player player;

    // 监视器数据
    public final List<MonitorData> monitors = new ArrayList<>();
    public boolean intelPurchased = false;
    
    // 技能冷却和剩余次数（用于HUD显示）
    public int placeCooldown = 0; // 放置冷却（tick）
    public int remainingPlaces = 2; // 剩余可放置次数

    public static class MonitorData {
        public double x, y, z;
        public ResourceLocation worldId;

        public MonitorData(double x, double y, double z, ResourceLocation worldId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.worldId = worldId;
        }
    }

    public IntelligencePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        monitors.clear();
        intelPurchased = false;
        placeCooldown = 0;
        remainingPlaces = 2;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean canPlaceMonitor() {
        return remainingPlaces > 0;
    }

    public void addMonitor(double x, double y, double z, ResourceLocation worldId) {
        if (monitors.size() >= 2) return;
        monitors.add(new MonitorData(x, y, z, worldId));
        remainingPlaces--;
        sync();
    }

    public void removeMonitor(int index) {
        if (index >= 0 && index < monitors.size()) {
            monitors.remove(index);
            sync();
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        CompoundTag monitorsTag = new CompoundTag();
        monitorsTag.putInt("count", monitors.size());
        for (int i = 0; i < monitors.size(); i++) {
            MonitorData m = monitors.get(i);
            CompoundTag mt = new CompoundTag();
            mt.putDouble("x", m.x);
            mt.putDouble("y", m.y);
            mt.putDouble("z", m.z);
            mt.putString("world", m.worldId.toString());
            monitorsTag.put("m" + i, mt);
        }
        tag.put("monitors", monitorsTag);
        tag.putBoolean("intelPurchased", intelPurchased);
        tag.putInt("placeCooldown", placeCooldown);
        tag.putInt("remainingPlaces", remainingPlaces);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        monitors.clear();
        if (tag.contains("monitors")) {
            CompoundTag monitorsTag = tag.getCompound("monitors");
            int count = monitorsTag.getInt("count");
            for (int i = 0; i < count; i++) {
                String key = "m" + i;
                if (monitorsTag.contains(key)) {
                    CompoundTag mt = monitorsTag.getCompound(key);
                    monitors.add(new MonitorData(
                            mt.getDouble("x"),
                            mt.getDouble("y"),
                            mt.getDouble("z"),
                            ResourceLocation.parse(mt.getString("world"))
                    ));
                }
            }
        }
        intelPurchased = tag.getBoolean("intelPurchased");
        placeCooldown = tag.getInt("placeCooldown");
        remainingPlaces = tag.getInt("remainingPlaces");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    // ==================== 事件注册 ====================

    public static void registerEvents() {
        // 监听玩家死亡（无击杀者）
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            checkMonitors(player, null, deathReason);
        });

        // 监听玩家死亡（有击杀者）
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            checkMonitors(victim, killer, deathReason);
        });
    }

    private static void checkMonitors(Player victim, Player killer, ResourceLocation deathReason) {
        Level level = victim.level();
        if (level.isClientSide()) return;

        ResourceLocation worldId = level.dimension().location();

        // 遍历所有在线玩家，检查情报官的监视器
        for (Player p : level.players()) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
            if (!gameWorld.isRole(p, ModRoles.INTELLIGENCE)) continue;

            IntelligencePlayerComponent comp = KEY.get(p);
            if (comp == null || comp.monitors.isEmpty()) continue;

            // 检查每个监视器
            for (int i = comp.monitors.size() - 1; i >= 0; i--) {
                MonitorData monitor = comp.monitors.get(i);
                if (!monitor.worldId.equals(worldId)) continue;

                double dx = victim.getX() - monitor.x;
                double dy = victim.getY() - monitor.y;
                double dz = victim.getZ() - monitor.z;
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq <= 25.0) { // 5格半径 (5²=25)
                    // 触发监视器
                    comp.removeMonitor(i);

                    // 提醒情报官
                    if (p instanceof ServerPlayer sp) {
                        sp.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.noellesroles.intelligence.monitor_triggered")
                                        .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD),
                                true
                        );
                        // 播放吸取经验音效
                        sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                    }

                    // 高亮范围内所有玩家2秒（发光效果）
                    for (Player nearby : level.players()) {
                        double ndx = nearby.getX() - monitor.x;
                        double ndy = nearby.getY() - monitor.y;
                        double ndz = nearby.getZ() - monitor.z;
                        double ndistSq = ndx * ndx + ndy * ndy + ndz * ndz;
                        if (ndistSq <= 25.0) {
                            nearby.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, true)); // 2秒=40tick
                        }
                    }

                    break; // 一个监视器触发后就不再检查其他监视器（死亡只触发一次）
                }
            }
        }
    }
}
