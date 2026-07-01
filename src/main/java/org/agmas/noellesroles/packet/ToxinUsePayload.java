package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.HSRConstants;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.jetbrains.annotations.NotNull;

public record ToxinUsePayload(int target) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ToxinUsePayload> ID = new CustomPacketPayload.Type<>(Noellesroles.id("toxinuse"));
    public static final StreamCodec<FriendlyByteBuf, ToxinUsePayload> CODEC;

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    static {
        CODEC = StreamCodec.composite(ByteBufCodecs.INT, ToxinUsePayload::target, ToxinUsePayload::new);
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<ToxinUsePayload> {
        public void receive(@NotNull ToxinUsePayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            Entity var5 = player.serverLevel().getEntity(payload.target());
            if (var5 instanceof Player target) {
                if (!((double)target.distanceTo(player) > (double)3.0F)) {
                    ((SREPlayerPoisonComponent)SREPlayerPoisonComponent.KEY.get(target)).setPoisonTicks(HSRConstants.toxinPoisonTime, player.getUUID());
                    player.playSound(NRSounds.SYRINGE_STAB, 0.15F, 1.0F);
                    player.swing(InteractionHand.MAIN_HAND);
                    if (!player.isCreative()) {
                        // 消耗 1 点耐久而非整支毒针（与 ToxinItem 一致）。/ consume one durability, not the whole toxin.
                        if (org.agmas.noellesroles.content.item.ToxinDurability.consumeOne(player.getMainHandItem())) {
                            player.displayClientMessage(net.minecraft.network.chat.Component
                                    .translatable("message.noellesroles.toxin.depleted")
                                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
                        }
                        player.getCooldowns().addCooldown(ModItems.TOXIN, (Integer) ModItems.ITEM_COOLDOWNS.get(ModItems.TOXIN));
                    }

                }
            }
        }
    }
}
