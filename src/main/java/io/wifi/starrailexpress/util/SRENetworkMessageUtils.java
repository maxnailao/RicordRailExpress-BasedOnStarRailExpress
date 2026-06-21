package io.wifi.starrailexpress.util;

import org.agmas.noellesroles.commands.BroadcastCommand;

import io.wifi.starrailexpress.content.command.NarratorCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public class SRENetworkMessageUtils {
    public static void sendTitle(ServerPlayer target, Component message) {
        target.connection.send(new ClientboundSetTitleTextPacket(message));
    }

    public static void sendSubtitle(ServerPlayer target, Component message) {
        target.connection.send(new ClientboundSetSubtitleTextPacket(message));
    }

    public static void sendTitleTime(ServerPlayer target, int fadeIn, int stay, int fadeOut) {
        target.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
    }

    public static void sendBroadcast(ServerPlayer target, Component message) {
        BroadcastCommand.BroadcastMessage(target, message);
    }

    public static void sendNarrator(ServerPlayer target, Component message, boolean shouldInterrupt) {
        NarratorCommand.sendNarratorToPlayer(target, message, shouldInterrupt);
    }

    public static void sendActionbar(ServerPlayer target, Component message) {
        target.displayClientMessage(message, true);
    }
}
