package org.agmas.noellesroles.game.roles.innocence.avenger;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 复仇者组件
 *
 * 功能：
 * - 存储绑定的目标玩家
 * - 当目标死亡时激活复仇能力
 * - 激活后可以看到凶手并获得武器
 */
public class AvengerPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<AvengerPlayerComponent> KEY = ModComponents.AVENGER;

    /** 复仇心切持续时间（15秒） */
    public static final int RUSH_DURATION_TICKS = 15 * 20;

    private final Player player;

    // 绑定的目标玩家 UUID
    public UUID targetPlayer = null;

    // 是否已激活复仇能力
    public boolean activated = false;

    // 凶手的 UUID（目标被杀后记录）
    public UUID killerUuid = null;

    // 目标玩家的名字（用于 HUD 显示）
    public String targetName = "";

    // 是否已绑定目标（第一次使用后设置为 true）
    public boolean bound = false;

    // ==================== 复仇心切技能状态 ====================

    // 复仇心切是否已使用过（每局一次）
    public boolean rushUsed = false;

    // 复仇心切是否正在生效（15秒冲刺窗口）
    public boolean rushActive = false;

    // 复仇心切剩余 tick
    public int rushTicksRemaining = 0;

    // 复仇心切是否已成功（凶手已死，恢复正常击杀权限）
    public boolean rushSucceeded = false;

    // 技能赠送的护盾层数（成功后需回收）
    public int grantedShieldLayers = 0;

    public AvengerPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.targetPlayer = null;
        this.activated = false;
        this.killerUuid = null;
        this.targetName = "";
        this.bound = false;
        this.resetRushFields();
        bindRandomTarget();
        this.sync();
    }

    @Override
    public void clear() {
        this.targetPlayer = null;
        this.activated = false;
        this.killerUuid = null;
        this.targetName = "";
        this.bound = false;
        this.resetRushFields();
        this.sync();
    }

    /** 重置复仇心切相关字段（不触发效果移除，效果由游戏重置/自然过期处理） */
    private void resetRushFields() {
        this.rushUsed = false;
        this.rushActive = false;
        this.rushTicksRemaining = 0;
        this.rushSucceeded = false;
        this.grantedShieldLayers = 0;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 绑定目标玩家
     * 
     * @param target 目标玩家 UUID
     * @param name   目标玩家名字
     */
    public void bindTarget(UUID target, String name) {
        this.targetPlayer = target;
        this.targetName = name;
        this.bound = true;
        this.sync();

        // 发送绑定消息
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.bound", name)
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
    }

    /**
     * 随机绑定一个无辜或中立玩家
     */
    public void bindRandomTarget() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (!gameWorld.isRole(player, ModRoles.AVENGER)) {
            return;
        }
        List<UUID> innocentPlayers = new ArrayList<>();

        gameWorld.getRoles().forEach((uuid, role) -> {
            if (uuid.equals(player.getUUID()))
                return; // 排除自己
            Player targetPlayer = player.level().getPlayerByUUID(uuid);
            if (targetPlayer == null)
                return;
            if ((role.isInnocent() || (role.isNeutrals() && !role.isNeutralForKiller()))
                && !role.isKillerTeam()
                && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                innocentPlayers.add(uuid);
            }
        });

        if (!innocentPlayers.isEmpty()) {
            Collections.shuffle(innocentPlayers);
            UUID targetUuid = innocentPlayers.get(0);
            Player target = player.level().getPlayerByUUID(targetUuid);
            if (target != null) {
                bindTarget(targetUuid, target.getName().getString());
            }
        }
    }

    /**
     * 激活复仇能力
     * 
     * @param killer 凶手的 UUID（可能为空，比如跌落死亡）
     */
    public void activate(UUID killer) {
        if (activated)
            return;

        this.activated = true;
        this.killerUuid = killer;

        if (player instanceof ServerPlayer serverPlayer) {
            ConfigWorldComponent.onPlayerUsedSkill( serverPlayer);
            // 发送激活消息
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.activated", targetName)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    true);

            // 给予左轮手枪
            serverPlayer.addItem(new ItemStack(TMMItems.REVOLVER));

            // 如果知道凶手，发送凶手信息
            if (killer != null) {
                Player killerPlayer = player.level().getPlayerByUUID(killer);
                if (killerPlayer != null) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.avenger.killer_revealed",
                                    killerPlayer.getName().getString())
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
            } else {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.avenger.unknown_killer")
                                .withStyle(ChatFormatting.GRAY),
                        true);
            }
        }

        this.sync();
    }

    /**
     * 检查目标是否存活
     */
    public boolean isTargetAlive() {
        if (targetPlayer == null)
            return false;
        Player target = player.level().getPlayerByUUID(targetPlayer);
        return target != null && GameUtils.isPlayerAliveAndSurvival(target);
    }

    /**
     * 获取凶手玩家名（用于 HUD 显示）
     */
    public String getKillerName() {
        if (killerUuid == null)
            return "";
        Player killer = player.level().getPlayerByUUID(killerUuid);
        return killer != null ? killer.getName().getString() : "";
    }

    /**
     * 检查凶手是否存活
     */
    public boolean isKillerAlive() {
        if (killerUuid == null)
            return false;
        Player killer = player.level().getPlayerByUUID(killerUuid);
        return killer != null && GameUtils.isPlayerAliveAndSurvival(killer);
    }

    // ==================== 复仇心切技能 ====================

    /**
     * 尝试释放复仇心切（G 键技能入口）
     * 前置条件：复仇已激活、本局未使用过、凶手已知且存活
     *
     * @return 是否成功释放
     */
    public boolean tryUseRush() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.AVENGER))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return false;

        if (!activated) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.rush.not_activated")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (rushUsed) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.rush.already_used")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (killerUuid == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.rush.killer_unknown")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!isKillerAlive()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.rush.killer_dead")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        startRush(serverPlayer);
        return true;
    }

    /**
     * 开启复仇心切：速度2 + 无限体力 + 一层护盾 + 凶手红色透视，并警告凶手
     */
    private void startRush(ServerPlayer serverPlayer) {
        rushUsed = true;
        rushActive = true;
        rushSucceeded = false;
        rushTicksRemaining = RUSH_DURATION_TICKS;

        // 15秒速度2与无限体力（多给5tick缓冲避免边界提前失效）
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                RUSH_DURATION_TICKS + 5, 1, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA,
                RUSH_DURATION_TICKS + 5, 0, false, false, true));

        // 获得一层护盾
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
        armor.addArmor();
        grantedShieldLayers = 1;

        ConfigWorldComponent.onPlayerUsedSkill(serverPlayer);

        // 复仇者自身提示
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.avenger.rush.started",
                        RUSH_DURATION_TICKS / 20)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                true);
        serverPlayer.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.8F, 1.4F);

        // 以广播员同款位置（屏幕上方居中横幅）警告凶手
        Player killer = player.level().getPlayerByUUID(killerUuid);
        if (killer instanceof ServerPlayer killerSp) {
            ServerPlayNetworking.send(killerSp, new BroadcastMessageS2CPacket(
                    Component.translatable("message.noellesroles.avenger.rush.warning_killer")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            killerSp.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.8F, 0.7F);
        }

        this.sync();
    }

    /**
     * 复仇成功：凶手死亡（无论是否由复仇者亲手击杀）
     * - 移除技能赠送的护盾
     * - 解除禁杀限制，恢复正常击杀
     * - 超时不再触发死亡
     */
    public void succeedRush() {
        if (!rushActive)
            return;
        rushActive = false;
        rushSucceeded = true;

        // 回收技能赠送的护盾层
        if (grantedShieldLayers > 0) {
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
            int remove = Math.min(grantedShieldLayers, Math.max(armor.getArmor(), 0));
            if (remove > 0) {
                armor.removeArmor(remove);
            }
            grantedShieldLayers = 0;
        }

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.avenger.rush.success")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    true);
            sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        this.sync();
    }

    /**
     * 复仇失败：15秒内未能击杀凶手，自身死亡，死因为“复仇失败”
     */
    private void failRush() {
        rushActive = false;
        grantedShieldLayers = 0;
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(ModEffects.INFINITE_STAMINA);
        this.sync();
        // 四参重载为 forceDeath，无视护盾强制死亡
        GameUtils.killPlayer(player, true, null, Noellesroles.id("revenge_failed"));
    }

    /**
     * 防御性重置：角色丢失/游戏未运行等异常场景下静默清除冲刺状态，避免跨局残留
     */
    private void resetRushStateSilently() {
        if (!rushUsed && !rushActive && !rushSucceeded)
            return;
        resetRushFields();
        this.sync();
    }

    /**
     * 复仇心切计时：凶手死亡则成功，超时则自身死亡
     */
    private void tickRush() {
        // 凶手已死（复仇者亲手或他人击杀均视为成功）
        if (!isKillerAlive()) {
            succeedRush();
            return;
        }
        rushTicksRemaining--;
        if (rushTicksRemaining <= 0) {
            failRush();
        }
    }

    public void sync() {
        ModComponents.AVENGER.sync(this.player);
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());

        // 只有复仇者角色才处理；角色丢失时静默重置，防止状态残留
        if (!gameWorld.isRole(player, ModRoles.AVENGER)) {
            resetRushStateSilently();
            return;
        }

        // 游戏未运行时不推进复仇心切计时，避免大厅/结算期间误触发死亡
        if (!gameWorld.isRunning()) {
            resetRushStateSilently();
            return;
        }

        // 复仇者自身死亡（技能期间被反杀等）时静默结束冲刺
        if (rushActive && !GameUtils.isPlayerAliveAndSurvival(player)) {
            resetRushStateSilently();
            return;
        }

        // 复仇心切计时（优先于其余逻辑）
        if (rushActive) {
            tickRush();
        }

        // 如果已激活，不需要继续检测绑定目标
        if (activated)
            return;

        // 如果没有绑定目标，不检测
        if (targetPlayer == null || !bound)
            return;

        // 检测目标是否死亡
        var refugeeC = RefugeeComponent.KEY.get(player.level());
        boolean isRefugeeAlive = false;
        if (refugeeC.isAnyRevivals) {
            isRefugeeAlive = true;
        }
        if (!gameWorld.isSkillAvailable) {
            // player.displayClientMessage(
            //         Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!isRefugeeAlive) {
            if (!isTargetAlive()) {
                // 目标已死亡，激活复仇能力
                // 注意：此时我们不知道凶手是谁，需要通过 Mixin 在死亡时记录
                // 这里只是备用检测
                activate(null);
            }
        }

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
        if (targetPlayer != null) {
            tag.putUUID("targetPlayer", targetPlayer);
        }
        tag.putBoolean("activated", activated);
        if (killerUuid != null) {
            tag.putUUID("killerUuid", killerUuid);
        }
        tag.putString("targetName", targetName);
        tag.putBoolean("bound", bound);
        tag.putBoolean("rushUsed", rushUsed);
        tag.putBoolean("rushActive", rushActive);
        tag.putInt("rushTicksRemaining", rushTicksRemaining);
        tag.putBoolean("rushSucceeded", rushSucceeded);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.targetPlayer = tag.contains("targetPlayer") ? tag.getUUID("targetPlayer") : null;
        this.activated = tag.getBoolean("activated");
        this.killerUuid = tag.contains("killerUuid") ? tag.getUUID("killerUuid") : null;
        this.targetName = tag.getString("targetName");
        this.bound = tag.getBoolean("bound");
        this.rushUsed = tag.getBoolean("rushUsed");
        this.rushActive = tag.getBoolean("rushActive");
        this.rushTicksRemaining = tag.getInt("rushTicksRemaining");
        this.rushSucceeded = tag.getBoolean("rushSucceeded");
    }
}