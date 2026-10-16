/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item.ora;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ModDataComponentTypes;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 欧拉一拳（{@link FunnyItems#BOWEN_BADGE}）的连打逻辑。<b>不限职业</b>：谁拿到这个物品谁都能用。
 * <p>
 * 右键进入攻击期间，绑定第一个打到的目标；被绑定目标无法移动、无法使用物品，并始终被固定在
 * 使用者水平正前方，直到自身或目标死亡，或 {@value #RUSH_TICKS} tick 内没打满
 * {@value #PUNCHES_TO_KILL} 次而超时。
 * <p>
 * 状态存放方式（原来挂在承太郎的职业数据上，因此只有承太郎能用）：
 * <ul>
 * <li><b>服务端权威状态</b>在 {@link #ACTIVE} 里，按使用者 UUID 索引，与职业无关；</li>
 * <li><b>客户端镜像</b>写在物品的 {@link ModDataComponentTypes#ORA_RUSH} 组件上，手持物品会自动
 * 同步给所有能看见持有者的玩家，渲染（金色挥拳）与 HUD 直接读它，不用新增网络包；</li>
 * <li>事件只注册<b>一次</b>（{@link #registerEvents()}），逐个使用者在统一的服务端 tick 里结算。</li>
 * </ul>
 */
public final class OraPunchManager {

    public static final int RUSH_TICKS = 6 * 20;
    public static final int PUNCHES_TO_KILL = 20;
    /** 原 30s，冷却 -60% 后为 12s。 */
    public static final int FULL_COOLDOWN_TICKS = 12 * 20;
    public static final int FAIL_COOLDOWN_TICKS = FULL_COOLDOWN_TICKS / 2;
    public static final double PUNCH_REACH = 4.0;
    public static final double HOLD_FRONT_DISTANCE = 1.6;
    public static final int MIN_PUNCH_INTERVAL_TICKS = 2;

    private static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.84f, 0.18f), 1.15f);
    /** 使用者 UUID → 连打进度。仅服务端。 */
    private static final Map<UUID, Rush> ACTIVE = new ConcurrentHashMap<>();
    /** 被绑定目标 UUID → 使用者 UUID。仅服务端。 */
    private static final Map<UUID, UUID> BOUND_TARGET_TO_ATTACKER = new ConcurrentHashMap<>();
    private static boolean eventsRegistered = false;

    private OraPunchManager() {
    }

    /** 一次连打的服务端进度。 */
    private static final class Rush {
        @Nullable
        UUID targetUuid = null;
        int punchCount = 0;
        long rushEndGameTime = 0L;
        long lastPunchGameTime = 0L;
        boolean nextOffhandSwing = false;
    }

    // ==================== 注册 ====================

    public static void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!isBoundTarget(player)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.jojo.ora.item_locked")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!isBoundTarget(player)) {
                return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!isBoundTarget(player)) {
                return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
        });

        OnPlayerDeath.EVENT.register((victim, deathReason) -> endRushIfInvolved(victim, false));

        OnGameEnd.EVENT.register((level, game) -> {
            // 清表之前先把镜像擦掉，免得「还在寻找目标」的快照（没有倒计时）留到下一局。
            for (UUID attackerId : ACTIVE.keySet()) {
                syncMirror(level.getPlayerByUUID(attackerId), null);
            }
            ACTIVE.clear();
            BOUND_TARGET_TO_ATTACKER.clear();
        });

        // 统一的服务端 tick：逐个结算当前正在连打的使用者，不给每个使用者单独注册事件。
        ServerTickEvents.END_SERVER_TICK.register(OraPunchManager::serverTick);
    }

    // ==================== 查询（两端可用） ====================

    /** 是否正被别人的欧拉一拳绑住（服务端权威，客户端不参与判定）。 */
    public static boolean isBoundTarget(Player player) {
        return player != null && BOUND_TARGET_TO_ATTACKER.containsKey(player.getUUID());
    }

    /**
     * 是否正在连打（已绑定目标且未超时）。渲染与动画用，两端都能调。
     * <p>
     * 读的是手持物品上的镜像组件，所以对「别人」也成立——这正是第三人称疯狂挥拳需要的。
     */
    public static boolean isRushing(Player player) {
        OraRushState state = stateOf(player);
        return state != null && state.bound() && !state.isExpired(player.level());
    }

    /** 主手或副手是否拿着欧拉一拳。 */
    public static boolean isHoldingOraPunch(Player player) {
        if (player == null) {
            return false;
        }
        return player.getMainHandItem().is(FunnyItems.BOWEN_BADGE)
                || player.getOffhandItem().is(FunnyItems.BOWEN_BADGE);
    }

    /**
     * 手持的欧拉一拳上属于该玩家自己的连打快照；没有连打、或快照属于别人（物品被捡走）时返回 null。
     */
    @Nullable
    public static OraRushState stateOf(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        OraRushState state = readMirror(player.getMainHandItem(), player.getUUID());
        return state != null ? state : readMirror(player.getOffhandItem(), player.getUUID());
    }

    /** 连打是否已超时。服务端查权威表，客户端查物品镜像。 */
    public static boolean isRushExpired(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        if (!player.level().isClientSide) {
            Rush rush = ACTIVE.get(player.getUUID());
            return rush != null && isExpired(player.level(), rush);
        }
        OraRushState state = stateOf(player);
        return state != null && state.isExpired(player.level());
    }

    // ==================== 服务端逻辑 ====================

    /** 右键欧拉一拳：开始寻找目标 / 继续连打。只在服务端调用。 */
    public static void onOraUse(Player user) {
        if (!(user instanceof ServerPlayer attacker) || attacker.level().isClientSide) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(attacker)) {
            return;
        }
        Rush rush = ACTIVE.get(attacker.getUUID());
        if (rush != null && isExpired(attacker.level(), rush)) {
            endRush(attacker, true);
            return;
        }
        if (attacker.getCooldowns().isOnCooldown(FunnyItems.BOWEN_BADGE)) {
            return;
        }
        if (rush == null) {
            rush = startSeeking(attacker);
        }
        tryPunch(attacker, rush);
    }

    private static void serverTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Rush> entry : ACTIVE.entrySet()) {
            ServerPlayer attacker = server.getPlayerList().getPlayer(entry.getKey());
            if (attacker == null) {
                // 使用者掉线：直接放人，否则目标会被 MOVE_BANED 永久锁在原地。
                ACTIVE.remove(entry.getKey(), entry.getValue());
                unbind(entry.getValue(), entry.getKey());
                continue;
            }
            tickRush(attacker, entry.getValue());
        }
    }

    private static void tickRush(ServerPlayer attacker, Rush rush) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker)) {
            endRush(attacker, false);
            return;
        }
        if (rush.targetUuid != null) {
            if (isExpired(attacker.level(), rush)) {
                endRush(attacker, true);
                return;
            }
            Player target = attacker.level().getPlayerByUUID(rush.targetUuid);
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                endRush(attacker, true);
                return;
            }
            restrainBoundTarget(attacker, target);
            if (attacker.level().getGameTime() % 5 == 0) {
                lockTargetItems(target);
            }
        }
        // 换手 / 重新拿起物品后补写镜像；值没变时 ItemStack 不会被判定为变化，不会多发包。
        syncMirror(attacker, rush);
    }

    private static Rush startSeeking(ServerPlayer attacker) {
        Rush rush = new Rush();
        ACTIVE.put(attacker.getUUID(), rush);
        attacker.displayClientMessage(
                Component.translatable("message.noellesroles.jojo.ora.seeking")
                        .withStyle(ChatFormatting.GOLD),
                true);
        syncMirror(attacker, rush);
        return rush;
    }

    private static void tryPunch(ServerPlayer attacker, Rush rush) {
        Player looked = findPunchTarget(attacker);
        if (rush.targetUuid == null) {
            if (looked == null || looked.getUUID().equals(attacker.getUUID())) {
                return;
            }
            bindTarget(attacker, looked, rush);
            applyPunch(attacker, looked, rush);
            return;
        }
        Player bound = attacker.level().getPlayerByUUID(rush.targetUuid);
        if (bound == null || !GameUtils.isPlayerAliveAndSurvival(bound)) {
            endRush(attacker, true);
            return;
        }
        if (looked == null || !looked.getUUID().equals(rush.targetUuid)) {
            return;
        }
        long now = attacker.level().getGameTime();
        if (now - rush.lastPunchGameTime < MIN_PUNCH_INTERVAL_TICKS) {
            return;
        }
        applyPunch(attacker, bound, rush);
    }

    private static void bindTarget(ServerPlayer attacker, Player target, Rush rush) {
        unbind(rush, attacker.getUUID());
        rush.targetUuid = target.getUUID();
        rush.punchCount = 0;
        rush.rushEndGameTime = attacker.level().getGameTime() + RUSH_TICKS;
        BOUND_TARGET_TO_ATTACKER.put(target.getUUID(), attacker.getUUID());
        lockTargetItems(target);
        restrainBoundTarget(attacker, target);
        attacker.displayClientMessage(
                Component.translatable("message.noellesroles.jojo.ora.bound", target.getName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                true);
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.displayClientMessage(
                    Component.translatable("message.noellesroles.jojo.ora.target_bound", attacker.getName())
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    true);
        }
        syncMirror(attacker, rush);
    }

    private static void applyPunch(ServerPlayer attacker, Player target, Rush rush) {
        rush.punchCount++;
        rush.lastPunchGameTime = attacker.level().getGameTime();
        InteractionHand hand = rush.nextOffhandSwing ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        attacker.swing(hand, true);
        rush.nextOffhandSwing = !rush.nextOffhandSwing;

        target.invulnerableTime = 0;
        target.hurt(attacker.damageSources().playerAttack(attacker), 1.0F);
        target.hurtMarked = true;
        spawnOraEffects(attacker, target);
        lockTargetItems(target);
        restrainBoundTarget(attacker, target);

        if (rush.punchCount >= PUNCHES_TO_KILL) {
            finishKill(attacker, target, rush);
            return;
        }
        attacker.displayClientMessage(
                Component.translatable(
                        "message.noellesroles.jojo.ora.punch",
                        rush.punchCount,
                        PUNCHES_TO_KILL,
                        remainingSeconds(attacker.level(), rush))
                        .withStyle(ChatFormatting.YELLOW),
                true);
        syncMirror(attacker, rush);
    }

    private static void finishKill(ServerPlayer attacker, Player target, Rush rush) {
        GameUtils.killPlayer(target, true, attacker, Noellesroles.id("bowen"));
        attacker.displayClientMessage(
                Component.translatable("message.noellesroles.jojo.ora.kill", target.getName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                true);
        // killPlayer 会触发 OnPlayerDeath → endRushIfInvolved，那边已经把表清掉了（不带冷却），
        // 这里补上击杀该有的完整冷却，并确保镜像被擦掉。
        applyItemCooldown(attacker, FULL_COOLDOWN_TICKS);
        ACTIVE.remove(attacker.getUUID(), rush);
        unbind(rush, attacker.getUUID());
        syncMirror(attacker, null);
    }

    /**
     * 结束某个使用者的连打。
     *
     * @param failCooldown 是否按「没打完」结算（一半冷却 + 超时提示）
     */
    public static void endRush(@Nullable Player attacker, boolean failCooldown) {
        if (attacker == null) {
            return;
        }
        Rush rush = ACTIVE.remove(attacker.getUUID());
        if (rush == null) {
            return;
        }
        unbind(rush, attacker.getUUID());
        if (failCooldown) {
            applyItemCooldown(attacker, FAIL_COOLDOWN_TICKS);
            attacker.displayClientMessage(
                    Component.translatable("message.noellesroles.jojo.ora.timeout")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
        syncMirror(attacker, null);
    }

    /** 死亡时：自己是使用者就收掉连打，自己是被绑目标就放开并结束绑定者的连打。 */
    private static void endRushIfInvolved(Player victim, boolean applyFailCooldown) {
        if (victim == null) {
            return;
        }
        endRush(victim, applyFailCooldown);
        UUID attackerId = BOUND_TARGET_TO_ATTACKER.remove(victim.getUUID());
        if (attackerId == null || victim.level() == null) {
            return;
        }
        endRush(victim.level().getPlayerByUUID(attackerId), applyFailCooldown);
    }

    private static void unbind(Rush rush, UUID attackerId) {
        if (rush.targetUuid != null) {
            BOUND_TARGET_TO_ATTACKER.remove(rush.targetUuid, attackerId);
        }
    }

    private static boolean isExpired(Level level, Rush rush) {
        return rush.targetUuid != null && rush.rushEndGameTime > 0 && level != null
                && level.getGameTime() >= rush.rushEndGameTime;
    }

    private static float remainingSeconds(Level level, Rush rush) {
        if (rush.rushEndGameTime <= 0 || level == null) {
            return 0.0F;
        }
        return Math.max(0.0F, (rush.rushEndGameTime - level.getGameTime()) / 20.0F);
    }

    // ==================== 客户端镜像 ====================

    /** 把服务端进度写到手上的欧拉一拳里；{@code rush} 为 null 表示擦除。 */
    private static void syncMirror(@Nullable Player attacker, @Nullable Rush rush) {
        if (attacker == null) {
            return;
        }
        OraRushState state = rush == null ? null
                : new OraRushState(attacker.getUUID(), rush.targetUuid != null, rush.punchCount,
                        rush.rushEndGameTime);
        writeMirror(attacker.getMainHandItem(), state);
        writeMirror(attacker.getOffhandItem(), state);
    }

    private static void writeMirror(ItemStack stack, @Nullable OraRushState state) {
        if (!stack.is(FunnyItems.BOWEN_BADGE)) {
            return;
        }
        if (state == null) {
            stack.remove(ModDataComponentTypes.ORA_RUSH);
        } else {
            stack.set(ModDataComponentTypes.ORA_RUSH, state);
        }
    }

    @Nullable
    private static OraRushState readMirror(ItemStack stack, UUID holder) {
        if (!stack.is(FunnyItems.BOWEN_BADGE)) {
            return null;
        }
        OraRushState state = stack.get(ModDataComponentTypes.ORA_RUSH);
        // 物品被丢掉再被别人捡走时快照还在，靠 owner 把它和当前持有者区分开。
        return state != null && holder.equals(state.owner()) ? state : null;
    }

    // ==================== 目标控制与表现 ====================

    private static void applyItemCooldown(Player player, int ticks) {
        if (player == null) {
            return;
        }
        player.getCooldowns().addCooldown(FunnyItems.BOWEN_BADGE, ticks);
    }

    private static void lockTargetItems(Player target) {
        ItemCooldowns cooldowns = target.getCooldowns();
        applyCooldownIfPresent(cooldowns, target.getMainHandItem(), 10);
        applyCooldownIfPresent(cooldowns, target.getOffhandItem(), 10);
        for (ItemStack stack : target.getInventory().items) {
            applyCooldownIfPresent(cooldowns, stack, 10);
        }
    }

    private static void applyCooldownIfPresent(ItemCooldowns cooldowns, ItemStack stack, int ticks) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        cooldowns.addCooldown(stack.getItem(), ticks);
    }

    /** 禁止移动，并把目标钉在使用者水平正前方。 */
    private static void restrainBoundTarget(Player attacker, Player target) {
        if (attacker == null || target == null) {
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, false, false, false));
        pinTargetInFront(attacker, target);
    }

    private static void pinTargetInFront(Player attacker, Player target) {
        Vec3 look = attacker.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0e-6) {
            flat = new Vec3(0.0, 0.0, 1.0);
        } else {
            flat = flat.normalize();
        }
        Vec3 desired = attacker.position().add(flat.scale(HOLD_FRONT_DISTANCE));
        // connection.teleport 是无视碰撞的硬传送：若直接送到正前方，承太郎贴墙站立时
        // 那个点会落在方块内 / 墙的另一侧，被抱者就会穿墙或卡在方块里。
        // 先把期望点沿「当前位置 → 期望点」做碰撞收敛，让被抱者贴着障碍停下。
        Vec3 front = resolveHoldPosition(target, attacker.level(), desired);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0f;
        target.hurtMarked = true;
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.connection.teleport(front.x, front.y, front.z,
                    serverTarget.getYRot(), serverTarget.getXRot());
        } else {
            target.teleportTo(front.x, front.y, front.z);
        }
    }

    /**
     * 把「抱住位置」收敛到目标碰撞箱不与方块碰撞箱重叠的地方。
     *
     * <p>{@link net.minecraft.server.network.ServerGamePacketListenerImpl#teleport}（即
     * {@code connection.teleport}）不做任何碰撞检测，直接改写坐标，因此被抱者可能被塞进
     * 实心方块内部。这里用 {@link Level#noCollision} 沿「当前位置 → 期望点」线性采样：
     * 遇到第一个进入障碍的点就停在其前一个位置，于是被抱者会贴着方块停下，既不穿越方块、
     * 也不会卡进方块里；沿途无障碍时结果就是期望点本身，正常跟随行为不变。
     *
     * @param target  被抱住的目标
     * @param level   世界
     * @param desired 期望抱住位置
     * @return 实际应传送到的（无碰撞）位置
     */
    private static Vec3 resolveHoldPosition(Entity target, Level level, Vec3 desired) {
        Vec3 current = target.position();
        Vec3 delta = desired.subtract(current);
        if (delta.lengthSqr() < 1.0e-8) {
            return current;
        }
        AABB base = target.getBoundingBox();
        final int STEPS = 8;
        Vec3 safe = current;
        for (int i = 1; i <= STEPS; i++) {
            Vec3 step = current.add(delta.scale(i / (double) STEPS));
            if (!level.noCollision(target, base.move(step.subtract(current)))) {
                // 第一个进入障碍的采样点：停在这里之前的位置，绝不硬塞进方块
                break;
            }
            safe = step;
        }
        return safe;
    }

    @Nullable
    private static Player findPunchTarget(Player attacker) {
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(PUNCH_REACH));
        AABB search = attacker.getBoundingBox().expandTowards(look.scale(PUNCH_REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                attacker, eye, end, search,
                entity -> entity instanceof Player p
                        && !p.isSpectator()
                        && p.isPickable()
                        && GameUtils.isPlayerAliveAndSurvival(p)
                        && !p.getUUID().equals(attacker.getUUID()),
                PUNCH_REACH * PUNCH_REACH);
        if (hit == null) {
            return null;
        }
        BlockHitResult blockHit = attacker.level().clip(new ClipContext(
                eye, hit.getLocation(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        if (blockHit.getType() != HitResult.Type.MISS
                && blockHit.getLocation().distanceToSqr(eye) + 0.05 < hit.getLocation().distanceToSqr(eye)) {
            return null;
        }
        Entity entity = hit.getEntity();
        return entity instanceof Player player ? player : null;
    }

    private static void spawnOraEffects(ServerPlayer attacker, Player target) {
        Level level = attacker.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.55;
        double z = target.getZ();
        serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z, 8, 0.25, 0.35, 0.25, 0.35);
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 6, 0.2, 0.3, 0.2, 0.25);
        serverLevel.sendParticles(GOLD_DUST, x, y, z, 10, 0.3, 0.4, 0.3, 0.02);
        serverLevel.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        float pitch = 0.85f + attacker.getRandom().nextFloat() * 0.5f;
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, pitch);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.55F,
                1.1f + attacker.getRandom().nextFloat() * 0.3f);
    }
}
