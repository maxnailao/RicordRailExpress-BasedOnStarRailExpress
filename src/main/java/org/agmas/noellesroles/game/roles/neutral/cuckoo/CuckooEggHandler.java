package org.agmas.noellesroles.game.roles.neutral.cuckoo;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

/**
 * 布谷鸟蛋破坏处理（纯tick检测，无需右键触发）：
 * - 非布谷鸟玩家在蛋3格内蹲下看着蛋 → 累计破坏进度，5秒后打碎
 * - 非布谷鸟玩家在蛋1格内 → actionbar提示可蹲下破坏
 * - 布谷鸟本人无法打碎自己的蛋
 */
public class CuckooEggHandler {
    /** 破坏所需tick数（5秒 = 100tick） */
    public static final int BREAK_TICKS = 100;
    /** 破坏交互距离（格） */
    private static final double BREAK_DISTANCE = 3.0;
    /** 提示距离（格） */
    private static final double HINT_DISTANCE = 1.0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CuckooEggHandler::tick);
    }

    /** 客户端/服务端通用：仅凭同步的 tag 判断实体是否为蛋的交互实体（用于射线瞄准过滤） */
    public static boolean isEggInteraction(Entity entity) {
        return entity != null && entity.getTags().contains(CuckooEggData.EGG_INTERACTION_TAG);
    }

    /**
     * 枪/刀/棒子命中蛋的统一服务端处理：
     * 按注册表解析真实蛋（防伪造实体ID），布谷鸟本人无法打碎自己的蛋，成功时记录回放并破碎。
     * @return true 表示蛋已被打碎
     */
    public static boolean tryBreakEggByAttacker(Entity clickedEntity, ServerPlayer attacker) {
        if (!(attacker.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return false;
        CuckooEggData.EggInfo info = CuckooEggData.resolveEggInfo(clickedEntity);
        if (info == null || info.eggEntity == null || info.eggEntity.isRemoved()) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (gameWorld.isRole(attacker, ModRoles.CUCKOO) && info.ownerUuid.equals(attacker.getUUID())) {
            attacker.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.cant_break_own_egg")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.cuckoo.egg_crushed",
                        GameReplayUtils.getReplayPlayerDisplayText(attacker, true)));
        breakEgg(info.eggEntity, info, attacker.server);
        CuckooEggData.unregisterEgg(info.eggEntity.getUUID());
        return true;
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (CuckooEggData.getAllEggs().isEmpty()) return;
        var level = server.overworld();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        var gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (!gameWorld.isRunning()) return;

        var iter = CuckooEggData.getAllEggs().entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            UUID eggUuid = entry.getKey();
            CuckooEggData.EggInfo info = entry.getValue();

            // 使用缓存的实体引用
            Entity eggEntity = info.eggEntity;
            // 验证实体仍然有效
            if (eggEntity == null || eggEntity.isRemoved() || !(eggEntity instanceof Display.BlockDisplay)
                    || !eggEntity.getUUID().equals(eggUuid)) {
                // 蛋已丢失：同步清理绑定的交互实体，避免残留
                if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                    info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
                }
                CuckooEggData.unregisterEgg(eggUuid);
                iter.remove();
                continue;
            }

            // 扫描附近的玩家
            boolean hasActiveBreaker = false;
            for (var player : serverLevel.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player) || player.isSpectator()) continue;

                double dist = player.distanceTo(eggEntity);

                // 布谷鸟玩家
                if (gameWorld.isRole(player, ModRoles.CUCKOO)) {
                    // 布谷鸟蹲下看自己的蛋 → 提示不能打碎
                    if (dist <= BREAK_DISTANCE && player.isShiftKeyDown() && isLookingAt(player, eggEntity)
                            && info.ownerUuid.equals(player.getUUID())) {
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            sp.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.cant_break_own_egg")
                                    .withStyle(ChatFormatting.RED), true);
                        }
                    }
                    continue;
                }

                // 非布谷鸟玩家在1格内 → actionbar提示可蹲下破坏
                if (dist <= HINT_DISTANCE) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        sp.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.egg_hint")
                                .withStyle(ChatFormatting.GREEN), true);
                    }
                }

                // 非布谷鸟玩家在3格内蹲下看着蛋 → 累计破坏进度
                if (dist <= BREAK_DISTANCE && player.isShiftKeyDown() && isLookingAt(player, eggEntity)) {
                    info.breakProgress++;
                    hasActiveBreaker = true;
                    int secondsLeft = Math.max(1, (BREAK_TICKS - info.breakProgress + 19) / 20);
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        sp.displayClientMessage(Component.translatable("message.noellesroles.cuckoo.egg_break_progress", secondsLeft)
                                .withStyle(ChatFormatting.YELLOW), true);
                    }

                    if (info.breakProgress >= BREAK_TICKS) {
                        // 回放记录：布谷鸟蛋被玩家踩碎
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp2) {
                            SRE.REPLAY_MANAGER.recordCustomEvent(
                                Component.translatable("replay.event.cuckoo.egg_crushed",
                                    GameReplayUtils.getReplayPlayerDisplayText(sp2, true)));
                        }
                        breakEgg(eggEntity, info, server);
                        iter.remove();
                        hasActiveBreaker = false;
                        break;
                    }
                }
            }

            // 无人蹲下破坏 → 进度衰减
            if (!hasActiveBreaker && info.breakProgress > 0) {
                info.breakProgress = Math.max(0, info.breakProgress - 2);
            }
        }
    }

    /** 检测玩家是否正在看着指定实体 */
    private static boolean isLookingAt(net.minecraft.world.entity.player.Player player, Entity target) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 toTarget = target.position().add(0, target.getBbHeight() / 2.0, 0).subtract(eyePos);
        double dist = toTarget.length();
        if (dist > BREAK_DISTANCE) return false;
        toTarget = toTarget.normalize();
        double dot = lookVec.dot(toTarget);
        // 约40度视角范围（cos(40°) ≈ 0.766）
        return dot > 0.766;
    }

    /** 破碎蛋并通知主人组件 */
    public static void breakEgg(Entity eggEntity, CuckooEggData.EggInfo info, net.minecraft.server.MinecraftServer server) {
        UUID owner = info.ownerUuid;
        if (owner != null) {
            var ownerPlayer = server.getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                var comp = CuckooPlayerComponent.KEY.get(ownerPlayer);
                if (comp != null) comp.onEggBroken(eggEntity);
            }
        }
        // 同步移除蛋绑定的交互实体
        if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
            info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
        }
        if (eggEntity != null && !eggEntity.isRemoved()) {
            // 播放海龟蛋破碎音效
            eggEntity.level().playSound(null, eggEntity.blockPosition(),
                    net.minecraft.sounds.SoundEvents.TURTLE_EGG_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            eggEntity.remove(Entity.RemovalReason.DISCARDED);
        }
    }
}
