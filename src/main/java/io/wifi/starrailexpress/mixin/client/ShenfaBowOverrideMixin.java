package io.wifi.starrailexpress.mixin.client;

import io.wifi.starrailexpress.client.model.GeneralModel;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 弓皮肤拉弓覆盖旁路。
 * 原版弓模型自带 use_duration → bow_pulling 覆盖，会把拉弓时的模型换成原版 pulling 弓身、绕过皮肤三档变体。
 * 这里仅在"该 stack 已解析出弓皮肤"时返回 GeneralModel 本身（改由 GeneralModel.emitItemQuads 选择皮肤 pulling_0/1/2）；
 * 未装备弓皮肤时不拦截，保留原版 pulling 拉弓动画，避免误伤默认玩家。
 */
@Mixin(ItemOverrides.class)
public class ShenfaBowOverrideMixin {

    @Inject(method = "resolve(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;I)"
            + "Lnet/minecraft/client/resources/model/BakedModel;", at = @At("HEAD"), cancellable = true)
    private void shenfa$resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
            @Nullable LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
        if (!(model instanceof GeneralModel gm)) {
            return;
        }
        String type = gm.getItemType();
        if (!ItemSkinManager.SkinTypes.BOW.equals(type)
                && !ItemSkinManager.SkinTypes.CROSSBOW.equals(type)) {
            return;
        }
        String skinName = stack.get(SREDataComponentTypes.SKIN);
        if (skinName == null && entity instanceof Player player) {
            skinName = ItemSkinManager.getEquippedSkin(player, stack);
        }
        // 解析出真实皮肤 → 交回 GeneralModel.emitItemQuads 走皮肤 pulling 变体；否则保留原版覆盖。
        if (skinName != null && !"default".equals(skinName)) {
            cir.setReturnValue(model);
        }
    }
}