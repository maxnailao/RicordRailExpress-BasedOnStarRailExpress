package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.packet.CanMoveInTimeStopS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;

import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TimeStopEffect extends MobEffect {
    public static List<UUID> canMovePlayers = new ArrayList<>();
    public static Map<UUID, Vec3> clientPositions = new java.util.HashMap<>();
    public static int freezeTime = 0;
    public static int freezeStatedTime = 0;
    public static int freezeMaxTime = 0;
//
//    public static boolean hasCooldown(Player player){
//        return player.getCooldowns().isOnCooldown(Items.BIRCH_LOG);
//    }
//    public static void setCooldown (Player player, int time){
//        player.getCooldowns().addCooldown(Items.BIRCH_LOG, time);
//    }

    public TimeStopEffect() {
        super(MobEffectCategory.NEUTRAL, Color.white.getRGB());
    }

    @Override
    public boolean isEnabled(FeatureFlagSet featureFlagSet) {
        return true;
    }

    public static boolean tryTriggerStart(ServerPlayer serverPlayer, int time, Component displaySkillTitle) {
        if (serverPlayer.hasEffect(ModEffects.TIME_STOP))
            return false;
        triggerStart(serverPlayer, time, displaySkillTitle);
        return true;
    }

    /**
     * 强制彻底结束时间停止（回合结束 / 新回合开始时的兜底清理）。
     *
     * <p><b>为什么必须有这个方法：</b>解除时停的唯一逻辑写在
     * {@code ServerTickEvents.START_SERVER_TICK} 里，靠 {@link #freezeTime} 每 tick 自减到 0
     * 才调用 {@code tickRateManager().setFrozen(false)}。但时停期间服务端 tick 是被冻结的，
     * 一旦「时停还没走完，这一局就结束了」（神父「天堂制造」加速阶段正是这种时序），
     * 倒计时就永远走不完 —— 玩家身上残留 {@code TIME_STOP} 效果，
     * 而客户端有一整套 mixin（{@code CantControlMixin} / {@code MouseHandleSuppressMixin} /
     * {@code LivingEntityUseCallbackMixin} / {@code TimeStopShader} 等）会按这个效果
     * 封锁移动、鼠标、用物品、动画与音效，并持续绘制时停画面 ——
     * 表现就是「回合结束后卡死、且屏幕上一直挂着时停画面」。</p>
     *
     * <p>本方法把三样东西一次性清干净：玩家身上的效果、静态状态（含 {@code freezeTime}）、
     * 以及服务端 tick 冻结标记。它在游戏主线程上执行，即使 tick 速率处于冻结状态也能生效。</p>
     *
     * @param level 目标世界
     */
    public static void forceStop(net.minecraft.server.level.ServerLevel level) {
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            player.removeEffect(ModEffects.TIME_STOP);
            ServerPlayNetworking.send(player, new RemoveStatusBarPayload("Time_Stop"));
        }
        canMovePlayers.clear();
        clientPositions.clear();
        freezeTime = 0;
        // 客户端侧静态缓存也必须复位：它们是 static 字段，在单人游戏里与客户端同处一个 JVM，
        // 跨「退出存档 / 重进」不会被清掉。GameTimeFreezeMixin 会把客户端时间钉在
        // freezeStatedTime 上，TimeStopShader / 状态栏按 freezeMaxTime 计算进度 ——
        // 若不复位，重进世界后依然是被时停锁死的状态，必须重启整个客户端。
        freezeStatedTime = 0;
        freezeMaxTime = 0;
        MinecraftServer server = level.getServer();
        if (server != null && server.tickRateManager().isFrozen()) {
            server.tickRateManager().setFrozen(false);
        }
    }

    public static void triggerStart(ServerPlayer serverPlayer, int time, Component displaySkillTitle) {
        canMovePlayers.clear();
        canMovePlayers.add(serverPlayer.getUUID());
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
        var broadcastMessage = displaySkillTitle;

        SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(serverPlayer.level());
        gameTimeComponent.setTime(gameTimeComponent.time + time);
        ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Time_Stop"));
        serverPlayer.serverLevel().players().forEach(
                player -> {
                    player.stopUsingItem();
                    if (player.isSleeping()) {
                        player.stopSleeping();
                    }
                    player.playNotifySound(NRSounds.TIME_STOP, SoundSource.PLAYERS, 1.0F, 1.0F);

                    BroadcastCommand.BroadcastMessage(player, broadcastMessage);

                    if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                        if (WorldModifierComponent.KEY.get(player.level()).isModifier(player,
                                SEModifiers.SPLIT_PERSONALITY)) {

                        } else {
                            canMovePlayers.add(player.getUUID());
                        }
                    } else {
                        if (gameWorldComponent.isRole(player, RedHouseRoles.MAID_SAKUYA)) {
                            canMovePlayers.add(player.getUUID());
                        }
                        // if (gameWorldComponent.isRole(player, ModRoles.JOJO)) {
                        // canMovePlayers.add(player.getUUID());
                        // }
                        if (gameWorldComponent.isRole(player, ModRoles.CLOCKMAKER)) {
                            canMovePlayers.add(player.getUUID());
                        }
                    }
                });
        serverPlayer.serverLevel().players().forEach(
                player -> {
                    player.addEffect(new MobEffectInstance((ModEffects.TIME_STOP), time, 0, false, false, false));
                    ServerPlayNetworking.send(player, new CanMoveInTimeStopS2CPacket(canMovePlayers, time));
                });
        MinecraftServer server = serverPlayer.getServer();
        ServerTickRateManager serverTickRateManager = server.tickRateManager();

        freezeTime = time;
        serverTickRateManager.setFrozen(true);
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int i) {
        return super.applyEffectTick(livingEntity, i);
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        super.removeAttributeModifiers(attributeMap);
        canMovePlayers.clear();
        clientPositions.clear();

    }

    public static int effectStatedTime = 0;

    @Override
    public void onEffectStarted(LivingEntity livingEntity, int i) {
        super.onEffectStarted(livingEntity, i);
    }

}
