package org.agmas.noellesroles.game.blindness;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.SoundEchoS2CPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生物声纹感知服务（移植自"失明症"模组 EntitySoundEcho 机制）
 * <p>
 * 失明症玩家看不见环境，但能"听见"周围生物：每 {@link #SCAN_INTERVAL_TICKS} tick
 * 扫描一次失明玩家 {@link #LISTEN_RADIUS} 格内的生物，按威胁程度分类
 * （危险/普通/脚步），计算声音强度与墙体遮挡，随后下发声纹标记包：
 * 客户端在屏幕中心周围渲染方向性声纹标记，并揭示声源附近少量方块的弱轮廓。
 */
public final class SoundEchoService {

    /** 扫描间隔（tick） */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** 监听半径（格） */
    private static final double LISTEN_RADIUS = 12.0;
    /** 声纹揭示的声源附近方块数上限 */
    private static final int MAX_REVEAL_BLOCKS = 4;
    /** 同一生物的声纹触发冷却（tick），防止刷屏 */
    private static final long ECHO_COOLDOWN_TICKS = 30;
    /** 被墙体遮挡时的强度衰减系数 */
    private static final float OCCLUDED_STRENGTH_SCALE = 0.6F;

    /** 每个生物下一次允许发声纹的时间（游戏 tick） */
    private static final Map<UUID, Long> NEXT_ECHO_AT = new HashMap<>();

    private SoundEchoService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.overworld().getGameTime();
            if (now % SCAN_INTERVAL_TICKS != 0) {
                return;
            }
            NEXT_ECHO_AT.values().removeIf(time -> time < now);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.hasEffect(ModEffects.BLINDNESS_SICKNESS)) {
                    continue;
                }
                scanForPlayer(player, now);
            }
        });
    }

    private static void scanForPlayer(ServerPlayer player, long now) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 playerPos = player.position();
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(LISTEN_RADIUS), entity -> entity != player && entity.isAlive());
        for (LivingEntity entity : entities) {
            double distance = entity.position().distanceTo(playerPos);
            if (distance > LISTEN_RADIUS || distance < 0.5) {
                continue;
            }
            SoundEchoS2CPacket.Category category = categorize(entity);
            if (category == null) {
                continue;
            }
            Long nextAllowed = NEXT_ECHO_AT.get(entity.getUUID());
            if (nextAllowed != null && now < nextAllowed) {
                continue;
            }
            NEXT_ECHO_AT.put(entity.getUUID(), now + ECHO_COOLDOWN_TICKS);

            float strength = (float) (1.0 - distance / LISTEN_RADIUS);
            boolean occluded = isOccluded(player, entity);
            if (occluded) {
                strength *= OCCLUDED_STRENGTH_SCALE;
            }
            BlockPos blockCenter = entity.blockPosition();
            List<org.agmas.noellesroles.packet.ContactRevealS2CPacket.Entry> entries =
                    CaneContactService.buildSoundEchoEntries(player, blockCenter, MAX_REVEAL_BLOCKS);
            ServerPlayNetworking.send(player, new SoundEchoS2CPacket(entity.position(), category,
                    strength, occluded, blockCenter, entries));
        }
    }

    /** 按生物类型分类；静止的玩家与无法产生声音的目标返回 null 跳过 */
    private static SoundEchoS2CPacket.Category categorize(LivingEntity entity) {
        if (entity instanceof Enemy) {
            return SoundEchoS2CPacket.Category.DANGER;
        }
        if (entity instanceof ServerPlayer other) {
            // 玩家只有在移动时才产生脚步声
            boolean moving = entity.distanceToSqr(entity.xo, entity.yo, entity.zo) > 1.0E-6D;
            return moving ? SoundEchoS2CPacket.Category.FOOTSTEP : null;
        }
        if (entity instanceof Animal || entity instanceof WaterAnimal) {
            return SoundEchoS2CPacket.Category.AMBIENT;
        }
        return SoundEchoS2CPacket.Category.AMBIENT;
    }

    /** 眼部到声源的射线是否被方块遮挡（墙后声音模糊化） */
    private static boolean isOccluded(ServerPlayer player, LivingEntity entity) {
        Vec3 soundPos = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        HitResult hit = player.level().clip(new ClipContext(player.getEyePosition(), soundPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK;
    }
}
