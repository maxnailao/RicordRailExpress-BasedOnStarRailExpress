package org.agmas.noellesroles.game.roles.innocence.imironman;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 铁傀儡组件 - 平民阵营
 *
 * 技能「铁拳冲击」（G 键）：
 * - 对准玩家按下技能键将其击退2格、击飞4格，并造成缓慢II+失明2秒
 * - 技能射程2.7格，最多存储3次，存储恢复CD 30秒，释放间隔CD 6秒
 * （充能上限与释放间隔由统一技能系统管理，存储恢复在本组件 serverTick 中处理）
 *
 * 被动「铁躯」：
 * - 免疫一次球棒伤害（通过 AllowPlayerDeathWithKiller 拦截死因 bat_hit）
 * - 被球棒击打后获得技能禁用、禁止移动、按键禁用、失明效果5秒
 */
public class ImironmanPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ImironmanPlayerComponent> KEY = ModComponents.IMIRONMAN_TIEKUILEI;

    /** 技能 ID - 铁拳冲击 */
    public static final ResourceLocation SKILL_ID = SRE.id("imironman_iron_punch");

    private final Player player;

    /** 被动：一次性球棒免疫是否已消耗 */
    public boolean batShieldUsed;

    /** 充能恢复计时（tick），仅当充能未满时累计 */
    private int rechargeTicks;

    public ImironmanPlayerComponent(Player player) {
        this.player = player;
    }

    static {
        // 被动「铁躯」：免疫一次球棒伤害，随后施加5秒禁用类debuff
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!GameConstants.DeathReasons.BAT.equals(deathReason)) {
                return true;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorld.isRole(player, ModRoles.IMIRONMAN_TIEKUILEI)) {
                return true;
            }
            ImironmanPlayerComponent comp = KEY.get(player);
            if (comp.batShieldUsed) {
                return true;
            }
            comp.batShieldUsed = true;
            comp.sync();

            // 技能禁用 + 禁止移动 + 按键禁用 + 失明，持续5秒
            int debuffTicks = (int) (NoellesRolesConfig.HANDLER.instance().imironmanPassiveDebuffSeconds * 20);
            player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, debuffTicks, 0));
            player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, debuffTicks, 0));
            player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, debuffTicks, 0));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, debuffTicks, 0));

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.imironman.bat_immune")
                                .withStyle(ChatFormatting.GOLD),
                        true);
                serverPlayer.playNotifySound(SoundEvents.IRON_GOLEM_HURT, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            if (killer instanceof ServerPlayer killerPlayer) {
                killerPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.imironman.bat_blocked")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            // 否决本次球棒死亡
            return false;
        });
    }

    // ==================== 初始化/清理 ====================

    @Override
    public void init() {
        this.batShieldUsed = false;
        this.rechargeTicks = 0;
        this.sync();
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    // ==================== 技能逻辑 ====================

    /**
     * 技能「铁拳冲击」：对准玩家将其击退2格、击飞4格，并造成缓慢II+失明2秒
     *
     * @return true 表示命中并消耗充能与释放间隔冷却
     */
    public boolean useIronPunch() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();

        // 服务端射线检测准星方向2.7格内的存活玩家
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(serverPlayer,
                e -> e instanceof ServerPlayer p && p != serverPlayer
                        && GameUtils.isPlayerAliveAndSurvival(p),
                cfg.imironmanSkillRange);
        if (!(hit instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof ServerPlayer target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("hud.noellesroles.imironman.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // 击退2格（水平方向远离自己）
        double dx = serverPlayer.getX() - target.getX();
        double dz = serverPlayer.getZ() - target.getZ();
        target.knockback(cfg.imironmanKnockback, dx, dz);
        // 击飞4格（附加垂直速度）
        target.setDeltaMovement(target.getDeltaMovement().add(0.0, cfg.imironmanLaunchVelocity, 0.0));
        target.hurtMarked = true;
        // 玩家受服务端击退需主动同步速度
        target.connection
                .send(new ClientboundSetEntityMotionPacket(target.getId(), target.getDeltaMovement()));

        // 缓慢II + 失明，持续2秒
        int effectTicks = (int) (cfg.imironmanSkillEffectSeconds * 20);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, effectTicks, 1));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, effectTicks, 0));

        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0f, 1.0f);
        target.displayClientMessage(
                Component.translatable("message.noellesroles.imironman.punched",
                        serverPlayer.getDisplayName()).withStyle(ChatFormatting.RED),
                true);
        return true;
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        // 守卫仅基于实体状态，避免阶段切换误判；未进入对局时 maxCharges <= 0 自然跳过
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        // 充能恢复：每30秒恢复1次，直到充满
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        SREAbilityPlayerComponent.SkillState state = ability.getSkillState(SKILL_ID);
        if (state.maxCharges <= 0 || state.charges >= state.maxCharges) {
            rechargeTicks = 0;
            return;
        }
        rechargeTicks++;
        if (rechargeTicks >= NoellesRolesConfig.HANDLER.instance().imironmanRechargeSeconds * 20) {
            rechargeTicks = 0;
            state.charges++;
            ability.charges = state.charges;
            ability.sync();
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.imironman.charge_recovered",
                            state.charges, state.maxCharges).withStyle(ChatFormatting.AQUA),
                    true);
        }
    }

    // ==================== 同步 ====================

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("batShieldUsed", batShieldUsed);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        batShieldUsed = tag.getBoolean("batShieldUsed");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
