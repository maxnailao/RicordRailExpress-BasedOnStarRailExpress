package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 网络包：导盲杖探测揭示（移植自"失明症"模组 ContactReveal）
 * <p>
 * 服务端完成探测判定后下发给失明症玩家，客户端据此渲染发光方块轮廓。
 * 条目使用相对中心方块的 byte 偏移编码以压缩包体积；每个条目携带
 * 可见面掩码（每方向一位），客户端只勾勒真正可见的面，防止透墙作弊显示。
 */
public record ContactRevealS2CPacket(int sequence, BlockPos center, List<Entry> entries)
        implements CustomPacketPayload {

    /** 单次揭示条目数上限（中心块 + 6 邻接面） */
    public static final int MAX_ENTRIES = 7;

    public static final Type<ContactRevealS2CPacket> ID = new Type<>(Noellesroles.id("contact_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContactRevealS2CPacket> CODEC = StreamCodec
            .ofMember(ContactRevealS2CPacket::encode, ContactRevealS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.sequence);
        buf.writeBlockPos(this.center);
        buf.writeByte(this.entries.size());
        for (Entry entry : this.entries) {
            entry.write(buf);
        }
    }

    public static ContactRevealS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int sequence = buf.readVarInt();
        BlockPos center = buf.readBlockPos();
        int count = Math.min(buf.readByte() & 0xFF, MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(Entry.read(buf));
        }
        return new ContactRevealS2CPacket(sequence, center, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /**
     * 单条揭示条目：相对中心块的偏移 + 是否为中心块 + 可见面掩码。
     */
    public record Entry(byte dx, byte dy, byte dz, boolean center, byte faceMask) {

        public static Entry relativeTo(BlockPos center, BlockPos pos, boolean isCenter, int faceMask) {
            return new Entry((byte) (pos.getX() - center.getX()), (byte) (pos.getY() - center.getY()),
                    (byte) (pos.getZ() - center.getZ()), isCenter, (byte) faceMask);
        }

        public BlockPos resolve(BlockPos center) {
            return center.offset(this.dx, this.dy, this.dz);
        }

        /** 偏移与掩码的基础合法性校验，客户端接收时防御异常包 */
        public boolean isValid() {
            return Math.abs(this.dx) <= 8 && Math.abs(this.dy) <= 8 && Math.abs(this.dz) <= 8;
        }

        void write(FriendlyByteBuf buf) {
            buf.writeByte(this.dx);
            buf.writeByte(this.dy);
            buf.writeByte(this.dz);
            buf.writeBoolean(this.center);
            buf.writeByte(this.faceMask);
        }

        static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readByte());
        }
    }
}
