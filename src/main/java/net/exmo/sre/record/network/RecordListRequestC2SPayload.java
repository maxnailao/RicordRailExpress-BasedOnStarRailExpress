package net.exmo.sre.record.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 -> 服务端：按需请求战绩列表中的一页（{@code offset} 起 {@code limit} 条）。
 * 仅在滚动到对应区间时发送，以减少流量。
 */
public record RecordListRequestC2SPayload(int offset, int limit) implements CustomPacketPayload {
    public static final Type<RecordListRequestC2SPayload> ID = new Type<>(SRE.id("record_list_request"));
    public static final StreamCodec<FriendlyByteBuf, RecordListRequestC2SPayload> CODEC =
            CustomPacketPayload.codec(RecordListRequestC2SPayload::write, RecordListRequestC2SPayload::new);

    private RecordListRequestC2SPayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(offset);
        buffer.writeVarInt(limit);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
