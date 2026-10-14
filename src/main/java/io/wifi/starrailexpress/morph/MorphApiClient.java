package io.wifi.starrailexpress.morph;

import java.util.UUID;

import org.agmas.noellesroles.component.DeathPenaltyComponent;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.morph.ClientMorphCache;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import io.wifi.starrailexpress.event.OnResolveDisplayedSkinOwner;
import io.wifi.starrailexpress.event.client.OnGameStartedClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class MorphApiClient {

    /**
     * 客户端：解析「当前显示的皮肤属于谁」。
     * 先走 {@link OnResolveDisplayedSkinOwner}（统一变形覆盖层），未命中则返回本人。
     */
    public static UUID resolveDisplayedOwnerUuid(AbstractClientPlayer player) {
        if (player == null) {
            return null;
        }
        UUID resolved = OnResolveDisplayedSkinOwner.EVENT.invoker().resolveDisplayedOwner(player);
        return resolved != null ? resolved : player.getUUID();
    }

    /**
     * 客户端：当前是否为「无真实玩家可复制」的贴图变形。
     */
    public static boolean isTextureMorph(AbstractClientPlayer player) {
        return player != null && ClientMorphCache.get(player.getUUID()).isTexture();
    }

    /**
     * 客户端：应显示的玩家名（跟随显示皮肤拥有者）。
     * <p>
     * 例外：本地观察者处于旁观（无死亡惩罚）/ 创造时，名字不跟随伪装，一律显示本人真实名字
     * （皮肤与附属物仍按伪装渲染，见 {@link #resolveDisplayedOwnerUuid}）。
     */
    public static Component getDisplayedName(Player target) {
        if (target == null) {
            return Component.literal("");
        }
        if (!(target instanceof AbstractClientPlayer clientPlayer)) {
            return target.getName();
        }
        if (isTextureMorph(clientPlayer)) {
            return target.getName();
        }
        UUID owner = shouldRevealRealName() ? target.getUUID() : resolveDisplayedOwnerUuid(clientPlayer);
        if (owner == null || owner.equals(target.getUUID())) {
            return target.getName();
        }
        PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(owner);
        Minecraft client = Minecraft.getInstance();
        if (info == null && client.getConnection() != null) {
            info = client.getConnection().getPlayerInfo(owner);
        }
        if (info != null && info.getProfile() != null) {
            return Component.literal(info.getProfile().getName());
        }
        return target.getName();
    }

    /**
     * 本地观察者是否不参与变幻、名牌一律显示本人真实名字。
     * <p>
     * 即旁观（无死亡惩罚）与创造：判定与 {@code SREClientEvents} 中「旁观不参与变幻」的名牌事件一致。
     * 死亡惩罚下名牌本来就不渲染，故排除。
     */
    public static boolean shouldRevealRealName() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }
        return !DeathPenaltyComponent.hasPenalty(client.player) && !SREClient.isPlayerAliveAndInSurvival();
    }

    /**
     * 注册客户端默认解析器（皮肤覆盖层 + 显示拥有者）。
     */
    public static void registerClient() {
        OnResolveDisplayedSkinOwner.EVENT.register(player -> {
            if (io.wifi.starrailexpress.SRE.isLobby || io.wifi.starrailexpress.client.SREClient.isInLobby) {
                return null;
            }
            MorphAppearance overlay = ClientMorphCache.get(player.getUUID());
            if (overlay.isPlayer() && overlay.targetPlayer() != null
                    && !overlay.targetPlayer().equals(player.getUUID())) {
                return overlay.targetPlayer();
            }
            return null;
        });
        OnGettingPlayerSkin.EVENT.register(player -> {
            MorphAppearance overlay = ClientMorphCache.get(player.getUUID());
            if (overlay.isTexture() && overlay.texture() != null) {
                return PlayerSkinResult.playerSkin(overlay.texture(),
                        overlay.slim() ? Model.SLIM : Model.WIDE);
            }
            if (overlay.isPlayer() && overlay.targetPlayer() != null
                    && !overlay.targetPlayer().equals(player.getUUID())) {
                PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(overlay.targetPlayer());
                Minecraft client = Minecraft.getInstance();
                if (info == null && client.getConnection() != null) {
                    info = client.getConnection().getPlayerInfo(overlay.targetPlayer());
                }
                if (info != null && info.getSkin() != null) {
                    return PlayerSkinResult.playerSkin(info.getSkin());
                }
            }
            return PlayerSkinResult.SKIP;
        });
        // 开局 / 结束时清空本地缓存，与服务端的生命周期清理保持一致。
        OnGameStartedClient.EVENT.register(ClientMorphCache::clear);
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> ClientMorphCache.clear());
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> ClientMorphCache.clear());
    }
}
