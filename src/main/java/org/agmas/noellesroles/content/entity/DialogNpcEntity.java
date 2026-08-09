package org.agmas.noellesroles.content.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.dialog.DialogDataManager;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * 对话 NPC 实体 —— 玩家人物形态的可对话交互对象。
 * <p>
 * 由 {@link org.agmas.noellesroles.content.item.DialogNpcItem} 放置生成，
 * 外观为完整的玩家模型（皮肤贴图位于
 * {@code noellesroles:textures/entity/dialog_npc/<skin>.png}），
 * 玩家右键后服务端读取 {@code <world>/train_dialogs/<dialogId>.json}
 * 对话配置并发送 S2C 包打开对话界面。
 * <p>
 * 实体不可被普通攻击伤害，仅创造模式玩家攻击可将其移除；
 * 创造模式手持对话角色物品右键可重新绑定对话配置。
 */
public class DialogNpcEntity extends LivingEntity {

    private static final EntityDataAccessor<String> DIALOG_ID = SynchedEntityData.defineId(
            DialogNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_ID = SynchedEntityData.defineId(
            DialogNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(
            DialogNpcEntity.class, EntityDataSerializers.BOOLEAN);

    public DialogNpcEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DIALOG_ID, DialogDataManager.DEFAULT_DIALOG_ID);
        builder.define(SKIN_ID, "");
        builder.define(SLIM, false);
    }

    public String getDialogId() {
        return this.entityData.get(DIALOG_ID);
    }

    public void setDialogId(String dialogId) {
        this.entityData.set(DIALOG_ID, dialogId == null ? DialogDataManager.DEFAULT_DIALOG_ID : dialogId);
    }

    public String getSkinId() {
        return this.entityData.get(SKIN_ID);
    }

    public void setSkinId(String skinId) {
        this.entityData.set(SKIN_ID, skinId == null ? "" : skinId);
    }

    /** 是否使用 Alex（细臂）模型，由对话 JSON 的 "slim" 字段决定 */
    public boolean isSlim() {
        return this.entityData.get(SLIM);
    }

    public void setSlim(boolean slim) {
        this.entityData.set(SLIM, slim);
    }

    @Override
    public void tick() {
        super.tick();
        // 服务端定期根据对话配置刷新名字与皮肤（带缓存，开销极小）
        if (!this.level().isClientSide && this.tickCount % 20 == 5 && this.getServer() != null) {
            DialogDataManager.refreshEntityFromConfig(this.getServer(), this);
        }
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.level().isClientSide) {
            // 创造模式手持对话角色物品右键：重新绑定对话配置
            if (stack.is(ModItems.DIALOG_NPC_ITEM) && player.hasInfiniteMaterials()) {
                String newId = org.agmas.noellesroles.content.item.DialogNpcItem.readDialogId(stack);
                this.setDialogId(newId);
                this.setCustomName(null);
                DialogDataManager.refreshEntityFromConfig(player.level().getServer(), this);
                player.sendSystemMessage(Component.translatable(
                        "message.noellesroles.dialog_npc.rebound", newId));
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                DialogDataManager.openDialog(serverPlayer, this);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // 仅创造模式玩家可以移除该实体
        if (source.getEntity() instanceof Player player && player.hasInfiniteMaterials()) {
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // 不推动其他实体
    }

    @Override
    public void push(@NotNull Entity entity) {
        // 不被推动也不推动别人
    }

    @Override
    public void push(double x, double y, double z) {
        // 免疫外力推动
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, @NotNull DamageSource source) {
        return false;
    }

    @Override
    public void travel(@NotNull Vec3 travelVector) {
        // NPC 原地站立，不做位移
        if (this.isInWater()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1, 0, 1));
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("DialogId", this.getDialogId());
        compound.putString("NpcSkin", this.getSkinId());
        compound.putBoolean("SlimModel", this.isSlim());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setDialogId(compound.getString("DialogId"));
        this.setSkinId(compound.getString("NpcSkin"));
        this.setSlim(compound.getBoolean("SlimModel"));
    }

    @Override
    public @NotNull Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public @NotNull ItemStack getItemBySlot(@NotNull EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(@NotNull EquipmentSlot slot, @NotNull ItemStack stack) {
        // 不装备任何物品
    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
