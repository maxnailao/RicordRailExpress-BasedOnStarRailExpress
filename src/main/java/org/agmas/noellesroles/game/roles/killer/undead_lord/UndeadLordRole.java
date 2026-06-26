package org.agmas.noellesroles.game.roles.killer.undead_lord;

import java.util.ArrayList;
import java.util.List;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.NotNull;

/**
 * 亡灵之主（杀手阵营，控场 / 滚雪球）。
 * 专属商店出售亡灵延命药剂、瘟疫之雾、亡者召唤符、感染增幅器、灵魂锁链、时之沙漏。
 */
public class UndeadLordRole extends NormalRole {

    public UndeadLordRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean hideScoreboard) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, hideScoreboard);
    }

    private static NoellesRolesConfig config() {
        return NoellesRolesConfig.HANDLER.instance();
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> entries = new ArrayList<>();

        // 亡灵延命药剂：所有现存亡灵 +30 秒
        entries.add(effectEntry(Items.GLISTERING_MELON_SLICE, 80, "life_elixir", comp -> {
            comp.extendAllUndead(30 * 20);
        }));

        // 瘟疫之雾：在所在位置释放毒雾
        entries.add(effectEntry(Items.FERMENTED_SPIDER_EYE, 120, "plague_fog", comp -> {
            comp.releasePlagueFog(config().undeadLordFogSeconds * 20);
        }));

        // 亡者召唤符：立即召唤 2 个临时亡灵（45 秒，无需尸体）
        entries.add(effectEntry(Items.WITHER_SKELETON_SKULL, 150, "summon_charm", comp -> {
            comp.summonTemporaryUndead(2, config().undeadLordCharmLifetimeSeconds * 20);
        }));

        // 感染增幅器：接下来 60 秒内亡灵攻击感染翻倍
        entries.add(effectEntry(Items.BLAZE_POWDER, 100, "infection_amp", comp -> {
            comp.startInfectionAmp(config().undeadLordAmpSeconds * 20);
        }));

        // 灵魂锁链：绑定一个亡灵跟随自己 20 秒
        entries.add(effectEntry(Items.CHAIN, 60, "soul_chain", comp -> {
            comp.soulChainNearest(config().undeadLordSoulChainSeconds * 20);
        }));

        // 时之沙漏：重置所有现存亡灵持续时间为满值
        entries.add(effectEntry(Items.CLOCK, 200, "hourglass", comp -> {
            comp.resetAllUndeadLifetime();
        }));

        return entries;
    }

    /**
     * 构造一个“即时效果”商店条目：购买后只执行效果、扣金币，不向背包发放物品。
     */
    private ShopEntry effectEntry(net.minecraft.world.item.Item icon, int price, String nameKey,
            java.util.function.Consumer<UndeadLordPlayerComponent> effect) {
        ItemStack stack = new ItemStack(icon);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.translatable("shop.noellesroles.undead_lord." + nameKey));
        return new ShopEntry(stack, price, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                if (!(player instanceof ServerPlayer serverPlayer)) {
                    return false;
                }
                SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
                if (!gameWorldComponent.isRole(player, UndeadLordRole.this)) {
                    return false;
                }
                UndeadLordPlayerComponent comp = UndeadLordPlayerComponent.KEY.maybeGet(serverPlayer).orElse(null);
                if (comp == null) {
                    return false;
                }
                effect.accept(comp);
                player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                        SoundSource.PLAYERS, 0.8f, 0.8f);
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.undead_lord.shop_used")
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        true);
                return true;
            }
        };
    }
}
