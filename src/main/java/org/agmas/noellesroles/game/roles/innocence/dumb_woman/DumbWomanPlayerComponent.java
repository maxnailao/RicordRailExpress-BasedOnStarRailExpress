package org.agmas.noellesroles.game.roles.innocence.dumb_woman;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 哑女角色组件
 * - 存活时：3秒禁言（voice_silence + chat_ban），持续刷新
 * - 死亡后（旁观模式）：不施加禁言，允许死后说话
 * - 永久夜视效果
 * - 强制赋予夜猫子修饰符
 */
public class DumbWomanPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 禁言效果持续时间：3秒 = 60 tick */
    private static final int SILENCE_DURATION_TICKS = 60;

    public static final ComponentKey<DumbWomanPlayerComponent> KEY = ModComponents.DUMB_WOMAN;

    private final Player player;

    public DumbWomanPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        // 角色分配时应用效果
        applyEffects();
        sync();
    }

    @Override
    public void clear() {
        removeEffects();
        sync();
    }

    /**
     * 应用哑女的被动效果
     * 仅在存活（冒险模式）时施加禁言效果，死亡后不施加
     */
    private void applyEffects() {
        if (!(player instanceof ServerPlayer sp)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRole(player, ModRoles.DUMB_WOMAN)) return;

        // 仅在存活时施加禁言效果（3秒持续时间，由 serverTick 持续刷新）
        if (!sp.isSpectator()) {
            sp.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE,
                    SILENCE_DURATION_TICKS, 0, false, false, false));
            sp.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN,
                    SILENCE_DURATION_TICKS, 0, false, false, false));
        }

        // 永久夜视
        sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                Integer.MAX_VALUE, 0, false, false, false));

        // 强制赋予夜猫子修饰符
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        if (!modifiers.isModifier(player.getUUID(), TraitorAndModifiers.NIGHT_OWL)) {
            modifiers.addModifier(player.getUUID(), TraitorAndModifiers.NIGHT_OWL);
        }
    }

    /**
     * 移除哑女的所有被动效果
     */
    private void removeEffects() {
        if (!(player instanceof ServerPlayer sp)) return;

        sp.removeEffect(ModEffects.VOICE_SILENCE);
        sp.removeEffect(ModEffects.CHAT_BAN);
        sp.removeEffect(MobEffects.NIGHT_VISION);

        // 移除夜猫子修饰符
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.NIGHT_OWL)) {
            modifiers.removeModifier(player.getUUID(), TraitorAndModifiers.NIGHT_OWL);
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || gameWorld.gameStatus != SREGameWorldComponent.GameStatus.ACTIVE) return;
        if (!gameWorld.isRole(player, ModRoles.DUMB_WOMAN)) return;

        // 确保夜视效果始终存在（每40 tick检查一次，避免每tick开销）
        if (player.level().getGameTime() % 40 == 0) {
            if (!sp.hasEffect(MobEffects.NIGHT_VISION)) {
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                        Integer.MAX_VALUE, 0, false, false, false));
            }

            if (sp.isSpectator()) {
                // 死亡后（旁观模式）：移除禁言效果，允许死后说话
                sp.removeEffect(ModEffects.VOICE_SILENCE);
                sp.removeEffect(ModEffects.CHAT_BAN);
            } else {
                // 存活时（冒险模式）：以3秒持续时间刷新禁言效果
                sp.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE,
                        SILENCE_DURATION_TICKS, 0, false, false, false));
                sp.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN,
                        SILENCE_DURATION_TICKS, 0, false, false, false));
            }

            // 强制保持夜猫子修饰符：开局 assignModifiers 会清空全部修饰符再随机分配，
            // init() 中加上的夜猫子会被清掉，这里每40 tick补回，确保哑女局内始终持有
            if (!sp.isSpectator()) {
                WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
                if (!modifiers.isModifier(player.getUUID(), TraitorAndModifiers.NIGHT_OWL)) {
                    modifiers.addModifier(player.getUUID(), TraitorAndModifiers.NIGHT_OWL);
                }
            }
        }
    }

    public void sync() {
        ModComponents.DUMB_WOMAN.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
