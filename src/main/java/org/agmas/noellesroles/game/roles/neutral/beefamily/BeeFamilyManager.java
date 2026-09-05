package org.agmas.noellesroles.game.roles.neutral.beefamily;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * 蜜蜂家族（蜂后 / 马蜂 / 工蜂）的服务端总管：事件接线、技能定义、胜负判定与全灭还原。
 */
public class BeeFamilyManager {

    /** 蜂后【召唤增强】的价格：下次右键尸体复活出的将是马蜂而非工蜂。 */
    public static final int BEE_QUEEN_IMPROVE_PRICE = 150;
    /** 蜂后【选择继承者】的价格。 */
    public static final int BEE_QUEEN_SELECT_QUEEN_PRICE = 300;
    /** 毒针施加的中毒时长。 */
    public static final int BEE_POISON_TICKS = 20 * 20;
    /** 蜂后右键尸体复活的金币花费。 */
    public static final int REVIVE_COST_MONEY = 75;
    /** 蜂后复活尸体的冷却。 */
    public static final int REVIVE_COOLDOWN = 30 * 20;
    /** 家族成员击杀一人后，给场上存活蜂后的金币奖励。 */
    public static final int KILL_AWARD_TO_QUEEN = 50;
    /** 工蜂的存活上限，超时即被处决。 */
    public static final int BEE_WORKER_DEATH_TIMEOUT_TICKS = 120 * 20;
    /** 【选择继承者】射线检测尸体的距离。 */
    public static final double MARK_RAYCAST_DISTANCE = 5.0;

    /**
     * 领袖已招募蜂后时置为 true：场上所有蜜蜂家族职业释放技能后，
     * 中毒致死时间减半。每局开始时由 {@link #resetQueenLeaderBonus()} 复位。
     *
     * <p>
     * 外层暂无「领袖-追随者」联动系统，因此目前没有调用 {@link #setQueenLeaderBonus(boolean)}
     * 的地方；保留开关以便该系统移植后直接接入。
     */
    public static boolean QUEEN_LEADER_BONUS = false;
    public static boolean pendingCheck = false;

    public static void setQueenLeaderBonus(boolean value) {
        QUEEN_LEADER_BONUS = value;
    }

    public static void resetQueenLeaderBonus() {
        QUEEN_LEADER_BONUS = false;
    }

