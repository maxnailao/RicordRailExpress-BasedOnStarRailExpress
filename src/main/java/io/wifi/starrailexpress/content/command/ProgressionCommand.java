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
import org.agmas.harpymodloader.SREDisableManager;

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
        // 本局被禁用的职业不可用自选卡（客户端已置灰并拦截，这里再挡一次防手打命令绕过）
        if (role != null && SREDisableManager.isRoleDisabled(role)) {
            player.displayClientMessage(
                    Component.translatable("message.sre.pass.selfselect_role_disabled",
                            Harpymodloader.getRoleName(role)),
                    true);
            return 0;
        }
        if (role == null || !TMMRoles.isSelfSelectableRole(role)) {
            player.displayClientMessage(Component.translatable("message.sre.pass.selfselect_failed"), true);
            return 0;
        }
        // 本局该职业已被其他玩家自选：保留（退回）自选卡，只给第一位
        if (Harpymodloader.isRoleClaimedByOthers(role, player.getUUID())) {
            player.displayClientMessage(
                    Component.literal("该职业本局已被其他玩家自选，已为你保留自选卡：")
                            .append(Harpymodloader.getRoleName(role)),
                    true);
            return 0;
        }
        if (!BackpackManager.useSelfSelectCard(player, role)) {
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
