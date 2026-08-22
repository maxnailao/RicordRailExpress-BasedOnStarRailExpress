package org.agmas.noellesroles.game.blindness;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.item.GuidanceCaneItem;
import org.agmas.noellesroles.packet.ContactRevealS2CPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 导盲杖探测核心服务（移植自"失明症"模组 CaneContactService）
 * <p>
 * 服务端权威计算探测结果：从玩家眼部沿视线（可带横扫偏角）做 4 格射线检测，
 * 命中方块后收集中心块 + 六个直接邻接面作为候选，剔除空气/超距/不可见面，
 * 最终通过 {@link ContactRevealS2CPacket} 下发给客户端渲染发光轮廓。
 * 客户端不自行探测，天然防作弊。
 */
public final class CaneContactService {

    /** 探测射线最大距离（格） */
    private static final double MAX_CONTACT_DISTANCE = 4.0;
    /** 揭示方块与眼部的最大距离平方（7 格） */
    private static final double MAX_REVEAL_DISTANCE_SQUARED = 7.0 * 7.0;
    /** 单次探测最多揭示的方块数：中心块 + 6 邻接面 */
    public static final int MAX_REVEALS_PER_CONTACT = 7;

    /** 每名玩家递增的探测序列号，随包下发供客户端区分探测批次 */
    private static final Map<UUID, Integer> CONTACT_SEQUENCES = new HashMap<>();

    private CaneContactService() {
    }

