package org.agmas.noellesroles.mixin.roles.kidnapper;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.kidnapper.KidnappedCCA;
import org.agmas.noellesroles.game.roles.neutral.kidnapper.KidnapperPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 绑匪审判阶段：任何击杀（枪杀/刀杀）都不致死，
 * 改为「受害者被传送回自己的房间并捆绑」。
 * force=true 的强制击杀（游戏重置/管理员清理）不做拦截。
 */
@Mixin(GameUtils.class)
public class KidnapperKillMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V", at = @At("HEAD"), cancellable = true)
    private static void onKidnapperKill(Player victim, boolean spawnBody, Player killer,
                                        ResourceLocation identifier, boolean force, CallbackInfo ci) {
        if (killer == null || victim == null || force)
            return;
        if (!(killer instanceof ServerPlayer killerSp) || !(victim instanceof ServerPlayer victimSp))
            return;
        if (!KidnapperPlayerComponent.isJudgmentPhase(killer))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(victimSp))
            return;

        KidnapperPlayerComponent comp = KidnapperPlayerComponent.KEY.get(killer);
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(victim.level());

        // 计入绑架人数
        if (!comp.kidnapped.contains(victim.getUUID()))
            comp.kidnapped.add(victim.getUUID());

        // 捆绑状态（禁移动/禁言/禁道具，每 tick 续期）
        boolean isKillerTarget = game.getAllKillerPlayers().contains(victim.getUUID());
        KidnappedCCA.get(victim).setKidnapped(killer.getUUID(), isKillerTarget);
        // 被击杀后传回自己房间并捆绑：属「原地被绑」，渲染双手背后低头姿态
        KidnappedCCA.get(victim).markBoundIdle();

        // 传送回受害者自己的房间
        GameUtils.teleportBackToRoom(victimSp);

        victim.level().playSound(null, victim.blockPosition(),
                SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
        killerSp.displayClientMessage(Component.translatable(
                        "message.noellesroles.kidnapper.shot_captured", victim.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED), false);
        comp.sync();
        ci.cancel();
    }
}