package io.wifi.starrailexpress.content.entity;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.packet.CustomParticleS2CPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Vector3f;

import java.util.List;

/**
 * 神罚弓射出的箭：外观复用原版箭渲染（在别人眼里就是一根箭），
 * 飞行时每 tick 在客户端沿箭体喷出天蓝/金黄粒子，形成"跟着箭走"的发光拖尾，
 * 与蓄力时的搭箭粒子箭相呼应，如同把那支粒子箭射了出去。
 */
public class ShenfaArrowEntity extends Arrow {

    private static final DustParticleOptions SHAFT =
            new DustParticleOptions(new Vector3f(0.30F, 0.82F, 1.00F), 1.0F); // 天蓝：拖尾
    private static final DustParticleOptions TIP =
            new DustParticleOptions(new Vector3f(1.00F, 0.85F, 0.20F), 1.2F);  // 金黄：头部高光

    public ShenfaArrowEntity(EntityType<? extends ShenfaArrowEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        // 击杀特效（仅击杀者可见）：神罚箭射死玩家时，只向箭的owner发圣剑天降的自定义形状粒子包
        if (level().isClientSide) {
            return;
        }
        if (entityHitResult.getEntity() instanceof ServerPlayer victim
                && (!victim.isAlive() || victim.getHealth() <= 0.0F)) {
            if (getOwner() instanceof ServerPlayer killer) {
                PacketTracker.sendToClient(killer, new CustomParticleS2CPayload(List.of(
                        CustomParticleS2CPayload.Entry.at(SRE.id("shenfa_holy_sword"),
                                victim.getX(), victim.getY() + 1.0D, victim.getZ(), 0))));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !isRemoved()) {
            // 天蓝发光拖尾（跟随箭体位置，箭走它就走）
            for (int i = 0; i < 2; i++) {
                level().addParticle(SHAFT,
                        getX() + (random.nextFloat() - 0.5F) * 0.12F,
                        getY() + (random.nextFloat() - 0.5F) * 0.12F,
                        getZ() + (random.nextFloat() - 0.5F) * 0.12F,
                        0.0D, 0.0D, 0.0D);
            }
            // 头部金黄高光，模拟箭镞能量
            level().addParticle(TIP,
                    getX() + (random.nextFloat() - 0.5F) * 0.06F,
                    getY() + (random.nextFloat() - 0.5F) * 0.06F,
                    getZ() + (random.nextFloat() - 0.5F) * 0.06F,
                    0.0D, 0.0D, 0.0D);
            // 少量白色 END_ROD 点缀
            if (random.nextFloat() < 0.5F) {
                level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }
}