package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.content.entity.GrenadeEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反人员地雷方块实体
 * <ul>
 * <li>布设后等待布设者离开布设位置</li>
 * <li>布设者离开后经过 3 秒进入待发状态</li>
 * <li>待发状态下玩家踩到地雷时发出按钮声，踩雷者离开踩踏坐标即引爆</li>
 * <li>引爆造成以地雷为中心半径 2.5 格的爆炸，范围内所有玩家被炸死，击杀者记为布设者</li>
 * </ul>
 */
public class LandmineBlockEntity extends BlockEntity {
    /** 布设者离开后的布防延迟（3秒） */
    public static final int ARM_DELAY_TICKS = 60;
    /** 爆炸半径 */
    public static final float EXPLOSION_RADIUS = 2.5F;

    /** 客户端已加载的地雷实例集合，供透视渲染器遍历 */
    public static final Set<LandmineBlockEntity> CLIENT_INSTANCES = ConcurrentHashMap.newKeySet();

    /** 布设者 UUID */
    private @Nullable UUID owner;
    /** 布设者是否已离开布设位置 */
    private boolean ownerLeft;
    /** 布防倒计时 */
    private int armTimer;
    /** 是否已进入待发状态 */
    private boolean armed;
    /** 当前踩雷玩家 UUID */
    private @Nullable UUID victim;
    /** 踩雷玩家踩踏时的坐标 */
    private @Nullable BlockPos victimStepPos;

    public LandmineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.LANDMINE_BLOCK_ENTITY, pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && level.isClientSide) {
            CLIENT_INSTANCES.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_INSTANCES.remove(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && level.isClientSide) {
            CLIENT_INSTANCES.add(this);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LandmineBlockEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 阶段一：布设者尚未离开布设位置，不进行任何判定
        if (!entity.ownerLeft) {
            Player ownerPlayer = entity.owner != null ? serverLevel.getPlayerByUUID(entity.owner) : null;
            if (ownerPlayer == null || !ownerPlayer.blockPosition().equals(pos)) {
                entity.ownerLeft = true;
                entity.setChanged();
            } else {
                return;
            }
        }
        // 阶段二：布设者离开后延迟 3 秒才进入待发状态
        if (!entity.armed) {
            entity.armTimer++;
            if (entity.armTimer < ARM_DELAY_TICKS) {
                return;
            }
            entity.armed = true;
            entity.setChanged();
        }
        // 阶段三：已有玩家踩雷
        if (entity.victim != null) {
            Player victimPlayer = serverLevel.getPlayerByUUID(entity.victim);
            if (victimPlayer == null || victimPlayer.isRemoved()
                    || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(victimPlayer)) {
                // 踩雷者离线或已死亡，重置踩踏状态
                entity.victim = null;
                entity.victimStepPos = null;
                entity.setChanged();
                return;
            }
            // 踩雷者离开其踩踏坐标 → 引爆
            if (!victimPlayer.blockPosition().equals(entity.victimStepPos)) {
                entity.explode(serverLevel, pos);
            }
            return;
        }
        // 阶段四：检测是否有玩家踩上地雷
        for (Player player : serverLevel.players()) {
            if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
                continue;
            }
            if (player.blockPosition().equals(pos)) {
                entity.victim = player.getUUID();
                entity.victimStepPos = player.blockPosition().immutable();
                // 踩雷时发出按按钮的声音
                serverLevel.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS,
                        1.0F, 0.8F);
                entity.setChanged();
                break;
            }
        }
    }

    /**
     * 引爆地雷：播放爆炸粒子与音效，炸死范围内所有玩家
     */
    private void explode(ServerLevel level, BlockPos pos) {
        // 先移除方块，避免重复引爆
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.1D;
        double cz = pos.getZ() + 0.5D;

        // 爆炸音效与粒子
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 80, 0.6D, 0.6D, 0.6D, 0.15D);

        // 击杀者记为布设该地雷的玩家
        Player killer = owner != null ? level.getPlayerByUUID(owner) : null;

        // 空间多层采样检测爆炸范围内的玩家（复用手雷的判定逻辑）
        var hitted = new HashSet<Entity>();
        double[] yOffsets = { 0.0, 0.5, -0.5, 1.0, -1.0 };
        for (double yOff : yOffsets) {
            hitted.addAll(GrenadeEntity.getPlayersAffectedByExplosion(level, cx, cy + yOff, cz, EXPLOSION_RADIUS));
        }
        for (Entity entity : hitted) {
            if (entity instanceof Player player) {
                GameUtils.killPlayer(player, true, killer, Noellesroles.id("landmine_explosion"));
            } else if (entity instanceof PuppeteerBodyEntity puppeteerBody) {
                puppeteerBody.playerHurt(killer, Noellesroles.id("landmine_explosion"));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putBoolean("OwnerLeft", ownerLeft);
        tag.putInt("ArmTimer", armTimer);
        tag.putBoolean("Armed", armed);
        if (victim != null) {
            tag.putUUID("Victim", victim);
        }
        if (victimStepPos != null) {
            tag.putInt("VictimStepX", victimStepPos.getX());
            tag.putInt("VictimStepY", victimStepPos.getY());
            tag.putInt("VictimStepZ", victimStepPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerLeft = tag.getBoolean("OwnerLeft");
        armTimer = tag.getInt("ArmTimer");
        armed = tag.getBoolean("Armed");
        victim = tag.hasUUID("Victim") ? tag.getUUID("Victim") : null;
        if (tag.contains("VictimStepX")) {
            victimStepPos = new BlockPos(tag.getInt("VictimStepX"), tag.getInt("VictimStepY"),
                    tag.getInt("VictimStepZ"));
        }
    }
}
