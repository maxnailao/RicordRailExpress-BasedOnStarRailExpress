package org.agmas.noellesroles.game.roles.neutral.prewitch;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 预备魔女 / 魔女的技能派发器。
 *
 * <p>
 * 两态共用一个技能按钮（预备魔女为 {@link #PRE_WITCH_SKILL_ID}，魔女为 {@link #MAJO_SKILL_ID}），
 * 具体释放哪一个技能由组件里开局随机到的 {@link PreWitchPlayerComponent.GrantedSkill} 决定。
 *
 * <p>
 * 因为同一个按钮要对应多个冷却各不相同的技能，这里不使用技能定义自带的冷却，
 * 而是在释放成功后把真实冷却写回组件的 {@code skillCooldownTicks}，
 * 由组件在下一 tick 同步到技能状态上（技能定义冷却写 0）。
 *
 * <p>
 * 冷却规则：预备魔女形态下所有技能统一 30 秒；转化为魔女后，禁锢与破法者 60 秒，
 * 其余技能（召回者 / 时空旅者 / 死亡回溯）45 秒。魔女释放技能不消耗金币。
 */
public final class PreWitchSkillDispatcher {

    /** 预备魔女的技能按钮 id */
    public static final ResourceLocation PRE_WITCH_SKILL_ID = Noellesroles.id("prewitch_ability");
    /** 魔女的技能按钮 id */
    public static final ResourceLocation MAJO_SKILL_ID = Noellesroles.id("majo_ability");

    // ==================== 各形态冷却（tick） ====================
    /** 预备魔女：所有技能统一 30 秒冷却 */
    private static final int CD_PRE_WITCH = 30 * 20;
    /** 魔女：禁锢 / 破法者（控制类技能）60 秒 */
    private static final int CD_MAJO_CONTROL = 60 * 20;
    /** 魔女：其余技能（召回者 / 时空旅者 / 死亡回溯）45 秒 */
    private static final int CD_MAJO_OTHER = 45 * 20;

    /** 净化者的射程（格） */
    private static final double PURIFY_RANGE = 6.0D;
    /** 净化者消耗的金币 */
    private static final int PURIFY_COST = 75;
    /** 召回者传送消耗的金币 */
    private static final int RECALLER_COST = 100;
    /** 时空旅者放置传送门消耗的金币 */
    private static final int RUIKE_COST = 125;

    /** 明星技能范围（格） */
    private static final double STAR_APPEAL_RANGE = 15.0D;
    /** 明星发光时长（tick） */
    private static final int STAR_GLOW_TICKS = 40;

    /** 禁锢的搜索半径（格） */
    private static final double LOCKDOWN_RANGE = 3.0D;
    /** 禁锢时长（5 秒） */
    private static final int LOCKDOWN_TICKS = 5 * 20;

    private PreWitchSkillDispatcher() {
    }

    /**
     * 释放当前随机到的技能。
     *
     * @param player      施法者
     * @param target      客户端准星目标（可能为 null）
     * @param transformed 是否已经转化为魔女
     * @return 是否成功释放（成功时冷却已经写入组件）
     */
    public static boolean use(ServerPlayer player, @Nullable UUID target, boolean transformed) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        PreWitchPlayerComponent component = PreWitchPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (component == null) {
            return false;
        }

        // 组件若是被延迟创建（例如中途改职），这里补一次随机，避免出现「没有技能」
        component.ensureSkillRolled();

        // 魔女形态释放技能不消耗金币
        boolean free = transformed;
        int cooldown = switch (component.getGrantedSkill()) {
            case RECALLER -> useRecaller(player, free);
            case RUIKE -> useRuike(player, level, free);
            case JINGHUAZHE -> transformed ? useSpellbreaker(player, level) : usePurify(player, target);
            case SUPERSTAR -> transformed ? useLockdown(player, level) : useStarAppeal(player, level);
            case REWIND -> hintRewind(player);
        };
        if (cooldown < 0) {
            return false;
        }
        // 形态统一冷却：预备魔女固定 30 秒；魔女按技能 45 秒（禁锢 / 破法者 60 秒）
        component.setSkillCooldownTicks(transformed ? cooldown : CD_PRE_WITCH);
        return true;
    }

    /** 该玩家当前形态对应的技能按钮 id */
    public static ResourceLocation skillIdOf(boolean transformed) {
        return transformed ? MAJO_SKILL_ID : PRE_WITCH_SKILL_ID;
    }

    /** 把技能状态上的冷却同步进组件（改职后按钮 id 变化，需要迁移剩余冷却） */
    public static void syncCooldownFromAbility(ServerPlayer player, boolean transformed) {
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        PreWitchPlayerComponent.KEY.maybeGet(player).ifPresent(component -> {
            int remaining = ability.getSkillState(skillIdOf(transformed)).cooldown;
            if (remaining > 0) {
                component.setSkillCooldownTicks(remaining);
            }
        });
    }

    // ==================== 召回者 ====================

    /**
     * 召回者：第一次按键放置标记，第二次按键花 100 金币回到标记处。
     */
    private static int useRecaller(ServerPlayer player, boolean free) {
        RecallerPlayerComponent recaller = RecallerPlayerComponent.KEY.get(player);
        if (!recaller.placed) {
            recaller.setPosition();
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.prewitch.recaller_marked")
                            .withStyle(ChatFormatting.AQUA),
                    true);
            return CD_MAJO_OTHER;
        }
        if (!free) {
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop.balance < RECALLER_COST) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.insufficient_funds")
                                .withStyle(ChatFormatting.RED),
                        true);
                return -1;
            }
            shop.addToBalance(-RECALLER_COST);
        }
        recaller.teleport();
        player.displayClientMessage(
                Component.translatable("message.noellesroles.prewitch.recaller_recalled")
                        .withStyle(ChatFormatting.AQUA),
                true);
        return CD_MAJO_OTHER;
    }

    // ==================== 时空旅者 ====================

    /**
     * 时空旅者：花 125 金币在原地放置传送门，最多同时存在两个并自动配对。
     */
    private static int useRuike(ServerPlayer player, ServerLevel level, boolean free) {
        if (!free) {
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop.balance < RUIKE_COST) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.insufficient_funds")
                                .withStyle(ChatFormatting.RED),
                        true);
                return -1;
            }
            shop.addToBalance(-RUIKE_COST);
        }
        // 组件可能尚未创建（非时空旅者职业也会用到这个技能），按需创建
        org.agmas.noellesroles.game.roles.innocence.ruike.RuikePlayerComponent component = ModComponents.RUIKE
                .get(player);

        // 已有两个传送门时先销毁最早放置的那个
        if (component.getPortalCount() >= 2) {
            UUID oldestUuid = component.portalUuids.get(0);
            var oldestEntity = level.getEntity(oldestUuid);
            if (oldestEntity instanceof org.agmas.noellesroles.content.entity.RuikePortalEntity oldPortal) {
                oldPortal.discard();
            } else {
                component.removePortal(oldestUuid);
            }
        }

        var portal = new org.agmas.noellesroles.content.entity.RuikePortalEntity(
                org.agmas.noellesroles.init.ModEntities.RUIKE_PORTAL, level);
        portal.setPos(player.getX(), player.getY(), player.getZ());
        portal.setYRot(player.getYRot());
        portal.setOwnerUUID(player.getUUID());
        level.addFreshEntity(portal);

        // 场上有另一个传送门时双向配对
        if (component.getPortalCount() == 1) {
            UUID existingPortalUuid = component.portalUuids.get(0);
            var existingPortal = level.getEntity(existingPortalUuid);
            if (existingPortal instanceof org.agmas.noellesroles.content.entity.RuikePortalEntity existing) {
                existing.setPairUUID(portal.getUUID());
                portal.setPairUUID(existing.getUUID());
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.ruike.portal_paired")
                                .withStyle(ChatFormatting.AQUA),
                        true);
            }
        } else {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.ruike.portal_placed_first")
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        }

        component.addPortal(portal.getUUID());
        level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.0F);
        return CD_MAJO_OTHER;
    }

    // ==================== 净化者 ====================

    /**
     * 净化者：花 75 金币清空准星对准的玩家（6 格内）身上的所有效果。
     */
    private static int usePurify(ServerPlayer player, @Nullable UUID targetUuid) {
        Player target = targetUuid == null ? null : player.level().getPlayerByUUID(targetUuid);
        if (target == null || target == player || !GameUtils.isPlayerAliveAndSurvival(target)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.jinghuazhe.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return -1;
        }
        if (player.distanceTo(target) > PURIFY_RANGE) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.jinghuazhe.too_far")
                            .withStyle(ChatFormatting.RED),
                    true);
            return -1;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < PURIFY_COST) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds")
                            .withStyle(ChatFormatting.RED),
                    true);
            return -1;
        }
        shop.addToBalance(-PURIFY_COST);
        target.removeAllEffects();
        player.displayClientMessage(
                Component.translatable("message.noellesroles.jinghuazhe.purified", target.getName().getString())
                        .withStyle(ChatFormatting.AQUA),
                true);
        target.displayClientMessage(
                Component.translatable("message.noellesroles.jinghuazhe.been_purified")
                        .withStyle(ChatFormatting.AQUA),
                true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                1.0F, 1.5F);
        return CD_MAJO_OTHER;
    }

    // ==================== 破法者（净化者转化而来） ====================

    /**
     * 破法者：封禁周围 50 格内所有非杀手阵营玩家的技能与物品栏，持续 30 秒。
     */
    private static int useSpellbreaker(ServerPlayer player, ServerLevel level) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRunning()) {
            return -1;
        }
        int affected = 0;
        AABB area = player.getBoundingBox().inflate(SpellbreakerPlayerComponent.ABILITY_RADIUS);
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, area,
                p -> p != player && GameUtils.isPlayerAliveAndSurvival(p))) {
            if (target.distanceToSqr(player) > SpellbreakerPlayerComponent.ABILITY_RADIUS
                    * SpellbreakerPlayerComponent.ABILITY_RADIUS
                    || !SpellbreakerPlayerComponent.isNonKiller(target, gameWorld)) {
                continue;
            }
            int duration = SpellbreakerPlayerComponent.ABILITY_DURATION;
            target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, duration, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, duration, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.DREAMCORE_FILTER, duration, 0, false, false, false));
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.spellbreaker.area_silenced")
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
            affected++;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F,
                0.7F);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.spellbreaker.ability_used", affected)
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);
        return CD_MAJO_CONTROL;
    }

    // ==================== 明星 ====================

    /**
     * 明星：让 15 格内的玩家全部看向自己，自己发光 2 秒，并按人数获得金币。
     */
    private static int useStarAppeal(ServerPlayer player, ServerLevel level) {
        int affected = 0;
        for (Player target : level.players()) {
            if (target == player || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (target.distanceToSqr(player) > STAR_APPEAL_RANGE * STAR_APPEAL_RANGE) {
                continue;
            }
            if (target instanceof ServerPlayer serverTarget) {
                double dx = player.getX() - target.getX();
                double dy = (player.getY() + player.getEyeHeight(player.getPose()))
                        - (target.getY() + target.getEyeHeight(target.getPose()));
                double dz = player.getZ() - target.getZ();
                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
                serverTarget.connection.teleport(target.getX(), target.getY(), target.getZ(), yaw, pitch);
                serverTarget.displayClientMessage(
                        Component.translatable("message.noellesroles.star.attracted")
                                .withStyle(ChatFormatting.GOLD),
                        true);
                affected++;
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, STAR_GLOW_TICKS + 5, 0, false, false, true));

        int reward = Math.min(affected * 10, 150);
        if (reward > 0) {
            SREPlayerShopComponent.KEY.get(player).addToBalance(reward);
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.star.ability_used", affected)
                        .withStyle(ChatFormatting.GOLD),
                true);
        return CD_MAJO_OTHER;
    }

    // ==================== 禁锢（明星转化而来） ====================

    /**
     * 禁锢：让 3 格内最近的玩家 5 秒内无法移动。
     */
    private static int useLockdown(ServerPlayer player, ServerLevel level) {
        ServerPlayer nearest = null;
        double nearestDistance = LOCKDOWN_RANGE * LOCKDOWN_RANGE;
        for (ServerPlayer target : level.players()) {
            if (target == player || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            double distance = target.distanceToSqr(player);
            if (distance > nearestDistance) {
                continue;
            }
            nearestDistance = distance;
            nearest = target;
        }
        if (nearest == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.prewitch.lockdown_no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return -1;
        }
        nearest.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, LOCKDOWN_TICKS, 0, false, false, true));
        nearest.displayClientMessage(
                Component.translatable("message.noellesroles.prewitch.lockdown_victim")
                        .withStyle(ChatFormatting.DARK_RED),
                true);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.prewitch.lockdown_used", nearest.getName().getString())
                        .withStyle(ChatFormatting.RED),
                true);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F,
                0.6F);
        return CD_MAJO_CONTROL;
    }

    // ==================== 死亡回溯 ====================

    /**
     * 死亡回溯是被动，按键只提示效果。
     */
    private static int hintRewind(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.noellesroles.prewitch.rewind_hint")
                        .withStyle(ChatFormatting.AQUA),
                true);
        return CD_MAJO_OTHER;
    }
}
