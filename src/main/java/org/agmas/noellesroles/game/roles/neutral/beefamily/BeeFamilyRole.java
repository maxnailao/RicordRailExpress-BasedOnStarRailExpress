package org.agmas.noellesroles.game.roles.neutral.beefamily;

import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

/**
 * 蜜蜂家族职业基类：蜂后 / 马蜂 / 工蜂 共用。
 *
 * <p>
 * 家族成员之间互不击杀（见 {@link BeeFamilyManager#registerEvents()}），拥有独立的蜜蜂文字频道，
 * 全员存活到最后即独立胜利；家族全灭时所有由其它职业转来的成员会被还原回原职业。
 */
public class BeeFamilyRole extends EggRole {

    public BeeFamilyRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.addFlag("bee_family");
        // 蜜蜂家族不会被毒针 / 病毒感染（内层同名开关）。
        this.setCanBePoisoned(false);
        // 必须在 TMMRoles.registerRole() 之前挂上组件，否则不会被收进 TMMRoles.COMPONENT_KEYS，
        // 开局 / 结算时的 clear() 就不会作用于本组件，上一局的频道与继承者会残留。
        this.setComponentKey(BeeFamilyComponent.KEY);
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer player) {
        // RoleMethodDispatcher.onInit 是先调 role.onInit() 再调 component.init()，
        // 所以这里先自行复位一次，避免把上一局的频道回显给玩家。
        BeeFamilyComponent data = BeeFamilyComponent.getNullable(player);
        if (data != null) {
            data.init();
        }
        // 工蜂是一次性士兵：存活时间写在能力组件的 duration 上，归零即由 serverTick 处决。
        if (RoleUtils.isPlayerTheJob(player, BounsRoles.BEE_WORKER)) {
            getAbilityComponent(player).setDuration(BeeFamilyManager.BEE_WORKER_DEATH_TIMEOUT_TICKS);
        }
        player.displayClientMessage(getChannelText(player), true);
    }

    /**
     * 蜜蜂家族成员死亡时调用。
     *
     * <p>
     * 两件事：① 死者是蜂后且已标记继承者 → 继承者复活接任蜂后并继承金币；
     * ② 无论谁死，都排一次「家族是否全灭」的检查（延后到下一 tick，
     * 因为死亡事件里死者的存活状态可能尚未落地）。
     */
    public static void onDeath(Player victim, @Nullable Player killer, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer player)) {
            return;
        }
        if (RoleUtils.getPlayerRole(victim) == BounsRoles.BEE_QUEEN) {
            BeeFamilyComponent data = BeeFamilyComponent.getNullable(player);
            if (data != null && data.markTarget != null) {
                var reviveTarget = player.serverLevel().getPlayerByUUID(data.markTarget);
                if (reviveTarget instanceof ServerPlayer successor
                        && !GameUtils.isPlayerAliveAndSurvival(successor)) {
                    final SRERole beforeRole = RoleUtils.getPlayerRole(successor);
                    RoleUtils.changeRole(successor, BounsRoles.BEE_QUEEN);
                    SREItemUtils.clearItem(successor);

                    // 新蜂后继承老蜂后的金币，且保底 100，避免接手后什么都买不起
                    final var victimShopCca = SREPlayerShopComponent.KEY.get(victim);
                    final var reviveShopCca = SREPlayerShopComponent.KEY.get(successor);
                    reviveShopCca.balance += victimShopCca.balance;
                    if (reviveShopCca.balance < 100) {
                        reviveShopCca.balance = 100;
                    }
                    reviveShopCca.sync();
                    victimShopCca.setBalance(0);

                    GameUtils.revivePlayerToItsRoom(successor);
                    RoleUtils.sendWelcomeAnnouncement(successor);
                    // 必须写在 changeRole 之后：changeRole 会触发 ModdedRoleAssigned，
                    // 进而调用组件的 init() 把 beforeRole 清空。
                    if (!(beforeRole instanceof BeeFamilyRole)) {
                        BeeFamilyComponent successorData = BeeFamilyComponent.getNullable(successor);
                        if (successorData != null) {
                            successorData.beforeRole = beforeRole;
                        }
                    }
                }
            }
        }
        // 检查蜜蜂家族是否全体死亡。如果是恢复死者原本职业。
        BeeFamilyManager.pendingCheckFailure();
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        if (RoleUtils.isPlayerTheJob(player, BounsRoles.BEE_WORKER)) {
            if (getAbilityComponent(player).duration <= 0) {
                GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.BEE_WORKER_TIMEOUT);
            }
        }
    }

    /** 当前频道提示文本，用于 actionbar 与 HUD。 */
    public static Component getChannelText(Player player) {
        BeeFamilyComponent data = BeeFamilyComponent.getNullable(player);
        if (data == null) {
            return Component.empty();
        }
        return Component
                .translatable("hud.noellesroles.bee_family.channel",
                        data.beeChannel
                                ? Component.translatable("hud.noellesroles.bee_family.channel.bee")
                                        .withStyle(ChatFormatting.YELLOW)
                                : Component.translatable("hud.noellesroles.bee_family.channel.normal")
                                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GOLD);
    }
}
