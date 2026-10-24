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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.GhostPhantomEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
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
            if (io.wifi.starrailexpress.anticheat.ClickAntiCheat.isLocked(player)) {
                return;
            }
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

            // 列车假人：可被匕首击杀
            if (targetEntity instanceof io.wifi.starrailexpress.traindummy.TrainDummyEntity dummy
                    && dummy.canReceiveHits()) {
                if (dummy.distanceTo(player) > 3.0) {
                    return;
                }
                ItemStack dummyKnife = player.getMainHandItem();
                boolean dummyDurabilityKnife = KillerKnifeDurability.isDurabilityModeEnabled(player.level())
                        && KillerKnifeDurability.isMarkedKnife(dummyKnife);
                if (dummyDurabilityKnife && KillerKnifeDurability.isDepleted(dummyKnife)) {
                    player.displayClientMessage(
                            Component.translatable("message.sre.knife.depleted")
                                    .withStyle(ChatFormatting.DARK_RED),
                            true);
                    return;
                }
                io.wifi.starrailexpress.traindummy.TrainDummyCombat.applyLethal(dummy, player,
                        GameConstants.DeathReasons.KNIFE);
                dummy.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
                if (dummyDurabilityKnife && KillerKnifeDurability.consumeOne(dummyKnife, player)) {
                    player.displayClientMessage(
                            Component.translatable("message.sre.knife.broken")
                                    .withStyle(ChatFormatting.DARK_RED),
                            true);
                }
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
            // 塑水苦无特别皮肤：击杀音效仅对击杀者播放，其他玩家仍听见正常音效
            if (io.wifi.starrailexpress.util.SushuiKunaiSkinHandler.hasKunaiSkinEquipped(player, knife)) {
                io.wifi.starrailexpress.util.SushuiKunaiSkinHandler.playKillSound(
                        player, target.getX(), target.getY(), target.getZ());
                // 传入 player 作为排除对象，确保击杀者不会听到原版刺击音效
                player.level().playSound(player, target.blockPosition(), TMMSounds.ITEM_KNIFE_STAB,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
            } else {
                target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            }
            // 暗星特别皮肤：击杀玩家后切换形态（天使⇄恶魔）
            if (io.wifi.starrailexpress.util.AnxingSkinHandler.hasAnxingSkinEquipped(player, knife)) {
                io.wifi.starrailexpress.util.AnxingSkinHandler.switchForm(player);
            }
            // 星光（传承）皮肤：刀死人后在受害者位置长出黑灰粒子手，托出深蓝白粒子球并捏爆。
            // 仅击杀者本人可见：用 sendParticles(..., ServerPlayer) 只把粒子发给 player（击杀者），
            // 不再全员广播；"持星光"条件已由 xingguangKill（主手刀皮肤判定）保证。
            // 判定与其他特别皮肤 Handler 统一：组件命中即 true，否则回落实时装备查询（组件会被冻结）
            boolean xingguangKill = io.wifi.starrailexpress.client.XingguangClientEffects.SKIN_ID
                    .equals(knife.get(io.wifi.starrailexpress.index.SREDataComponentTypes.SKIN))
                    || io.wifi.starrailexpress.client.XingguangClientEffects.SKIN_ID
                    .equals(io.wifi.starrailexpress.util.ItemSkinManager.getEquippedSkin(player, knife));
            if (xingguangKill) {
                ServerLevel fxLevel = player.serverLevel();
                fxLevel.sendParticles(player, io.wifi.starrailexpress.index.TMMParticles.XINGGUANG_KILL_HAND, true,
                        target.getX(), target.getY() + 1.0, target.getZ(), 26, 0.0, 0.0, 0.0, 0.0);
                fxLevel.sendParticles(player, io.wifi.starrailexpress.index.TMMParticles.XINGGUANG_KILL_ORB, true,
                        target.getX(), target.getY() + 1.0, target.getZ(), 14, 0.0, 0.0, 0.0, 0.0);
                io.wifi.starrailexpress.network.PacketTracker.sendToClient(player,
                        new io.wifi.starrailexpress.network.original.XingguangKillFxS2CPayload(false, false));
            }
            // 猩红之爪（传承）皮肤：击杀后仅向击杀者发送通知，触发全屏暗红滤镜
            boolean clawKill = io.wifi.starrailexpress.client.XingguangPoseManager.XINGHONG_CLAW_SKIN_ID
                    .equals(knife.get(io.wifi.starrailexpress.index.SREDataComponentTypes.SKIN))
                    || io.wifi.starrailexpress.client.XingguangPoseManager.XINGHONG_CLAW_SKIN_ID
                    .equals(io.wifi.starrailexpress.util.ItemSkinManager.getEquippedSkin(player, knife));
            if (clawKill) {
                io.wifi.starrailexpress.network.PacketTracker.sendToClient(player,
                        new io.wifi.starrailexpress.network.original.XingguangKillFxS2CPayload(false, true));
                // 暗红巨拳砸向尸体：仅击杀者客户端播放（CustomParticleS2CPayload 已在 SREPayloadRegister 注册，无需新增）
                io.wifi.starrailexpress.network.PacketTracker.sendToClient(player,
                        new io.wifi.starrailexpress.network.packet.CustomParticleS2CPayload(java.util.List.of(
                                io.wifi.starrailexpress.network.packet.CustomParticleS2CPayload.Entry.at(
                                        io.wifi.starrailexpress.SRE.id("claw_giant_fist"),
                                        target.getX(), target.getY() + 1.0D, target.getZ(), 0))));
            }
            // 恶魔之刃特别皮肤：击杀玩家后，仅向击杀者本人发送紫粉色粒子特效
            if (io.wifi.starrailexpress.util.EmozhidaoSkinHandler.hasEmozhidaoSkinEquipped(player, knife)) {
                io.wifi.starrailexpress.network.PacketTracker.sendToClient(player,
                        new io.wifi.starrailexpress.network.original.EmozhidaoKillFxS2CPayload());
                // 恶魔之刃击杀音效（独立事件）：仅击杀者本人听见
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                        net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                                io.wifi.starrailexpress.index.TMMSounds.ITEM_KNIFE_EMOZHIDAO_KILL),
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        target.getX(), target.getY(), target.getZ(),
                        1.0f, 1.0f, player.getRandom().nextLong()));
            }
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
                    && !ModRoles.isLooseEndVariant(SREGameWorldComponent.KEY.get(player.level()).getRole(player))
                    && !SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                            SpecialGameModeRoles.SUPER_LOOSE_END)) {
                var cooldowns = player.getCooldowns();
                cooldowns.addCooldown(TMMItems.KNIFE, GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE));
            }
        }
    }
}
