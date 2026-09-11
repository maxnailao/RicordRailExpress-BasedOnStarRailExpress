package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.neutral.convict.ConvictChoiceManager;

/**
 * 重刑犯抉择提交包（C2S）。
 *
 * <p>客户端在「做出你的抉择」GUI 点击按钮后发送，服务端据此写入
 * {@link org.agmas.noellesroles.game.roles.neutral.convict.ConvictPlayerComponent} 的分支。</p>
 *
 * @param choiceIndex 选项序号：0=改过自新，1=毁灭一切，2=加入组织
 */
public record ConvictChoiceSelectC2SPacket(int choiceIndex) implements CustomPacketPayload {

    public static final Type<ConvictChoiceSelectC2SPacket> ID = new Type<>(Noellesroles.id("convict_choice_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConvictChoiceSelectC2SPacket> CODEC = StreamCodec
            .ofMember(ConvictChoiceSelectC2SPacket::encode, ConvictChoiceSelectC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(choiceIndex);
    }

    public static ConvictChoiceSelectC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new ConvictChoiceSelectC2SPacket(buf.readVarInt());
    }

    public static void handle(ConvictChoiceSelectC2SPacket payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> ConvictChoiceManager.selectChoice(context.player(), payload.choiceIndex()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
