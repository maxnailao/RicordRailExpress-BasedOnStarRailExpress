package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 网络包：生物声纹标记（移植自"失明症"模组 EntitySoundEcho）
 * <p>
 * 服务端周期性扫描失明玩家附近的生物后下发：客户端据此在屏幕中心周围
 * 渲染方向性声纹标记（危险红/普通白/脚步灰），并以弱轮廓揭示声源附近方块，
 * 为失明玩家提供"以耳代目"的环境感知。
 */
public record SoundEchoS2CPacket(Vec3 soundPos, Category category, float strength, boolean occluded,
        BlockPos blockCenter, List<ContactRevealS2CPacket.Entry> entries) implements CustomPacketPayload {

    /** 声纹揭示条目数上限 */
    public static final int MAX_ENTRIES = 4;

    public static final Type<SoundEchoS2CPacket> ID = new Type<>(Noellesroles.id("sound_echo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SoundEchoS2CPacket> CODEC = StreamCodec
            .ofMember(SoundEchoS2CPacket::encode, SoundEchoS2CPacket::decode);

    /** 声源类别，决定标记颜色与揭示强度 */
    public enum Category {
        /** 敌对生物：红色标记，揭示强度最高 */
        DANGER,
        /** 普通生物：白色标记 */
        AMBIENT,
        /** 玩家脚步：灰色标记，揭示强度最低 */
        FOOTSTEP
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVec3(this.soundPos);
        buf.writeByte(this.category.ordinal());
        buf.writeFloat(this.strength);
        buf.writeBoolean(this.occluded);
        buf.writeBlockPos(this.blockCenter);
        buf.writeByte(this.entries.size());
        for (ContactRevealS2CPacket.Entry entry : this.entries) {
            entry.write(buf);
        }
    }

    public static SoundEchoS2CPacket decode(RegistryFriendlyByteBuf buf) {
        Vec3 soundPos = buf.readVec3();
        Category[] categories = Category.values();
        int categoryIndex = buf.readByte() & 0xFF;
        Category category = categoryIndex < categories.length ? categories[categoryIndex] : Category.AMBIENT;
        float strength = buf.readFloat();
        boolean occluded = buf.readBoolean();
        BlockPos blockCenter = buf.readBlockPos();
        int count = Math.min(buf.readByte() & 0xFF, MAX_ENTRIES);
        List<ContactRevealS2CPacket.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(ContactRevealS2CPacket.Entry.read(buf));
        }
        return new SoundEchoS2CPacket(soundPos, category, strength, occluded, blockCenter, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
