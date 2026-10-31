package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.data.RoleData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.roles.innocence.jidongzhe.JidongzheRoleData;
import org.agmas.noellesroles.packet.JidongzheSlideC2SPacket;
import org.agmas.noellesroles.packet.JidongzheSlidePoseS2CPacket;

import java.util.HashMap;
import java.util.Map;

/**
 * 机动者的客户端侧：滑铲输入上报 + 跪滑姿态状态与插值。
 *
 * <p>所有客户端逻辑都留在本类，RoleData 侧不引用任何客户端类，避免专用服务端加载到客户端符号。</p>
 */
public final class JidongzheClient {

    /** 姿态淡入 / 淡出的刻数：没有插值的话，姿势会瞬间摆到位再瞬间弹回，非常生硬 */
    private static final float BLEND_IN_TICKS = 2.0F;
    private static final float BLEND_OUT_TICKS = 3.0F;
    /** 结束包丢失时的兜底上限 */
    private static final long SAFETY_MAX_TICKS = 40L;

    private record SlideVisual(long startTick, int durationTicks, long forcedEndTick) {
    }

    private static final Map<Integer, SlideVisual> SLIDING = new HashMap<>();

    private static boolean wasSneakDown;
    private static boolean wasJumpDown;

    private JidongzheClient() {
    }

    /**
     * 注册姿态包接收器与输入检测（在 NoellesrolesClient.onInitializeClient 中调用）
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(JidongzheSlidePoseS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> applyPose(payload, context.client())));
        ClientTickEvents.END_CLIENT_TICK.register(JidongzheClient::tick);
    }

    /**
     * 蹲键 / 跳键的上升沿只能在客户端可靠判定，这里仅上报意图，位移仍由服务端结算。
     */
    private static void tick(Minecraft client) {
        if (client.player == null || client.screen != null) {
            return;
        }
        // 本地玩家持有机动者职业数据才检测输入（RoleData 仅同步给本人）
        boolean isJidongzhe = RoleData.getNullable(JidongzheRoleData.class, client.player) != null;
        boolean sneakDown = isJidongzhe && client.options.keyShift.isDown();
        boolean jumpDown = isJidongzhe && client.options.keyJump.isDown();

        if (sneakDown && !wasSneakDown && client.player.isSprinting()) {
            ClientPlayNetworking.send(new JidongzheSlideC2SPacket(false));
        } else if (jumpDown && !wasJumpDown && isSliding(client.player.getId())) {
            ClientPlayNetworking.send(new JidongzheSlideC2SPacket(true));
        }
        wasSneakDown = sneakDown;
        wasJumpDown = jumpDown;
    }

    private static void applyPose(JidongzheSlidePoseS2CPacket packet, Minecraft client) {
        long now = clientTime(client);
        if (packet.start()) {
            SLIDING.put(packet.entityId(),
                    new SlideVisual(now, Math.max(1, packet.durationTicks()), 0L));
        } else {
            SlideVisual visual = SLIDING.get(packet.entityId());
            if (visual == null) {
                return;
            }
            // 撞墙 / 滑铲跳会提前结束：记下真实结束点，让姿态从当前角度淡出而不是瞬间复位
            SLIDING.put(packet.entityId(),
                    new SlideVisual(visual.startTick(), visual.durationTicks(), now));
        }
    }

    /**
     * 该实体当前是否仍在滑铲位移中（用于滑铲跳的输入窗口，不含姿态淡出段）
     */
    public static boolean isSliding(int entityId) {
        SlideVisual visual = SLIDING.get(entityId);
        if (visual == null) {
            return false;
        }
        if (clientTime(Minecraft.getInstance()) >= endTick(visual)) {
            SLIDING.remove(entityId);
            return false;
        }
        return true;
    }

    /**
     * 本次滑铲的姿态混合系数：0 表示不应用姿态，1 表示完全进入跪滑。
     *
     * @param entityId 实体 id
     * @param partial  帧内插值（{@code ageInTicks - entity.tickCount}）
     */
    public static float slideBlend(int entityId, float partial) {
        SlideVisual visual = SLIDING.get(entityId);
        if (visual == null) {
            return 0.0F;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            SLIDING.remove(entityId);
            return 0.0F;
        }
        double now = client.level.getGameTime() + partial;
        long end = endTick(visual);
        if (now >= end + BLEND_OUT_TICKS || now > visual.startTick() + SAFETY_MAX_TICKS) {
            SLIDING.remove(entityId);
            return 0.0F;
        }
        double fadeIn = (now - visual.startTick()) / BLEND_IN_TICKS;
        double fadeOut = (end + BLEND_OUT_TICKS - now) / BLEND_OUT_TICKS;
        return (float) Math.max(0.0D, Math.min(1.0D, Math.min(fadeIn, fadeOut)));
    }

    private static long endTick(SlideVisual visual) {
        long natural = visual.startTick() + visual.durationTicks();
        return visual.forcedEndTick() > 0L ? Math.min(natural, visual.forcedEndTick()) : natural;
    }

    private static long clientTime(Minecraft client) {
        return client != null && client.level != null ? client.level.getGameTime() : 0L;
    }
}
