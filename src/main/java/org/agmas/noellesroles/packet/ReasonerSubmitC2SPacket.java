package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.neutral.reasoner.ReasonerPlayerComponent;

public record ReasonerSubmitC2SPacket(int question, String answer) implements CustomPacketPayload {
    public static final Type<ReasonerSubmitC2SPacket> ID = new Type<>(Noellesroles.id("reasoner_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReasonerSubmitC2SPacket> CODEC = StreamCodec
            .ofMember(ReasonerSubmitC2SPacket::encode, ReasonerSubmitC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(question);
        buf.writeUtf(answer);
    }

    public static ReasonerSubmitC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new ReasonerSubmitC2SPacket(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(ReasonerSubmitC2SPacket payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> ReasonerPlayerComponent.KEY.get(context.player())
                .submitAnswer(context.player(), payload.question(), payload.answer()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
