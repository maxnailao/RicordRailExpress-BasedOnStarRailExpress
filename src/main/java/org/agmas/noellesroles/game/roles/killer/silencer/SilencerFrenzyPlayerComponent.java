package org.agmas.noellesroles.game.roles.killer.silencer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静语者疯魔组件
 * 静语者(SILENCER)的商店购买特殊疯魔技能
 * - 与普通疯魔一样（球棒、护盾、疯魔皮肤、标准时长）
 * - 疯魔期间静语者获得静步效果（脚步声屏蔽）
 * - 疯魔开启时全体非杀手阵营玩家收到提示："嘘......安静......"
 * - 疯魔期间若有玩家说话（语音），获得缓慢I效果3秒
 * - 价格：350金币
 * - 购买CD：同普通疯魔模式
 */
public class SilencerFrenzyPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SilencerFrenzyPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "silencer_frenzy"),
            SilencerFrenzyPlayerComponent.class);

    /** 当前处于疯魔状态的静语者UUID集合（供语音线程安全读取） */
    public static final Set<UUID> ACTIVE_FRENZY_SILENCERS = ConcurrentHashMap.newKeySet();

    /** 说话者被施加的缓慢效果时长：3秒 = 60 ticks */
    private static final int SLOWNESS_DURATION = 3 * 20;

    private final Player player;
    public boolean inFrenzy = false;

    public SilencerFrenzyPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        this.inFrenzy = false;
        ACTIVE_FRENZY_SILENCERS.remove(this.player.getUUID());
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 启动静语者疯魔模式
     * - 复用普通疯魔逻辑（球棒、护盾、皮肤、状态栏）
     * - 额外给予静步效果
     * - 向全体非杀手阵营玩家发送提示
     */
    public boolean startFrenzy() {
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        if (psychoComponent.getPsychoTicks() > 0) {
            return false;
        }

        // 复用普通疯魔（给予球棒、设置护盾、更新计数、发送状态栏）
        if (!psychoComponent.startPsycho()) {
            return false;
        }

        this.inFrenzy = true;
        ACTIVE_FRENZY_SILENCERS.add(this.player.getUUID());
        this.sync();

        // 给予静步效果（屏蔽脚步声），时长覆盖整个疯魔周期
        player.addEffect(new MobEffectInstance(
                ModEffects.JINGBU, GameConstants.getPsychoTimer() + 20, 0,
                false, false, false));

        // 向全体非杀手阵营玩家发送提示
        if (player instanceof ServerPlayer serverPlayer) {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            Component warning = Component.translatable("message.noellesroles.silencer.frenzy_warning")
                    .withStyle(ChatFormatting.GRAY);
            for (ServerPlayer p : serverPlayer.serverLevel().players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p))
                    continue;
                // 非杀手阵营（不能使用杀手功能）
                if (gameWorldComponent.canUseKillerFeatures(p))
                    continue;
                p.displayClientMessage(warning, false);
            }
        }

        return true;
    }

    /**
     * 停止静语者疯魔模式
     */
    public void stopFrenzy() {
        if (!inFrenzy)
            return;

        this.inFrenzy = false;
        ACTIVE_FRENZY_SILENCERS.remove(this.player.getUUID());

        // 移除静步效果
        player.removeEffect(ModEffects.JINGBU);

        this.sync();
    }

    /**
     * 检查玩家是否处于静语者疯魔状态
     */
    public static boolean isInFrenzy(Player player) {
        return ACTIVE_FRENZY_SILENCERS.contains(player.getUUID());
    }

    /**
     * 是否有任意静语者处于疯魔状态
     */
    public static boolean isAnySilencerInFrenzy() {
        return !ACTIVE_FRENZY_SILENCERS.isEmpty();
    }

    /**
     * 语音事件回调：当玩家在静语者疯魔期间说话时调用。
     * 对说话者（非疯魔静语者本人）施加缓慢I效果3秒。
     * 注意：此方法在语音线程中被调用，需保证线程安全。
     */
    public static void onPlayerSpeak(ServerPlayer speaker) {
        if (ACTIVE_FRENZY_SILENCERS.isEmpty())
            return;
        // 疯魔静语者本人不受惩罚
        if (ACTIVE_FRENZY_SILENCERS.contains(speaker.getUUID()))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(speaker))
            return;
        // 已有缓慢效果则不重复施加（避免语音包刷屏）
        if (speaker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
            return;
        // 延迟到服务端主线程施加效果，保证线程安全
        speaker.server.execute(() -> {
            if (GameUtils.isPlayerAliveAndSurvival(speaker)
                    && !speaker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                speaker.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_DURATION, 0,
                        false, false, true));
            }
        });
    }

    @Override
    public void serverTick() {
        if (!inFrenzy)
            return;

        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        // 当psycho模式结束时，停止疯魔
        if (psychoComponent.getPsychoTicks() <= 0) {
            stopFrenzy();
            return;
        }

        // 每2秒刷新一次静步效果，保证覆盖剩余疯魔时间
        if (player.tickCount % 40 == 0) {
            int remaining = psychoComponent.getPsychoTicks() + 20;
            player.addEffect(new MobEffectInstance(
                    ModEffects.JINGBU, remaining, 0,
                    false, false, false));
        }
    }

    @Override
    public void clientTick() {
        // 客户端跟踪
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("inFrenzy", this.inFrenzy);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.inFrenzy = tag.getBoolean("inFrenzy");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
