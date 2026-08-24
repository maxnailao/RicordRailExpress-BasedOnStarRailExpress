package org.agmas.noellesroles.game.roles.innocence.huanling;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 幻灵 —— 平民阵营附身角色。
 *
 * <p>
 * 被动：心情持续锁定 100%、隐身、无法说话（文字+语音）。
 * 机制：
 * <ul>
 * <li>开局以隐身+静步的冒险模式寻找附身目标（默认 50s），对玩家按 G 附身；</li>
 * <li>附身后切换为旁观者，视角锁定宿主；游戏开局满 3 分钟时转换为宿主职业并现身（宿主存活）；</li>
 * <li>附身到杀手/中立阵营玩家：立即死亡，不留尸体，死因「附身失败」；</li>
 * <li>宿主死亡：幻灵转回冒险模式（隐身+静步+无敌+无碰撞），10s 内须重新附身，否则死亡（留尸体）；</li>
 * <li>Shift+G 主动脱离：转回隐身+静步的冒险模式，8s 内须重新附身，否则死亡（留尸体）。</li>
 * </ul>
 *
 * <p>
 * 隐私要点：组件仅同步给幻灵本人，宿主与其他玩家对附身无感知。
 */
public final class HuanlingPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<HuanlingPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("wcwobeiguifushenle_huanling"), HuanlingPlayerComponent.class);

    /** 死因：附身失败。 */
    public static final ResourceLocation DEATH_POSSESSION_FAILED = Noellesroles.id("possession_failed");

    /** 附身距离限制：16 格。 */
    private static final double POSSESS_RANGE_SQR = 16.0 * 16.0;

    private final Player player;

    // ===== 服务端权威状态 =====
    /** 本局是否处于幻灵活动期（开局后 → 现身/真正死亡前）。 */
    private boolean active;
    /** 游戏真正开始时的 gameTime（用于 3 分钟转换点计算）。 */
    private long roundStartTick = -1;
    /** 寻找阶段截止时间（gameTime）；-1 表示不在寻找阶段。 */
    private long searchDeadline = -1;
    /** 寻找阶段是否为宿主死亡后的宽限（额外给无敌+无碰撞；常态寻找仅隐身+静步）。 */
    private boolean searchInAdventure;
    /** 当前附身的宿主 UUID；非空即处于附身中。 */
    public UUID possessTarget;

    // ===== 客户端镜像（仅用于 HUD）=====
    public UUID clientPossessTarget;
    public int clientSearchTicks;
    public int clientTransformTicks;
    public boolean clientAdventureGrace;

    static {
        // 宿主死亡 → 转回冒险模式宽限寻找；幻灵自身死亡 → 清理状态。
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> handleAnyDeath(victim));
        OnPlayerDeath.EVENT.register((victim, reason) -> handleAnyDeath(victim));
    }

    public HuanlingPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 隐私：附身状态只对幻灵本人可见。 */
    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public void sync() {
        KEY.sync(player);
    }

    private boolean stateActive() {
        return active || possessTarget != null || searchDeadline >= 0;
    }

    @Override
    public void init() {
        // 开局分波分配会多次触发 init：已有局内状态时跳过，避免误清。
        if (stateActive()) {
            sync();
            return;
        }
        resetFields();
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            removePhaseEffects(sp);
            if (sp.getCamera() != sp) {
                sp.setCamera(sp);
            }
            TrainVoicePlugin.resetPlayer(sp.getUUID());
        }
        resetFields();
        sync();
    }

    private void resetFields() {
        active = false;
        roundStartTick = -1;
        searchDeadline = -1;
        searchInAdventure = false;
        possessTarget = null;
        clientPossessTarget = null;
        clientSearchTicks = 0;
        clientTransformTicks = 0;
        clientAdventureGrace = false;
    }

    /** 游戏开局满 3 分钟（可配置）的转换时间点。 */
    private long transformDeadline() {
        return roundStartTick + NoellesRolesConfig.HANDLER.instance().huanlingTransformGameSeconds * 20L;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp))
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        // 角色已变更（现身换职业/指令换职业）：清理残留状态。
        if (!game.isRole(sp, ModRoles.HUANYING)) {
            if (stateActive()) {
                removePhaseEffects(sp);
                if (sp.getCamera() != sp) {
                    sp.setCamera(sp);
                }
                resetFields();
                sync();
            }
            return;
        }
        // 被动维持（心情锁定/禁言/隐身），活动期内每 20 tick 刷新。
        if (active && sp.tickCount % 20 == 0) {
            refreshPassives(sp);
        }
        if (!game.isRunning())
            return;
        // 首个运行 tick：记录开局时间，进入旁观寻找阶段（每局仅一次）。
        if (roundStartTick < 0) {
            beginRound(sp);
        }
        // 已真正死亡（附身失败等）：本局不再驱动任何逻辑。
        if (!active)
            return;
        if (possessTarget != null) {
            tickPossessing(sp, game);
        } else if (searchDeadline >= 0) {
            tickSearching(sp);
        }
        if (sp.tickCount % 20 == 0)
            sync();
    }

    /** 开局：以隐身+静步的冒险模式开始寻找附身目标。 */
    private void beginRound(ServerPlayer sp) {
        active = true;
        roundStartTick = sp.level().getGameTime();
        int searchSeconds = NoellesRolesConfig.HANDLER.instance().huanlingInitialSearchSeconds;
        searchDeadline = roundStartTick + searchSeconds * 20L;
        searchInAdventure = false;
        sp.setGameMode(GameType.ADVENTURE);
        sp.setCamera(sp);
        applySearchEffects(sp);
        sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.round_start", searchSeconds)
                .withStyle(ChatFormatting.GRAY), false);
        sync();
    }

    /** 被动刷新：心情锁 100% + 禁言；寻找期（冒险模式）补隐身+静步，宿主死亡宽限另加无敌/无碰撞。 */
    private void refreshPassives(ServerPlayer sp) {
        SREPlayerMoodComponent.KEY.get(sp).setMood(1.0f);
        sp.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, 60, 0, false, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, 60, 0, false, false, false));
        if (possessTarget == null && !sp.isSpectator()) {
            applySearchEffects(sp);
            if (searchInAdventure) {
                sp.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 60, 0, false, false, false));
                sp.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 60, 0, false, false, false));
            }
        }
    }

    /** 寻找期常态效果：隐身 + 静步（短时长，每 20 tick 续期）。 */
    private void applySearchEffects(ServerPlayer sp) {
        sp.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 60, 0, false, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.FOOTSTEP_VANISH, 60, 0, false, false, false));
    }

    /** 附身中：跟随宿主、锁定视角；到 3 分钟整点则转换职业现身。 */
    private void tickPossessing(ServerPlayer sp, SREGameWorldComponent game) {
        Player t = sp.level().getPlayerByUUID(possessTarget);
        if (!(t instanceof ServerPlayer host) || !GameUtils.isPlayerAliveAndSurvival(host)) {
            // 宿主离线/消失（死亡事件未覆盖）：兜底进入冒险宽限寻找。
            beginAdventureGrace(sp, sp.position(), "message.noellesroles.huanling.host_lost");
            return;
        }
        if (sp.isSpectator()) {
            sp.teleportTo(sp.serverLevel(), host.getX(), host.getY(), host.getZ(), host.getYRot(), host.getXRot());
            sp.setCamera(host);
        }
        if (sp.level().getGameTime() >= transformDeadline()) {
            transformNow(sp, host, game);
        }
    }

    /** 寻找中：驱动倒计时，超时则死亡（留尸体，死因附身失败）。 */
    private void tickSearching(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        if (now >= searchDeadline) {
            searchDeadline = -1;
            active = false;
            sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.search_timeout")
                    .withStyle(ChatFormatting.RED), false);
            GameUtils.forceKillPlayer(sp, true, null, DEATH_POSSESSION_FAILED);
            sync();
            return;
        }
        if (sp.tickCount % 20 == 0) {
            int seconds = (int) ((searchDeadline - now + 19) / 20);
            sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.search_time", seconds)
                    .withStyle(searchInAdventure ? ChatFormatting.RED : ChatFormatting.GRAY), true);
        }
    }

    /**
     * G 键：附身准星目标。
     * 附身杀手/中立玩家时幻灵立即死亡（不留尸体）。
     */
    public boolean possess(ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp))
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        if (!game.isRunning() || !game.isRole(sp, ModRoles.HUANYING) || !active)
            return false;
        if (possessTarget != null || searchDeadline < 0)
            return false;
        if (target == null || target == sp || !GameUtils.isPlayerAliveAndSurvival(target)
                || game.isRole(target, ModRoles.HUANYING)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.no_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (sp.distanceToSqr(target) > POSSESS_RANGE_SQR) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.too_far")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // 附身杀手/中立阵营玩家：附身失败，立即死亡且不留尸体。
        SRERole targetRole = game.getRole(target);
        if (game.isKillerTeam(target) || (targetRole != null && targetRole.isNeutrals())) {
            active = false;
            searchDeadline = -1;
            sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.possess_killer")
                    .withStyle(ChatFormatting.RED), false);
            GameUtils.forceKillPlayer(sp, false, null, DEATH_POSSESSION_FAILED);
            sync();
            return true;
        }
        possessTarget = target.getUUID();
        searchDeadline = -1;
        searchInAdventure = false;
        // 附身切换为旁观者：先清掉寻找期效果，再锁定宿主视角并加入旁观语音频道。
        removeSearchEffects(sp);
        sp.setGameMode(GameType.SPECTATOR);
        TrainVoicePlugin.addPlayer(sp.getUUID());
        sp.setCamera(target);
        sp.teleportTo(sp.serverLevel(), target.getX(), target.getY(), target.getZ(),
                target.getYRot(), target.getXRot());
        sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.possess_start", target.getName())
                .withStyle(ChatFormatting.GRAY), false);
        // 若附身成功时游戏已过 3 分钟整点：立即转换职业现身。
        if (sp.level().getGameTime() >= transformDeadline()) {
            transformNow(sp, target, game);
        }
        sync();
        return true;
    }

    /** Shift+G：主动脱离宿主，转回隐身+静步的冒险模式，进入 8s 寻找。 */
    public boolean detach() {
        if (!(player instanceof ServerPlayer sp))
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        if (!game.isRunning() || !game.isRole(sp, ModRoles.HUANYING) || !active || possessTarget == null)
            return false;
        int graceSeconds = NoellesRolesConfig.HANDLER.instance().huanlingDetachGraceSeconds;
        possessTarget = null;
        searchInAdventure = false;
        searchDeadline = sp.level().getGameTime() + graceSeconds * 20L;
        sp.setCamera(sp);
        sp.setGameMode(GameType.ADVENTURE);
        TrainVoicePlugin.resetPlayer(sp.getUUID());
        applySearchEffects(sp);
        sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.detached", graceSeconds)
                .withStyle(ChatFormatting.GRAY), false);
        sync();
        return true;
    }

    /** 宿主死亡：转回冒险模式（隐身+无敌+无碰撞），进入宽限寻找。 */
    private void onHostDeath(Player host) {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (possessTarget == null || !possessTarget.equals(host.getUUID()))
            return;
        Vec3 pos = host.position();
        possessTarget = null;
        beginAdventureGrace(sp, pos, "message.noellesroles.huanling.host_died");
    }

    /** 进入冒险模式宽限寻找：相机复位、传送、语音组还原、施加宽限效果。 */
    private void beginAdventureGrace(ServerPlayer sp, Vec3 pos, String msgKey) {
        int graceSeconds = NoellesRolesConfig.HANDLER.instance().huanlingRepossessGraceSeconds;
        searchInAdventure = true;
        searchDeadline = sp.level().getGameTime() + graceSeconds * 20L;
        sp.setCamera(sp);
        sp.teleportTo(sp.serverLevel(), pos.x, pos.y, pos.z, sp.getYRot(), 0f);
        sp.setGameMode(GameType.ADVENTURE);
        TrainVoicePlugin.resetPlayer(sp.getUUID());
        applyGraceEffects(sp);
        sp.displayClientMessage(Component.translatable(msgKey, graceSeconds)
                .withStyle(ChatFormatting.RED), false);
        sync();
    }

    private void applyGraceEffects(ServerPlayer sp) {
        applySearchEffects(sp);
        sp.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 60, 0, false, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 60, 0, false, false, false));
    }

    /** 幻灵自身真正死亡：清理状态（死亡流程本身已将其设为旁观；保留 roundStartTick 防止重新开局）。 */
    private void onSelfDeath() {
        if (player instanceof ServerPlayer sp && sp.getCamera() != sp) {
            sp.setCamera(sp);
        }
        removePhaseEffectsOnly();
        active = false;
        searchDeadline = -1;
        searchInAdventure = false;
        possessTarget = null;
        sync();
    }

    /** 到 3 分钟整点：转换为宿主职业并在宿主位置现身（宿主存活）。 */
    private void transformNow(ServerPlayer sp, ServerPlayer host, SREGameWorldComponent game) {
        SRERole hostRole = game.getRole(host);
        Vec3 pos = host.position();
        // 先清幻灵阶段表现与状态，再按宿主职业现身。
        removePhaseEffects(sp);
        sp.setCamera(sp);
        possessTarget = null;
        searchDeadline = -1;
        searchInAdventure = false;
        active = false;
        // 现身：冒险模式 + 传送 + 语音组还原。
        GameUtils.revivePlayer(sp, pos.x, pos.y, pos.z);
        if (hostRole != null) {
            RoleUtils.changeRole(sp, hostRole);
        }
        RoleUtils.sendWelcomeAnnouncement(sp);
        sp.displayClientMessage(Component.translatable("message.noellesroles.huanling.transform_done")
                .withStyle(ChatFormatting.GREEN), false);
        sync();
    }

    /** 移除幻灵阶段的全部药水效果。 */
    private void removePhaseEffects(ServerPlayer sp) {
        removeSearchEffects(sp);
        sp.removeEffect(ModEffects.CHAT_BAN);
        sp.removeEffect(ModEffects.VOICE_SILENCE);
        sp.removeEffect(ModEffects.INVINCIBLE);
        sp.removeEffect(ModEffects.NO_COLLIDE);
    }

    /** 移除寻找期效果（隐身+静步）。 */
    private void removeSearchEffects(ServerPlayer sp) {
        sp.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        sp.removeEffect(ModEffects.FOOTSTEP_VANISH);
    }

    /** 仅移除药水效果（自身死亡时使用，不触碰其它状态）。 */
    private void removePhaseEffectsOnly() {
        if (player instanceof ServerPlayer sp) {
            removePhaseEffects(sp);
        }
    }

    /** 任意玩家死亡：分发「宿主死亡」与「幻灵自身死亡」。 */
    private static void handleAnyDeath(Player dead) {
        if (!(dead.level() instanceof ServerLevel level))
            return;
        for (ServerPlayer p : level.players()) {
            HuanlingPlayerComponent comp = KEY.maybeGet(p).orElse(null);
            if (comp == null)
                continue;
            if (dead == p) {
                comp.onSelfDeath();
                continue;
            }
            comp.onHostDeath(dead);
        }
    }

    @Override
    public void clientTick() {
        // 客户端本地递减倒计时，服务端每 20 tick 同步校正。
        if (clientSearchTicks > 0)
            clientSearchTicks--;
        if (clientTransformTicks > 0)
            clientTransformTicks--;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        // 仅同步给幻灵本人（见 shouldSyncWith）。
        if (possessTarget != null)
            tag.putUUID("Possess", possessTarget);
        long now = player.level().getGameTime();
        tag.putInt("SearchTicks", searchDeadline >= 0 ? (int) Math.max(0, searchDeadline - now) : -1);
        tag.putInt("TransformTicks", active && roundStartTick >= 0
                ? (int) Math.max(0, transformDeadline() - now)
                : -1);
        tag.putBoolean("AdvGrace", searchInAdventure);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        clientPossessTarget = tag.hasUUID("Possess") ? tag.getUUID("Possess") : null;
        clientSearchTicks = tag.getInt("SearchTicks");
        clientTransformTicks = tag.getInt("TransformTicks");
        clientAdventureGrace = tag.getBoolean("AdvGrace");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
