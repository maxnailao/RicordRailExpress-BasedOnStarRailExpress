package org.agmas.noellesroles.mixin.roles.phantom;

import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.game.roles.killer.phantom.PhantomFrenzyPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幽灵幻影模式 - 刀的物品冷却修改
 * 幽灵在幻影疯魔期间使用刀冷却为3秒（60ticks）。
 * 使用 @Inject 在 addCooldown 调用前捕获玩家信息，
 * 配合 @ModifyArg 将冷却设为60ticks。
 */
@Mixin(KnifeStabPayload.Receiver.class)
public class PhantomFrenzyKnifeCooldownMixin {

    /** 幽灵幻影期间刀的冷却：3秒 = 60 ticks */
    @Unique
    private static final int PHANTOM_FRENZY_KNIFE_CD = 3 * 20;

    @Unique
    private static final ThreadLocal<Boolean> PHANTOM_FRENZY_NO_CD = ThreadLocal.withInitial(() -> false);

    /**
     * 在 addCooldown 调用前，检查玩家是否处于幽灵幻影模式。
     */
    @Inject(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V",
            ordinal = 0))
    private void onBeforeKnifeCooldownPhantom(KnifeStabPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (PhantomFrenzyPlayerComponent.isInFrenzy(player)) {
            PHANTOM_FRENZY_NO_CD.set(true);
        } else {
            PHANTOM_FRENZY_NO_CD.set(false);
        }
    }

    /**
     * 修改 addCooldown 的冷却参数（index=1）。
     * 幽灵幻影期间冷却设为3秒。
     */
    @ModifyArg(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V",
            ordinal = 0), index = 1)
    private int onModifyKnifeCooldownPhantom(Item item, int cooldown) {
        boolean noCd = PHANTOM_FRENZY_NO_CD.get();
        PHANTOM_FRENZY_NO_CD.set(false); // 清理标记
        if (noCd) {
            return PHANTOM_FRENZY_KNIFE_CD;
        }
        return cooldown;
    }
}
