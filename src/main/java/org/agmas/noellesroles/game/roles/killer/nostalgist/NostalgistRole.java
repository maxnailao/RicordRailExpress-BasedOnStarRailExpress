package org.agmas.noellesroles.game.roles.killer.nostalgist;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 怀旧者（里世界·杀手阵营）。
 *
 * <p>当场上存在一名以上杀手时，怀旧者处于「里世界」：视角灰白，对所有阵营隐身、
 * 奔跑无声无粒子、无法被看见/听见/攻击；但身处里世界时无法击杀任何人，只能潜行、开锁与侦察。
 * 当场上仅剩怀旧者一名杀手时，里世界崩塌，怀旧者现身为普通杀手（见
 * {@link NostalgistPlayerComponent}）。
 *
 * <p>商店为通用杀手商店（{@link ShopContent#defaultKnifeEntries}）。
 */
public class NostalgistRole extends NormalRole {

    public NostalgistRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        // 通用杀手商店（与默认刀手商店一致）
        return ShopContent.defaultKnifeEntries;
    }
}
