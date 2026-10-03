package org.agmas.noellesroles.game.roles.neutral.kidnapper;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 绑匪（独立中立）。
 *
 * <p>G 键技能：瞄准附近玩家将其绑架（禁言、禁技能、禁道具、禁背包），
 * 绑架期间目标沿着绑匪走过的轨迹跟随在绑匪身后，再按一次 G 将其放在当前位置。
 * 每成功绑架一人获得 {@link #KIDNAP_REWARD_COINS} 金币。
 * 绑架目标若是杀手，60 秒后可自行挣脱；可被……其他玩家救下；
 * 挣脱/被救都会提示绑匪「有人逃脱了」。
 *
 * <p>绑满 6 人且场上存活人数达标后，按 G（视线无目标时）进入『审判阶段』：
 * 绑架技能失效，获得枪、德林加与刀，枪击杀人不掉落；枪冷却按进入审判时的绑架人数快照递减
 * （6人-20%，7人获盾，8人-40%，9人-60%，10人-80%）；审判阶段绑匪杀死的人记为被绑架（在自己房间被捆绑）。
 * 当场上所有其他存活玩家都被绑架时绑匪胜利。
 */
public class KidnapperPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<KidnapperPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "kidnapper_role"),
            KidnapperPlayerComponent.class);

    /** 绑架施法距离（格）：必须贴近目标 3 格内才能绑架 */
    public static final double KIDNAP_RANGE = 3.0;
    /** 绑架技能冷却（10 秒） */
    public static final int KIDNAP_COOLDOWN_TICKS = 10 * 20;
    /** 绑架奖励：每成功绑架一名玩家获得的金币 */
    public static final int KIDNAP_REWARD_COINS = 25;
    /** 进入审判阶段所需绑架人数 */
    public static final int JUDGMENT_REQUIRED_COUNT = 6;
    /** 审判阶段持续时间（2 分 30 秒） */
    public static final int JUDGMENT_DURATION_TICKS = (2 * 60 + 30) * 20;
    /** 逃脱提示在 HUD 左上角的显示时长（5 秒） */
    public static final int ESCAPE_NOTICE_TICKS = 5 * 20;
    /** 跟随目标：沿轨迹落后绑匪多少格（弧长） */
    public static final double FOLLOW_DISTANCE = 2.0;
    /** 人质距跟随目标小于该半径（格）时不再移动，避免原地抖动 */
    public static final double FOLLOW_DEAD_ZONE = 1.0;
    /** 人质跟随移速（格/tick）：统一使用疾跑速度，绑匪步行拖拽时人质也跑着跟，保证不掉队 */
    public static final double FOLLOW_SPRINT_SPEED = 0.28;
    /** 轨迹面包屑点最小间距（格）：绑匪水平位移超过该值才记录新轨迹点，避免点过密 */
    public static final double TRAIL_MIN_SPACING = 0.1;
    /** 轨迹保留弧长上限（格）：覆盖跟随距离即可，及时裁掉旧轨迹防止无限增长 */
    public static final double TRAIL_MAX_LENGTH = FOLLOW_DISTANCE * 4.0;

    public final Player player;

    /** 已被绑架的玩家（含审判阶段击杀记为绑架的人） */
    public final List<UUID> kidnapped = new ArrayList<>();
    /** 正在拖拽的人质 */
    public UUID draggingTarget = null;
    /** 拖拽跟随：绑匪的行走轨迹面包屑（队首为最新点），人质沿该轨迹落后 FOLLOW_DISTANCE 弧长跟随 */
    private final ArrayDeque<Vec3> followTrail = new ArrayDeque<>();
    /** 是否已进入审判阶段 */
    public boolean judgmentPhase = false;
    /** 进入审判阶段时的真实绑架人数快照（枪冷却缩减只按此计算，审判期击杀不计入） */
    public int judgmentKidnapCount = 0;
    /** 审判阶段剩余时间（tick），服务端倒计时并按秒同步给客户端 */
    public int judgmentRemainingTicks = 0;
    /** 绑架技能冷却 */
    public int kidnapCooldown = 0;
    /** 客户端显示的绑架人数（服务端通过同步 NBT 下发） */
    public int kidnappedCount = 0;
    /** 逃脱提示剩余显示时间（tick），> 0 时 HUD 左上角显示「有人质逃脱」 */
    public int escapeNoticeTicks = 0;

    public KidnapperPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        kidnapped.clear();
        draggingTarget = null;
        followTrail.clear();
        judgmentPhase = false;
        judgmentKidnapCount = 0;
        judgmentRemainingTicks = 0;
        kidnapCooldown = 0;
        kidnappedCount = 0;
        escapeNoticeTicks = 0;
    }

    @Override
    public void clear() {
        this.init();
    }

    // ==================== G 键技能 ====================

    public void tryUseSkill(ServerPlayer sp) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        if (!game.isRunning() || !game.isRole(sp, ModRoles.kidnapper))
            return;

        // 安全检查：安全时间内禁止使用技能
        if (sp.hasEffect(ModEffects.SAFE_TIME)) {
            sp.displayClientMessage(Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }

        // 正在拖拽：把人质放在当前位置（仍然处于被绑状态）
        if (draggingTarget != null) {
            draggingTarget = null;
            sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.dropped")
                    .withStyle(ChatFormatting.YELLOW), true);
            sync();
            return;
        }

        if (judgmentPhase) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.skill_disabled")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 视线射线选取附近目标（同 LEON 踢人）
        HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnViewVector(sp,
                e -> e instanceof ServerPlayer p
                        && GameUtils.isPlayerAliveAndSurvival(p)
                        && p != sp,
                KIDNAP_RANGE);

        if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof ServerPlayer victim) {
            if (kidnapped.contains(victim.getUUID())) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.already")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (kidnapCooldown > 0) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.cooldown",
                                kidnapCooldown / 20)
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            // 捆绳捆绑：必须持有捆绳才能绑架
            if (findRope(sp).isEmpty()) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.no_rope")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            kidnap(sp, victim, game);
        } else {
            // 视线无目标：若绑满 6 人且场上人数达标，尝试进入审判阶段
            tryEnterJudgmentPhase(sp, game);
        }
    }

    private void kidnap(ServerPlayer sp, ServerPlayer victim, SREGameWorldComponent game) {
        kidnapped.add(victim.getUUID());
        draggingTarget = victim.getUUID();
        // 新拖拽开始：用「人质位置 → 绑匪位置」播种轨迹，人质先沿这条连线平滑走到绑匪身后，
        // 避免远距离绑架时目标点直接变成绑匪脚下、人质被瞬间拉过去（看起来像瞬移）；
        // 之后绑匪每真实移动一段距离就追加面包屑，人质继续沿轨迹跟随
        followTrail.clear();
        followTrail.addFirst(sp.position());
        followTrail.addLast(victim.position());
        kidnapCooldown = KIDNAP_COOLDOWN_TICKS;

        boolean isKillerTarget = game.getAllKillerPlayers().contains(victim.getUUID());
        KidnappedCCA.get(victim).setKidnapped(sp.getUUID(), isKillerTarget);

        sp.level().playSound(null, victim.blockPosition(),
                SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.kidnapped",
                        victim.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED), true);

        // 绑架奖励：每成功绑架一人获得 25 金币（局内商店金币，自动同步到客户端）
        SREPlayerShopComponent.KEY.get(sp).addToBalance(KIDNAP_REWARD_COINS);
        sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.kidnap_reward",
                        KIDNAP_REWARD_COINS)
                .withStyle(ChatFormatting.GOLD), false);

        // 消耗捆绳耐久：每绑架一次消耗1点，耐久耗尽后捆绳损毁
        ItemStack rope = findRope(sp);
        if (!rope.isEmpty()) {
            int damage = rope.getDamageValue() + 1;
            rope.setDamageValue(damage);
            if (damage >= rope.getMaxDamage()) {
                rope.shrink(1);
                sp.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
        sync();
    }

    /** 在背包中查找捆绳 */
    private ItemStack findRope(ServerPlayer sp) {
        for (var compartment : sp.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (stack.is(org.agmas.noellesroles.init.ModItems.KIDNAP_ROPE))
                    return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private void tryEnterJudgmentPhase(ServerPlayer sp, SREGameWorldComponent game) {
        if (kidnapped.size() < JUDGMENT_REQUIRED_COUNT) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.not_enough",
                            kidnapped.size(), JUDGMENT_REQUIRED_COUNT)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(sp.level() instanceof ServerLevel level))
            return;
        int aliveCount = 0;
        for (ServerPlayer p : level.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p))
                aliveCount++;
        }
        if (aliveCount < JUDGMENT_REQUIRED_COUNT) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.players_not_enough",
                            aliveCount, JUDGMENT_REQUIRED_COUNT)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        judgmentPhase = true;
        // 冻结进入审判时的真实绑架人数：之后审判阶段杀人只计入 kidnapped（胜利判定），不再影响枪冷却缩减
        judgmentKidnapCount = kidnapped.size();
        // 审判阶段限时 2 分 30 秒
        judgmentRemainingTicks = JUDGMENT_DURATION_TICKS;
        draggingTarget = null;
        // 审判阶段奖励：绑架人数达到 7 人及以上额外获得 1 层护盾
        if (judgmentKidnapCount >= 7) {
            SREArmorPlayerComponent.KEY.get(sp).addArmor();
        }
        // 审判阶段武装：枪 + 巡警手枪 + 刀
        sp.addItem(new ItemStack(TMMItems.REVOLVER));
        sp.addItem(new ItemStack(org.agmas.noellesroles.init.ModItems.PATROLLER_REVOLVER));
        sp.addItem(new ItemStack(TMMItems.KNIFE));
        // 审判阶段演出（仿黑警时刻）：熄灯 + 闪电 + 音效 + 全局广播 + 全局标记
        playJudgmentEffects(sp);
        sync();
    }

    /** 审判阶段演出效果（仿黑警时刻：熄灯 + 全局广播） */
    private void playJudgmentEffects(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel serverWorld))
            return;

        // 全局标记：供客户端 HUD 横幅与审判阶段音乐使用
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        game.setKidnapperJudgmentActive(true);
        // 倒计时初值：供全场正上方 HUD 立即显示完整时长
        game.setKidnapperJudgmentRemainingSeconds(JUDGMENT_DURATION_TICKS / 20);

        // 全场熄灯 5 秒（100 tick）开场演出
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(serverWorld);
        blackout.triggerBlackout(true, 100);

        // 绑匪本人：动作栏提示
        sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.judgment_entered")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);

        // 全局广播：通知所有玩家审判阶段已开启
        var broadcastMessage = Component.translatable("message.noellesroles.kidnapper.judgment_broadcast")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        sp.server.getPlayerList().getPlayers().forEach(p ->
                BroadcastCommand.BroadcastMessage(p, broadcastMessage));
    }

    // ==================== 每 tick ====================

    @Override
    public void serverTick() {
        // 拖拽人质期间技能冷却冻结，放下人质后才开始递减
        if (kidnapCooldown > 0 && draggingTarget == null) {
            kidnapCooldown--;
            // HUD 按秒显示冷却：每秒同步一次给客户端，倒计时才不会卡住
            if (kidnapCooldown % 20 == 0)
                sync();
        }
        if (escapeNoticeTicks > 0) {
            escapeNoticeTicks--;
            // 归零时同步一次，让 HUD 提示准时消失
            if (escapeNoticeTicks == 0)
                sync();
        }
        if (!(player instanceof ServerPlayer sp))
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sp.level());
        if (!game.isRunning())
            return;

        // 绑匪死亡 → 释放所有肉票
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            releaseAll(sp, true);
            if (judgmentPhase)
                endJudgmentPhase(sp, game);
            return;
        }

        // 审判阶段倒计时（2 分 30 秒，按秒同步给客户端 HUD）
        if (judgmentPhase && judgmentRemainingTicks > 0) {
            judgmentRemainingTicks--;
            if (judgmentRemainingTicks % 20 == 0) {
                sync();
                // 剩余秒数写入全场组件，供所有玩家正上方倒计时 HUD 显示（setter 内部自动 sync）
                game.setKidnapperJudgmentRemainingSeconds(judgmentRemainingTicks / 20);
            }
            if (judgmentRemainingTicks == 0) {
                endJudgmentPhase(sp, game);
                // 审判超时：处决绑匪（同黑警时刻超时自毁）；下一 tick 死亡分支会自动释放所有肉票
                sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.judgment_timeout")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
                GameUtils.killPlayer(sp, true, null, Noellesroles.id("kidnapper_judgment_timeout"));
            }
        }

        // 拖拽：人质沿绑匪走过的轨迹跟随在绑匪身后
        if (draggingTarget != null) {
            Player t = sp.level().getPlayerByUUID(draggingTarget);
            if (t == null || !GameUtils.isPlayerAliveAndSurvival(t)) {
                notifyEscape(sp);
                removeFromKidnapped(draggingTarget);
                draggingTarget = null;
                sync();
            } else if (sp.distanceToSqr(t) > 64 * 64) {
                // 距离过远（例如被传送走）视为逃脱
                notifyEscape(sp);
                KidnappedCCA.get(t).release(true);
                draggingTarget = null;
                sync();
            } else if (t.level() != sp.level()) {
                // 跨维度兜底：直接拉到绑匪同维度的身后锚点
                Vec3 view = sp.getViewVector(1.0f);
                Vec3 anchor = sp.position().subtract(view.multiply(FOLLOW_DISTANCE, 0.0, FOLLOW_DISTANCE));
                if (t instanceof ServerPlayer st)
                    st.teleportTo(sp.serverLevel(), anchor.x, sp.getY(), anchor.z, st.getYRot(), st.getXRot());
                // 兜底传送后重置跟随状态，人质留在新位置，绑匪再次移动时才继续跟随
                followTrail.clear();
            } else if (t instanceof ServerPlayer st) {
                followBehind(sp, st);
            }
        }

        checkWin(sp, game);
    }

    /**
     * 人质沿着绑匪走过的轨迹跟随在绑匪身后。
     *
     * <p>绑匪每次真实水平位移超过 {@link #TRAIL_MIN_SPACING} 时，把当前位置作为面包屑记入 {@link #followTrail}
     * （原地转身不产生轨迹点）；人质的目标点是沿轨迹折线落后绑匪 {@link #FOLLOW_DISTANCE} 格弧长的点，
     * 因此人质严格沿着绑匪踩过的路线行走、不抄近路。
     * 刚绑住时轨迹弧长不足 {@link #FOLLOW_DISTANCE}，目标点取轨迹最旧点（绑住时的位置），
     * 绑匪继续走远后目标点才沿轨迹后退。
     * 轨迹只保留最近 {@link #TRAIL_MAX_LENGTH} 格弧长，长距离拖拽不会无限增长。
     * 人质超出死区半径时，每 tick 朝目标点以疾跑速度移动，绝不瞬移、绝不超速。
     *
     * <p>服务端权威移动（同操纵师 InControlCCA 模式）：人质处于 MOVE_BANED，
     * 客户端不会争抢移动输入，权威位置由 connection.teleport 推送。
     */
    private void followBehind(ServerPlayer sp, ServerPlayer st) {
        Vec3 pos = sp.position();

        // 记录面包屑：只有绑匪真实水平移动超过最小间距才记录新轨迹点，
        // 原地转身位置不变、轨迹不动，人质就不会被带着转
        if (followTrail.isEmpty()) {
            followTrail.addFirst(pos);
        } else {
            Vec3 head = followTrail.getFirst();
            double tx = pos.x - head.x;
            double tz = pos.z - head.z;
            if (tx * tx + tz * tz > TRAIL_MIN_SPACING * TRAIL_MIN_SPACING) {
                followTrail.addFirst(pos);
                trimTrail();
            }
        }

        // 目标点 = 沿轨迹落后绑匪 FOLLOW_DISTANCE 弧长的点，人质沿绑匪走过的路线跟随，不抄近路
        Vec3 target = trailTargetPoint(pos);

        double hx = 0.0;
        double hz = 0.0;
        double dx = target.x - st.getX();
        double dz = target.z - st.getZ();
        double distSqr = dx * dx + dz * dz;
        if (distSqr > FOLLOW_DEAD_ZONE * FOLLOW_DEAD_ZONE) {
            double dist = Math.sqrt(distSqr);
            // 剩余距离不足一步时只走剩余距离，避免在目标点两侧来回抖动
            double step = Math.min(FOLLOW_SPRINT_SPEED, dist);
            hx = dx / dist * step;
            hz = dz / dist * step;
        }

        double vy;
        if (st.onGround()) {
            vy = 0.0;
            // 前方有 1 格高的障碍 → 起跳（头顶有空间才跳，否则只会撞墙）
            if ((hx != 0.0 || hz != 0.0) && needStepJump(st, hx, hz)) {
                vy = 0.42;
            }
        } else {
            // 空中：按重力下坠
            vy = st.getDeltaMovement().y - 0.08;
            if (vy < -3.0)
                vy = -3.0;
        }

        Vec3 delta = new Vec3(hx, vy, hz);
        st.move(MoverType.SELF, delta);
        st.setDeltaMovement(hx, vy, hz);
        if (delta.lengthSqr() > 1.0e-5) {
            // 推送权威位置到目标客户端（目标处于 MOVE_BANED，不会与之争抢）
            st.connection.teleport(st.getX(), st.getY(), st.getZ(), st.getYRot(), st.getXRot());
        }
        st.hasImpulse = true;
    }

    /** 裁剪轨迹：只保留最近 {@link #TRAIL_MAX_LENGTH} 弧长，丢弃队尾最旧的面包屑点 */
    private void trimTrail() {
        double length = 0.0;
        Vec3 prev = null;
        boolean overLimit = false;
        var it = followTrail.iterator();
        while (it.hasNext()) {
            Vec3 p = it.next();
            if (overLimit) {
                it.remove();
                continue;
            }
            if (prev != null) {
                length += horizontalDistance(prev, p);
                if (length > TRAIL_MAX_LENGTH)
                    overLimit = true;
            }
            prev = p;
        }
    }

    /** 沿轨迹插值找出落后绑匪 {@link #FOLLOW_DISTANCE} 弧长的点；轨迹不足长时返回轨迹最旧点 */
    private Vec3 trailTargetPoint(Vec3 kidnapperPos) {
        double remaining = FOLLOW_DISTANCE;
        Vec3 prev = kidnapperPos;
        for (Vec3 p : followTrail) {
            double seg = horizontalDistance(prev, p);
            if (seg > 1.0e-6) {
                if (seg >= remaining) {
                    double t = remaining / seg;
                    return new Vec3(
                            prev.x + (p.x - prev.x) * t,
                            prev.y + (p.y - prev.y) * t,
                            prev.z + (p.z - prev.z) * t);
                }
                remaining -= seg;
            }
            prev = p;
        }
        return prev;
    }

    /** 两点间的水平（XZ 平面）距离 */
    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 前进方向上是否有需要跳跃的 1 格高障碍（且头顶两格可通行，跳得过去） */
    private static boolean needStepJump(Player t, double hx, double hz) {
        Vec3 ahead = t.position().add(hx * 3.0, 0.0, hz * 3.0);
        BlockPos footPos = BlockPos.containing(ahead.x, ahead.y, ahead.z);
        var level = t.level();
        var footState = level.getBlockState(footPos);
        if (footState.isAir() || !footState.isSuffocating(level, footPos))
            return false;
        BlockPos h1 = footPos.above();
        BlockPos h2 = footPos.above(2);
        return !level.getBlockState(h1).isSuffocating(level, h1)
                && !level.getBlockState(h2).isSuffocating(level, h2);
    }

    /** 绑匪胜利：场上所有其他存活玩家都被绑架 */
    private void checkWin(ServerPlayer sp, SREGameWorldComponent game) {
        if (!(sp.level() instanceof ServerLevel level))
            return;
        boolean anyOther = false;
        boolean allCaptured = true;
        for (ServerPlayer p : level.players()) {
            if (p == sp || !GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            anyOther = true;
            if (!KidnappedCCA.KEY.get(p).isKidnapped && !kidnapped.contains(p.getUUID()))
                allCaptured = false;
        }
        if (anyOther && allCaptured && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            var roundEnd = SREGameRoundEndComponent.KEY.get(level);
            roundEnd.CustomWinnerID = "kidnapper";
            roundEnd.CustomWinnerPlayers.add(sp.getUUID());
            roundEnd.setRoundEndData(level.players(), GameUtils.WinStatus.CUSTOM);
            GameUtils.stopGame(level);
        }
    }

    public void removeFromKidnapped(UUID uuid) {
        if (kidnapped.remove(uuid))
            sync();
    }

    public void notifyEscape(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatable("message.noellesroles.kidnapper.escaped")
                .withStyle(ChatFormatting.RED), false);
        sp.level().playSound(null, sp.blockPosition(),
                SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
        // HUD 左上角逃脱提示：持续 5 秒
        escapeNoticeTicks = ESCAPE_NOTICE_TICKS;
        sync();
    }

    private void releaseAll(ServerPlayer sp, boolean announce) {
        List<UUID> copy = new ArrayList<>(kidnapped);
        for (UUID uuid : copy) {
            Player t = sp.level().getPlayerByUUID(uuid);
            if (t != null)
                KidnappedCCA.get(t).release(announce);
        }
        kidnapped.clear();
        draggingTarget = null;
        sync();
    }

    /** 结束审判阶段（超时或绑匪死亡）：清除全局标记并广播 */
    private void endJudgmentPhase(ServerPlayer sp, SREGameWorldComponent game) {
        judgmentPhase = false;
        judgmentRemainingTicks = 0;
        game.setKidnapperJudgmentActive(false);
        var endMessage = Component.translatable("message.noellesroles.kidnapper.judgment_ended")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        sp.server.getPlayerList().getPlayers().forEach(p ->
                BroadcastCommand.BroadcastMessage(p, endMessage));
        sync();
    }

    // ==================== 事件注册 ====================

    public static boolean isJudgmentPhase(Player player) {
        if (player == null)
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return game.isRunning() && game.isRole(player, ModRoles.kidnapper)
                && KEY.get(player).judgmentPhase;
    }

    /** 枪冷却倍率：6人 -20%；7人 档位奖励为护盾（冷却保持 -20%）；8人 -40%；9人 -60%；10人 -80% */
    public static float gunCooldownMultiplier(int kidnappedCount) {
        int c = Math.min(Math.max(kidnappedCount, 6), 10);
        return switch (c) {
            case 6, 7 -> 0.8f; // -20%（7人额外奖励是护盾，冷却不再递减）
            case 8 -> 0.6f;    // -40%
            case 9 -> 0.4f;    // -60%
            default -> 0.2f;   // -80%
        };
    }

    /** 在 NRCombatEvents.registerCombatRoleEvents() 中调用 */
    public static void registerEvents() {
        // 审判阶段枪不掉落
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            if (isJudgmentPhase(player))
                return TrueFalseResult.FALSE;
            return TrueFalseResult.PASS;
        });

        // 审判阶段枪冷却按「进入审判时的绑架人数快照」缩减（审判期击杀不计入）
        OnRevolverUsed.EVENT.register((player, target) -> {
            if (!isJudgmentPhase(player))
                return;
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand.is(TMMItemTags.GUNS)) {
                int base = GameConstants.ITEM_COOLDOWNS.getOrDefault(mainHand.getItem(),
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0));
                int cd = Math.round(base * gunCooldownMultiplier(KEY.get(player).judgmentKidnapCount));
                // 延迟到下一 tick：GunShootPayload 末尾会在本次事件中无条件补一次满额基础冷却，
                // 必须等它执行完再覆盖为缩减后的冷却，否则缩减会被冲掉
                player.getServer().execute(() -> player.getCooldowns().addCooldown(mainHand.getItem(), cd));
            }
        });

        // 其他玩家潜行右键被绑架者 → 开始解绳（平民/狼被绑满 60 秒、中立满 120 秒后才允许）；
        // 救援者可以是任何玩家（绑匪本人除外），需保持潜行并留在人质附近持续 5 秒才能解救成功
        // （进度在 KidnappedCCA 中推进）
        UseEntityCallback.EVENT.register((user, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(user instanceof ServerPlayer rescuer)
                    || !(entity instanceof ServerPlayer victim))
                return InteractionResult.PASS;
            if (!rescuer.isShiftKeyDown())
                return InteractionResult.PASS;
            KidnappedCCA vic = KidnappedCCA.get(victim);
            if (!vic.isKidnapped || vic.kidnapper == null
                    || vic.kidnapper.equals(rescuer.getUUID()))
                return InteractionResult.PASS;
            if (!vic.canBeRescued()) {
                rescuer.displayClientMessage(Component.translatable(
                                "message.noellesroles.kidnapped.rescue_too_early",
                                vic.rescueUnlockTicks() / 20)
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            vic.startRescue(rescuer);
            return InteractionResult.SUCCESS;
        });

        // 人质禁言反馈：文字聊天由 CHAT_BAN + ALLOW_CHAT_MESSAGE 在「广播阶段」拦截。
        // 不能再像旧 KidnapperChatMixin 那样在 handleChat 的 HEAD 直接 cancel —— 那会让签名聊天
        // 的确认链断裂，客户端在对局结束后也发不出消息（表现为「对局后无法说话」，需重进服务器）。
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            if (sender == null || !KidnappedCCA.KEY.get(sender).isKidnapped)
                return true;
            sender.displayClientMessage(Component.translatable("message.noellesroles.kidnapped.muted")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        });
    }

    // ==================== 序列化 ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("judgmentPhase", judgmentPhase);
        tag.putInt("judgmentKidnapCount", judgmentKidnapCount);
        tag.putInt("judgmentRemainingTicks", judgmentRemainingTicks);
        tag.putInt("kidnapCooldown", kidnapCooldown);
        tag.putInt("kidnappedCount", kidnapped.size());
        tag.putInt("escapeNoticeTicks", escapeNoticeTicks);
        if (draggingTarget != null)
            tag.putUUID("draggingTarget", draggingTarget);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        judgmentPhase = tag.getBoolean("judgmentPhase");
        judgmentKidnapCount = tag.getInt("judgmentKidnapCount");
        judgmentRemainingTicks = tag.getInt("judgmentRemainingTicks");
        kidnapCooldown = tag.getInt("kidnapCooldown");
        kidnappedCount = tag.getInt("kidnappedCount");
        escapeNoticeTicks = tag.getInt("escapeNoticeTicks");
        draggingTarget = tag.hasUUID("draggingTarget") ? tag.getUUID("draggingTarget") : null;
    }
}