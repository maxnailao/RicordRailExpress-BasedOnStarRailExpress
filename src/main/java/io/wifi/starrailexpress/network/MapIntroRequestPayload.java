package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MapIntroRequestPayload() implements CustomPacketPayload {
    public static final Type<MapIntroRequestPayload> ID = new Type<>(SRE.id("map_intro_request"));
    public static final StreamCodec<FriendlyByteBuf, MapIntroRequestPayload> CODEC =
            CustomPacketPayload.codec(MapIntroRequestPayload::write, MapIntroRequestPayload::new);

    private MapIntroRequestPayload(FriendlyByteBuf buffer) {
        this();
    }

    private void write(FriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
