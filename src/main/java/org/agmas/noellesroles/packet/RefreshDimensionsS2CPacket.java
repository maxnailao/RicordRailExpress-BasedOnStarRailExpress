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

package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 让客户端重新计算玩家尺寸 / 眼高（{@code Player#refreshDimensions}）。
 * <p>
 * 实体伪装会改眼高，服务端改完后客户端需要同 tick 之后自己刷一遍，否则相机、名牌会停在上一个
 * 实体的眼高上。服务端在同步完伪装状态后再补发本包（见 {@code GameUtils.refreshPlayerDimension}）。
 */
public record RefreshDimensionsS2CPacket() implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "refresh_dimensions");
    public static final Type<RefreshDimensionsS2CPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshDimensionsS2CPacket> CODEC;

    public RefreshDimensionsS2CPacket() {
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {

    }

    public static RefreshDimensionsS2CPacket read(FriendlyByteBuf buf) {
        return new RefreshDimensionsS2CPacket();
    }


    static {
        CODEC = StreamCodec.ofMember(RefreshDimensionsS2CPacket::write, RefreshDimensionsS2CPacket::read);
    }
}
