/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 蕾米莉亚的血仆（大小姐的仆从）。
 *
 * <p>
 * 由蕾米莉亚击杀玩家后转换而来：中立偏狼阵营（{@code setNeutralForKiller(true)}），
 * 存活时间由 {@code RemiliaBloodServantRoleData.MAX_ALIVE}（180 秒）控制，
 * 惧怕水与强光（见 <code>RemiliaBloodServantRoleData#envCheck</code>）。
 * </p>
 *
 * <p>
 * 移植自 StarRailExpress 的 {@code THRemiliaServantRole}。RKD 侧差异：
 * </p>
 * <ul>
 * <li>RKD 的 {@code SRERole} 没有 {@code setCanEarnKillerCoinAwardsFromKills}，省略；</li>
 * <li>RKD 的 {@code SRERole} 没有 {@code canIncreaseSurvivingKillers()}，省略
 * （该标志只影响「存活杀手数」统计，不影响本职业的可玩性）；</li>
 * <li>RKD 没有 {@code MoneyUtils} / {@code clearTaskRewardTracking}，
 * 起始金币改用 {@link SREPlayerShopComponent#setBalance(int)}。</li>
 * </ul>
 */
public class THRemiliaServantRole extends TouhouRole {

    public THRemiliaServantRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        setCanAutoAddMoney(true);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        // RKD 的 KillerKnifeShopEntry 只接受价格（刀本身在构造里写死）
        SHOP.add(new KillerKnifeShopEntry(SREConfig.instance().knifePrice));
        SHOP.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(),
                SREConfig.instance().revolverPrice, ShopEntry.Type.WEAPON));
        SHOP.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(),
                SREConfig.instance().grenadePrice, ShopEntry.Type.WEAPON));
        // 关灯：SRE 用 ShopContent.getBlackoutShopEntry()，RKD 没有这个静态方法，按默认商店写法内联
        SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(),
                SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        return SHOP;
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
        // 起始金币清零，并免费给一把刀
        SREPlayerShopComponent.KEY.get(serverPlayer).setBalance(0);
        MCItemsUtils.insertStackInFreeSlot(serverPlayer, TMMItems.KNIFE.getDefaultInstance());
    }
}
