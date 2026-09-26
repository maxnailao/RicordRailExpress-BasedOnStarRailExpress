package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.Rpg7Item;
import org.agmas.noellesroles.content.item.Rpg7ShootPayload;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;

/**
 * RPG-7 火箭弹抛射物
 * <ul>
 *   <li>以接近弩箭的速度直线飞行，带重力下坠与烟雾/火焰拖尾粒子；</li>
 *   <li>命中方块或实体后引爆：C4 范围、严格不穿墙（视线被实体方块遮挡即无效），
 *       范围内存活的玩家被击杀；</li>
 *   <li>渲染见 {@link Rpg7RocketRenderer}（复用火箭弹物品模型）。</li>
 * </ul>
 */
public class Rpg7RocketEntity extends AbstractArrow {

    public Rpg7RocketEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public Rpg7RocketEntity(EntityType<? extends AbstractArrow> entityType, LivingEntity owner, Level level) {
        super(entityType, owner, level, ModItems.RPG7_AMMO.getDefaultInstance(), null);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    protected double getDefaultGravity() {
        return Rpg7Item.ROCKET_GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && !this.inGround) {
            // 火箭弹路径拖尾粒子
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        explode();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        explode();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ModItems.RPG7_AMMO.getDefaultInstance();
    }

    /** 服务端引爆：C4 范围、严格不穿墙。 */
    private void explode() {
        ServerLevel level = (ServerLevel) this.level();
        Vec3 center = this.position();

        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS, 5.0f, 1.0f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 2, 0, 0, 0, 0.2);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 60, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 40, 0.4, 0.4, 0.4, 0.05);

        double radius = Rpg7Item.BLAST_RADIUS;
        Player attacker = this.getOwner() instanceof Player p ? p : null;
        for (ServerPlayer victim : level.players()) {
            if (!victim.isAlive() || victim.isSpectator() || victim.isCreative()) {
                continue;
            }
            if (victim.distanceToSqr(center) > radius * radius) {
                continue;
            }
            if (!hasClearLineOfSight(level, center, victim)) {
                continue;
            }
            GameUtils.killPlayer(victim, true, attacker, Noellesroles.id("rpg_explosion"));
        }
        this.discard();
    }

    /** 从爆炸中心到目标（眼睛或身体中心）做射线检测，任一畅通即视为可命中（不穿墙）。 */
    private static boolean hasClearLineOfSight(ServerLevel level, Vec3 from, ServerPlayer victim) {
        Vec3 eye = victim.getEyePosition();
        Vec3 body = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
        return clearLine(level, from, eye) || clearLine(level, from, body);
    }

    private static boolean clearLine(ServerLevel level, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    // === 服务端射击 / 装填入口（由 Rpg7ShootPayload 调用） ===

    public static void handleAction(ServerPlayer player, Rpg7ShootPayload.Action action) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.RPG7)) {
            return;
        }
        switch (action) {
            case SHOOT -> handleShoot(player, stack);
            case RELOAD -> handleReload(player, stack);
        }
    }

    private static void handleShoot(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        if (Rpg7Item.getAmmoCount(stack) <= 0) {
            return;
        }

        if (!player.isCreative()) {
            Rpg7Item.setAmmoCount(stack, Rpg7Item.getAmmoCount(stack) - 1);
        }
        player.getCooldowns().addCooldown(stack.getItem(), Rpg7Item.COOLDOWN_TICKS);

        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();

        Rpg7RocketEntity rocket = new Rpg7RocketEntity(ModEntities.RPG7_ROCKET, player, level);
        rocket.setPos(eye.x + look.x * 0.5, eye.y + look.y * 0.5 - 0.1, eye.z + look.z * 0.5);
        rocket.shoot(look.x, look.y, look.z, Rpg7Item.ROCKET_SPEED, 0);
        level.addFreshEntity(rocket);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                NRSounds.RPG7_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void handleReload(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        if (Rpg7Item.getAmmoCount(stack) >= Rpg7Item.MAX_AMMO) {
            return;
        }

        boolean hasAmmo = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack inv = player.getInventory().getItem(i);
            if (inv.is(ModItems.RPG7_AMMO)) {
                if (!player.isCreative()) {
                    inv.shrink(1);
                }
                hasAmmo = true;
                break;
            }
        }
        if (!hasAmmo) {
            return;
        }

        Rpg7Item.setAmmoCount(stack, 1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.6f, 1f);
    }
}
