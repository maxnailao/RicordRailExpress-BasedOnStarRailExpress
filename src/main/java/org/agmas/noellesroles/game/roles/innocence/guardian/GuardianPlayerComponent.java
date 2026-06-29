package org.agmas.noellesroles.game.roles.innocence.guardian;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.zhizhang.ZhizhangPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 监护人角色组件
 * - 技能：花费125金币，解除智力障碍患者的语音禁用和聊天混乱效果12秒，
 *        并给予2秒无敌，需要智力障碍患者在5格内，CD30秒
 * - 被动：智力障碍患者在监护人视角中白色高亮
 */
public class GuardianPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<GuardianPlayerComponent> KEY = ModComponents.GUARDIAN;

    private final Player player;

    // ===== 技能常量 =====
    /** 技能冷却（tick），30秒 = 600 tick */
    public static final int SKILL_COOLDOWN = 600;
    /** 技能花费 */
    public static final int SKILL_COST = 125;
    /** 技能作用范围 */
    public static final double SKILL_RANGE = 5.0;
    /** 免疫持续时间（tick），12秒 = 240 tick */
    public static final int IMMUNITY_DURATION = 240;
    /** 无敌持续时间（tick），2秒 = 40 tick */
    public static final int INVINCIBILITY_DURATION = 40;

    /** 技能冷却计时器 */
    public int skillCooldown = 0;

    /** 监护人看到的智力障碍玩家 UUID（同步到客户端） */
    public UUID zhizhangUUID = null;

    /** 监护人视角下看到的智力障碍患者免疫剩余时间（tick），仅监护人可见 */
    public int zhizhangImmunityTicks = 0;

    public GuardianPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.skillCooldown = 0;
        this.zhizhangUUID = null;
        // 查找场上的智力障碍患者
        findZhizhang();
        sync();
    }

    @Override
    public void clear() {
        this.skillCooldown = 0;
        this.zhizhangUUID = null;
        sync();
    }

    /**
     * 查找场上的智力障碍患者并记录 UUID
     */
    private void findZhizhang() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return;

        for (ServerPlayer other : sp.serverLevel().players()) {
            if (gameWorld.isRole(other, ModRoles.ZHIZHANG)) {
                this.zhizhangUUID = other.getUUID();
                return;
            }
        }
        this.zhizhangUUID = null;
    }

    /**
     * 使用技能：保护智力障碍患者
     */
    public boolean useSkill() {
        if (!(player instanceof ServerPlayer sp)) return false;

        // 检查冷却
        if (skillCooldown > 0) {
            sp.displayClientMessage(
                Component.translatable("tip.noellesroles.cooldown", skillCooldown / 20)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 查找智力障碍患者
        if (zhizhangUUID == null) {
            findZhizhang();
        }
        if (zhizhangUUID == null) {
            sp.displayClientMessage(
                Component.translatable("message.noellesroles.guardian.no_zhizhang")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        var zhizhangPlayerRaw = sp.serverLevel().getPlayerByUUID(zhizhangUUID);
        if (!(zhizhangPlayerRaw instanceof ServerPlayer zhizhangPlayer)
            || !GameUtils.isPlayerAliveAndSurvival(zhizhangPlayer)) {
            sp.displayClientMessage(
                Component.translatable("message.noellesroles.guardian.no_zhizhang")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        // 检查距离
        if (sp.distanceTo(zhizhangPlayer) > SKILL_RANGE) {
            sp.displayClientMessage(
                Component.translatable("message.noellesroles.guardian.too_far")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }

        // 检查金币
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < SKILL_COST) {
            sp.displayClientMessage(
                Component.translatable("message.noellesroles.guardian.not_enough_money", SKILL_COST)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 扣除金币
        shop.addToBalance(-SKILL_COST);

        // 设置智力障碍患者免疫
        ZhizhangPlayerComponent zhizhangComp = ZhizhangPlayerComponent.KEY.get(zhizhangPlayer);
        zhizhangComp.setGuardianImmunity(IMMUNITY_DURATION);

        // 给予智力障碍患者2秒无敌（吸收效果）
        zhizhangPlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
            INVINCIBILITY_DURATION, 4, false, false, false)); // Absorption V 提供大量吸收血量

        // 进入冷却
        skillCooldown = SKILL_COOLDOWN;

        // 通知监护人
        sp.displayClientMessage(
            Component.translatable("message.noellesroles.guardian.skill_success",
                zhizhangPlayer.getDisplayName().getString())
                .withStyle(ChatFormatting.GREEN), true);

        // 通知智力障碍患者
        zhizhangPlayer.displayClientMessage(
            Component.translatable("message.noellesroles.zhizhang.guardian_protected")
                .withStyle(ChatFormatting.AQUA), true);

        sync();
        return true;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer recipient) {
        // 同步给监护人自己
        if (recipient == this.player) return true;
        // 也同步给智力障碍患者（以便客户端知道监护人看到了它）
        if (zhizhangUUID != null && recipient.getUUID().equals(zhizhangUUID)) return true;
        return false;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || gameWorld.gameStatus != SREGameWorldComponent.GameStatus.ACTIVE) return;
        if (!gameWorld.isRole(player, ModRoles.GUARDIAN)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 冷却递减
        if (skillCooldown > 0) {
            skillCooldown--;
        }

        // 从智力障碍患者组件同步免疫剩余时间（用于监护人HUD显示）
        if (zhizhangUUID != null && player.level().getGameTime() % 20 == 0) {
            var zhizhangRaw = sp.serverLevel().getPlayerByUUID(zhizhangUUID);
            if (zhizhangRaw instanceof ServerPlayer zp) {
                ZhizhangPlayerComponent zComp = ZhizhangPlayerComponent.KEY.get(zp);
                this.zhizhangImmunityTicks = zComp.guardianImmunityTicks;
            }
        }

        // 确保 zhizhangUUID 已设置
        if (zhizhangUUID == null && player.level().getGameTime() % 100 == 0) {
            findZhizhang();
            if (zhizhangUUID != null) sync();
        }

        // 每10秒同步一次
        if (player.level().getGameTime() % 200 == 0) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (player == null || player.level() == null) return;
        // 仅在本地玩家是监护人时执行客户端逻辑
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) return;
        if (!SREClient.gameComponent.isRole(player, ModRoles.GUARDIAN)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 客户端冷却模拟
        if (skillCooldown > 0) {
            skillCooldown--;
        }
        if (zhizhangImmunityTicks > 0) {
            zhizhangImmunityTicks--;
        }

        // 被动：智力障碍患者在监护人视角中高亮（与承太郎看迪奥相同的机制，无距离限制）
        if (zhizhangUUID != null) {
            Player zhizhangPlayer = player.level().getPlayerByUUID(zhizhangUUID);
            if (zhizhangPlayer != null && GameUtils.isPlayerAliveAndSurvival(zhizhangPlayer)) {
                if (!zhizhangPlayer.hasEffect(MobEffects.GLOWING)) {
                    zhizhangPlayer.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                        40, 0, false, false, true));
                }
            }
        }
    }

    public void sync() {
        ModComponents.GUARDIAN.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("skillCooldown", skillCooldown);
        tag.putInt("zhizhangImmunityTicks", zhizhangImmunityTicks);
        if (zhizhangUUID != null) {
            tag.putString("zhizhangUUID", zhizhangUUID.toString());
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.skillCooldown = tag.contains("skillCooldown") ? tag.getInt("skillCooldown") : 0;
        this.zhizhangImmunityTicks = tag.contains("zhizhangImmunityTicks") ? tag.getInt("zhizhangImmunityTicks") : 0;
        if (tag.contains("zhizhangUUID")) {
            try {
                this.zhizhangUUID = UUID.fromString(tag.getString("zhizhangUUID"));
            } catch (Exception e) {
                this.zhizhangUUID = null;
            }
        } else {
            this.zhizhangUUID = null;
        }
    }

    @Override
    public void writeToSyncNbtWithPlayer(CompoundTag tag, HolderLookup.Provider registryLookup, ServerPlayer recipient) {
        writeToSyncNbt(tag, registryLookup);
        // 仅对监护人发送免疫剩余时间（智力障碍患者自己看不到）
        if (zhizhangUUID != null && recipient.getUUID().equals(zhizhangUUID)) {
            tag.putInt("zhizhangImmunityTicks", 0); // 对智力障碍患者隐藏
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
