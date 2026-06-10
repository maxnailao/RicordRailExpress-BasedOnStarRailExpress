package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 自定义职业同步 Payload（服务端 → 客户端）
 * 包含 hash 字段，客户端可对比 hash 跳过重复解析
 */
public class CustomRoleSyncPayload implements CustomPacketPayload {
    public static final Type<CustomRoleSyncPayload> TYPE = new Type<>(SRE.id("custom_role_sync"));
    public static final StreamCodec<FriendlyByteBuf, CustomRoleSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CustomRoleSyncPayload::hash,
            ByteBufCodecs.STRING_UTF8, CustomRoleSyncPayload::jsonContent,
            CustomRoleSyncPayload::new);

    private final int hash;
    private final String jsonContent;

    public CustomRoleSyncPayload(int hash, String jsonContent) {
        this.hash = hash;
        this.jsonContent = jsonContent;
    }

    public int hash() {
        return hash;
    }

    public String jsonContent() {
        return jsonContent;
    }

    @Override
    public Type<CustomRoleSyncPayload> type() {
        return TYPE;
    }
}
