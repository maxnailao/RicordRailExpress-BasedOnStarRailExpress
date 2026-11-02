package org.agmas.noellesroles.role.killer;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.ReconBotRoleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 探路机器（杀手阵营）
 * - 杀手阵营（isInnocent=false, canUseKiller=true）→ 自带杀手透视（本能）+ 杀手心情条
 * - 专属商店：杀手默认商店但【移除撬锁器】
 * - 主动技能「钩爪」：见 {@link ReconBotRoleData}（服务端权威拉取物理）
 * - 狂暴模式改为持【双枪】（双枪客同款左右手），狂暴结束清除双枪
 */
public class ReconBotRole extends NormalRole {

    public ReconBotRole(ResourceLocation identifier, int color, MoodType moodType,
                        int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, false, true, moodType, maxSprintTime, canSeeTime);
    }

    // ===== 商店：杀手默认商店，去掉撬锁器 =====
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = ShopContent.getDefaultKnifeEntries(); // 可变副本
        shop.removeIf(entry -> entry.stack().is(TMMItems.LOCKPICK));
        return shop;
    }

    // ===== 狂暴（疯魔）：持双枪，结束清除 =====
    @Override
    public Item getPsychoItem() {
        // 主手武器取右手枪（供默认强制选槽/清理逻辑使用）
        return ModItems.DUAL_PISTOL_RIGHT;
    }

    @Override
    public List<Item> getPsychoSupportedWeapons(@NotNull Player player) {
        // 疯魔期间兼容武器为双枪，避免切槽包被服务端丢弃导致打不了人
        return List.of(ModItems.DUAL_PISTOL_RIGHT, ModItems.DUAL_PISTOL_LEFT);
    }

    @Override
    public boolean onPsychoGiveItem(@NotNull Player player, @NotNull SREPlayerPsychoComponent psychoComponent) {
        // 主手右手枪 + 副手左手枪
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DUAL_PISTOL_RIGHT));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.DUAL_PISTOL_LEFT));
        return true;
    }

    @Override
    public void onPsychoOver(@NotNull Player player, @NotNull SREPlayerPsychoComponent psychoComponent) {
        // 狂暴结束：清除双枪（右手枪由父流程 clearItem 清理，这里再兜底清左右手，避免残留）
        org.agmas.noellesroles.utils.MCItemsUtils.clearItem(player, ModItems.DUAL_PISTOL_RIGHT);
        org.agmas.noellesroles.utils.MCItemsUtils.clearItem(player, ModItems.DUAL_PISTOL_LEFT);
        if (player.getOffhandItem().is(ModItems.DUAL_PISTOL_LEFT)) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    // ===== 技能入口：G 键发射钩爪 / 拉取中再次按下断开 =====
    public static boolean triggerGrapple(RoleSkillContext ctx) {
        ServerPlayer player = ctx.player();
        if (player.isSpectator()) {
            return false;
        }
        ReconBotRoleData data = ReconBotRoleData.of(player);
        if (data == null) {
            return false;
        }
        // toggleable：拉取中再按技能键 = 断开（保留动能甩出）
        if (data.isGrappling()) {
            data.detachKeepMomentum();
            return true;
        }
        // 未在拉取中：必须技能就绪(冷却结束)才能再次发射，避免 toggleable 在 CD 内重复放钩
        if (!ctx.skillReady()) {
            return false;
        }
        return data.fireHook();
    }
}