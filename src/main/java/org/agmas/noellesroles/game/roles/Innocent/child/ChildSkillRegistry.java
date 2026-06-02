package org.agmas.noellesroles.game.roles.Innocent.child;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.api.RoleSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
            NRSounds.SQUEAKY_TOY
    };

    public static void register() {
        RoleSkill.register(ModRoles.CHILD, context -> {
            ServerPlayer player = context.player();
            ChildPlayerComponent comp = ChildPlayerComponent.KEY.get(player);
            if (comp == null) {
                return;
            }

            // Shift + 技能键：切换音效
            if (player.isCrouching()) {
                comp.childSoundIdx = (comp.childSoundIdx + 1) % SOUNDS.length;
                comp.sync();
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.child.sound_switched", comp.childSoundIdx + 1),
                        true
                );
                return;
            }

            // 正常按技能键：播放当前音效
            if (!comp.canUseAbility()) {
                return;
            }

            comp.setCooldown(GameConstants.getInTicks(0, 10)); // 10秒冷却

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
        });
    }
}