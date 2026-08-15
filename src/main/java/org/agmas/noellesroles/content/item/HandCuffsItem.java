package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HandCuffsItem extends Item {
    public HandCuffsItem(Item.Properties settings) {
        this(settings, 10);
    }

    public HandCuffsItem(Item.Properties settings, int durability) {
        super(settings.durability(durability));
    }

    public static final ResourceLocation SLOT_HANDCUFFS = SRE.id("handcuffs");

    /** CUSTOM_DATA 中标记"从前面铐住"的键 */
    public static final String TAG_CUFFED_FRONT = "noellesroles:cuffed_front";

    public static void putOnHandCuff(Player player, ItemStack stack) {
        ExtraSlotComponent.setSlot(player, SLOT_HANDCUFFS, stack);
    }

    public static ItemStack getHandCuffItemStack(Player player) {
        return ExtraSlotComponent.getSlot(player, SLOT_HANDCUFFS);
    }

    public static ItemStack putOffHandCuff(Player player) {
        return ExtraSlotComponent.removeSlot(player, SLOT_HANDCUFFS);
    }

    public static boolean hasHandCuff(Player player) {
        return ExtraSlotComponent.getSlot(player, SLOT_HANDCUFFS).getItem() instanceof HandCuffsItem;
    }

    /**
     * 是否是从前面铐住的（双手应在身前相交）
     */
    public static boolean isCuffedFromFront(Player player) {
        ItemStack stack = getHandCuffItemStack(player);
        if (!(stack.getItem() instanceof HandCuffsItem)) {
            return false;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean(TAG_CUFFED_FRONT);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int slot, boolean bl) {
        if (itemStack.getItem() instanceof HandCuffsItem) {
            if (entity instanceof Player player) {
                if (hasHandCuff(player)) {
                    if (!player.isSpectator()) {
                        player.addEffect(new MobEffectInstance(
                                MobEffects.MOVEMENT_SLOWDOWN,
                                (int) (20), // 持续时间（tick）
                                3, // 等级（0 = 速度 I）
                                false, // ambient（环境效果，如信标）
                                true, // showParticles（显示粒子）
                                true // showIcon（显示图标）
                        ));
                    }

                    if (!level.isClientSide && player.isShiftKeyDown() && level.getGameTime() % 20 == 0) {
                        ExtraSlotComponent.hurtAndBreak(player, itemStack, 1, SLOT_HANDCUFFS);
                    }
                }
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand interactionHand) {

        if (hasHandCuff(user))
            return InteractionResultHolder.pass(user.getItemInHand(interactionHand));
        if (user.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.pass(user.getItemInHand(interactionHand));
        user.getCooldowns().addCooldown(this, 20);

        return super.use(level, user, interactionHand);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity,
            InteractionHand hand) {
        if (hasHandCuff(user))
            return InteractionResult.PASS;
        if (user.getCooldowns().isOnCooldown(this))
            return InteractionResult.PASS;
        user.getCooldowns().addCooldown(this, 20);
        if (user.level().isClientSide)
            return InteractionResult.SUCCESS;
        if (entity instanceof Player target) {
            if (hasHandCuff(target)) {
                user.displayClientMessage(
                        Component.translatable("item.noellesroles.handcuffs.failed", user.getName())
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.FAIL;
            }
            ItemStack cuffStack = stack.copy();
            // 判断是否从前面铐住：使用者处于被铐者正面朝向内
            if (isInFrontOf(target, user)) {
                CompoundTag tag = cuffStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putBoolean(TAG_CUFFED_FRONT, true);
                cuffStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            putOnHandCuff(target, cuffStack);
            stack.shrink(1);
            user.displayClientMessage(Component.translatable("item.noellesroles.handcuffs.put", target.getName())
                    .withStyle(ChatFormatting.GOLD), true);
            target.displayClientMessage(
                    Component.translatable("item.noellesroles.handcuffs.recieved", user.getName())
                            .withStyle(ChatFormatting.RED),
                    true);
        } else {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 判断 subject 是否位于 target 的正面朝向内（水平面点积 > 0）
     */
    private static boolean isInFrontOf(Player target, LivingEntity subject) {
        Vec3 look = target.getViewVector(1.0F);
        Vec3 toSubject = subject.position().subtract(target.position());
        return look.x * toSubject.x + look.z * toSubject.z > 0;
    }
}
