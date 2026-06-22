package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block_entity.scene.MovingPlatformBlockEntity;
import org.jetbrains.annotations.NotNull;

public record MovingPlatformConfigC2SPacket(BlockPos pos, int distance, double speed, double collisionSize) implements CustomPacketPayload {
    public static final Type<MovingPlatformConfigC2SPacket> TYPE = new Type<>(Noellesroles.id("moving_platform_config"));
    public static final StreamCodec<FriendlyByteBuf, MovingPlatformConfigC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            MovingPlatformConfigC2SPacket::write, MovingPlatformConfigC2SPacket::new);

    private MovingPlatformConfigC2SPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt(), buf.readDouble(), buf.readDouble());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(distance);
        buf.writeDouble(speed);
        buf.writeDouble(collisionSize);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MovingPlatformConfigC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!player.isCreative()) return;
        BlockEntity be = player.serverLevel().getBlockEntity(payload.pos());
        if (be instanceof MovingPlatformBlockEntity mbe) {
            mbe.setDistance(payload.distance());
            mbe.setSpeed(payload.speed());
            mbe.setCollisionSize(payload.collisionSize());
            // 立刻重建平台实体，使配置立即生效
            mbe.recreatePlatform();
            // 同步到客户端，确保客户端 BlockEntity 数据更新
            var state = player.serverLevel().getBlockState(payload.pos());
            player.serverLevel().sendBlockUpdated(payload.pos(), state, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }
}
