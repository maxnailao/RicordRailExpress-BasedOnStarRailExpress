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

package org.agmas.noellesroles.content.item.ora;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 欧拉一拳连打状态的<b>客户端镜像</b>，作为数据组件挂在欧拉一拳物品上。
 * <p>
 * 服务端权威状态在 {@link OraPunchManager} 的静态表里；这里只是给客户端看的一份快照。
 * 走物品组件是因为手持物品栏本来就会自动同步给「所有能看见持有者的玩家」
 * （{@code ItemStack.matches} 一比不同就重发装备包），既不用写新的网络包，
 * 也不依赖任何职业数据——这正是「所有人都能用」所需要的。
 * <p>
 * {@link #owner} 与 {@link #rushEndGameTime} 同时兼作防串味用的校验：物品被丢掉 / 被别人捡走时，
 * 快照还留在物品上，靠这两个字段就能判定它跟当前持有者无关（或早就过期了），不会出现
 * 「捡到别人的欧拉一拳，手臂就一直在疯狂挥拳」这种残留。
 *
 * @param owner           发起连打的玩家
 * @param bound           是否已经绑定到目标（false 表示还在寻找目标）
 * @param punchCount      已经打中的次数
 * @param rushEndGameTime 连打截止的世界时间（tick），未绑定目标时为 0
 */
public record OraRushState(UUID owner, boolean bound, int punchCount, long rushEndGameTime) {

    public static final Codec<OraRushState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(OraRushState::owner),
            Codec.BOOL.optionalFieldOf("bound", false).forGetter(OraRushState::bound),
            Codec.INT.optionalFieldOf("punch_count", 0).forGetter(OraRushState::punchCount),
            Codec.LONG.optionalFieldOf("rush_end", 0L).forGetter(OraRushState::rushEndGameTime))
            .apply(instance, OraRushState::new));

    /** 连打是否已超时（只有绑定了目标才有倒计时）。 */
    public boolean isExpired(Level level) {
        return this.bound && this.rushEndGameTime > 0 && level != null
                && level.getGameTime() >= this.rushEndGameTime;
    }

    /** 剩余秒数，给 HUD 用。 */
    public float remainingSeconds(Level level) {
        if (this.rushEndGameTime <= 0 || level == null) {
            return 0.0F;
        }
        return Math.max(0.0F, (this.rushEndGameTime - level.getGameTime()) / 20.0F);
    }
}