    /** 注册清理事件，防止跨局状态残留 */
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GuidanceCaneItem.clearSweepState(handler.getPlayer());
            CONTACT_SEQUENCES.remove(handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CONTACT_SEQUENCES.clear());
    }

    /**
     * 执行一次探测。
     *
     * @param player       探测玩家
     * @param yawOffset    视角水平偏角（横扫模式使用）
     * @param sweepContact 是否为横扫探测（横扫时不播放未命中的空挥音）
     * @return 是否命中方块
     */
    public static boolean performContact(ServerPlayer player, float yawOffset, boolean sweepContact) {
        Vec3 start = player.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot() + yawOffset);
        BlockHitResult hit = raycast(player, start, start.add(direction.scale(MAX_CONTACT_DISTANCE)));
        if (hit.getType() == HitResult.Type.MISS) {
            if (!sweepContact) {
                playMiss(player);
            }
            return false;
        }
        BlockPos center = hit.getBlockPos().immutable();
        List<RevealCandidate> candidates = buildCandidates(player, center, hit.getDirection());
        if (candidates.isEmpty()) {
            if (!sweepContact) {
                playMiss(player);
            }
            return false;
        }
        int sequence = CONTACT_SEQUENCES.merge(player.getUUID(), 1, Integer::sum);
        List<ContactRevealS2CPacket.Entry> entries = candidates.stream()
                .map(candidate -> ContactRevealS2CPacket.Entry.relativeTo(center, candidate.pos(),
                        candidate.center(), candidate.visibleFaces()))
                .toList();
        ServerPlayNetworking.send(player, new ContactRevealS2CPacket(sequence, center, entries));
        org.agmas.noellesroles.Noellesroles.LOGGER.info("[失明症] 探测命中: seq={} center={} entries={}",
                sequence, center.toShortString(), entries.size());
        playMaterialFeedback(player, center, sweepContact ? 0.55F : 0.8F);
        return true;
    }

    /** 收集中心块 + 邻接面候选（含双格方块补全），按可见性剔除后按距离排序截取 */
    private static List<RevealCandidate> buildCandidates(ServerPlayer player, BlockPos center, Direction hitFace) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        positions.add(center);
        for (Direction direction : Direction.values()) {
            positions.add(center.relative(direction));
        }
        // 双格方块（门、高草等）补全另一半
        BlockPos structureMate = null;
        for (BlockPos pos : List.copyOf(positions)) {
            BlockState state = player.level().getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                structureMate = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                        ? pos.above()
                        : pos.below();
                break;
            }
        }
        if (structureMate != null) {
            positions.add(structureMate);
        }
        List<RevealCandidate> result = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (result.size() >= MAX_REVEALS_PER_CONTACT) {
                break;
            }
            BlockState state = player.level().getBlockState(pos);
            if ((state.isAir() && state.getFluidState().isEmpty())
                    || Vec3.atCenterOf(pos).distanceToSqr(player.getEyePosition()) > MAX_REVEAL_DISTANCE_SQUARED) {
                continue;
            }
            VoxelShape shape = revealShape(player, pos, state);
            if (shape.isEmpty()) {
                continue;
            }
            // 中心块只勾勒被杖点中的那一面；其余块逐面检测可见性，避免透墙画轮廓
            int visibleFaces = pos.equals(center) ? 1 << hitFace.get3DDataValue() : visibleFaceMask(player, pos, shape);
            if (visibleFaces == 0) {
                continue;
            }
            result.add(new RevealCandidate(pos.immutable(), pos.equals(center), visibleFaces));
        }
        result.sort(Comparator.comparing(RevealCandidate::center).reversed()
                .thenComparingDouble(candidate -> Vec3.atCenterOf(candidate.pos()).distanceToSqr(player.getEyePosition()))
                .thenComparingLong(candidate -> candidate.pos().asLong()));
        return result.size() <= MAX_REVEALS_PER_CONTACT
                ? List.copyOf(result)
                : List.copyOf(result.subList(0, MAX_REVEALS_PER_CONTACT));
    }

    /** 取方块轮廓形状，固体轮廓为空时回退到流体形状 */
    static VoxelShape revealShape(ServerPlayer player, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(player.level(), pos, CollisionContext.of(player));
        if (shape.isEmpty() && !state.getFluidState().isEmpty()) {
            shape = state.getFluidState().getShape(player.level(), pos);
        }
        return shape;
    }

    /** 逐面中心采样射线，只有从眼部真正可见的面才写入掩码位；空形状直接返回 0 防崩溃 */
    static int visibleFaceMask(ServerPlayer player, BlockPos pos, VoxelShape shape) {
        if (shape.isEmpty()) {
            return 0;
        }
        AABB box = shape.bounds().move(pos);
        Vec3 eye = player.getEyePosition();
        int mask = 0;
        for (Direction direction : Direction.values()) {
            Vec3 sample = faceCenter(box, direction).add(direction.getStepX() * -0.01,
                    direction.getStepY() * -0.01, direction.getStepZ() * -0.01);
            BlockHitResult visibility = raycast(player, eye, sample);
            if (visibility.getType() == HitResult.Type.BLOCK && visibility.getBlockPos().equals(pos)) {
                mask |= 1 << direction.get3DDataValue();
            }
        }
        return mask;
    }

    private static Vec3 faceCenter(AABB box, Direction direction) {
        double x = (box.minX + box.maxX) * 0.5;
        double y = (box.minY + box.maxY) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        return switch (direction) {
            case DOWN -> new Vec3(x, box.minY, z);
            case UP -> new Vec3(x, box.maxY, z);
            case NORTH -> new Vec3(x, y, box.minZ);
            case SOUTH -> new Vec3(x, y, box.maxZ);
            case WEST -> new Vec3(box.minX, y, z);
            case EAST -> new Vec3(box.maxX, y, z);
        };
    }

    private static BlockHitResult raycast(ServerPlayer player, Vec3 start, Vec3 end) {
        return player.level().clip(
                new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
    }

    /** 未命中时播放空挥音，提示玩家"前方无物" */
    private static void playMiss(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.25F, 1.35F);
    }

    /** 按材质播放敲击反馈音，用听觉替代视觉传达方块材质信息 */
    private static void playMaterialFeedback(ServerPlayer player, BlockPos pos, float volume) {
        BlockState state = player.level().getBlockState(pos);
        SoundType group = state.getSoundType();
        SoundEvent sound;
        float pitch;
        if (!state.getFluidState().isEmpty()) {
            sound = SoundEvents.GENERIC_SPLASH;
            pitch = 0.9F;
        } else if (group == SoundType.GLASS) {
            sound = SoundEvents.GLASS_HIT;
            pitch = 1.45F;
        } else if (group == SoundType.METAL || group == SoundType.COPPER) {
            sound = SoundEvents.ANVIL_LAND;
            pitch = 1.65F;
        } else if (group == SoundType.WOOD || group == SoundType.BAMBOO_WOOD) {
            sound = SoundEvents.WOOD_HIT;
            pitch = 0.72F;
        } else if (state.is(BlockTags.LEAVES)) {
            sound = SoundEvents.GRASS_HIT;
            pitch = 1.15F;
        } else if (group == SoundType.GRASS || group == SoundType.SAND || group == SoundType.GRAVEL) {
            sound = SoundEvents.GRAVEL_HIT;
            pitch = 0.62F;
        } else {
            sound = SoundEvents.STONE_HIT;
            pitch = 1.2F;
        }
        player.level().playSound(null, pos, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** 声纹揭示专用：揭示声源周围少量实体方块轮廓，供 SoundEchoService 复用 */
    public static List<ContactRevealS2CPacket.Entry> buildSoundEchoEntries(ServerPlayer player, BlockPos origin,
            int maxBlocks) {
        List<RevealCandidate> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (candidates.size() >= maxBlocks) {
                        break;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = player.level().getBlockState(pos);
                    if (state.isAir() && state.getFluidState().isEmpty()) {
                        continue;
                    }
                    VoxelShape shape = revealShape(player, pos, state);
                    // 空形状无包围盒（bounds 会抛异常），整块实心方块无轮廓信息，均跳过
                    if (shape.isEmpty() || shape == Shapes.block()) {
                        continue;
                    }
                    int faces = visibleFaceMask(player, pos, shape);
                    if (faces == 0) {
                        faces = 0b111111;
                    }
                    candidates.add(new RevealCandidate(pos.immutable(), false, faces));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(
                candidate -> Vec3.atCenterOf(candidate.pos()).distanceToSqr(player.getEyePosition())));
        return candidates.stream()
                .limit(maxBlocks)
                .map(candidate -> ContactRevealS2CPacket.Entry.relativeTo(origin, candidate.pos(), false,
                        candidate.visibleFaces()))
                .toList();
    }

    private record RevealCandidate(BlockPos pos, boolean center, int visibleFaces) {
    }
}