    public static void registerEvents() {
        // 蜜蜂家族成员之间互不击杀
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var cca = SREGameWorldComponent.getInstance(victim);
            if (cca.getRole(victim) instanceof BeeFamilyRole && cca.getRole(killer) instanceof BeeFamilyRole) {
                return false;
            }
            return true;
        });

        ServerTickEvents.END_WORLD_TICK.register(BeeFamilyManager::tick);

        // 蜜蜂频道
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
            if (gameWorldComponent.getRole(serverPlayer) instanceof BeeFamilyRole role) {
                if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(serverPlayer)) {
                    return true;
                }
                var data = BeeFamilyComponent.getNullable(serverPlayer);
                if (data == null) {
                    return true;
                }
                if (data.beeChannel) { // bee频道
                    var broadcastMessage = Component
                            .translatable("message.bee_family.broadcast_prefix",
                                    Component.literal("(")
                                            .append(RoleUtils.getRoleOrModifierNameWithColor(role))
                                            .append(")")
                                            .withStyle(ChatFormatting.YELLOW),
                                    Component.literal("").append(serverPlayer.getDisplayName())
                                            .withStyle(ChatFormatting.AQUA),
                                    Component.literal(message.signedContent()).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.GOLD);
                    serverPlayer.getServer().getPlayerList().getPlayers().forEach((p) -> {
                        var prole = gameWorldComponent.getRole(p);
                        if (prole == null) {
                            return;
                        }
                        // 已淘汰的玩家也能看到蜜蜂频道，方便观战时理解局势
                        if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                            p.displayClientMessage(broadcastMessage, false);
                        }
                        if (!(prole instanceof BeeFamilyRole)) {
                            return;
                        }
                        BroadcastCommand.BroadcastMessage(p, broadcastMessage);
                        p.displayClientMessage(broadcastMessage, false);
                    });
                    return false;
                }
            }
            return true;
        });

        RoleSkill.register(BounsRoles.BEE_WORKER,
                RoleSkill.skill(SRE.id("bee_family_poison"), "skill.noellesroles.bee_family_poison",
                        (ctx) -> triggerSkill(ctx, true))
                        .cooldownSeconds(60).showOnHud(true).announceToSelf(true).build(),
                RoleSkill.skill(SRE.id("bee_channel"), "skill.noellesroles.bee_channel",
                        BeeFamilyManager::changeChannel)
                        .showOnHud(true)
                        .cooldownTicks(1)
                        .toggleable(true)
                        .announceToSelf(false)
                        .build());
        RoleSkill.register(BounsRoles.BEE_WASP,
                RoleSkill.skill(SRE.id("bee_family_poison"), "skill.noellesroles.bee_family_poison",
                        (ctx) -> triggerSkill(ctx, false))
                        .showOnHud(true).cooldownSeconds(30).announceToSelf(true).build(),
                RoleSkill.skill(SRE.id("bee_channel"), "skill.noellesroles.bee_channel",
                        BeeFamilyManager::changeChannel)
                        .cooldownTicks(1)
                        .showOnHud(true)
                        .toggleable(true)
                        .announceToSelf(false)
                        .build());
        RoleSkill.register(BounsRoles.BEE_QUEEN,
                RoleSkill.skill(SRE.id("bee_queen/improve"), "skill.noellesroles.bee_queen.improve",
                        (ctx) -> improveNextSummon(ctx))
                        .showOnHud(true).cooldownSeconds(60).announceToSelf(true).build(),
                RoleSkill.skill(SRE.id("bee_queen/mark"), "skill.noellesroles.bee_queen.mark",
                        (ctx) -> markSuccessor(ctx))
                        .showOnHud(true)
                        .cooldownSeconds(60)
                        .announceToSelf(true)
                        .build(),
                RoleSkill.skill(SRE.id("bee_channel"), "skill.noellesroles.bee_channel",
                        BeeFamilyManager::changeChannel)
                        .cooldownTicks(1)
                        .showOnHud(true)
                        .toggleable(true)
                        .announceToSelf(false)
                        .build());

        // 蜂后右键尸体：花费金币把死者复活为工蜂（或增强后的马蜂）
        UseEntityCallback.EVENT.register(((player, level, interactionHand, entity, entityHitResult) -> {
            if (!(player instanceof ServerPlayer interacting)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(interacting)) {
                return InteractionResult.PASS;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, BounsRoles.BEE_QUEEN)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof PlayerBodyEntity body)) {
                return InteractionResult.PASS;
            }
            // 检查是否是葬仪伪造的尸体，不能复活伪造的尸体
            if (PlayerBodyEntityComponent.KEY.get(body).isFakeBody) {
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.necromancer.cannot_revive_fake_body")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            if (!gameWorldComponent.isSkillAvailable) {
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            var serverLevel = (ServerLevel) level;

            var revived = serverLevel.getPlayerByUUID(body.getPlayerUuid());
            if (!(revived instanceof ServerPlayer revivedPlayer) || !revivedPlayer.isSpectator()) {
                return InteractionResult.PASS;
            }
            // activate cooldown
            SREAbilityPlayerComponent cca = SREAbilityPlayerComponent.KEY.get(player);
            if (cca.hasCooldown()) {
                return InteractionResult.PASS;
            }
            if (!hasBalance(interacting, REVIVE_COST_MONEY)) {
                player.displayClientMessage(
                        Component.translatable("hud.noellesroles.bee_family.money", REVIVE_COST_MONEY)
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.PASS;
            }
            addToBalance(interacting, -REVIVE_COST_MONEY);
            cca.setCooldown(REVIVE_COOLDOWN);

            SRERole reviveRole = BounsRoles.BEE_WORKER;
            if (cca.status == 1) {
                reviveRole = BounsRoles.BEE_WASP;
                cca.status = 0;
            }

            final SRERole selectedRole = reviveRole;
            serverLevel.players().forEach(a -> {
                a.playNotifySound(SoundEvents.BEE_LOOP_AGGRESSIVE, revivedPlayer.getSoundSource(), 1.2f, 1f);
                if (gameWorldComponent.getRole(a) instanceof BeeFamilyRole) {
                    a.displayClientMessage(Component.translatable("hud.noellesroles.bee.revived_player",
                            RoleUtils.getRoleOrModifierNameWithColor(BounsRoles.BEE_QUEEN),
                            RoleUtils.getRoleOrModifierNameWithColor(selectedRole))
                            .withStyle(ChatFormatting.GOLD), true);
                }
            });
            revivedPlayer.getInventory().clearContent();
            final SRERole beforeRole = gameWorldComponent.getRole(revivedPlayer);
            RoleUtils.changeRole(revivedPlayer, selectedRole);
            GameUtils.revivePlayer(revivedPlayer, body.getX(), body.getY(), body.getZ());
            body.discard(); // like it never existed

            RoleUtils.sendWelcomeAnnouncement(revivedPlayer);
            // 同样必须写在 changeRole 之后，否则会被组件的 init() 清掉
            if (!(beforeRole instanceof BeeFamilyRole)) {
                rememberBeforeRole(revivedPlayer, beforeRole);
            }
            ConfigWorldComponent.onPlayerUsedSkill(interacting);
            return InteractionResult.CONSUME;
        }));

        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (RoleUtils.getPlayerRole(player) instanceof BeeFamilyRole) {
                BeeFamilyRole.onDeath(player, killer, deathReason);
            }
        });
        // 家族成员击杀一人后，给场上存活的蜂后发奖金
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (killer == null) {
                return;
            }
            var worldcca = SREGameWorldComponent.getInstance(player);
            if (!(worldcca.getRole(killer) instanceof BeeFamilyRole)) {
                return;
            }
            for (final var p : player.level().players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                    continue;
                }
                if (worldcca.isRole(p, BounsRoles.BEE_QUEEN)) {
                    SREPlayerShopComponent.KEY.get(p).addToBalance(KILL_AWARD_TO_QUEEN);
                }
            }
        });

        // 蜜蜂家族独立胜利的归属判定：CustomWinnerID 为 "bee_family" 时，
        // 所有仍持有蜜蜂家族职业的玩家都算赢家（含已死亡但未被还原的成员）。
        GameUtils.CustomWinnersPredicates.add(entry -> {
            if (entry.getValue() != null && entry.getValue().equals("bee_family")) {
                return entry.getKey() instanceof ServerPlayer sp
                        && RoleUtils.getPlayerRole(sp) instanceof BeeFamilyRole;
            }
            return false;
        });
    }

    private static boolean changeChannel(RoleSkillContext ctx) {
        var data = BeeFamilyComponent.getNullable(ctx.player());
        if (data == null) {
            return false;
        }
        data.turnChannel();
        return true;
    }

    /** 蜂后【召唤增强】：花金币把下一次复活的目标从工蜂升级为马蜂。 */
    private static boolean improveNextSummon(RoleSkillContext ctx) {
        final var player = ctx.player();
        if (!hasBalance(player, BEE_QUEEN_IMPROVE_PRICE)) {
            player.displayClientMessage(Component.translatable("skill.noellesroles.bee_queen.no_money",
                    BEE_QUEEN_IMPROVE_PRICE).withStyle(ChatFormatting.RED), true);
            return false;
        }
        final var cca = SREAbilityPlayerComponent.KEY.get(player);
        if (cca.status >= 1) {
            player.displayClientMessage(Component.translatable("skill.noellesroles.bee_queen.already")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        addToBalance(player, -BEE_QUEEN_IMPROVE_PRICE);
        cca.status = 1;
        ConfigWorldComponent.onPlayerUsedSkill(player);
        return true;
    }

    /**
     * 蜂后【选择继承者】：对准一名已死亡玩家的尸体，花金币标记它。
     * 蜂后死亡时该玩家会复活并接任蜂后，同时继承蜂后的金币。
     */
    private static boolean markSuccessor(RoleSkillContext ctx) {
        final var player = ctx.player();
        if (!hasBalance(player, BEE_QUEEN_SELECT_QUEEN_PRICE)) {
            player.displayClientMessage(
                    Component.translatable("skill.noellesroles.bee_queen.select.no_money",
                            BEE_QUEEN_SELECT_QUEEN_PRICE).withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        final var data = BeeFamilyComponent.getNullable(player);
        if (data == null) {
            return false;
        }
        if (data.markTarget != null && player.level().getPlayerByUUID(data.markTarget) != null) {
            player.displayClientMessage(
                    Component.translatable("skill.noellesroles.bee_queen.select.already")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        // 外层统一技能的 target 由客户端射线提供，但客户端只会挑玩家实体，
        // 挑不到尸体，所以这里在服务端自己对着视线方向找 PlayerBodyEntity。
        PlayerBodyEntity be = findLookedAtBody(player);
        if (be == null || be.getPlayerUuid() == null) {
            noTarget(player);
            return false;
        }
        if (!(player.level().getPlayerByUUID(be.getPlayerUuid()) instanceof ServerPlayer marktargetplayer)) {
            noTarget(player);
            return false;
        }
        if (GameUtils.isPlayerAliveAndSurvival(marktargetplayer)) {
            noTarget(player);
            return false;
        }
        addToBalance(player, -BEE_QUEEN_SELECT_QUEEN_PRICE);
        data.markSuccessor(marktargetplayer.getUUID());
        ConfigWorldComponent.onPlayerUsedSkill(player);
        return true;
    }

    @Nullable
    private static PlayerBodyEntity findLookedAtBody(ServerPlayer player) {
        HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(
                player,
                entity -> entity instanceof PlayerBodyEntity,
                MARK_RAYCAST_DISTANCE);
        if (hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerBodyEntity body) {
            return body;
        }
        return null;
    }

    private static void noTarget(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("tip.noellesroles.no_target").withStyle(ChatFormatting.RED), true);
    }

    public static boolean checkBeeFamilyVictory(ServerLevel world) {
        int alive = 0, beeAlive = 0;
        var gameComponent = SREGameWorldComponent.getInstance(world);
        for (ServerPlayer p : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            alive++;
            if (gameComponent.getRole(p) instanceof BeeFamilyRole) {
                beeAlive++;
            }
        }
        if (beeAlive > 0 && alive == beeAlive) {
            RoleUtils.customWinnerWin(world, WinStatus.CUSTOM, "bee_family",
                    OptionalInt.of(BounsRoles.BEE_QUEEN.color()));
            return true;
        }
        return false;
    }

    /**
     * 毒针：给目标上中毒，若目标已中毒则直接毒发身亡。
     *
     * @param willDeathAfterSkill 工蜂为 true —— 刺出这一针后自己也会死（一次性士兵）
     */
    public static boolean triggerSkill(RoleSkillContext ctx, boolean willDeathAfterSkill) {
        final var player = ctx.player();
        if (ctx.target() == null) {
            noTarget(player);
            return false;
        }
        if (!(player.level().getPlayerByUUID(ctx.target()) instanceof ServerPlayer target)) {
            noTarget(player);
            return false;
        }
        if (RoleUtils.getPlayerRole(target) instanceof BeeFamilyRole) {
            noTarget(player);
            return false;
        }
        int poisonTicks = QUEEN_LEADER_BONUS ? BEE_POISON_TICKS / 2 : BEE_POISON_TICKS;
        var ppc = SREPlayerPoisonComponent.KEY.get(target);
        if (ppc.poisonTicks > 0) {
            GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.POISON);
        } else {
            ppc.setPoisonTicks(poisonTicks, player.getUUID());
        }
        if (willDeathAfterSkill) {
            GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.BEE_USED_OUT_SKILL);
        }
        return true;
    }

    /** 场上还有存活的蜜蜂家族成员时，阻止常规的杀手 / 乘客结算。 */
    public static boolean shouldPreventGameEnd(ServerLevel serverLevel) {
        var cca = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer p : serverLevel.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p) && cca.getRole(p) instanceof BeeFamilyRole) {
                return true;
            }
        }
        return false;
    }

    /** 检查蜜蜂家族是否全体死亡。如果是恢复死者原本职业。 */
    public static void checkBeeFamilyFailure(ServerLevel serverLevel) {
        final var gamecca = SREGameWorldComponent.KEY.get(serverLevel);
        int aliveBees = 0;
        for (var player : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (gamecca.getRole(player) instanceof BeeFamilyRole) {
                aliveBees++;
            }
        }
        if (aliveBees <= 0) {
            beeFamilyFailed(serverLevel);
        }
    }

    public static void tick(ServerLevel world) {
        if (pendingCheck) {
            pendingCheck = false;
            checkBeeFamilyFailure(world);
        }
    }

    public static void beeFamilyFailed(ServerLevel serverLevel) {
        SRE.LOGGER.info("Bee family failed! Try restore roles.");
        final var gamecca = SREGameWorldComponent.KEY.get(serverLevel);
        for (var player : serverLevel.players()) {
            if (!(gamecca.getRole(player) instanceof BeeFamilyRole)) {
                continue;
            }
            var data = BeeFamilyComponent.getNullable(player);
            if (data == null || data.beforeRole == null || data.beforeRole instanceof BeeFamilyRole) {
                continue;
            }
            SRERole beforeRole = data.beforeRole;
            data.beforeRole = null;
            // record=true 写入回放；addStats=false 避免把还原误计为「又玩了一局原职业」；
            // noEventCall=true 避免重复发放初始物品与重播入场报幕
            RoleUtils.changeRole(player, beforeRole, true, false, false, true);
        }
    }

    public static void pendingCheckFailure() {
        pendingCheck = true;
    }

    public static void reset() {
        pendingCheck = false;
    }

    /**
     * 记录玩家转职为蜜蜂家族之前的职业。
     * 必须在其 {@code changeRole} 已经返回之后调用：改职会触发组件 {@code init()} 清空该字段。
     */
    private static void rememberBeforeRole(Player player, @Nullable SRERole beforeRole) {
        var data = BeeFamilyComponent.getNullable(player);
        if (data != null) {
            data.beforeRole = beforeRole;
        }
    }

    private static boolean hasBalance(Player player, int amount) {
        return SREPlayerShopComponent.KEY.get(player).balance >= amount;
    }

    private static void addToBalance(Player player, int amount) {
        SREPlayerShopComponent.KEY.get(player).addToBalance(amount);
    }

    /** 供外部（如领袖系统）查询某 UUID 是否属于蜜蜂家族。 */
    public static boolean isBeeFamily(ServerLevel level, UUID uuid) {
        return SREGameWorldComponent.KEY.get(level).isRole(uuid, BounsRoles.BEE_QUEEN)
                || SREGameWorldComponent.KEY.get(level).isRole(uuid, BounsRoles.BEE_WASP)
                || SREGameWorldComponent.KEY.get(level).isRole(uuid, BounsRoles.BEE_WORKER);
    }
}
