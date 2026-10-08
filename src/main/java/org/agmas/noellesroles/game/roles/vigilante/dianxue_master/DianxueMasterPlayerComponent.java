package org.agmas.noellesroles.game.roles.vigilante.dianxue_master;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.DianxueMasterSyncS2CPacket;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Random;
import java.util.UUID;

public class DianxueMasterPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<DianxueMasterPlayerComponent> KEY = ModComponents.DIANXUE_MASTER;

    public static final int POINT_COUNT = 5;
    private static final int GATE_TICKS = 60;
    private static final int STEP_TICKS = 80;
    private static final double PUSH_STRENGTH = 0.5D;
    private static final double PUSH_UPWARD = 0.15D;
    private static final double MAX_RANGE_SQR = 64.0D * 64.0D;
    private static final int RESTRICT_TICKS = 40;

    private static final int PHASE_GATE = 0;
    private static final int PHASE_SEQUENCE = 1;
    private static final int ACT_OPEN = 0;
    private static final int ACT_UPDATE = 1;
    private static final int ACT_CLOSE = 2;

    private final Player player;
    private final Random random = new Random();

    private boolean active = false;
    private UUID targetId = null;
    private int phase = PHASE_GATE;
    private int activeIndex = 0;
    private int stepRemainingTicks = 0;
    private int stepMaxTicks = GATE_TICKS;

    public DianxueMasterPlayerComponent(Player player) {
        this.player = player;
    }

    @Override public Player getPlayer() { return player; }
    @Override public boolean shouldSyncWith(ServerPlayer p) { return p == player; }
    public void sync() { KEY.sync(player); }

    @Override public void init() { resetState(); }
    @Override public void clear() { resetState(); }
    private void resetState() { active = false; targetId = null; phase = PHASE_GATE; activeIndex = 0; stepRemainingTicks = 0; }

    public boolean startPressing(ServerPlayer target) {
        if (active) return false;
        if (!(player instanceof ServerPlayer master)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(master) || !GameUtils.isPlayerAliveAndSurvival(target)) return false;

        Vec3 dir = flatten(target.position().subtract(master.position()));
        if (dir.lengthSqr() < 1.0e-4) dir = flatten(master.getLookAngle());
        dir = dir.normalize();

        target.push(dir.x * PUSH_STRENGTH, PUSH_UPWARD, dir.z * PUSH_STRENGTH);
        target.hurtMarked = true;
        target.connection.send(new ClientboundSetEntityMotionPacket(target.getId(), target.getDeltaMovement()));

        applyRestrictions(target);
        target.setLastHurtByMob(master);
        target.setLastHurtByPlayer(master);
        target.displayClientMessage(Component.translatable("message.noellesroles.dianxue_master.victim")
                .withStyle(ChatFormatting.DARK_RED), true);

        this.active = true;
        this.targetId = target.getUUID();
        this.phase = PHASE_GATE;
        this.activeIndex = random.nextInt(POINT_COUNT);
        this.stepRemainingTicks = GATE_TICKS;
        this.stepMaxTicks = GATE_TICKS;
        send(master, ACT_OPEN);
        return true;
    }

    @Override
    public void serverTick() {
        if (!active) return;
        if (!(player instanceof ServerPlayer master)) { stop(); return; }
        if (!GameUtils.isPlayerAliveAndSurvival(master)) { closeAndStop(master); return; }
        if (!(master.level().getPlayerByUUID(targetId) instanceof ServerPlayer target)
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            closeAndStop(master);
            return;
        }
        if (master.distanceToSqr(target) > MAX_RANGE_SQR) { closeAndStop(master); return; }
        applyRestrictions(target);
        stepRemainingTicks--;
        send(master, ACT_UPDATE);
        if (stepRemainingTicks <= 0) onStepTimeout(master, target);
    }

    private void onStepTimeout(ServerPlayer master, ServerPlayer target) {
        if (phase == PHASE_GATE) {
            master.displayClientMessage(Component.translatable("message.noellesroles.dianxue_master.gate_miss")
                    .withStyle(ChatFormatting.GRAY), true);
            closeAndStop(master);
            return;
        }
        advanceSequence(master);
    }

    private void advanceSequence(ServerPlayer master) {
        activeIndex++;
        if (activeIndex >= POINT_COUNT) {
            closeAndStop(master);
            return;
        }
        stepRemainingTicks = STEP_TICKS;
        stepMaxTicks = STEP_TICKS;
        send(master, ACT_UPDATE);
    }

    public void handleClientClick(ServerPlayer master, int index) {
        if (!active || index != activeIndex) return;
        if (!(master.level().getPlayerByUUID(targetId) instanceof ServerPlayer target)
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            closeAndStop(master);
            return;
        }

        if (phase == PHASE_GATE) {
            phase = PHASE_SEQUENCE;
            activeIndex = 0;
            stepRemainingTicks = STEP_TICKS;
            stepMaxTicks = STEP_TICKS;
            send(master, ACT_UPDATE);
            return;
        }

        applyEffect(target, master, activeIndex);
        if (activeIndex == POINT_COUNT - 1) {
            closeAndStop(master);
            return;
        }
        advanceSequence(master);
    }

    private void applyEffect(ServerPlayer target, ServerPlayer master, int index) {
        switch (index) {
            case 0 -> SREPlayerMoodComponent.KEY.get(target).addMood(0.25f);
            case 1 -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, GameConstants.getInTicks(0, 10), 0, false, true, true));
            case 2 -> target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, GameConstants.getInTicks(0, 10), 0, false, true, true));
            case 3 -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GameConstants.getInTicks(0, 10), 0, false, true, true));
            case 4 -> {
                target.setLastHurtByMob(master);
                target.setLastHurtByPlayer(master);
                GameUtils.killPlayer(target, true, master, Noellesroles.id("dianxue"));
            }
        }
    }

    private void send(ServerPlayer master, int action) {
        String name = (targetId != null && master.level().getPlayerByUUID(targetId) != null)
                ? master.level().getPlayerByUUID(targetId).getGameProfile().getName() : "";
        ServerPlayNetworking.send(master, new DianxueMasterSyncS2CPacket(
                action, phase, activeIndex, targetId, name,
                Math.max(0, stepRemainingTicks), stepMaxTicks));
    }

    private void closeAndStop(ServerPlayer master) {
        removeRestrictions(master);
        send(master, ACT_CLOSE);
        stop();
    }

    private void stop() { resetState(); }

    /** 点穴期间限制目标：锁移动/技能/道具使用/背包，仅保留视角转动（由 MobEffectKeyMixin 屏蔽按键）。 */
    private void applyRestrictions(ServerPlayer target) {
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, RESTRICT_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, RESTRICT_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, RESTRICT_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, RESTRICT_TICKS, 0, false, false, true));
    }

    /** 点穴结束/中断时移除上述限制效果。 */
    private void removeRestrictions(ServerPlayer master) {
        if (targetId == null) return;
        if (master.level().getPlayerByUUID(targetId) instanceof ServerPlayer target) {
            target.removeEffect(ModEffects.MOVE_BANED);
            target.removeEffect(ModEffects.SKILL_BANED);
            target.removeEffect(ModEffects.USED_BANED);
            target.removeEffect(ModEffects.INVENTORY_BANED);
        }
    }

    private static Vec3 flatten(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    @Override public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider p) {}
}