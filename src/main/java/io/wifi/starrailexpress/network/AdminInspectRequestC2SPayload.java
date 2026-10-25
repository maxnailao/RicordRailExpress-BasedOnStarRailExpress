package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端(管理员) → 服务端：请求某个在线玩家的可管理数据快照
 * （余额 / 阵营卡 / 仓库 / 称号）。仅权限 2 会被服务端响应。
 */
public record AdminInspectRequestC2SPayload(String target) implements CustomPacketPayload {

    public static final Type<AdminInspectRequestC2SPayload> ID =
            new Type<>(SRE.id("admin_inspect_request"));
    public static final StreamCodec<FriendlyByteBuf, AdminInspectRequestC2SPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> buf.writeUtf(payload.target(), 64),
                    buf -> new AdminInspectRequestC2SPayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}