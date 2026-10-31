package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.innocence.jidongzhe.JidongzheRole;
import org.agmas.noellesroles.game.roles.innocence.jidongzhe.JidongzheRoleData;
import org.agmas.noellesroles.packet.JidongzheSlideC2SPacket;
import org.agmas.noellesroles.packet.JidongzheSlidePoseS2CPacket;
import org.agmas.noellesroles.packet.JidongzheStaminaS2CPacket;

import java.util.HashMap;
import java.util.Map;

/**
 * 机动者的客户端侧：滑铲输入上报、滑铲姿态保持、他人姿态同步、体力数值回落。
 *
 * <p>滑铲不是一套动画，而是一个**静态姿势**：只在滑铲位移阶段保持，位移结束或跃起时立刻取消。
 * 所以这里只维护「实体 id -> 姿态保持到哪一刻」，渲染侧按布尔值取用，不做任何插值。</p>
 *
 * <p>本人姿态由本地预测建立，不等服务端回包；服务端姿态包用于其他玩家，并在到达时校正本人的结束时刻。</p>
 *
 * <p>所有客户端逻辑都留在本类，RoleData 侧不引用任何客户端类，避免专用服务端加载到客户端符号。</p>
 */
public final class JidongzheClient {

    /** 实体 id -> 姿态保持到的客户端世界刻（不含） */
    private static final Map<Integer, Long> POSE_UNTIL = new HashMap<>();

    private static boolean wasSneakDown;
    private static boolean wasJumpDown;
    /** 疾跑记忆刻数：原版在按下蹲键的同刻就关掉疾跑，直接判 isSprinting() 会漏掉整次触发 */
    private static int sprintMemoryTicks;
    /** 本地镜像的滑铲冷却截止刻（客户端世界刻），用于预测距离与限制连点 */
    private static long localSlideReadyAt;

    /** 一次性诊断日志：确认状态侧与渲染侧到底哪一环没通（排查完可删） */
    private static boolean loggedLocalSlide;
    private static boolean loggedPoseApplied;

    private JidongzheClient() {
    }

    /**
     * 注册姿态包 / 体力包接收器与输入检测（在 NoellesrolesClient.onInitializeClient 中调用）
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(JidongzheSlidePoseS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> applyPose(payload, context.client())));
        // 体力在服务端与客户端各自模拟，服务端扣/回的数值只能靠这个包落回本地体力条
        ClientPlayNetworking.registerGlobalReceiver(JidongzheStaminaS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().player instanceof PlayerStaminaGetter stamina) {
                        stamina.starrailexpress$setStamina(payload.stamina());
                    }
                }));
        ClientTickEvents.END_CLIENT_TICK.register(JidongzheClient::tick);
    }

    /**
     * 蹲键 / 跳键的上升沿只能在客户端可靠判定。这里立刻本地进入滑铲姿态，并把意图上报，
     * 位移与体力结算仍由服务端说了算。
     */
    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) {
            return;
        }
        long now = client.level.getGameTime();
        int selfId = client.player.getId();

        // 本地玩家持有机动者职业数据才检测输入（RoleData 仅同步给本人）
        boolean isJidongzhe = RoleData.getNullable(JidongzheRoleData.class, client.player) != null;
        if (client.player.isSprinting()) {
            sprintMemoryTicks = JidongzheRole.SPRINT_MEMORY_TICKS;
        } else if (sprintMemoryTicks > 0) {
            sprintMemoryTicks--;
        }
        boolean sneakDown = isJidongzhe && client.options.keyShift.isDown();
        boolean jumpDown = isJidongzhe && client.options.keyJump.isDown();

        if (sneakDown && !wasSneakDown && sprintMemoryTicks > 0 && now >= localSlideReadyAt) {
            int duration = predictSlideDurationTicks(now);
            localSlideReadyAt = now + JidongzheRole.SLIDE_COOLDOWN_TICKS;
            POSE_UNTIL.put(selfId, now + duration);
            if (!loggedLocalSlide) {
                loggedLocalSlide = true;
                Noellesroles.LOGGER.info("[机动者] 本地进入滑铲姿态：entityId={} 持续={}刻", selfId, duration);
            }
            ClientPlayNetworking.send(new JidongzheSlideC2SPacket(false));
            sprintMemoryTicks = 0;
        } else if (jumpDown && !wasJumpDown && POSE_UNTIL.getOrDefault(selfId, 0L) > now) {
            // 跃起即取消姿态，不等服务端回包
            POSE_UNTIL.remove(selfId);
            ClientPlayNetworking.send(new JidongzheSlideC2SPacket(true));
        }
        wasSneakDown = sneakDown;
        wasJumpDown = jumpDown;
    }

    /**
     * 本地按同一份公式估算本次滑铲的持续刻数。服务端以实际走过的格数收尾，可能比估算多几刻，
     * 所以本地保持时长加上同样的冗余量，真实结束由服务端结束包提前取消。
     */
    private static int predictSlideDurationTicks(long now) {
        long remaining = Math.max(0L, localSlideReadyAt - now);
        double distance = JidongzheRole.slideDistanceForCooldown(remaining);
        return JidongzheRole.estimateSlideTicks(distance) + JidongzheRole.SLIDE_TICK_TOLERANCE;
    }

    private static void applyPose(JidongzheSlidePoseS2CPacket packet, Minecraft client) {
        if (client.level == null) {
            return;
        }
        long now = client.level.getGameTime();
        if (packet.start()) {
            Long pending = POSE_UNTIL.get(packet.entityId());
            if (pending != null && pending > now) {
                // 本地预测已在保持（两端用的是同一份距离公式，长度一致），不重起点也不改时长
                return;
            }
            POSE_UNTIL.put(packet.entityId(), now + Math.max(1, packet.durationTicks()));
        } else {
            // 位移结束：立刻取消姿态
            POSE_UNTIL.remove(packet.entityId());
        }
    }

    /**
     * 该实体当前是否处于滑铲位移阶段（渲染侧唯一的判定入口）
     */
    public static boolean isSlidingPose(int entityId) {
        Long until = POSE_UNTIL.get(entityId);
        if (until == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            POSE_UNTIL.remove(entityId);
            return false;
        }
        if (client.level.getGameTime() >= until) {
            POSE_UNTIL.remove(entityId);
            return false;
        }
        return true;
    }

    /**
     * 渲染侧第一次真正应用姿态时打一条日志，与上面的本地日志配对，用于定位「状态没到」还是「渲染没跑」。
     */
    public static void logPoseAppliedOnce(int entityId) {
        if (loggedPoseApplied) {
            return;
        }
        loggedPoseApplied = true;
        Noellesroles.LOGGER.info("[机动者] 滑铲姿态 mixin 生效：entityId={}", entityId);
    }
}
