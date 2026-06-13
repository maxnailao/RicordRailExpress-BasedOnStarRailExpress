package org.agmas.noellesroles.content.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;

public class DurabilityBoatEntity extends Boat {

    /** 耐久值，初始 90，每秒 -1（即 90 秒存在时间） */
    public int durability = 90;

    public DurabilityBoatEntity(EntityType<? extends Boat> entityType, Level level) {
        super(entityType, level);
        // 强制橡木船外观
        this.setVariant(Type.OAK);
    }

    // ===== 耐久系统 =====
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        // 有乘客时才消耗耐久
        if (this.getFirstPassenger() != null) {
            if (this.level().getGameTime() % 20 == 0) {
                this.durability--;
            }
            if (this.durability <= 0) {
                this.ejectPassengers();
                this.discard();
                // 耐久耗尽无法通知具体玩家（已被弹射），此处仅销毁
            }
        }
    }

    // ===== Shift + 右键回收 =====
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // 无乘客 + Shift 按下 → 回收
        if (this.getPassengers().isEmpty() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                ItemStack boatItem = new ItemStack(ModItems.DURABILITY_BOAT);
                boatItem.setDamageValue(boatItem.getMaxDamage() - this.durability);
                if (!player.getInventory().add(boatItem)) {
                    player.drop(boatItem, false);
                }
                this.discard();
            }
            return InteractionResult.SUCCESS;
        }
        // 其他情况走原版逻辑（普通右键上船）
        return super.interact(player, hand);
    }

    // ===== NBT 持久化耐久 =====
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Durability", this.durability);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Durability")) {
            this.durability = tag.getInt("Durability");
        }
    }
}
