package org.agmas.noellesroles.mixin.roles.werewolfkiller;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.GhostPhantomEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.game.roles.killer.werewolfkiller.WerewolfKillerPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 狼刀捅击处理
 *
 * <p>1. 狼刀自身的击杀冷却强制：冷却中的狼刀无法造成击杀。</p>
 * <p>2. 午夜狼嚎期间：举刀落刀都没有声音（静默击杀，不播放刺击音效），
 * 击杀后狼刀冷却缩短至6秒。</p>
 * <p>3. 其余成功击杀在原版刀冷却施加点同步为狼刀施加对应冷却：
 * 黑灯状态18秒，其余同普通刀。</p>
 */
@Mixin(KnifeStabPayload.Receiver.class)
public class WolfKnifeStabMixin {

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void noe$wolfKnifeSilentStab(KnifeStabPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ModItems.WOLF_KNIFE))
            return;

        // 狼刀击杀冷却强制：冷却中无法击杀
        if (!player.isCreative() && player.getCooldowns().isOnCooldown(ModItems.WOLF_KNIFE)) {
            ci.cancel();
            return;
        }

        // 仅午夜狼嚎期间接管处理（无声击杀）
        if (!WerewolfKillerPlayerComponent.isHowling(player))
            return;

        Entity targetEntity = player.serverLevel().getEntity(payload.target());

        // 鬼魅幻影实体（同原版逻辑，但不播放音效）
        if (targetEntity instanceof GhostPhantomEntity phantomEntity) {
            if (phantomEntity.distanceTo(player) > 4.0) {
                ci.cancel();
                return;
            }
            phantomEntity.playerHurt(player, GameConstants.DeathReasons.PHANTOM_DESTROYED);
            player.swing(InteractionHand.MAIN_HAND);
            applyHowlCooldown(player);
            ci.cancel();
            return;
        }

        // 傀儡师假人（同原版逻辑，但不播放音效）
        if (targetEntity instanceof PuppeteerBodyEntity puppeteerBodyEntity) {
            if (puppeteerBodyEntity.distanceTo(player) > 4.0) {
                ci.cancel();
                return;
            }
            puppeteerBodyEntity.playerHurt(player, Noellesroles.id("knife_puppeteer_body"));
            player.swing(InteractionHand.MAIN_HAND);
            applyHowlCooldown(player);
            ci.cancel();
            return;
        }

        // 玩家目标：静默击杀
        if (!(targetEntity instanceof ServerPlayer target))
            return;
        if (target.distanceTo(player) > 3.0) {
            ci.cancel();
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            ci.cancel();
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        final var role = game.getRole(player);
        if (role != null) {
            if (!role.onUseKnifeHit(player, target)) {
                ci.cancel();
                return;
            }
        }
        GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.KNIFE);
        // 不播放任何刺击音效（落刀无声）
        player.swing(InteractionHand.MAIN_HAND);
        applyHowlCooldown(player);
        ci.cancel();
    }

    /**
     * 午夜狼嚎期间击杀后狼刀冷却：6秒
     */
    private static void applyHowlCooldown(ServerPlayer player) {
        if (player.isCreative())
            return;
        player.getCooldowns().addCooldown(ModItems.WOLF_KNIFE, WerewolfKillerPlayerComponent.HOWL_KNIFE_CD);
    }

    /**
     * 成功击杀后（原版给刀施加冷却的位置），为狼刀本身按状态施加冷却：
     * 午夜狼嚎6秒、黑灯18秒、其余与普通刀一致。
     */
    @Inject(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"))
    private void noe$wolfKnifeKillCooldown(KnifeStabPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (player.isCreative())
            return;
        if (!player.getMainHandItem().is(ModItems.WOLF_KNIFE))
            return;
        int cooldown;
        if (WerewolfKillerPlayerComponent.isHowling(player)) {
            cooldown = WerewolfKillerPlayerComponent.HOWL_KNIFE_CD;
        } else if (WerewolfKillerPlayerComponent.isBlackout(player)) {
            cooldown = WerewolfKillerPlayerComponent.BLACKOUT_KNIFE_CD;
        } else {
            cooldown = GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 30 * 20);
        }
        player.getCooldowns().addCooldown(ModItems.WOLF_KNIFE, cooldown);
    }
}
