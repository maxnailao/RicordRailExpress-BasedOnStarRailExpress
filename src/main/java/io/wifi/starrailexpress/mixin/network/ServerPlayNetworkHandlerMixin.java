package io.wifi.starrailexpress.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayer player;

    @WrapMethod(method = "handleSetCarriedItem")
    private void tmm$invalid(ServerboundSetCarriedItemPacket packet, @NotNull Operation<Void> original) {
        if (SRE.isLobby) {
            original.call(packet);
            return;
        }
        SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(this.player);
        if (component.getPsychoTicks() > 0) {
            // 疯魔武器不在背包里时不锁栏位（例如 Dream：巨幕面具从不入包）。
            // 否则所有选栏包都会被丢弃，服务端主手与客户端脱同步，
            // 表现就是「狂暴期间打不了人」——上游专门为此加了 hasPsychoSupportedWeapon 判定。
            if (!component.isPsychoSupportedWeapon(this.player.getInventory().getItem(packet.getSlot()))
                    && component.hasPsychoSupportedWeapon()) {
                // 幽灵幻影疯魔(type=2)：允许切换到空手
                if (!(component.type == 2 && this.player.getInventory().getItem(packet.getSlot()).isEmpty()))
                    return;
            }
        }
        original.call(packet);
    }
}