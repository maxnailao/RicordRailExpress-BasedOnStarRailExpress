package org.agmas.noellesroles.compat;

import com.ikunkk02afk.blindness.component.BlindnessComponents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 失明症模组兼容桥：所有对 blindness 类的引用放在内部类中懒加载，
 * 保证模组缺失时不会 NoClassDefFoundError。
 * fork 版失明症默认对所有人关闭，仅由盲女角色分配时开启。
 */
public final class BlindnessCompat {
    private BlindnessCompat() {}

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded("blindness");
    }

    public static void setBlind(Player player, boolean blind) {
        if (!isLoaded()) return;
        Impl.setBlind(player, blind);
    }

    public static boolean isBlind(Player player) {
        if (!isLoaded()) return false;
        return Impl.isBlind(player);
    }

    public static void giveGuidanceCane(Player player) {
        if (!isLoaded()) return;
        Impl.giveGuidanceCane(player);
    }

    public static ItemStack guidanceCaneStack() {
        if (!isLoaded()) return ItemStack.EMPTY;
        return Impl.guidanceCaneStack();
    }

    public static boolean isGuidanceCane(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isLoaded()) return false;
        return Impl.isGuidanceCane(stack);
    }

    private static final class Impl {
        private static final Item GUIDANCE_CANE = BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath("blindness", "guidance_cane"))
                .orElse(null);

        static ItemStack guidanceCaneStack() {
            return GUIDANCE_CANE == null ? ItemStack.EMPTY : new ItemStack(GUIDANCE_CANE);
        }

        static boolean isGuidanceCane(ItemStack stack) {
            return GUIDANCE_CANE != null && stack.is(GUIDANCE_CANE);
        }

        static void setBlind(Player player, boolean blind) {
            BlindnessComponents.PLAYER.get(player).setBlindnessEnabled(blind);
        }

        static boolean isBlind(Player player) {
            return BlindnessComponents.PLAYER.get(player).blindnessEnabled();
        }

        static void giveGuidanceCane(Player player) {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("blindness", "guidance_cane"))
                    .ifPresent(item -> {
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            if (player.getInventory().getItem(i).is(item)) {
                                return;
                            }
                        }
                        ItemStack stack = new ItemStack(item);
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                    });
        }
    }
}