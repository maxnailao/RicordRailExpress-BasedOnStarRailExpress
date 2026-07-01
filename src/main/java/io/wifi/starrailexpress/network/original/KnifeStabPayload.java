package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeDurability;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.GhostPhantomEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public record KnifeStabPayload(int target) implements CustomPacketPayload {
    public static final Type<KnifeStabPayload> ID = new Type<>(SRE.id("knifestab"));
    public static final StreamCodec<FriendlyByteBuf, KnifeStabPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT,
            KnifeStabPayload::target, KnifeStabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<KnifeStabPayload> {

        /**
         * 根据玩家主手物品应用对应的刀冷却
         * 海盗弯刀使用独立15秒冷却，其他刀使用原版刀冷却
         */
        private static void applyKnifeCooldown(ServerPlayer player) {
            if (player.isCreative()) return;
            var cooldowns = player.getCooldowns();
            if (player.getMainHandItem().is(ModItems.PIRATE_CUTLASS)) {
                cooldowns.addCooldown(ModItems.PIRATE_CUTLASS,
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(ModItems.PIRATE_CUTLASS, 15 * 20));
            } else {
                cooldowns.addCooldown(TMMItems.KNIFE, GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE));
            }
        }

        /**
         * 海盗弯刀成功击杀玩家后在服务端同步消耗
         * 仅在击杀玩家时调用，幻影/假人不消耗
         */
        private static void consumePirateCutlass(ServerPlayer player) {
            if (player.getMainHandItem().is(ModItems.PIRATE_CUTLASS)) {
                player.getMainHandItem().shrink(1);
            }
        }

        @Override
        public void receive(@NotNull KnifeStabPayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            Entity targetEntity = player.serverLevel().getEntity(payload.target());

            // 检查是否是鬼魅幻影实体
            if (targetEntity instanceof GhostPhantomEntity phantomEntity) {
                if (phantomEntity.distanceTo(player) > 4.0)
                    return;
                phantomEntity.playerHurt(player, GameConstants.DeathReasons.PHANTOM_DESTROYED);
                phantomEntity.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
                player.swing(InteractionHand.MAIN_HAND);
                applyKnifeCooldown(player);
                return;
            }

            // 检查是否是傀儡师假人
            if (targetEntity instanceof PuppeteerBodyEntity puppeteerBodyEntity) {
                if (puppeteerBodyEntity.distanceTo(player) > 4.0)
                    return;
                puppeteerBodyEntity.playerHurt(player, Noellesroles.id("knife_puppeteer_body"));
                puppeteerBodyEntity.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
                player.swing(InteractionHand.MAIN_HAND);
                applyKnifeCooldown(player);
                return;
            }

            // 检查是否是玩家
            if (!(targetEntity instanceof ServerPlayer target))
                return;
            if (target.distanceTo(player) > 3.0)
                return;
            // 杀手刀有限耐久：耗尽的刀不可用（但不会消失），需要重新购买替换。
            // Killer knife limited durability: a depleted knife cannot be used (but is not removed);
            // the killer must re-buy to replace it. Only applies to stamped knives in murder modes.
            ItemStack knife = player.getMainHandItem();
            boolean durabilityKnife = KillerKnifeDurability.isDurabilityModeEnabled(player.level())
                    && KillerKnifeDurability.isMarkedKnife(knife);
            if (durabilityKnife && KillerKnifeDurability.isDepleted(knife)) {
                player.displayClientMessage(
                        Component.translatable("message.sre.knife.depleted").withStyle(ChatFormatting.DARK_RED), true);
                return;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            final var role = game.getRole(player);
            if (role != null) {
                if (!role.onUseKnifeHit(player, target)) {
                    return;
                }
            }
            GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.KNIFE);
            target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            // 成功捅人后消耗 1 点耐久；耗尽时提示重新购买。
            // Consume one durability after a successful stab; warn when it becomes depleted.
            if (durabilityKnife && KillerKnifeDurability.consumeOne(knife,player)) {
                player.displayClientMessage(
                        Component.translatable("message.sre.knife.broken").withStyle(ChatFormatting.DARK_RED), true);
            }
            player.swing(InteractionHand.MAIN_HAND);
            // 海盗弯刀独立冷却判断；原版刀对LOOSE_END角色无冷却
            if (player.getMainHandItem().is(ModItems.PIRATE_CUTLASS)) {
                applyKnifeCooldown(player);
                consumePirateCutlass(player);
            } else if (!player.isCreative()
                    && !SREGameWorldComponent.KEY.get(player.level()).isRole(player, TMMRoles.LOOSE_END)
                    && !SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                            SpecialGameModeRoles.SUPER_LOOSE_END)) {
                var cooldowns = player.getCooldowns();
                cooldowns.addCooldown(TMMItems.KNIFE, GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE));
            }
        }
    }
}
