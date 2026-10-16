package org.agmas.noellesroles.game.roles.neutral.priest;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 神父：平民阵营，由持有「神的使命」的钟表匠在场上只剩一名平民时转变而来。
 * 随平民获胜；完成咏诵并让时间停止后，好人获胜。
 *
 * <p>构造参数沿用本仓库 {@code NormalRole} 的「isInnocent / canUseKiller」形式：
 * 神父属于好人方（isInnocent = true）、不使用杀手能力（canUseKiller = false），
 * 对应上游 {@code RoleTeam.NEUTRALS} + {@code isInnocent=true} 的阵营语义。</p>
 */
public class PriestRole extends CustomWinnerRole {

    public PriestRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        if (winStatus == WinStatus.PASSENGERS || winStatus == WinStatus.TIME) {
            return true;
        }
        return original;
    }

    public static boolean openChant(ServerPlayer player) {
        return PriestHeavenManager.openChantScreen(player);
    }

    public static void submitLyric(ServerPlayer player, String text) {
        PriestHeavenManager.submitLyric(player, text);
    }
}
