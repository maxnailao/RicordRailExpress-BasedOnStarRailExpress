package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

/**
 * 扮演者登车报幕伪装 Mixin
 *
 * 扮演者本人不能知晓自己的真实身份：开局报幕（职业名/目标/介绍）需要显示为其扮演的平民职业。
 * 因此在发送 AnnounceWelcomePayload 前，将扮演者的职业 ID 替换为其扮演的职业 ID。
 *
 * 目标: SREMurderGameMode.assignRole - 修改发送给扮演者的 AnnounceWelcomePayload 中的职业
 */
@Mixin(io.wifi.starrailexpress.game.modes.SREMurderGameMode.class)
public class BanyanzheWelcomeAnnounceMixin {

    @ModifyArgs(
        method = "assignRole",
        at = @At(
            value = "INVOKE",
            target = "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking;send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"
        )
    )
    private static void banyanzhe$disguiseWelcomePayload(Args args, ServerLevel serverWorld,
            SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        Object target = args.get(0);
        Object payload = args.get(1);
        if (!(target instanceof ServerPlayer player) || !(payload instanceof AnnounceWelcomePayload announce))
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.BANYANZHE))
            return;
        var comp = ModComponents.BANYANZHE.maybeGet(player).orElse(null);
        if (comp == null)
            return;
        // 确保已选定扮演职业（幂等），并以扮演职业的身份报幕
        comp.pickDisguiseIfAbsent();
        if (comp.disguiseRoleId == null)
            return;
        args.set(1, new AnnounceWelcomePayload(comp.disguiseRoleId.toString(), announce.killers(),
                announce.targets()));
    }
}
