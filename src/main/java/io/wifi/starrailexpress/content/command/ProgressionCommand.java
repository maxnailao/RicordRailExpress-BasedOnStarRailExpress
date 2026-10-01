package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;

public final class ProgressionCommand {
    private ProgressionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:pass")
                .then(Commands.literal("activate")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(context -> activate(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "type")))))
                .then(Commands.literal("selfselect")
                        .then(Commands.argument("role", StringArgumentType.greedyString())
                                .executes(context -> selfSelect(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "role"))))));
    }

    private static int activate(ServerPlayer player, String rawType) {
        ProgressionState.FactionCardType type = ProgressionState.FactionCardType.fromString(rawType);
        if (!ProgressionDataManager.activateFactionCard(player, type)) {
            player.displayClientMessage(Component.translatable("message.sre.pass.faction.assign_failed"), true);
            return 0;
        }
        return 1;
    }

    private static int selfSelect(ServerPlayer player, String rawRole) {
        ResourceLocation roleId = ResourceLocation.tryParse(rawRole);
        SRERole role = roleId == null ? null : TMMRoles.getRole(roleId);
        if (role == null || !TMMRoles.isSelfSelectableRole(role)
                || !BackpackManager.useSelfSelectCard(player, role)) {
            player.displayClientMessage(Component.translatable("message.sre.pass.selfselect_failed"), true);
            return 0;
        }
        player.displayClientMessage(
                Component.literal("已使用自选职业卡，本局职业锁定为：")
                        .append(Harpymodloader.getRoleName(role)),
                true);
        return 1;
    }
}
