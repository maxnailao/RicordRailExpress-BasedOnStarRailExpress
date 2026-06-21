package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface GoodsContainer {
   List<ShopEntry> getShops();

   void addItem(ShopEntry shopEntry);

   void removeItem(ItemStack itemStack);

   boolean removeItemStack(int stackid);
}
