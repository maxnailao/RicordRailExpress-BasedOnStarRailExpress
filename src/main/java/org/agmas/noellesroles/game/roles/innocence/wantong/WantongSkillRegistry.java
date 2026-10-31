package org.agmas.noellesroles.game.roles.innocence.wantong;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

/**
 * 顽童技能 - 平民阵营
 * 技能键：收回自己给他人戴上的玩具手铐（仅限自己铐的、仅限玩具手铐），直接回到自己背包。
 */
public class WantongSkillRegistry {

    /** 收回距离（格） */
    private static final double RETRIEVE_RANGE_SQR = 5.0D * 5.0D;

    public static void register() {
        RoleSkill.register(ModRoles.WANTONG_XIAOPIHAI, RoleSkill.skill(
                SRE.id("wantong_retrieve_cuffs"),
                "skill.noellesroles.wantong.retrieve_cuffs",
                context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
                    if (role == null || !role.identifier().equals(ModRoles.WANTONG_XIAOPIHAI_ID))
                        return false;
                    UUID targetId = context.target();
                    if (targetId == null
                            || !(player.level().getPlayerByUUID(targetId) instanceof ServerPlayer target)) {
                        player.displayClientMessage(
                                Component.literal("需要瞄准一个玩家才能收回手铐").withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    if (!GameUtils.isPlayerAliveAndSurvival(target)
                            || player.distanceToSqr(target) > RETRIEVE_RANGE_SQR
                            || !player.hasLineOfSight(target)) {
                        return false;
                    }
                    if (!HandCuffsItem.isWearingToyHandcuffs(target)) {
                        player.displayClientMessage(
                                Component.literal("只能收回玩具手铐").withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    if (!player.getUUID().equals(HandCuffsItem.getCuffer(target))) {
                        player.displayClientMessage(
                                Component.literal("只能收回自己给他人戴上的玩具手铐").withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    HandCuffsItem.takeToyHandcuffsFrom(target, player);
                    player.displayClientMessage(
                            Component.literal("从")
                                    .append(target.getDisplayName())
                                    .append("手上收回了玩具手铐").withStyle(ChatFormatting.GREEN), true);
                    target.displayClientMessage(
                            Component.literal("手里的玩具手铐被")
                                    .append(player.getDisplayName())
                                    .append("收了回去").withStyle(ChatFormatting.GOLD), true);
                    return true;
                }).showOnHud(true).announceToSelf(false).build());
    }
}