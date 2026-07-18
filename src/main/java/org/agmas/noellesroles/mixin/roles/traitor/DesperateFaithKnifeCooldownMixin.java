package org.agmas.noellesroles.mixin.roles.traitor;

import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 绝境信徒修饰符 - 刀的物品冷却减半（只能触发一次）
 * 使用 @Redirect 拦截 addCooldown 调用，将冷却时间减半。
 */
@Mixin(KnifeStabPayload.Receiver.class)
public class DesperateFaithKnifeCooldownMixin {

    @Redirect(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V",
            ordinal = 0))
    private int onKnifeCooldown(ItemCooldowns cooldowns, Item item, int cooldown,
            KnifeStabPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        // 检查玩家是否有绝境信徒修饰符
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.DESPERATE_FAITH)) {
            return cooldown / 2;
        }
        return cooldown;
    }
}
