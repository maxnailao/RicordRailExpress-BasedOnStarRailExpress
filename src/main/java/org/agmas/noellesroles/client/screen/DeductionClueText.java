package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.game.forensic.ForensicCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveFlavor;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveClue;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.Locale;

/**
 * 客户端：把一条 {@link DetectiveClue} 渲染成推理之书页面上的一行可读文本。
 *
 * <p>书页用「字段名：值」的固定格式（{@code screen.noellesroles.great_detective.clue.*}），
 * 值本身统一交给 {@link DetectiveFlavor#clueValue(DetectiveClue)} 做本地化；
 * 聊天栏里的随机化叙述文案才用 flavor 键。
 */
public final class DeductionClueText {

    private DeductionClueText() {
    }

    /** 渲染书页上的一行线索。 */
    public static Component render(DetectiveClue clue) {
        // WEAPON 是枚举名，书页上要翻成「凶器类型」；其余类型名恰好等于键名。
        String suffix = clue.type() == DetectiveClue.ClueType.WEAPON
                ? "weapon"
                : clue.type().name().toLowerCase(Locale.ROOT);
        return Component.translatable("screen.noellesroles.great_detective.clue." + suffix,
                DetectiveFlavor.clueValue(clue).copy().withStyle(ChatFormatting.WHITE));
    }

    /** 把线索值里的凶器大类解析回枚举（供外部需要时使用）。 */
    public static ForensicCategory parseCategory(String value) {
        try {
            return ForensicCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ForensicCategory.UNKNOWN;
        }
    }

    /** 把线索值里的职业 ID 解析成显示名。 */
    public static Component roleName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Component name = rl == null ? null : RoleUtils.getRoleName(rl);
        return name != null ? name : Component.literal(id);
    }

    /** 把线索值里的修饰符 ID 解析成显示名。 */
    public static Component modifierName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Component name = rl == null ? null : RoleUtils.getModifierName(rl);
        return name != null ? name : Component.literal(id);
    }
}
