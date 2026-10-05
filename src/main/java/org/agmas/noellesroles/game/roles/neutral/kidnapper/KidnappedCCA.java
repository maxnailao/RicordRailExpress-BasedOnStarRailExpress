package org.agmas.noellesroles.game.roles.neutral.kidnapper;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 被绑架者组件（人质侧）。
 *
 * <p>被绑架期间：禁止移动/使用物品/使用技能/说话
 * （文字聊天由 CHAT_BAN + ALLOW_CHAT_MESSAGE 拦截，语音聊天由 VOICE_SILENCE 拦截）；
 * 但<b>允许转视角、允许切换物品栏、允许打开背包并在商店购买物品</b>，
 * 因此不再施加 TURN_BANED / INVENTORY_BANED（这两个效果会同时屏蔽鼠标转向、滚轮与 E 键）。
 * 狼（杀手）被绑 90 秒后可主手持撬锁器持续 5 秒自行挣脱（消耗撬锁器），每局仅限一次；
 * 平民被绑 60 秒后可被队友潜行右键救援，解绳需救援者保持潜行并靠近人质持续 5 秒；
 * 中立没有队友，被绑 120 秒后任何玩家（绑匪除外）都可以潜行右键给他解绳。
 * 挣脱/被救会给绑匪提示「有人逃脱了」。
 *
 * <p>注意：shouldSyncWith 对所有玩家返回 true，
 * 以便客户端（InstinctRenderer / 名字颜色渲染）判断「被绑架者不被杀手本能透视」「红/粉色显示」。
 */
