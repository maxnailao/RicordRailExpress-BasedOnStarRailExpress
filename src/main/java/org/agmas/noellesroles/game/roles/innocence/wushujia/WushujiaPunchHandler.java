package org.agmas.noellesroles.game.roles.innocence.wushujia;

import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 武术家击打处理器
 *
 * 被动：可以左键击打其他玩家（空手时），与红美铃和熊孩子一样
 * 心流模式下：通过Mixin注入LivingEntity.hurt()的RETURN点，
 * 仅在伤害实际造成后追踪连击并施加递增debuff效果：
 * - 1-2次：缓慢I 3s
 * - 3-4次：缓慢II 3s + 反胃 3s
 * - 5+次：缓慢IV 3s + 反胃 3s + 黑暗 3s + 按键禁用(USED_BANED) 3s
 * 2秒未受新打击则重置连击计数
 */
public class WushujiaPunchHandler {

    /**
     * 记录每个攻击者（UUID）当前的连击状态
     */
    public static final Map<UUID, PunchRecord> PUNCH_RECORDS = new HashMap<>();

    /**
     * 连击重置时间窗口（tick），2秒 = 40 tick
     */
    private static final long RESET_WINDOW_TICKS = 40L;

    /**
     * 注册武术家的被动击打权限（允许空手攻击其他玩家）
     */
    public static void register() {
        AllowPlayerPunching.EVENT.register((player) -> {
            if (player.hasEffect(ModEffects.SAFE_TIME)) {
                return false;
            }
            if (SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.WUSHUJIA)) {
                if (player.getMainHandItem().isEmpty()) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 当武术家心流模式下的攻击确认造成伤害后调用（由WushujiaHurtMixin调用）
     * 追踪连击计数并根据连击次数施加debuff效果
     *
     * @param attacker 攻击者（武术家玩家）
     * @param victim   受害者
     */
    public static void onFlowHitConfirmed(Player attacker, Player victim) {
        var comp = WushujiaPlayerComponent.KEY.get(attacker);
        if (!comp.isInFlow()) {
            return;
        }

        UUID attackerUUID = attacker.getUUID();
        UUID victimUUID = victim.getUUID();
        long now = attacker.level().getGameTime();

        PunchRecord record = PUNCH_RECORDS.computeIfAbsent(attackerUUID, k -> new PunchRecord());

        // 如果目标换人，重置计数
        if (!victimUUID.equals(record.targetUUID)) {
            record.targetUUID = victimUUID;
            record.count = 0;
        }

        // 如果距离上次打击超过2秒，重置计数
        if (record.lastHitTime > 0 && (now - record.lastHitTime) > RESET_WINDOW_TICKS) {
            record.count = 0;
        }

        record.count++;
        record.lastHitTime = now;

        // 更新组件的连击数（用于HUD显示）
        comp.comboCount = record.count;
        comp.sync();

        // 根据连击次数施加debuff效果
        applyComboEffects(victim, record.count);

        // 发送提示消息
        sendActionBar(attacker, buildMessage(record.count));
    }

    /**
     * 根据连击次数给受害者施加debuff效果
     */
    private static void applyComboEffects(Player victim, int count) {
        int duration = 60; // 3秒 = 60 tick

        if (count >= 5) {
            // 5+次：缓慢IV + 反胃 + 黑暗 + 按键禁用(USED_BANED)
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, true, true));
            victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0, false, true, true));
            victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, true, true));
            victim.addEffect(new MobEffectInstance(ModEffects.USED_BANED, duration, 0, false, true, true));
        } else if (count >= 3) {
            // 3-4次：缓慢II + 反胃
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1, false, true, true));
            victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0, false, true, true));
        } else {
            // 1-2次：缓慢I
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0, false, true, true));
        }
    }

    /**
     * 构建打击提示消息
     */
    private static Component buildMessage(int count) {
        if (count >= 5) {
            return Component.translatable("message.wushujia.combo.5plus", count)
                    .withStyle(ChatFormatting.RED);
        } else if (count >= 3) {
            return Component.translatable("message.wushujia.combo.3_4", count)
                    .withStyle(ChatFormatting.YELLOW);
        } else {
            return Component.translatable("message.wushujia.combo.1_2", count)
                    .withStyle(ChatFormatting.GREEN);
        }
    }

    private static void sendActionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    /**
     * 存储单个攻击者的连击状态
     */
    private static class PunchRecord {
        UUID targetUUID = null;
        int count = 0;
        long lastHitTime = 0;
    }
}
