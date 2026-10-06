package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：批量开箱结果
 *
 * @param success        是否至少成功开出一个
 * @param resultQualities 每个结果品质（0-5）
 * @param resultSkinIds   每个结果皮肤 ID（格式 itemType/skinName）
 * @param duplicates      每个结果是否重复皮肤
 */
public record OpenBoxBatchResultS2CPayload(
        boolean success,
        List<Integer> resultQualities,
        List<String> resultSkinIds,
        List<Boolean> duplicates
) implements CustomPacketPayload {

    public static final Type<OpenBoxBatchResultS2CPayload> ID =
            new Type<>(SRE.id("cs2_open_box_batch_result"));
    public static final StreamCodec<FriendlyByteBuf, OpenBoxBatchResultS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeBoolean(payload.success);
                int n = payload.resultQualities.size();
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeVarInt(payload.resultQualities.get(i));
                    buf.writeUtf(payload.resultSkinIds.get(i), 256);
                    buf.writeBoolean(payload.duplicates.get(i));
                }
            },
            buf -> {
                boolean success = buf.readBoolean();
                int n = buf.readVarInt();
                List<Integer> qs = new ArrayList<>(n);
                List<String> ss = new ArrayList<>(n);
                List<Boolean> ds = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    qs.add(buf.readVarInt());
                    ss.add(buf.readUtf(256));
                    ds.add(buf.readBoolean());
                }
                return new OpenBoxBatchResultS2CPayload(success, qs, ss, ds);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}