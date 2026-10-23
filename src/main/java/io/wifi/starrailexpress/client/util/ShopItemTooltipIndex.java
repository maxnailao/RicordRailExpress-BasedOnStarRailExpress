package io.wifi.starrailexpress.client.util;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.RoleInitialItems;
import org.agmas.noellesroles.init.RoleShopHandler;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Builds the client-side index used to show which roles sell or start with an item. */
public final class ShopItemTooltipIndex {
    private static long builtVersion = -1;
    private static Map<Item, List<SRERole>> shopRolesByItem = Map.of();
    private static Map<Item, List<SRERole>> startRolesByItem = Map.of();

    private ShopItemTooltipIndex() {}

    public static List<SRERole> rolesFor(Item item) {
        return lookup(item, shopRolesByItem, true);
    }

    public static List<SRERole> startingRolesFor(Item item) {
        return lookup(item, startRolesByItem, false);
    }

    private static List<SRERole> lookup(Item item, Map<Item, List<SRERole>> index, boolean shop) {
        if (item == null || item == Items.AIR) return List.of();
        ensureBuilt();
        return index.getOrDefault(item, List.of());
    }

    private static void ensureBuilt() {
        long version = RoleShopHandler.shopVersion;
        if (builtVersion == version && (!shopRolesByItem.isEmpty() || !startRolesByItem.isEmpty())) return;
        Map<Item, List<SRERole>> shops = new IdentityHashMap<>();
        Map<Item, List<SRERole>> starts = new IdentityHashMap<>();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (role == null) continue;
            try {
                List<ShopEntry> entries = ShopContent.getShopEntries(role.identifier());
                if (entries != null) for (ShopEntry entry : entries) {
                    if (entry != null) add(shops, entry.stack(), role);
                }
            } catch (Exception ignored) {}
            try {
                List<ItemStack> items = RoleInitialItems.getInitialItemsForRole(role);
                if (items != null) for (ItemStack stack : items) add(starts, stack, role);
            } catch (Exception ignored) {}
        }
        sort(shops);
        sort(starts);
        shopRolesByItem = shops;
        startRolesByItem = starts;
        builtVersion = version;
    }

    private static void add(Map<Item, List<SRERole>> index, ItemStack stack, SRERole role) {
        if (stack == null || stack.isEmpty() || stack.is(Items.AIR) || stack.is(Items.BARRIER)) return;
        List<SRERole> roles = index.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>());
        if (!roles.contains(role)) roles.add(role);
    }

    private static void sort(Map<Item, List<SRERole>> index) {
        Comparator<SRERole> comparator = Comparator.comparing(role -> RoleUtils.getRoleName(role).getString(), String.CASE_INSENSITIVE_ORDER);
        index.values().forEach(list -> list.sort(comparator));
    }
}
