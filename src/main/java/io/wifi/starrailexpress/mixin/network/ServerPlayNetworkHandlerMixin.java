package io.wifi.starrailexpress.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Item;
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

            Item psychoItem = TMMItems.BAT;
            SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
            if (role != null) {
                psychoItem = role.getPsychoItem();
            }
            if (!this.player.getInventory().getItem(packet.getSlot()).is(psychoItem)) {
                // 幽灵幻影疯魔(type=2)：允许切换到空手
                if (!(component.type == 2 && this.player.getInventory().getItem(packet.getSlot()).isEmpty()))
                    return;
            }
        }
        original.call(packet);
    }
}