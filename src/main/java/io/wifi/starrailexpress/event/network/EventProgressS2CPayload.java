package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端→客户端：单玩家任务包进度（逐条件计数向量 + 发放/可领取标记）。counts[i] 对应包内第 i 条条件。 */
public record EventProgressS2CPayload(String packKey, int[] counts, boolean claimed, boolean claimable)
        implements CustomPacketPayload {
    public static final Type<EventProgressS2CPayload> ID = new Type<>(SRE.id("sre_event_progress"));
    public static final StreamCodec<FriendlyByteBuf, EventProgressS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.packKey, 128);
                int[] c = payload.counts == null ? new int[0] : payload.counts;
                buf.writeVarInt(c.length);
                for (int v : c) buf.writeVarInt(v);
                buf.writeBoolean(payload.claimed);
                buf.writeBoolean(payload.claimable);
            },
            buf -> {
                String pk = buf.readUtf(128);
                int n = buf.readVarInt();
                int[] c = new int[n];
                for (int i = 0; i < n; i++) c[i] = buf.readVarInt();
                boolean claimed = buf.readBoolean();
                boolean claimable = buf.readBoolean();
                return new EventProgressS2CPayload(pk, c, claimed, claimable);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}