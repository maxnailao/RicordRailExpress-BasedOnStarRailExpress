package org.agmas.noellesroles.mixin.roles.traitor;

import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 绝境信徒修饰符 - 刀的物品冷却减半
 * 使用 @Inject 在 addCooldown 调用前捕获玩家信息，
 * 配合 @ModifyArg 修改冷却参数。
 */
@Mixin(KnifeStabPayload.Receiver.class)
public class DesperateFaithKnifeCooldownMixin {

    @Unique
    private static final ThreadLocal<Integer> DESPERATE_FAITH_COOLDOWN = ThreadLocal.withInitial(() -> -1);

    /**
     * 在 addCooldown 调用前，检查玩家是否有绝境信徒修饰符，
     * 将减半后的冷却值存入 ThreadLocal。
     * @Inject 在 INJECT 阶段执行，早于 @ModifyArg 的 INJECT_APPLY 阶段。
     */
    @Inject(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V",
            ordinal = 0))
    private void onBeforeKnifeCooldown(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.DESPERATE_FAITH)) {
            // 标记需要减半，实际值由 @ModifyArg 处理
            DESPERATE_FAITH_COOLDOWN.set(1);
        } else {
            DESPERATE_FAITH_COOLDOWN.set(-1);
        }
    }

    /**
     * 修改 addCooldown 的冷却参数（index=1）。
     * 参数签名必须匹配目标方法 addCooldown(Item, int) 的参数 (Item, int)。
     * 从 ThreadLocal 读取是否需要减半的标记。
     */
    @ModifyArg(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V",
            ordinal = 0), index = 1)
    private int onModifyKnifeCooldown(Item item, int cooldown) {
        int flag = DESPERATE_FAITH_COOLDOWN.get();
        DESPERATE_FAITH_COOLDOWN.set(-1); // 清理标记
        if (flag > 0) {
            return cooldown / 2;
        }
        return cooldown;
    }
}
