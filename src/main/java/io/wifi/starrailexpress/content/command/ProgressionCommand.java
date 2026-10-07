package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
import io.wifi.starrailexpress.progression.SelfSelectClaimService;
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

        // 占位规则统一在 SelfSelectClaimService 里（先到先得 / 每局每种职业只出现一位 / 失败退还）
        SelfSelectClaimService.Result result = SelfSelectClaimService.claim(player, role);
        switch (result) {
            case OK -> {
                player.displayClientMessage(
                        Component.literal("已使用自选职业卡，本局职业锁定为：")
                                .append(Harpymodloader.getRoleName(role)),
                        true);
                return 1;
            }
            case SLOT_FULL -> {
                // 不生效：广播提示，并且**没有扣卡**（等于返还自选卡）
                SelfSelectClaimService.broadcastClaimed(player.getServer(), player, role);
                player.displayClientMessage(
                        Component.translatable("message.sre.pass.selfselect_claimed_self",
                                Harpymodloader.getRoleName(role),
                                Harpymodloader.getRoleCapacity(role)),
                        true);
                return 0;
            }
            case SAME_ROLE -> {
                player.displayClientMessage(
                        Component.literal("你本局已经选过这个职业了：")
                                .append(Harpymodloader.getRoleName(role)),
                        true);
                return 0;
            }
            case NO_CARD -> {
                player.displayClientMessage(Component.translatable("message.sre.pass.selfselect_failed"), true);
                return 0;
            }
            default -> {
                return 0;
            }
        }
    }
}
