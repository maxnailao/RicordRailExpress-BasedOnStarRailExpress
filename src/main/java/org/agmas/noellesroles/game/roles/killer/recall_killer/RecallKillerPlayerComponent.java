package org.agmas.noellesroles.game.roles.killer.recall_killer;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 召回杀手组件
 * 技能与平民召回者一致：
 * - 第一次使用：记录当前位置为召回点
 * - 第二次使用：传送回召回点并清空
 * 不包含储能/金币逻辑（由 AbilityHandler 控制冷却）
 *
 * 额外：锚点可见粒子效果
 * - placed==true 时在锚点处持续刷“粒子柱”
 * - teleport() 后 placed=false，粒子随锚点消失
 * 后续可通过RecallKillerPlayerComponent.ENABLE_ANCHOR_PARTICLE = false;关闭标记
 */
public class RecallKillerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 锚点粒子标记开关：true 启用，false 禁用 */
    public static boolean ENABLE_ANCHOR_PARTICLE = true;

    /** 粒子刷新间隔（tick）：越小越明显，但越耗 */
    private static final int ANCHOR_PARTICLE_INTERVAL = 2;

    private final Player player;

    /** 是否已放置召回点 */
    public boolean placed = false;

    /** 召回点坐标 */
    public double x = 0;
    public double y = 0;
    public double z = 0;

    private int anchorParticleTick = 0;

    public RecallKillerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.placed = false;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.anchorParticleTick = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        ModComponents.RECALL_KILLER.sync(this.player);
    }

    @Override
    public void clientTick() {
        // 无客户端 Tick 逻辑
    }

    @Override
    public void serverTick() {
        if (!ENABLE_ANCHOR_PARTICLE) return;
        if (!placed) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        anchorParticleTick++;
        if (anchorParticleTick % ANCHOR_PARTICLE_INTERVAL != 0) return;

        spawnAnchorMarker(serverLevel, x, y, z);
    }

    /** 放置召回点 */
    public void setPosition() {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.placed = true;
        this.anchorParticleTick = 0;

        // 放点瞬间给一个更明显的提示（可选）
        if (ENABLE_ANCHOR_PARTICLE && player.level() instanceof ServerLevel serverLevel) {
            spawnAnchorBurst(serverLevel, x, y, z);
        }

        this.sync();
    }

    /** 传送回召回点 */
    public void teleport() {
        if (!placed) return;

        double fromX = player.getX();
        double fromY = player.getY();
        double fromZ = player.getZ();

        // 召回前：让锚点位置“消散”一下（可选）
        if (ENABLE_ANCHOR_PARTICLE && player.level() instanceof ServerLevel serverLevel) {
            spawnAnchorDisappear(serverLevel, x, y, z);
        }

        if (player.level() instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            ConfigWorldComponent.onPlayerUsedSkill(sp);
            playTeleportEffects(serverLevel, fromX, fromY, fromZ);
        }

        player.teleportTo(x, y, z);

        if (player.level() instanceof ServerLevel serverLevel) {
            playTeleportEffects(serverLevel, x, y, z);
        }

        this.placed = false;
        this.anchorParticleTick = 0;
        this.sync();
    }

    /** 一键：未放点则放点；已放点则传送 */
    public void useSkill() {
        if (!placed) setPosition();
        else teleport();
    }

    /** 锚点持续可见标记 */
    private void spawnAnchorMarker(ServerLevel level, double px, double py, double pz) {
        // 使用方块中心，让标记更稳、更“像一个锚点”
        double cx = Math.floor(px) + 0.5D;
        double cz = Math.floor(pz) + 0.5D;
        double baseY = py + 0.05D;

        // 柱子（END_ROD）更明显
        /*for (int i = 0; i < 10; i++) {
            double yy = baseY + i * 0.35D; // ~3.2格高
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    cx, yy, cz,
                    2,
                    0.03D, 0.03D, 0.03D,
                    0.0D
            );
        }*/

        // 底部环
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2D * i / 16D;
            double ox = Math.cos(angle) * 0.3D;
            double oz = Math.sin(angle) * 0.3D;
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    cx + ox, baseY + 0.15D, cz + oz,
                    1,
                    0.0D, 0.0D, 0.0D,
                    0.0D
            );
        }

        // 顶部一点“闪烁”
        /*level.sendParticles(
                ParticleTypes.ENCHANT,
                cx, baseY + 3.1D, cz,
                2,
                0.12D, 0.03D, 0.12D,
                0.0D
        );*/
    }

    /** 放点瞬间爆发提示（可选） */
    private void spawnAnchorBurst(ServerLevel level, double px, double py, double pz) {
        double cx = Math.floor(px) + 0.5D;
        double cz = Math.floor(pz) + 0.5D;
        double baseY = py + 0.2D;
        level.sendParticles(ParticleTypes.END_ROD, cx, baseY, cz, 12, 0.35D, 0.2D, 0.35D, 0.01D);
        level.playSound(null, cx, py, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    /** 锚点消失提示（可选） */
    private void spawnAnchorDisappear(ServerLevel level, double px, double py, double pz) {
        double cx = Math.floor(px) + 0.5D;
        double cz = Math.floor(pz) + 0.5D;
        level.sendParticles(ParticleTypes.POOF, cx, py + 0.2D, cz, 10, 0.3D, 0.2D, 0.3D, 0.01D);
    }

    private void playTeleportEffects(ServerLevel serverLevel, double centerX, double centerY, double centerZ) {
        double particleY = centerY + 0.9D;

        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2D * i / 16D;
            double offsetX = Math.cos(angle) * 0.8D;
            double offsetZ = Math.sin(angle) * 0.8D;
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    centerX + offsetX, particleY, centerZ + offsetZ,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                centerX, particleY, centerZ,
                10, 0.25D, 0.35D, 0.25D, 0.05D);

        serverLevel.playSound(null, centerX, centerY, centerZ,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putDouble("x", this.x);
        tag.putDouble("y", this.y);
        tag.putDouble("z", this.z);
        tag.putBoolean("placed", this.placed);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.x = tag.contains("x") ? tag.getDouble("x") : 0;
        this.y = tag.contains("y") ? tag.getDouble("y") : 0;
        this.z = tag.contains("z") ? tag.getDouble("z") : 0;
        this.placed = tag.contains("placed") && tag.getBoolean("placed");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不需要跨存档保留则留空；如需要可改为与 writeToSyncNbt 相同
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不需要跨存档保留则留空；如需要可改为与 readFromSyncNbt 相同
    }
}