public class KidnappedCCA implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<KidnappedCCA> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "kidnapped"),
            KidnappedCCA.class);

    /** 被绑后解锁队友救援所需时间（60 秒） */
    public static final int RESCUE_UNLOCK_TICKS = 60 * 20;
    /** 狼（杀手）解锁自救所需被绑时间（90 秒）；解锁后主手持撬锁器持续 5 秒即可挣脱，消耗一个撬锁器 */
    public static final int KILLER_ESCAPE_TICKS = 90 * 20;
    /** 狼（杀手）解锁后需持续手持撬锁器的时长（5 秒），中途放下则重新计时 */
    public static final int LOCKPICK_ESCAPE_TICKS = 5 * 20;
    /** 中立人质解锁「被解绳」所需被绑时间（120 秒）：中立没有队友，到点后任何玩家都能来解绳 */
    public static final int NEUTRAL_RESCUE_UNLOCK_TICKS = 120 * 20;
    /** 队友救援触发后解绳所需持续时长（5 秒），中途站起或走远则重新计时 */
    public static final int RESCUE_DURATION_TICKS = 5 * 20;
    /** 解绳期间救援者与人质的最大允许距离（平方值，4 格） */
    public static final double RESCUE_MAX_DISTANCE_SQR = 16.0;

    public final Player player;
    public UUID kidnapper;
    public boolean isKidnapped = false;
    /**
     * 人质「已被放下/原地被绑」姿态标记：true 表示处于被绑且不再被拖拽（原地站立），
     * 客户端据此渲染「双手背后、低头」的捆绑动作；拖拽跟随过程中为 false。
     * 需同步给所有客户端（shouldSyncWith=true），供 PlayerModel.setupAnim mixin 读取。
     */
    public boolean boundIdle = false;
    /** 绑架对象是否为杀手（狼） */
    public boolean isKillerTarget = false;
    /** 绑架对象是否为中立阵营：中立没有队友，解绳解锁时间更长（120 秒），但到点后任何人都能救 */
    public boolean isNeutralTarget = false;
    /** 狼自救计时（tick），达到 KILLER_ESCAPE_TICKS 后解锁撬锁自救 */
    public int escapeTicks = 0;
    /** 狼撬锁自救进度（tick）：解锁后主手持撬锁器累计，中途放下清零，满 LOCKPICK_ESCAPE_TICKS 后挣脱 */
    public int lockpickTicks = 0;
    /** 自救机会是否已用完（狼每局仅一次，init 时重置） */
    public boolean usedSelfEscape = false;
    /** 已被绑架时长（tick），达到 RESCUE_UNLOCK_TICKS 后队友才可救援 */
    public int kidnappedTicks = 0;
    /** 正在解绳救援的队友（null 表示无人正在救援） */
    public UUID rescuer;
    /** 队友解绳进度（tick），达到 RESCUE_DURATION_TICKS 后解救成功；中断则清零 */
    public int rescueTicks = 0;

    public KidnappedCCA(Player player) {
        this.player = player;
    }

    public static KidnappedCCA get(Player player) {
        return KEY.get(player);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 同步给所有玩家：客户端渲染（红/粉配色、本能透视排除）需要读取此状态 */
    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return true;
    }

    public void sync() {
        KEY.sync(player);
    }

    public void setKidnapped(UUID kidnapper, boolean killerTarget) {
        this.kidnapper = kidnapper;
        this.isKidnapped = true;
        this.isKillerTarget = killerTarget;
        // 新绑架默认进入拖拽跟随，非原地捆绑
        this.boundIdle = false;
        // 中立阵营人质：解绳解锁时间延长到 120 秒，但到点后任何玩家都能来解绳
        SRERole role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        this.isNeutralTarget = !killerTarget && role != null && role.isNeutrals();
        this.escapeTicks = 0;
        this.lockpickTicks = 0;
        this.kidnappedTicks = 0;
        this.rescuer = null;
        this.rescueTicks = 0;
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.being_dragged")
                    .withStyle(ChatFormatting.DARK_RED), false);
            if (killerTarget) {
                if (usedSelfEscape) {
                    // 自救机会已用完：只能等队友救援
                    sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.no_self_escape")
                            .withStyle(ChatFormatting.GOLD), false);
                } else {
                    // 狼可在 90 秒后自行挣脱（每局一次）
                    sp.displayClientMessage(Component.translatable(
                                    "message.noellesroles.kidnapped.killer_escape_hint",
                                    KILLER_ESCAPE_TICKS / 20)
                            .withStyle(ChatFormatting.GOLD), false);
                }
            }
            // 解绳提示：中立用「任何人都能救你」文案，其余用「队友救援」文案
            sp.displayClientMessage(Component.translatable(
                            isNeutralTarget ? "message.noellesroles.kidnapped.neutral_rescue_hint"
                                    : "message.noellesroles.kidnapped.rescue_hint",
                            rescueUnlockTicks() / 20)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        sync();
    }

    /** 本人质解锁「被解绳」所需的被绑时长（tick）：中立 120 秒，平民/狼 60 秒 */
    public int rescueUnlockTicks() {
        return isNeutralTarget ? NEUTRAL_RESCUE_UNLOCK_TICKS : RESCUE_UNLOCK_TICKS;
    }

    /** 是否已可被解绳救援：达到本人质的解锁时长后，任何玩家（绑匪除外）都能潜行右键解绳 */
    public boolean canBeRescued() {
        return isKidnapped && kidnappedTicks >= rescueUnlockTicks();
    }

    /** 队友潜行右键触发解绳：救援者需保持潜行并留在人质附近，持续 5 秒后解救成功 */
    public void startRescue(ServerPlayer newRescuer) {
        if (!isKidnapped || !canBeRescued())
            return;
        if (rescuer != null && !newRescuer.getUUID().equals(rescuer)) {
            // 已有其他队友正在解绳
            newRescuer.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.rescue_in_progress")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        boolean fresh = rescuer == null;
        this.rescuer = newRescuer.getUUID();
        if (fresh) {
            this.rescueTicks = 0;
            newRescuer.displayClientMessage(Component.translatable(
                            "message.noellesroles.kidnapped.rescue_started_rescuer",
                            RESCUE_DURATION_TICKS / 20)
                    .withStyle(ChatFormatting.GOLD), false);
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable(
                                "message.noellesroles.kidnapped.rescue_started_victim",
                                RESCUE_DURATION_TICKS / 20)
                        .withStyle(ChatFormatting.GREEN), false);
            }
            sync();
        }
    }

    /** 标记人质为「原地被绑」（放下/绑回房间）：触发客户端捆绑姿态渲染 */
    public void markBoundIdle() {
        if (!isKidnapped)
            return;
        this.boundIdle = true;
        sync();
    }

    /** 释放；escaped=true（挣脱/被救）时提示绑匪「有人逃脱了」 */
    public void release(boolean escaped) {
        UUID kUuid = this.kidnapper;
        this.isKidnapped = false;
        this.boundIdle = false;
        this.kidnapper = null;
        this.isKillerTarget = false;
        this.isNeutralTarget = false;
        this.escapeTicks = 0;
        this.lockpickTicks = 0;
        this.kidnappedTicks = 0;
        this.rescuer = null;
        this.rescueTicks = 0;
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.released")
                    .withStyle(ChatFormatting.GREEN), false);
            // 通知绑匪
            if (escaped && kUuid != null) {
                Player k = sp.level().getPlayerByUUID(kUuid);
                if (k instanceof ServerPlayer kidnapperPlayer) {
                    KidnapperPlayerComponent comp = KidnapperPlayerComponent.KEY.get(kidnapperPlayer);
                    comp.removeFromKidnapped(sp.getUUID());
                    if (comp.draggingTarget != null && comp.draggingTarget.equals(sp.getUUID()))
                        comp.draggingTarget = null;
                    comp.notifyEscape(kidnapperPlayer);
                    comp.sync();
                }
            }
        }
        sync();
    }

    @Override
    public void init() {
        this.kidnapper = null;
        this.isKidnapped = false;
        this.boundIdle = false;
        this.isKillerTarget = false;
        this.isNeutralTarget = false;
        this.escapeTicks = 0;
        this.lockpickTicks = 0;
        this.kidnappedTicks = 0;
        this.rescuer = null;
        this.rescueTicks = 0;
        this.usedSelfEscape = false;
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        if (!isKidnapped)
            return;
        if (!(player instanceof ServerPlayer sp))
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());

        // 兜底：对局未在进行（结束/未开始）时立刻解除捆绑，
        // 否则残留的 isKidnapped 会让 CHAT_BAN 等效果跨局续期，玩家「对局后无法说话」
        if (!game.isRunning()) {
            release(false);
            return;
        }

        // 人质死亡 → 静默解除
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            release(false);
            return;
        }

        // 绑匪死亡/离线/不再是绑匪 → 自动获释
        Player k = kidnapper == null ? null : sp.level().getPlayerByUUID(kidnapper);
        if (k == null || !GameUtils.isPlayerAliveAndSurvival(k) || !game.isRole(k, ModRoles.kidnapper)) {
            release(false);
            return;
        }

        // 禁锢效果（每 tick 续期，短时长）
        // 注意：不再施加 TURN_BANED / INVENTORY_BANED —— 人质可以转视角、切换物品栏、开背包逛商店
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, true, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 10, 0, true, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, 10, 0, true, false, true));
        // 禁止说话：禁文字聊天 + 禁语音聊天（续期停止后约 0.5 秒自动失效）
        sp.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, 10, 0, true, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, 10, 0, true, false, true));
        sp.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 10, 0, true, false, true));

        // 被绑时长累计：平民/狼满 60 秒、中立满 120 秒后解锁「被解绳」
        kidnappedTicks++;
        if (kidnappedTicks == rescueUnlockTicks()) {
            sp.displayClientMessage(Component.translatable(
                            isNeutralTarget ? "message.noellesroles.kidnapped.neutral_rescue_unlocked"
                                    : "message.noellesroles.kidnapped.rescue_unlocked")
                    .withStyle(ChatFormatting.GREEN), false);
            sync();
        } else if (kidnappedTicks % 20 == 0) {
            // 每秒同步一次，供本人 HUD 倒计时显示
            sync();
        }

        // 队友解绳救援：潜行右键触发后需持续 5 秒，期间救援者须保持潜行并留在人质 4 格内
        if (rescuer != null) {
            Player r = sp.level().getPlayerByUUID(rescuer);
            ServerPlayer rs = r instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            boolean valid = rs != null
                    && GameUtils.isPlayerAliveAndSurvival(rs)
                    && rs.isShiftKeyDown()
                    && !rs.getUUID().equals(kidnapper)
                    && rs.distanceToSqr(sp) <= RESCUE_MAX_DISTANCE_SQR;
            if (!valid) {
                // 救援中断：站起/走远/死亡/离线 → 清零，需重新潜行右键
                rescuer = null;
                rescueTicks = 0;
                sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.rescue_interrupted")
                        .withStyle(ChatFormatting.RED), false);
                if (rs != null) {
                    rs.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.rescue_interrupted")
                            .withStyle(ChatFormatting.RED), false);
                }
                sync();
            } else {
                rescueTicks++;
                if (rescueTicks >= RESCUE_DURATION_TICKS) {
                    // 解绳完成：解救成功
                    rescuer = null;
                    rescueTicks = 0;
                    release(true);
                    return;
                }
                if (rescueTicks % 20 == 0) {
                    int remain = (RESCUE_DURATION_TICKS - rescueTicks + 19) / 20;
                    rs.displayClientMessage(Component.translatable(
                                    "message.noellesroles.kidnapped.rescue_progress", remain)
                            .withStyle(ChatFormatting.GREEN), true);
                    sync();
                }
            }
        }

        // 狼（杀手）自救：被绑 90 秒解锁后，主手持撬锁器持续 5 秒即可挣脱（消耗撬锁器），每局仅限一次
        if (isKillerTarget && !usedSelfEscape) {
            if (escapeTicks < KILLER_ESCAPE_TICKS) {
                escapeTicks++;
                if (escapeTicks == KILLER_ESCAPE_TICKS) {
                    // 时间到：提示操作方式
                    sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.escape_ready_hint")
                            .withStyle(ChatFormatting.GOLD), false);
                    sync();
                } else if (escapeTicks % 20 == 0) {
                    sync();
                }
            } else {
                boolean holdingLockpick = sp.getMainHandItem().is(TMMItems.LOCKPICK);
                if (holdingLockpick && lockpickTicks < LOCKPICK_ESCAPE_TICKS) {
                    lockpickTicks++;
                    if (lockpickTicks == 1) {
                        // 开始撬锁：提示保持手持
                        sp.displayClientMessage(Component.translatable(
                                        "message.noellesroles.kidnapped.escape_pick_start",
                                        LOCKPICK_ESCAPE_TICKS / 20)
                                .withStyle(ChatFormatting.GOLD), false);
                    }
                    if (lockpickTicks >= LOCKPICK_ESCAPE_TICKS) {
                        // 持满 5 秒：消耗撬锁器并挣脱
                        sp.getMainHandItem().shrink(1);
                        usedSelfEscape = true;
                        release(true);
                        sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.escaped_self")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
                        return;
                    }
                    if (lockpickTicks % 20 == 0) {
                        // 每秒同步一次，供本人 HUD 显示撬锁倒计时
                        sync();
                    }
                } else if (!holdingLockpick && lockpickTicks > 0) {
                    // 撬锁途中放下撬锁器：中断并重置，需重新持满 5 秒
                    lockpickTicks = 0;
                    sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.escape_pick_interrupted")
                            .withStyle(ChatFormatting.RED), false);
                    sync();
                }
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isKidnapped", isKidnapped);
        tag.putBoolean("boundIdle", boundIdle);
        tag.putBoolean("isKillerTarget", isKillerTarget);
        tag.putBoolean("isNeutralTarget", isNeutralTarget);
        tag.putBoolean("usedSelfEscape", usedSelfEscape);
        tag.putInt("kidnappedTicks", kidnappedTicks);
        tag.putInt("escapeTicks", escapeTicks);
        tag.putInt("lockpickTicks", lockpickTicks);
        tag.putInt("rescueTicks", rescueTicks);
        if (kidnapper != null)
            tag.putUUID("kidnapper", kidnapper);
        if (rescuer != null)
            tag.putUUID("rescuer", rescuer);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        isKidnapped = tag.getBoolean("isKidnapped");
        boundIdle = tag.getBoolean("boundIdle");
        isKillerTarget = tag.getBoolean("isKillerTarget");
        isNeutralTarget = tag.getBoolean("isNeutralTarget");
        usedSelfEscape = tag.getBoolean("usedSelfEscape");
        kidnappedTicks = tag.getInt("kidnappedTicks");
        escapeTicks = tag.getInt("escapeTicks");
        lockpickTicks = tag.getInt("lockpickTicks");
        rescueTicks = tag.getInt("rescueTicks");
        kidnapper = tag.hasUUID("kidnapper") ? tag.getUUID("kidnapper") : null;
        rescuer = tag.hasUUID("rescuer") ? tag.getUUID("rescuer") : null;
    }
}