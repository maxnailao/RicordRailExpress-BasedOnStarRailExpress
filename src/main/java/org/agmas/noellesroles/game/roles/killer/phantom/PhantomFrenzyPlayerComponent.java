package org.agmas.noellesroles.game.roles.killer.phantom;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 幽灵幻影组件
 * 幽灵(PHANTOM)的商店购买特殊疯魔技能
 * - 持续25秒
 * - 获得隐身 + 速度I效果
 * - 临时获得一把无限耐久的刀（3秒CD）
 * - 仅可在物品栏中切换空手和这把刀（由psycho系统的getPsychoItem锁定实现）
 * - 无护盾（armour=0）
 * - 击杀后爆出黑色+灰色粒子，发出音效
 * - 价格：400金币
 * - 购买CD：同普通疯魔模式
 */
public class PhantomFrenzyPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<PhantomFrenzyPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "phantom_frenzy"),
            PhantomFrenzyPlayerComponent.class);

    /** 疯魔持续时间：25秒 = 500 ticks */
    public static final int FRENZY_DURATION = 25 * 20;

    private final Player player;
    public boolean inFrenzy = false;

    /** 疯魔开始前保存的刀数量（stopPsycho会清除所有刀，结束后按此数量返还再扣一把） */
    private int savedKnifeCount = 0;

    public PhantomFrenzyPlayerComponent(Player player) {
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
     * 启动幽灵幻影模式
     * - 给予隐身 + 速度I
     * - 给予一把刀（psycho系统会锁定物品栏）
     * - 无护盾（armour=0）
     * - 持续25秒
     */
    public boolean startFrenzy() {
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        if (psychoComponent.getPsychoTicks() > 0) {
            return false;
        }

        // 保存当前背包中刀的数量（stopPsycho会清除所有刀，结束后用于返还）
        savedKnifeCount = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(TMMItems.KNIFE)) {
                savedKnifeCount += stack.getCount();
            }
        }

        // 给予一把刀
        if (!RoleUtils.insertStackInFreeSlot(player, new ItemStack(TMMItems.KNIFE))) {
            return false;
        }
        // 清除刀的冷却，避免疯魔前刀人留下的CD保留到疯魔期间
        player.getCooldowns().removeCooldown(TMMItems.KNIFE);

        // 设置psycho模式（不使用startPsycho避免给球棒）
        psychoComponent.setPsychoTicks(FRENZY_DURATION);
        psychoComponent.setArmour(0); // 无护盾
        psychoComponent.type = 2; // 幽灵专属疯魔类型
        psychoComponent.sync();

        // 更新psycho计数
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        gameWorldComponent.setPsychosActive(gameWorldComponent.getPsychosActive() + 1);

        // 触发状态栏
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Psycho"));
        }

        // 给予隐身效果（25秒，无粒子）
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, FRENZY_DURATION + 20, 0,
                false, false, false));

        // 给予速度I效果（25秒，无粒子）
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, FRENZY_DURATION + 20, 0,
                false, false, false));

        this.inFrenzy = true;
        this.sync();

        // 播放启动音效（多层叠加营造氛围）
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.6F, 1.5F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMBIENT_CAVE, SoundSource.PLAYERS, 0.5F, 0.8F);

        // 启动粒子特效：黑色烟雾
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1, player.getZ(),
                    25, 0.4, 0.8, 0.4, 0.05);
            serverLevel.sendParticles(ParticleTypes.ASH,
                    player.getX(), player.getY() + 1, player.getZ(),
                    20, 0.4, 0.8, 0.4, 0.03);
        }

        return true;
    }

    /**
     * 停止幽灵幻影模式
     */
    public void stopFrenzy() {
        if (!inFrenzy)
            return;

        this.inFrenzy = false;

        // stopPsycho 已经清除了所有刀，返还玩家原有的刀再没收一把
        // giveBack = savedKnifeCount - 1（至少为0）
        int giveBack = Math.max(0, savedKnifeCount - 1);
        for (int i = 0; i < giveBack; i++) {
            RoleUtils.insertStackInFreeSlot(player, new ItemStack(TMMItems.KNIFE));
        }
        savedKnifeCount = 0;

        // 移除隐身和速度效果
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);

        // 重置psycho type
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        psychoComponent.type = -1;
        psychoComponent.sync();

        this.sync();
    }

    /**
     * 检查玩家是否处于幽灵幻影状态
     */
    public static boolean isInFrenzy(Player player) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRole(player, ModRoles.PHANTOM))
            return false;
        return KEY.get(player).inFrenzy;
    }

    @Override
    public void serverTick() {
        if (!inFrenzy)
            return;

        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        // 当psycho模式结束时，停止幻影
        if (psychoComponent.getPsychoTicks() <= 0) {
            stopFrenzy();
            return;
        }

        // 疯魔期间强制保持隐身（避免被技能或其他途径移除导致与技能隐身冲突）
        if (!player.hasEffect(MobEffects.INVISIBILITY)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY, FRENZY_DURATION + 20, 0,
                    false, false, false));
        }

        // 每3秒发送一次环绕粒子（黑色烟雾）+ 环境音效
        if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 60 == 0) {
            serverLevel.sendParticles(ParticleTypes.ASH,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    5, 0.3, 0.5, 0.3, 0.02);
            // 周期性幽灵振翅声
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.4F, 0.9F);
        }

        // 每8秒播放一次低沉环境音
        if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 160 == 0) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMBIENT_CAVE, SoundSource.PLAYERS, 0.3F, 1.2F);
        }
    }

    @Override
    public void clientTick() {
        // 客户端跟踪
    }

    /**
     * 注册击杀特效事件
     * 幽灵幻影期间击杀玩家后爆出黑色+灰色粒子并发出音效
     */
    public static void registerKillEffectEvent() {
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReason, forceKill) -> {
            if (killer == null)
                return;
            if (!isInFrenzy(killer))
                return;

            // 只在刀击杀时触发特效
            if (!GameConstants.DeathReasons.KNIFE.equals(deathReason))
                return;

            if (killer.level() instanceof ServerLevel serverLevel) {
                // 黑色粒子：烟雾
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        victim.getX(), victim.getY() + 1, victim.getZ(),
                        20, 0.4, 0.6, 0.4, 0.08);
                // 灰色粒子：灰烬
                serverLevel.sendParticles(ParticleTypes.ASH,
                        victim.getX(), victim.getY() + 1, victim.getZ(),
                        25, 0.5, 0.7, 0.5, 0.06);
                // 黑色粒子：灵魂火焰（暗色）
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        victim.getX(), victim.getY() + 1.5, victim.getZ(),
                        15, 0.3, 0.5, 0.3, 0.1);

                // 击杀音效（多层叠加）
                serverLevel.playSound(null, victim.getX(), victim.getY() + 1, victim.getZ(),
                        SoundEvents.PHANTOM_DEATH, SoundSource.PLAYERS, 1.0F, 0.7F);
                serverLevel.playSound(null, victim.getX(), victim.getY() + 1, victim.getZ(),
                        SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8F, 1.0F);
                serverLevel.playSound(null, victim.getX(), victim.getY() + 1, victim.getZ(),
                        SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.0F, 0.5F);
                serverLevel.playSound(null, victim.getX(), victim.getY() + 1, victim.getZ(),
                        SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.5F, 1.8F);
            }
        });
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
