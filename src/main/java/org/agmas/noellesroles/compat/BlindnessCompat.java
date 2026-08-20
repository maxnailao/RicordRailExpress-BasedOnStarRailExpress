package org.agmas.noellesroles.compat;

import com.ikunkk02afk.blindness.component.BlindnessComponents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * “失明症”模组（ikunkk02-afk/blindness）兼容桥接。
 * 所有对 blindness 类的实际引用都放在内部类 BlindnessCompatImpl 中，
 * 由外层先做 isLoaded() 判断——模组缺失时内部类不会被类加载，避免 NoClassDefFoundError。
 */
public final class BlindnessCompat {

    public static final ResourceLocation GUIDANCE_CANE_ID = ResourceLocation.parse("blindness:guidance_cane");

    private BlindnessCompat() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded("blindness");
    }

    /** 开关玩家的失明体验（模组会自动同步到该玩家客户端） */
    public static void setBlind(Player player, boolean blind) {
        if (isLoaded()) {
            BlindnessCompatImpl.setBlind(player, blind);
        }
    }

    /** 给予导盲杖（背包已有则不给） */
    public static void giveGuidanceCane(Player player) {
        if (isLoaded()) {
            BlindnessCompatImpl.giveGuidanceCane(player);
        }
    }

    private static final class BlindnessCompatImpl {

        static void setBlind(Player player, boolean blind) {
            BlindnessComponents.PLAYER.get(player).setBlindnessEnabled(blind);
        }

        static void giveGuidanceCane(Player player) {
            var cane = BuiltInRegistries.ITEM.get(GUIDANCE_CANE_ID);
            if (cane == Items.AIR) {
                return;
            }
            boolean has = player.getInventory().items.stream().anyMatch(stack -> stack.is(cane));
            if (has) {
                return;
            }
            if (!player.getInventory().add(new ItemStack(cane))) {
                player.drop(new ItemStack(cane), false);
            }
        }
    }
}