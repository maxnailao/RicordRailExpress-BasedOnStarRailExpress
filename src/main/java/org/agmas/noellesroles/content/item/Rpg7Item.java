package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeRevolver;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * RPG-7 火箭筒
 * - 右键发射火箭弹（抛射物，带下坠与烟雾拖尾，命中方块/实体引爆）
 * - 左键装填（最高 1 发，自动消耗背包中的火箭弹）
 * - 初始无弹药，装填后模型显示炮弹部分（通过 custom_model_data 切换模型）
 * - 持枪动作用左轮手枪的抬手动作（{@link HeldLikeRevolver}）
 */
public class Rpg7Item extends Item implements HeldLikeRevolver {
    public static final int MAX_AMMO = 1;

    /** 火箭弹飞行速度（可动态调整，默认接近弩箭速度） */
    public static float ROCKET_SPEED = 4.0f;
    /** 火箭弹下坠（每 tick 向下的速度增量，默认同弩箭 0.05；越大下坠越快，0 则直线飞行） */
    public static double ROCKET_GRAVITY = 0.03;
    /** 爆炸半径（可动态调整，默认同 C4） */
    public static float BLAST_RADIUS = 7.0f;
    /** 射击冷却（tick） */
    public static int COOLDOWN_TICKS = 20 * 3;

    public Rpg7Item(Properties settings) {
        super(settings.stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (user.isSpectator() || !user.isAlive()) {
            return InteractionResultHolder.fail(stack);
        }
        if (user.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }

        if (getAmmoCount(stack) <= 0) {
            if (world.isClientSide) {
                user.displayClientMessage(
                        Component.translatable("message.noellesroles.rpg7.empty").withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (world.isClientSide) {
            SREGameWorldComponent gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                SRERole role = gameComponent.getRole(user);
                if (role != null && !role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }
            // 后坐力
            user.setXRot(user.getXRot() - 3);
            ClientPlayNetworking.send(new Rpg7ShootPayload(Rpg7ShootPayload.Action.SHOOT));
        } else {
            SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(world);
            SRERole role = gameComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    // === 弹药管理 ===

    public static int getAmmoCount(ItemStack stack) {
        return stack.getOrDefault(SREDataComponentTypes.AMMO_COUNT, 0);
    }

    public static void setAmmoCount(ItemStack stack, int count) {
        int clamped = Math.min(count, MAX_AMMO);
        stack.set(SREDataComponentTypes.AMMO_COUNT, clamped);
        updateModelData(stack, clamped > 0);
    }

    /** 依据是否装填切换 custom_model_data（1=显示炮弹，0=隐藏炮弹） */
    public static void updateModelData(ItemStack stack, boolean loaded) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(loaded ? 1 : 0));
    }

    // === 换弹（由 MouseHandlerMixin 在左键时调用） ===

    public static void tryReloadFromClient(Player user) {
        ItemStack stack = user.getMainHandItem();
        if (!stack.is(ModItems.RPG7))
            return;
        if (user.getCooldowns().isOnCooldown(stack.getItem()))
            return;
        if (getAmmoCount(stack) >= MAX_AMMO)
            return;

        boolean hasAmmo = false;
        for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
            if (user.getInventory().getItem(i).is(ModItems.RPG7_AMMO)) {
                hasAmmo = true;
                break;
            }
        }
        if (!hasAmmo)
            return;

        ClientPlayNetworking.send(new Rpg7ShootPayload(Rpg7ShootPayload.Action.RELOAD));
        user.getCooldowns().addCooldown(ModItems.RPG7, 20);
    }

    // === Tooltip / 耐久条 ===

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.rpg7.ammo", getAmmoCount(stack), MAX_AMMO)
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return getAmmoCount(stack) > 0 ? 13 : 0;
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return 0xFFAA00;
    }
}
