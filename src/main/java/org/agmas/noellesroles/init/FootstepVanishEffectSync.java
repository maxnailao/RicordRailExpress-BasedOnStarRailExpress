package org.agmas.noellesroles.init;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把"玩家身上的脚步消失效果"同步给所有其它客户端。
 *
 * <p>原版只会把玩家自身的 MobEffect 同步给他自己（{@code ServerPlayer.onEffectAdded}
 * 只发给本人的连接），其它玩家的效果不会下发。而 {@link FootstepVanishMixin 脚步/疾跑粒子拦截}
 * 会在<b>每个客户端</b>对<b>对应玩家</b>调用 {@code player.hasEffect(...)} 来决定是否
 * 静音/隐藏粒子。若不额外同步，其它客户端永远查不到<b>别人</b>的脚步消失效果，导致"别人仍能听到脚步"。</p>
 *
 * <p>这里在服务端每隔若干 tick 主动把 {@link ModEffects#FOOTSTEP_VANISH}、
 * {@link ModEffects#JINGMOQIANXING} 和 {@link ModEffects#JINGBU}
 * 以隐藏粒子/图标的形式广播给其他所有人。</p>
 */
public final class FootstepVanishEffectSync {

    private FootstepVanishEffectSync() {
    }

    /** 刷新间隔（tick）。小于客户端效果时长，保证不会在两次刷新之间过期。 */
    private static final int REFRESH_INTERVAL = 10;

    /** 下发到客户端的效果时长，需明显大于刷新间隔以避免闪断。 */
    private static final int SYNC_DURATION = 40;

    /** 记录上一次已广播脚步消失效果的玩家，便于在效果消失时下发移除包。 */
    private static final Map<UUID, Boolean> HAD_FOOTSTEP_VANISH = new HashMap<>();
    private static final Map<UUID, Boolean> HAD_JINGMOQIANXING = new HashMap<>();
    private static final Map<UUID, Boolean> HAD_JINGBU = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FootstepVanishEffectSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.overworld().getGameTime() % REFRESH_INTERVAL != 0) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!HAD_FOOTSTEP_VANISH.isEmpty()) HAD_FOOTSTEP_VANISH.clear();
            if (!HAD_JINGMOQIANXING.isEmpty()) HAD_JINGMOQIANXING.clear();
            if (!HAD_JINGBU.isEmpty()) HAD_JINGBU.clear();
            return;
        }

        for (ServerPlayer player : players) {
            syncEffect(player, players, ModEffects.FOOTSTEP_VANISH, HAD_FOOTSTEP_VANISH);
            syncEffect(player, players, ModEffects.JINGMOQIANXING, HAD_JINGMOQIANXING);
            syncEffect(player, players, ModEffects.JINGBU, HAD_JINGBU);
        }
    }

    private static void syncEffect(ServerPlayer player, List<ServerPlayer> allPlayers,
                                    net.minecraft.core.Holder<MobEffect> effect,
                                    Map<UUID, Boolean> hadMap) {
        MobEffectInstance instance = player.getEffect(effect);
        boolean had = hadMap.getOrDefault(player.getUUID(), false);

        if (instance != null) {
            hadMap.put(player.getUUID(), true);
            MobEffectInstance hidden = new MobEffectInstance(
                    effect, SYNC_DURATION, instance.getAmplifier(), false, false, false);
            ClientboundUpdateMobEffectPacket update =
                    new ClientboundUpdateMobEffectPacket(player.getId(), hidden, false);
            broadcastExcept(allPlayers, player, update);
        } else if (had) {
            hadMap.remove(player.getUUID());
            ClientboundRemoveMobEffectPacket remove =
                    new ClientboundRemoveMobEffectPacket(player.getId(), effect);
            broadcastExcept(allPlayers, player, remove);
        }
    }

    private static void broadcastExcept(List<ServerPlayer> players, ServerPlayer except,
                                        net.minecraft.network.protocol.Packet<?> packet) {
        for (ServerPlayer receiver : players) {
            if (receiver == except) continue;
            receiver.connection.send(packet);
        }
    }
}
