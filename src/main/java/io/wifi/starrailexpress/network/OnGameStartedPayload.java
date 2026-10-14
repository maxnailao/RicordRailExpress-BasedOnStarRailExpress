package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OnGameStartedPayload(String mapId) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID,
            "on_game_started");
    public static final Type<OnGameStartedPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OnGameStartedPayload> CODEC;


    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public OnGameStartedPayload {
        mapId = mapId == null ? "" : mapId;
    }

    public OnGameStartedPayload() {
        this("");
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(mapId, 256);

    }

    public static OnGameStartedPayload read(FriendlyByteBuf buf) {
        return new OnGameStartedPayload(buf.readUtf(256));
    }

    static {
        CODEC = StreamCodec.ofMember(OnGameStartedPayload::write, OnGameStartedPayload::read);
    }
}
