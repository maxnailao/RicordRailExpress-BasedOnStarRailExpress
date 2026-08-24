package org.agmas.noellesroles.mixin;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.game.roles.killer.banyanzhe.BanyanzhePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 扮演者聊天回忆兜底 Mixin
 *
 * 专用服务器上 Fabric 的 ALLOW_CHAT_MESSAGE 事件可能因聊天签名/转发链路差异而不触发
 * （单人游戏中则正常），因此直接在原版聊天包入口处触发扮演者的回忆判定。
 * 与 Fabric 事件监听共用 BanyanzhePlayerComponent.handleChatRecall，重复触发时由
 * recalled 状态保证幂等。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class BanyanzheChatRecallMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V", at = @At("HEAD"))
    private void banyanzhe$onChatPacket(ServerboundChatPacket packet, CallbackInfo ci) {
        BanyanzhePlayerComponent.handleChatRecall(this.player, packet.message());
    }
}
