package org.agmas.noellesroles.mixin.roles.elf;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
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
                        // 猎魔人的猎魔箭 - 强制击杀（无视护盾和无敌）
                        if (entityHitResult.getEntity() instanceof ServerPlayer target) {
                            isHit = true;
                            GameUtils.forceKillPlayer(target, true, serverPlayer, SRE.id("hunt_arrow"));
                            arrow.discard();
                            ci.cancel();
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
