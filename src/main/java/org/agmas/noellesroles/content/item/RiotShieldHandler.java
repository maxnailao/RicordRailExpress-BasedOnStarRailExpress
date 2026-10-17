package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.DreamRoleData;

public class RiotShieldHandler {
    public static void register() {
        AllowPlayerDeathWithKiller.EVENT.register(RiotShieldHandler::allowDeath);
    }

    /**
     * 可格挡的死亡原因白名单。
     *
     * <p>走原版攻击路线的武器（见 {@link #isVanillaRouteDeathReason(ResourceLocation)}）也计入白名单，
     * 但它们平时不会进入死亡管线，<b>必须在各自的伤害结算处调用
     * {@link #tryBlockAttack(Player, Player)}</b> 才能逐次格挡；白名单只兜住
     * 「虚拟血量归零的致死一击」。
     */
    public static boolean isBlockableDeathReason(ResourceLocation deathReason) {
        if (deathReason == null)
            return false;
        return deathReason.equals(GameConstants.DeathReasons.REVOLVER)
                || deathReason.equals(GameConstants.DeathReasons.ARROW)
                || deathReason.equals(GameConstants.DeathReasons.DERRINGER)
                || deathReason.equals(GameConstants.DeathReasons.TRIDENT)
                || deathReason.equals(GameConstants.DeathReasons.KNIFE)
                || deathReason.equals(GameConstants.DeathReasons.BAT)
                || deathReason.equals(SRE.TMMId("bat"))
                || deathReason.equals(GameConstants.DeathReasons.GRENADE)
                // 飞斧（实体用物品自身注册 id 作为死因）
                || deathReason.equals(ModItems.THROWING_AXE_DEATH_REASON)
                // 走原版攻击路线、伤害落在虚拟血量上的武器（Dream 铁斧 / 钻石剑 / 重锤）
                || isVanillaRouteDeathReason(deathReason);
    }

    /**
     * 走原版攻击路线、伤害落在虚拟血量（
     * {@code org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent}）上的武器死因：
     * Dream 铁斧 / Dream 钻石剑 / Dream 重锤。
     *
     * <p>移植自 SRE {@code content/item/RiotShieldHandler#isVanillaRouteDeathReason}；
     * RKD 没有武士刀（Katana），所以此处不含 {@code KatanaItem.DEATH_REASON}。
     *
     * <p>这些武器每次攻击都绕过死亡管线直接扣虚拟血量，只有虚拟血量归零判死时才会带着这些死因
     * 走到死亡拦截。因此逐次格挡必须由武器自身在扣血前调用 {@link #tryBlockAttack(Player, Player)}。
     */
    public static boolean isVanillaRouteDeathReason(ResourceLocation deathReason) {
        if (deathReason == null)
            return false;
        return deathReason.equals(DreamRoleData.DEATH_REASON_DREAM_AXE)
                || deathReason.equals(DreamDiamondSwordItem.DEATH_REASON)
                || deathReason.equals(DreamMaceItem.DEATH_REASON);
    }

    public static boolean allowDeath(Player victim, Player attacker,
            ResourceLocation deathReason) {
        if (attacker == null)
            return true;
        if ((attacker.isSpectator())
                || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(victim))
            return true;
        if (!isBlockableDeathReason(deathReason))
            return true;
        // 如果受害者正在举盾且主手/副手持有我们的防暴盾，则阻挡并损坏盾
        if (!victim.isUsingItem())
            return true;

        ItemStack main = victim.getMainHandItem();
        ItemStack off = victim.getOffhandItem();
        ItemStack shieldStack = null;
        boolean mainHand = false;
        if (main != null && main.is(ModItems.RIOT_SHIELD)) {
            shieldStack = main;
            mainHand = true;
        } else if (off != null && off.is(ModItems.RIOT_SHIELD)) {
            shieldStack = off;
            mainHand = false;
        }

        if (shieldStack == null)
            return true;

        // 检查是否来自玩家正面
        Vec3 look = victim.getLookAngle();
        Vec3 dir = attacker.position().subtract(victim.position());
        dir = new Vec3(dir.x, 0, dir.z);
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len == 0)
            return true;
        dir = dir.scale(1.0 / len);
        Vec3 l2 = new Vec3(look.x, 0, look.z);
        double llen = Math.sqrt(l2.x * l2.x + l2.z * l2.z);
        if (llen == 0)
            return true;
        l2 = l2.scale(1.0 / llen);
        double dot = l2.x * dir.x + l2.z * dir.z;
        if (dot < Math.cos(Math.toRadians(60.0))) {
            // 不是正面（允许一定夹角）
            return true;
        }

        // 阻挡：播放 entity.iron_golem.damage 音效并损坏盾
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.0F,
                1.0F);
        if (!victim.isCreative()) {
            shieldStack.hurtAndBreak(1, victim, mainHand ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }

        return false;
    }

    /**
     * 供走原版攻击路线的武器（如短管霰弹枪）在结算伤害前调用：
     * 复用 {@link #allowDeath} 的格挡判定（传入白名单内的死因），
     * 正面举盾时播放音效并消耗 1 点耐久。
     *
     * @return true 已被格挡，调用方应放弃本次攻击的全部伤害
     */
    public static boolean tryBlockAttack(Player victim, Player attacker) {
        return !allowDeath(victim, attacker, GameConstants.DeathReasons.BAT);
    }

    /**
     * 检查玩家是否正在举防暴盾牌
     */
    public static boolean isBlockingWithRiotShield(Player player) {
        if (!player.isUsingItem()) {
            return false;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return (main != null && main.is(ModItems.RIOT_SHIELD)) || 
               (off != null && off.is(ModItems.RIOT_SHIELD));
    }

    /**
     * 获取举盾提示消息
     */
    public static Component getShieldBlockingMessage() {
        return Component.translatable("message.noellesroles.shield_blocking")
                .withStyle(ChatFormatting.YELLOW);
    }
}
