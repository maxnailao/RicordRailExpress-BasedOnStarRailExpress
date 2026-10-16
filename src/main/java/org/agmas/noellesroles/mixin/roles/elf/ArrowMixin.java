package org.agmas.noellesroles.mixin.roles.elf;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnShieldBroken;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.raider.RaiderPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.cupid.CupidPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractArrow.class)
public class ArrowMixin {

    /** 掠夺者箭矢起始位置（用于15格距离判定） */
    @Unique
    private Vec3 noellesroles$raiderArrowStartPos = null;

    @Inject(method = "tick", at = @At("HEAD"))
    private void noellesroles$onArrowTick(CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level().isClientSide)
            return;
        // 掠夺者箭矢15格距离限制
        if (arrow.getOwner() instanceof ServerPlayer shooter) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(shooter.serverLevel());
            if (gameWorld.isRole(shooter, ModRoles.LUEDUOZHE)) {
                if (noellesroles$raiderArrowStartPos == null) {
                    noellesroles$raiderArrowStartPos = arrow.position();
                } else {
                    double dist = arrow.position().distanceTo(noellesroles$raiderArrowStartPos);
                    if (dist > 15.0) {
                        arrow.discard();
                    }
                }
            }
        }
    }
    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void noellesroles$onHitEntity(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (entityHitResult.getEntity() instanceof ServerPlayer player) {
            AbstractArrow arrow = (AbstractArrow) (Object) this;
            if (arrow instanceof SpectralArrow){
                // 检查是否是盗猎者的缓慢箭
                if (arrow.getOwner() instanceof ServerPlayer serverPlayer) {
                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.serverLevel());
                    if (gameWorld.isRole(serverPlayer, ModRoles.POACHER)) {
                        // 盗猎者的缓慢箭 - 应用缓慢效果
                        if (entityHitResult.getEntity() instanceof ServerPlayer target) {
                            handleSlowArrowHitEntity(target, arrow);
                            ci.cancel();
                            return;
                        }
                    } else if (gameWorld.isRole(serverPlayer, ModRoles.LIEMOREN)) {
                        // 猎魔人的猎魔箭 —— 双重伤害判定（不再是"巫毒式"无视护盾强制击杀）。
                        //
                        // 判定一：目标有护盾 → 只破盾，不致死（与狙击枪远距离一枪破盾一致）。
                        // 判定二：护盾已被判掉（或本来就没有）→ 正常击杀。
                        //
                        // 以前这里是 GameUtils.forceKillPlayer(...)，会绕过护盾与所有否决战
                        // （等效"巫毒魔法"）。改成走正常 killPlayer 管线后，护盾/无敌/否决战都重新生效。
                        if (entityHitResult.getEntity() instanceof ServerPlayer target) {
                            isHit = true;
                            arrow.discard();
                            ci.cancel();
                            noellesroles$huntArrowPierceAndKill(serverPlayer, target, arrow);
                            return;
                        }
                    } else {
                        // 游侠的光灵箭 - 显示发光效果
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0, false, false, true));
                        arrow.discard();
                        ci.cancel();
                        return;
                    }
                }
                return;
            }
            if (arrow instanceof Arrow) {
                // 检查是否是盗猎者的箭矢(通过射击者角色判断)
                if (arrow.getOwner() instanceof ServerPlayer serverPlayer) {
                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.serverLevel());

                    // 丘比特的再生箭 - 标记/绑定恋人（内部会判断角色与药水类型）
                    if (CupidPlayerComponent.handleArrowHit((Arrow) arrow, serverPlayer, player)) {
                        ci.cancel();
                        return;
                    }

                    // 检查是否是盗猎者
                    if (gameWorld.isRole(serverPlayer, ModRoles.POACHER)) {
                        // 盗猎者的普通箭/毒箭 - 直接击杀玩家，并立即销毁箭矢防止多杀
                        isHit = true;
                        GameUtils.killPlayer(player, true, serverPlayer, SRE.id("arrow"));
                        arrow.discard();
                        ci.cancel();
                        return;
                    }

                    // 游侠毒箭 - 击杀玩家
                    if (gameWorld.isRole(serverPlayer, ModRoles.ELF)) {
                        isHit = true;
                        GameUtils.killPlayer(player, true, serverPlayer, SRE.id("arrow"));
                    }

                    // 食尸鬼毒箭 - 击杀玩家
                    if (gameWorld.isRole(serverPlayer, ModRoles.GHOUL)) {
                        isHit = true;
                        GameUtils.killPlayer(player, true, serverPlayer, SRE.id("arrow"));
                        arrow.discard();
                        ci.cancel();
                        return;
                    }

                    // 猎魔人毒箭 - 击杀玩家
                    if (gameWorld.isRole(serverPlayer, ModRoles.LIEMOREN)) {
                        isHit = true;
                        GameUtils.killPlayer(player, true, serverPlayer, SRE.id("arrow"));
                        arrow.discard();
                        ci.cancel();
                        return;
                    }

                    // 掠夺者毒箭 - 击杀玩家
                    if (gameWorld.isRole(serverPlayer, ModRoles.LUEDUOZHE)) {
                        isHit = true;
                        GameUtils.killPlayer(player, true, serverPlayer, SRE.id("arrow"));
                        RaiderPlayerComponent raiderComp = ModComponents.RAIDER.get(serverPlayer);
                        if (raiderComp.inFrenzy) {
                            // 疯魔期间击杀特效：粒子 + 号角音效
                            if (serverPlayer.level() instanceof ServerLevel sl) {
                                sl.sendParticles(ParticleTypes.FLAME,
                                        player.getX(), player.getY() + 1, player.getZ(),
                                        15, 0.3, 0.5, 0.3, 0.1);
                                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                        player.getX(), player.getY() + 1, player.getZ(),
                                        12, 0.3, 0.5, 0.3, 0.05);
                                sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                                        player.getX(), player.getY() + 1.5, player.getZ(),
                                        20, 0.5, 0.8, 0.5, 0.3);
                                sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                                        SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8F, 1.2F);
                                sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                                        SoundEvents.PILLAGER_CELEBRATE, SoundSource.PLAYERS, 1.0F, 0.8F);
                            }
                        } else {
                            // 非疯魔：施加原版弩冷却30秒
                            serverPlayer.getCooldowns().addCooldown(Items.CROSSBOW, RaiderPlayerComponent.CROSSBOW_KILL_COOLDOWN);
                            raiderComp.crossbowKillCooldown = RaiderPlayerComponent.CROSSBOW_KILL_COOLDOWN;
                            raiderComp.sync();
                        }
                        arrow.discard();
                        ci.cancel();
                        return;
                    }
                }
            }
        }
    }

    private static boolean isHit = false;

    /**
     * 猎魔箭：<b>穿透护盾，直接击杀</b>。
     *
     * <p>猎魔箭的定位是"无视防御的处决箭"，所以命中时不再走"先破盾、下一箭才致死"，
     * 而是<b>先剥掉目标身上所有护盾层，再正常击杀</b>。
     *
     * <p>之所以是"先剥盾再击杀"而不是直接 {@code forceKillPlayer} 硬来：
     * <ol>
     *   <li>{@code GameMode.killPlayer} 里护盾会先吃掉这次死亡并提前 return
     *       （见 {@code SREArmorPlayerComponent} 分支），不先剥盾就杀不掉；</li>
     *   <li>走正常 {@code killPlayer} 能保留 {@code AllowPlayerDeath} 等否决战与
     *       回放记录、死亡统计等既有管线，只把"护盾"这一层穿透掉 ——
     *       不可被 {@code AllowPlayerDeath} 拦截的玩家（如某些无敌状态）依然拦得住。</li>
     * </ol>
     *
     * <p>剥掉的护盾层：
     * <ul>
     *   <li>{@link SREArmorPlayerComponent} —— 通用护盾（里昂草药、巫师、决斗胜者等）；</li>
     *   <li>{@code SREPlayerPsychoComponent} —— 疯魔护盾。</li>
     * </ul>
     */
    @Unique
    private static void noellesroles$huntArrowPierceAndKill(ServerPlayer shooter, ServerPlayer target,
            AbstractArrow arrow) {
        ServerLevel level = shooter.serverLevel();
        pierceAllShields(shooter, target, level);
        // 护盾已剥净 → 正常击杀管线
        GameUtils.killPlayer(target, true, shooter, SRE.id("hunt_arrow"));
    }

    /**
     * 剥掉目标身上所有护盾层；每剥掉一层都给双方反馈。
     *
     * @return 是否至少剥掉了一层
     */
    @Unique
    private static boolean pierceAllShields(ServerPlayer shooter, ServerPlayer target, ServerLevel level) {
        boolean pierced = false;

        // ① 通用护盾层
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(target);
        if (armor != null && armor.getArmor() > 0) {
            armor.armor = 0;
            armor.sync();
            OnShieldBroken.EVENT.invoker().onShieldBroken(target, shooter);
            pierced = true;
        }

        // ② 疯魔护盾层
        var psycho = SREPlayerPsychoComponent.KEY.get(target);
        if (psycho != null && psycho.getArmour() > 0) {
            psycho.setArmour(0);
            pierced = true;
        }

        if (pierced) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            ParticleFx.burst(level, ParticleTypes.ENCHANTED_HIT,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    24, 0.4, 0.4, 0.4, 0.05);
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.liemoren.hunt_arrow_pierced")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            shooter.displayClientMessage(
                    Component.translatable("message.noellesroles.liemoren.hunt_arrow_pierced_self")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
        return pierced;
    }

    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void noellesroles$onHitEntitTail(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (isHit) {
            AbstractArrow arrow = (AbstractArrow) (Object) this;
            arrow.discard();
            isHit = false;
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void noellesroles$onHitPlayerBody(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (entityHitResult.getEntity() instanceof PlayerBodyEntity) {
            AbstractArrow arrow = (AbstractArrow) (Object) this;
            arrow.discard();
        }
    }

    @Inject(method = "onHitBlock", at = @At("TAIL"))
    private void noellesroles$onHitBlock(BlockHitResult blockHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        AbstractArrow arrow = (AbstractArrow) (Object) this;

        // 检查是否是盗猎者的缓慢箭(通过射击者角色和箭矢类型判断)
        if (arrow instanceof SpectralArrow && arrow.getOwner() instanceof ServerPlayer shooter) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(shooter.serverLevel());
            if (gameWorld.isRole(shooter, ModRoles.POACHER)) {
                handleSlowArrowHitBlock(blockHitResult, arrow);
                return;
            }
        }

        if (arrow instanceof SpectralArrow arrow1) {
            if (arrow.getOwner() instanceof ServerPlayer serverPlayer) {
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.serverLevel());
                if (gameWorld.isRole(serverPlayer, ModRoles.LIEMOREN)) {
                    // 猎魔人的猎魔箭命中方块 - 直接销毁
                    arrow1.discard();
                    return;
                }
                if (gameWorld.isRole(serverPlayer, ModRoles.ELF)) {
                    // 获取箭矢击中的位置
                    BlockPos hitPos = blockHitResult.getBlockPos();
                    // 获取附近玩家列表(例如半径为5格)
                    List<ServerPlayer> nearbyPlayers = serverPlayer.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                            new AABB(hitPos).inflate(8));
                    // 输出附近玩家数量
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.arrow.near_by_players", nearbyPlayers.size())
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                    arrow1.discard();
                }
            }
        } else {
            arrow.discard();
        }
    }

    /**
     * 处理缓慢箭命中实体
     */
    private void handleSlowArrowHitEntity(ServerPlayer target, AbstractArrow arrow) {
        // 立即生成半径5格的缓慢1区域，持续10秒
        BlockPos hitPos = target.blockPosition();
        applySlowArea(target.serverLevel(), hitPos, 5, 1, 200); // 10秒 = 200tick

        // 被命中的玩家获得缓慢3和反胃效果5秒
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, false, true)); // 缓慢3 (等级2)
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true)); // 反胃

        // 区域内的所有玩家获得持续3秒的缓慢1效果
        List<ServerPlayer> nearbyPlayers = target.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                new AABB(hitPos).inflate(5));
        for (ServerPlayer player : nearbyPlayers) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true)); // 缓慢1, 3秒
        }

        arrow.discard();
    }

    /**
     * 处理缓慢箭命中方块
     */
    private void handleSlowArrowHitBlock(BlockHitResult blockHitResult, AbstractArrow arrow) {
        BlockPos hitPos = blockHitResult.getBlockPos();

        if (arrow.getOwner() instanceof ServerPlayer shooter) {
            // 第一阶段：初始半径2格，缓慢1，持续2秒
            applySlowArea(shooter.serverLevel(), hitPos, 2, 1, 40); // 2秒 = 40tick

            // 第二阶段：2秒后扩展到半径5格，缓慢1，持续8秒（总共10秒）
            // 使用Scheduler延迟40tick(2秒)后执行
            Scheduler.schedule(() -> {
                applySlowArea(shooter.serverLevel(), hitPos, 5, 1, 160); // 8秒 = 160tick
            }, 40);
        }

        arrow.discard();
    }

    /**
     * 应用缓慢区域效果
     * @param level 世界
     * @param center 中心位置
     * @param radius 半径
     * @param amplifier 效果等级
     * @param duration 持续时间(tick)
     */
    private void applySlowArea(net.minecraft.world.level.Level level, BlockPos center, int radius, int amplifier, int duration) {
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(center).inflate(radius));
        for (ServerPlayer player : nearbyPlayers) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier, false, false, true));
        }

        // 显示灰色药水粒子(稀疏密度)
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // 在区域中心生成稀疏的灰色药水粒子
            double particleCount = radius * 2; // 根据半径调整粒子数量，保持稀疏
            for (int i = 0; i < particleCount; i++) {
                double offsetX = (level.random.nextDouble() - 0.5) * 2 * radius;
                double offsetY = level.random.nextDouble() * 2;
                double offsetZ = (level.random.nextDouble() - 0.5) * 2 * radius;

                // 创建灰色粒子选项(RGB: 128, 128, 128 = 0x808080)
                int grayColor = 0x808080; // 灰色
                var particleOption = net.minecraft.core.particles.ColorParticleOption.create(
                    net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT,
                    grayColor
                );

                serverLevel.sendParticles(
                    particleOption,
                    center.getX() + 0.5 + offsetX,
                    center.getY() + 0.5 + offsetY,
                    center.getZ() + 0.5 + offsetZ,
                    1,     // 粒子数量
                    0.0,   // offsetX
                    0.0,   // offsetY
                    0.0,   // offsetZ
                    0.0    // 速度
                );
            }
        }
    }
}
