package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：开箱结果
 *
 * @param success       是否成功开箱
 * @param resultQuality 结果品质等级（0-5）
 * @param resultSkinId  结果皮肤 ID（格式 itemType/skinName）
 * @param isDuplicate   是否为重复皮肤
 * @param endCardIdx    目标卡片在滚动条中的索引
 * @param cardQualities 所有卡片品质列表
 * @param cardSkinIds   所有卡片皮肤 ID 列表
 */
public record OpenBoxResultS2CPayload(
        boolean success,
        int resultQuality,
        String resultSkinId,
        boolean isDuplicate,
        int endCardIdx,
        List<Integer> cardQualities,
        List<String> cardSkinIds
) implements CustomPacketPayload {

    public static final Type<OpenBoxResultS2CPayload> ID = new Type<>(SRE.id("cs2_open_box_result"));
    public static final StreamCodec<FriendlyByteBuf, OpenBoxResultS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeBoolean(payload.success);
                buf.writeVarInt(payload.resultQuality);
                buf.writeUtf(payload.resultSkinId, 256);
                buf.writeBoolean(payload.isDuplicate);
                buf.writeVarInt(payload.endCardIdx);
                int cardCount = payload.cardQualities.size();
                buf.writeVarInt(cardCount);
                for (int i = 0; i < cardCount; i++) {
                    buf.writeVarInt(payload.cardQualities.get(i));
                    buf.writeUtf(payload.cardSkinIds.get(i), 256);
                }
            },
            buf -> {
                boolean success = buf.readBoolean();
                int resultQuality = buf.readVarInt();
                String resultSkinId = buf.readUtf(256);
                boolean isDuplicate = buf.readBoolean();
                int endCardIdx = buf.readVarInt();
                int cardCount = buf.readVarInt();
                List<Integer> qualities = new ArrayList<>(cardCount);
                List<String> skinIds = new ArrayList<>(cardCount);
                for (int i = 0; i < cardCount; i++) {
                    qualities.add(buf.readVarInt());
                    skinIds.add(buf.readUtf(256));
                }
                return new OpenBoxResultS2CPayload(success, resultQuality, resultSkinId,
                        isDuplicate, endCardIdx, qualities, skinIds);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
