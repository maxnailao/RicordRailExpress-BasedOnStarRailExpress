package org.agmas.noellesroles.game.roles.innocence.child;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;

public class ChildSkillRegistry {
    //这里添加音频
    private static final SoundEvent[] SOUNDS = new SoundEvent[] {
            NRSounds.CHILD_LAUGH,
            NRSounds.FART,
            SoundEvents.SNOWBALL_THROW,
            SoundEvents.GENERIC_EXPLODE.value(),
            NRSounds.INFECTED_COUGH,
            NRSounds.FEI,
            NRSounds.SQUEAKY_TOY,
            NRSounds.A
    };

    /** 尝试摘下戴在自己身上的玩具手铐，成功则收进背包。真手铐摘不下来。 */
    private static boolean tryPickUpToyHandcuffs(ServerPlayer player) {
        if (!HandCuffsItem.isWearingToyHandcuffs(player)) {
            return false;
        }
        HandCuffsItem.takeToyHandcuffsFrom(player, player);
        player.displayClientMessage(
                Component.literal("你把玩具手铐摘下来收进了物品栏").withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    public static void register() {
        // 熊孩子统一技能：[0] 怪叫（默认槽位） / [1] 按技能键取下手铐。
        // 怪叫处理器里同样先尝试摘铐：无论 V 键当前选中哪个技能，被铐住按 G 都能把玩具手铐摘下来。
        RoleSkill.register(ModRoles.CHILD,
                RoleSkill.skill(SRE.id("child_noise"), "skill.noellesroles.child.noise", context -> {
                    ServerPlayer player = context.player();
                    ChildPlayerComponent comp = ChildPlayerComponent.KEY.get(player);
                    if (comp == null) {
                        return false;
                    }
                    // 戴着玩具手铐时：优先摘铐，不播放音效、不消耗冷却
                    if (tryPickUpToyHandcuffs(player)) {
                        return true;
                    }
                    // Shift + 技能键：切换音效（CLIENT 未定义 shifted 变体，蹲下状态在此按服务端按键状态判定）
                    if (player.isCrouching()) {
                        comp.childSoundIdx = (comp.childSoundIdx + 1) % SOUNDS.length;
                        comp.sync();
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.child.sound_switched", comp.childSoundIdx + 1),
                                true
                        );
                        return true;
                    }
                    if (!context.skillReady()) {
                        return false;
                    }
                    SoundEvent sound = SOUNDS[Math.floorMod(comp.childSoundIdx, SOUNDS.length)];
                    player.serverLevel().playSound(
                            null,
                            player.getX(), player.getY(), player.getZ(),
                            sound,
                            SoundSource.PLAYERS,
                            1.0f,
                            1.0f
                    );
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.child.ability_used"),
                            true
                    );
                    return true;
                }).cooldownSeconds(10).showOnHud(true).announceToSelf(false).build(),

                RoleSkill.skill(SRE.id("child_pickup_cuffs"), "skill.noellesroles.child.pickup_cuffs", context -> {
                    ServerPlayer player = context.player();
                    if (tryPickUpToyHandcuffs(player)) {
                        return true;
                    }
                    player.displayClientMessage(
                            Component.literal("身上没有可以取的玩具手铐").withStyle(ChatFormatting.RED), true);
                    // 返回 false：不消耗冷却充能；被铐时按 G 仍会走默认槽位"怪叫"里的摘铐兜底
                    return false;
                }).showOnHud(true).announceToSelf(false).build());
    }
}