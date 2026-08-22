package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.IsShootBackFire;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.GhostPhantomEntity;
import org.agmas.noellesroles.content.entity.IllusionDecoyEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * 双枪（左手/右手）射击网络包。
 * - leftHand=true 表示双枪-左手（副手）开枪，服务端会重新校验"双枪-右手处于冷却中"的核心判定
 * - leftHand=false 表示双枪-右手（主手）开枪
 * 命中与掉落逻辑与左轮手枪保持一致。
 */
public record DualPistolShootPayload(boolean leftHand, int targetId) implements CustomPacketPayload {

    public static final Type<DualPistolShootPayload> ID = new Type<>(SRE.id("dual_pistol_shoot"));
    public static final StreamCodec<FriendlyByteBuf, DualPistolShootPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DualPistolShootPayload::leftHand,
            ByteBufCodecs.INT, DualPistolShootPayload::targetId,
            DualPistolShootPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<DualPistolShootPayload> {
        @Override
        public void receive(@NotNull DualPistolShootPayload payload,
                ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            var cooldowns = player.getCooldowns();

            ItemStack stack;
            if (payload.leftHand()) {
                stack = player.getOffhandItem();
                if (!stack.is(ModItems.DUAL_PISTOL_LEFT))
                    return;
                // 核心判定：只有双枪-右手处于冷却中时，双枪-左手才能开枪（两枪轮流开火）
                if (!cooldowns.isOnCooldown(ModItems.DUAL_PISTOL_RIGHT))
                    return;
            } else {
                stack = player.getMainHandItem();
                if (!stack.is(ModItems.DUAL_PISTOL_RIGHT))
                    return;
            }
            if (cooldowns.isOnCooldown(stack.getItem()))
                return;

            // 设置与左轮手枪相同的冷却（创造模式也进入冷却：
            // 冷却是左手枪"右手枪在冷却中才能开枪"判定的同步手段，不能跳过）
            cooldowns.addCooldown(stack.getItem(), DualPistolItem.getRevolverCooldown());

            // 扳机声与枪声（与左轮手枪一致）
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            // 枪口火焰效果
            for (ServerPlayer tracking : PlayerLookup.tracking(player))
                PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));
            PacketTracker.sendToClient(player, new ShootMuzzleS2CPayload(player.getId()));

            Entity targetEntity = player.serverLevel().getEntity(payload.targetId());
            // 特殊实体命中处理（与左轮手枪一致）
            if (targetEntity instanceof GhostPhantomEntity phantomEntity
                    && phantomEntity.distanceToSqr(player) < 65 * 65) {
                phantomEntity.playerHurt(player, GameConstants.DeathReasons.PHANTOM_DESTROYED);
                return;
            }
            if (targetEntity instanceof IllusionDecoyEntity illusionDecoy
                    && illusionDecoy.distanceToSqr(player) < 65 * 65) {
                illusionDecoy.playerHurt(player);
                return;
            }
            if (targetEntity instanceof PuppeteerBodyEntity puppeteerBody
                    && puppeteerBody.distanceToSqr(player) < 30 * 30) {
                puppeteerBody.playerHurt(player, Noellesroles.id("gun_puppeteer_body"));
                return;
            }

            if (targetEntity instanceof ServerPlayer target
                    && target.distanceToSqr(player) < 30 * 30) {
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());

                boolean backfire = false;
                final var role = game.getRole(player);
                if (role != null) {
                    if (!role.onGunHit(player, target)) {
                        return;
                    }
                }
                backfire = IsShootBackFire.EVENT.invoker().isShootBackFire(player, target);

                boolean shouldDropRevolver = game.isInnocent(target) && !player.isCreative();
                var dropResult = AllowShootRevolverDrop.EVENT.invoker().allowDrop(player, target);
                if (dropResult.equals(TrueFalseResult.FALSE)) {
                    shouldDropRevolver = false;
                } else if (dropResult.equals(TrueFalseResult.TRUE)) {
                    shouldDropRevolver = true;
                }

                if (backfire) {
                    GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.BACKFIRE);
                } else if (shouldDropRevolver) {
                    // 误杀平民：清掉开枪的那把枪并掉落左轮手枪
                    player.setItemInHand(payload.leftHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                            ItemStack.EMPTY);
                    ItemEntity item = player.drop(TMMItems.REVOLVER.getDefaultInstance(), false, false);
                    if (item != null) {
                        item.setPickUpDelay(10);
                        item.setThrower(player);
                    }
                }

                if (!backfire) {
                    GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.REVOLVER);
                }
                OnRevolverUsed.EVENT.invoker().onPlayerShoot(player, target);
            } else {
                OnRevolverUsed.EVENT.invoker().onPlayerShoot(player, null);
            }
        }
    }
}
