package io.wifi.starrailexpress.content.title.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.title.Title;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：全量同步称号定义表。
 *
 * <p>客户端需要它才能把"拥有的称号 id"渲染成带颜色的文本。
 * 玩家登录时、以及发放 / 删除称号后推送。
 */
public record TitleCatalogS2CPayload(List<Title> titles) implements CustomPacketPayload {

    /** 单次同步的上限，防御性限制（正常情况下称号不会这么多） */
    public static final int MAX_ENTRIES = 512;

    public static final Type<TitleCatalogS2CPayload> ID = new Type<>(SRE.id("title_catalog"));
    public static final StreamCodec<FriendlyByteBuf, TitleCatalogS2CPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> {
                        List<Title> list = payload.titles();
                        int n = Math.min(list.size(), MAX_ENTRIES);
                        buf.writeVarInt(n);
                        for (int i = 0; i < n; i++) {
                            Title t = list.get(i);
                            buf.writeUtf(t.id(), 64);
                            buf.writeUtf(t.text(), Title.MAX_TEXT_LENGTH * 4);
                            buf.writeUtf(t.colorHex(), 8);
                            buf.writeBoolean(t.suffix());
                        }
                    },
                    buf -> {
                        int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
                        List<Title> list = new ArrayList<>(n);
                        for (int i = 0; i < n; i++) {
                            String id = buf.readUtf(64);
                            String text = buf.readUtf(Title.MAX_TEXT_LENGTH * 4);
                            String color = buf.readUtf(8);
                            boolean suffix = buf.readBoolean();
                            list.add(new Title(id, text, color, suffix));
                        }
                        return new TitleCatalogS2CPayload(list);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
