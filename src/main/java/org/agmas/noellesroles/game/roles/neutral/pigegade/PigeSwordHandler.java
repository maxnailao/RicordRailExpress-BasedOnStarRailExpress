package org.agmas.noellesroles.game.roles.neutral.pigegade;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 皮革嘎的的铁剑攻击处理
 * - 仅对坠木角色玩家生效
 * - 命中坠木3次后击杀
 * - 对其他玩家无伤害效果
 */
public class PigeSwordHandler {

    public static void register() {
        // 允许皮革嘎的持铁剑时攻击
        AllowPlayerPunching.EVENT.register(player -> {
            var gw = SREGameWorldComponent.KEY.get(player.level());
            return gw.isRole(player, ModRoles.PIGE)
                    && player.getMainHandItem().is(ModItems.PIGE_SWORD);
        });

        // 处理铁剑命中逻辑
        AttackEntityCallback.EVENT.register(PigeSwordHandler::onSwordHit);
    }

    private static InteractionResult onSwordHit(
            Player attacker,
            Level level,
            InteractionHand hand,
            Entity entity,
            EntityHitResult hitResult) {

        if (SRE.isLobby) return InteractionResult.PASS;
        if (!(entity instanceof Player victim)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(attacker)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(victim)) return InteractionResult.PASS;
        if (attacker.getUUID().equals(victim.getUUID())) return InteractionResult.PASS;

        var gw = SREGameWorldComponent.KEY.get(level);
        if (!gw.isRole(attacker, ModRoles.PIGE)) return InteractionResult.PASS;
        if (!attacker.getMainHandItem().is(ModItems.PIGE_SWORD)) return InteractionResult.PASS;

        // 仅对坠木生效
        if (!gw.isRole(victim, ModRoles.ZHUIMU)) {
            if (attacker instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.pige.sword_invalid_target")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL; // 取消对其他玩家的伤害
        }

        if (!level.isClientSide) {
            PigegadePlayerComponent comp = PigegadePlayerComponent.KEY.get(attacker);
            comp.onSwordHitZhuimu();

            if (attacker instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.pige.sword_hit",
                                comp.swordHitCount, 3)
                                .withStyle(ChatFormatting.GREEN), true);
            }

            // 3击击杀坠木
            if (comp.swordHitCount >= 3) {
                GameUtils.killPlayer(victim, true, attacker,
                        org.agmas.noellesroles.Noellesroles.id("pige_sword_kill"));
            }
        }

        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
