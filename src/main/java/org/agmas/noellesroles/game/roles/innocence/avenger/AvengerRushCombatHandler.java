package org.agmas.noellesroles.game.roles.innocence.avenger;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
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
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.Nullable;

/**
 * 复仇者“复仇心切”攻击拦截：
 * 技能生效的15秒内，复仇者只能攻击凶手，攻击其他玩家会被取消。
 * 复仇成功（rushActive 关闭）后不再拦截，恢复正常击杀。
 * - 近战左键：AttackEntityCallback 拦截
 * - 枪械/刀具等 killPlayer 路径：AllowPlayerDeathWithKiller 拦截死亡
 */
public class AvengerRushCombatHandler {

    public static void register() {
        AttackEntityCallback.EVENT.register(AvengerRushCombatHandler::onEntityAttacked);
        // 拦截枪械等远程击杀路径：复仇心切期间不允许杀死凶手以外的玩家
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null)
                return true;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(killer.level());
            if (!gameWorld.isRole(killer, ModRoles.AVENGER))
                return true;
            AvengerPlayerComponent comp = ModComponents.AVENGER.get(killer);
            if (!comp.rushActive)
                return true;
            // 凶手本人放行
            if (comp.killerUuid != null && comp.killerUuid.equals(victim.getUUID()))
                return true;
            if (killer instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.avenger.rush.block_kill")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        });
    }

    private static InteractionResult onEntityAttacked(Player attacker, Level level, InteractionHand hand,
            Entity entity, @Nullable EntityHitResult hitResult) {
        if (level.isClientSide || !(attacker instanceof ServerPlayer serverAttacker)) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof Player target)) {
            return InteractionResult.PASS;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRole(serverAttacker, ModRoles.AVENGER)) {
            return InteractionResult.PASS;
        }
        AvengerPlayerComponent comp = ModComponents.AVENGER.get(serverAttacker);
        if (!comp.rushActive) {
            return InteractionResult.PASS;
        }
        // 凶手本人放行
        if (comp.killerUuid != null && comp.killerUuid.equals(target.getUUID())) {
            return InteractionResult.PASS;
        }
        serverAttacker.displayClientMessage(
                Component.translatable("message.noellesroles.avenger.rush.block_kill")
                        .withStyle(ChatFormatting.RED),
                true);
        return InteractionResult.FAIL;
    }
}
