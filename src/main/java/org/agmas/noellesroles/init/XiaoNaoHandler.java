package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.OnTeammateKilledTeammate;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.TeamKillViolationHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.innocence.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.blood_feudist.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Set;

/**
 * 小脑惩罚
 */
public class XiaoNaoHandler {

    /** 小脑（误杀）死亡原因白名单 */
    private static final Set<String> XIAO_NAO_REASONS = Set.of(
            "revolver_shot",
            "general_attack",
            "sniper_rifle",
            "nunchuck_hit",
            "bat_hit",
            "gun_shot",
            "hoan_meirin_attack",
            "arrow",
            "trident",
            "knife_stab",
            "stalker_knife",
            "knife",
            "fell_out_of_train",
            "poison",
            "throwing_knife_hit",
            "throwing_knife",
            "bowen",
            "baton_kill",
            "fire_axe",
            "ninja_knife",
            "ninja_shuriken",
            "short_shotgun",
            "grenade",
            "zero_one_five_shot",
            "incinerator_pushed",
            "manhole_suffocation",
            "stalactite_impale",
            "flamethrower_burned",
            "boulder_crush",
            "desert_eagle_shot");

    /**
     * 判断给定的死亡原因是否属于小脑（误杀）原因。
     *
     * @param deathReason 死亡原因 ResourceLocation
     * @return 如果是小脑原因返回 true
     */
    public static boolean isXiaoNaoReason(ResourceLocation deathReason) {
        return deathReason != null && XIAO_NAO_REASONS.contains(deathReason.getPath());
    }

    public static void registerEvent() {
        TeamKillViolationHandler.registerEvent();
        OnTeammateKilledTeammate.EVENT.register((victim, killer, isInnocent, deathReason) -> {
            if (GameUtils.isPlayerAliveAndSurvival(killer)) {

                if (isInnocent) {
                    SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
                    if (gameWorldComponent.isRole(victim, TMMRoles.DISCOVERY_CIVILIAN)) {
                        // 跳过游客惩罚
                        return;
                    }
                    // 跳过家族成员（教父、教徒、侍卫）的惩罚——好人不应该因为打家族成员而受小脑惩罚
                    if (gameWorldComponent.getRole(victim) != null
                            && gameWorldComponent.getRole(victim).isMafiaTeam()) {
                        return;
                    }
                    // 检查是否是疯狂模式下的魔术师，如果是则不算误杀
                    if (gameWorldComponent.isRole(victim, ModRoles.MAGICIAN)) {
                        var psychoComponent = SREPlayerPsychoComponent.KEY.get(victim);
                        if (psychoComponent != null && psychoComponent.getPsychoTicks() > 0) {
                            // 魔术师处于疯狂模式，不算误杀
                            return;
                        }
                    }
                    // 检查是否是复仇者击杀复仇目标的凶手，如果是则不算误杀
                    if (gameWorldComponent.isRole(killer, ModRoles.AVENGER)) {
                        AvengerPlayerComponent avengerComp = ModComponents.AVENGER.get(killer);
                        if (avengerComp != null && avengerComp.killerUuid != null
                                && avengerComp.killerUuid.equals(victim.getUUID())) {
                            // 复仇者击杀的是杀死复仇目标的凶手，不算误杀
                            return;
                        }
                    }

                    if (gameWorldComponent.isRole(victim, ModRoles.VOODOO)) {
                        return;
                    }

                    //检查是否是黑警击杀，黑警击杀不算误杀
                    if (gameWorldComponent.isRole(killer, ModRoles.CORRUPT_COP)) {
                        return;
                    }
                    // 小脑(误杀)惩罚写这里
                    TeamKillViolationHandler.handle(victim, killer, isInnocent, deathReason);
                    if (NoellesRolesConfig.HANDLER.instance().accidentalKillPunishment) {
                        if (isXiaoNaoReason(deathReason)) {
                            GameUtils.killPlayer(killer, true, null, Noellesroles.id("shot_innocent"));
                            TeamKillViolationHandler.handle(victim, killer, isInnocent, deathReason);
                            // 仇杀客事件：误杀发生时强化仇杀客
                            for (ServerPlayer player : victim.serverLevel().players()) {
                                if (gameWorldComponent.isRole(player, ModRoles.BLOOD_FEUDIST)) {
                                    BloodFeudistPlayerComponent bfComp = ModComponents.BLOOD_FEUDIST.get(player);
                                    if (bfComp != null) {
                                        bfComp.onAccidentalKill();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

}
