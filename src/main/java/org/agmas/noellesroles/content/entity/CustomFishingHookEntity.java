package org.agmas.noellesroles.content.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.mixin.FishingHookAccessor;

import java.util.List;

/**
 * 自定义鱼钩实体 - 使用自定义奖励池代替原版钓鱼奖励
 */
public class CustomFishingHookEntity extends FishingHook {

    public static final ResourceKey<LootTable> CUSTOM_FISHING_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "gameplay/fishing"));

    public CustomFishingHookEntity(Player owner, Level level, int luckLevel, int lureLevel) {
        super(owner, level, luckLevel, lureLevel);
    }

    @Override
    public int retrieve(ItemStack usedItem) {
        Player player = this.getPlayerOwner();
        if (player == null) {
            this.discard();
            return 0;
        }

        // 如果鱼钩挂到了实体上，只拉回不生成奖励
        Entity hookedEntity = this.getHookedIn();
        if (hookedEntity != null) {
            hookedEntity.setDeltaMovement(hookedEntity.getDeltaMovement().add(0.0, 0.1, 0.0));
            player.fishing = null;
            this.discard();
            return 0;
        }

        int damageValue = 1;

        // 如果鱼咬钩了（nibble > 0 表示有鱼上钩），生成自定义奖励
        if (((FishingHookAccessor) this).getNibble() > 0 && !this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            LootTable lootTable = serverLevel.getServer()
                    .reloadableRegistries()
                    .getLootTable(CUSTOM_FISHING_LOOT);

            LootParams.Builder paramsBuilder = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, this.position())
                    .withParameter(LootContextParams.TOOL, usedItem)
                    .withParameter(LootContextParams.THIS_ENTITY, this)
                    .withLuck(EnchantmentHelper.getFishingLuckBonus(serverLevel, usedItem, this));

            List<ItemStack> loot = lootTable.getRandomItems(paramsBuilder.create(LootContextParamSets.FISHING));

            for (ItemStack item : loot) {
                ItemEntity itemEntity = new ItemEntity(
                        this.level(),
                        this.getX(), this.getY(), this.getZ(),
                        item);
                Vec3 motion = player.position().subtract(itemEntity.position()).scale(0.1);
                itemEntity.setDeltaMovement(motion.add(0.0, 0.2, 0.0));
                this.level().addFreshEntity(itemEntity);
            }

            if (!loot.isEmpty()) {
                // 给予经验
                this.level().addFreshEntity(
                        new ExperienceOrb(player.level(), player.getX(), player.getY() + 0.5,
                                player.getZ(), this.random.nextInt(6) + 1));

                // 根据距离计算耐久消耗
                double distance = this.position().distanceTo(player.position());
                damageValue = Math.max(1, (int) (distance * 3.0));
                damageValue = Math.min(damageValue, usedItem.getMaxDamage());
            }
        }

        // 移除鱼钩
        player.fishing = null;
        this.discard();

        return damageValue;
    }
}
