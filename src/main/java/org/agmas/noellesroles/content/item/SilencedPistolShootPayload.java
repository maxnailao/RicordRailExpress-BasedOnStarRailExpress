package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.IsShootBackFire;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
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
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.jetbrains.annotations.NotNull;

/**
 * 消音手枪网络包：处理射击(SHOOT)和装填(RELOAD)两种动作。
 * 射击时使用低音量播放消音枪声（传播半径约8格）。
 */
public record SilencedPistolShootPayload(Action action, int targetId) implements CustomPacketPayload {

    public enum Action {
        SHOOT, RELOAD
    }

    public static final Type<SilencedPistolShootPayload> ID = new Type<>(
            SRE.id("silenced_pistol_shoot"));
    public static final StreamCodec<FriendlyByteBuf, SilencedPistolShootPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, p -> p.action.ordinal(),
            ByteBufCodecs.INT, SilencedPistolShootPayload::targetId,
            (actionOrd, targetId) -> new SilencedPistolShootPayload(Action.values()[actionOrd], targetId));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<SilencedPistolShootPayload> {
        @Override
        public void receive(@NotNull SilencedPistolShootPayload payload,
                ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            ItemStack mainHandStack = player.getMainHandItem();

            if (!mainHandStack.is(ModItems.SILENCED_PISTOL))
                return;

            switch (payload.action()) {
                case SHOOT -> handleShoot(player, mainHandStack, payload.targetId());
                case RELOAD -> handleReload(player, mainHandStack);
            }
        }

        private void handleShoot(ServerPlayer player, ItemStack mainHandStack, int targetId) {
            // 冷却检查
            if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                return;
            // 弹药检查
            if (SilencedPistolItem.getAmmoCount(mainHandStack) <= 0)
                return;

            // 消耗弹药
            if (!player.isCreative()) {
                SilencedPistolItem.consumeAmmo(mainHandStack);
            }

            // 设置8秒冷却
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(), 8 * 20);
            }

            // 播放消音枪声 - 低音量(0.5f)，传播半径约8格 (0.5 * 16 = 8)
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    NRSounds.SILENCED_PISTOL_SHOOT, SoundSource.PLAYERS, 0.5f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            // 发送枪口火焰效果
            for (ServerPlayer tracking : PlayerLookup.tracking(player))
                PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));
            PacketTracker.sendToClient(player, new ShootMuzzleS2CPayload(player.getId()));

            // 处理命中逻辑
            Entity targetEntity = player.serverLevel().getEntity(targetId);
            if (targetEntity instanceof ServerPlayer target
                    && target.distanceToSqr(player) < SilencedPistolItem.RANGE * SilencedPistolItem.RANGE) {

                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
                var deathReason = SRE.id("silenced_pistol_shot");

                boolean backfire = false;
                final var role = game.getRole(player);
                if (role != null) {
                    if (!role.onGunHit(player, target)) {
                        return;
                    }
                }
                backfire = IsShootBackFire.EVENT.invoker().isShootBackFire(player, target);

                boolean shouldDropRevolver = game.isInnocent(target) && !player.isCreative()
                        && mainHandStack.is(TMMItemTags.GUNS);
                var dropResult = AllowShootRevolverDrop.EVENT.invoker().allowDrop(player, target);
                if (dropResult.equals(TrueFalseResult.FALSE)) {
                    shouldDropRevolver = false;
                } else if (dropResult.equals(TrueFalseResult.TRUE)) {
                    shouldDropRevolver = true;
                }

                if (backfire) {
                    GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.BACKFIRE);
                } else if (shouldDropRevolver) {
                    // 掉落左轮手枪
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    ItemEntity item = player.drop(TMMItems.REVOLVER.getDefaultInstance(), false, false);
                    if (item != null) {
                        item.setPickUpDelay(10);
                        item.setThrower(player);
                    }
                }

                if (!backfire) {
                    GameUtils.killPlayer(target, true, player, deathReason);
                }
                OnRevolverUsed.EVENT.invoker().onPlayerShoot(player, target);
            } else {
                OnRevolverUsed.EVENT.invoker().onPlayerShoot(player, null);
            }
        }

        private void handleReload(ServerPlayer player, ItemStack mainHandStack) {
            // 冷却检查
            if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                return;

            int currentAmmo = SilencedPistolItem.getAmmoCount(mainHandStack);
            if (currentAmmo >= SilencedPistolItem.MAX_AMMO)
                return;

            // 查找并消耗一颗消音手枪子弹
            boolean hasBullet = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack invStack = player.getInventory().getItem(i);
                if (invStack.is(ModItems.SILENCED_PISTOL_BULLET)) {
                    if (!player.isCreative()) {
                        invStack.shrink(1);
                    }
                    hasBullet = true;
                    break;
                }
            }
            if (!hasBullet)
                return;

            // 装填子弹
            SilencedPistolItem.setAmmoCount(mainHandStack, currentAmmo + 1);

            // 播放装填声音
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    NRSounds.SILENCED_PISTOL_RELOAD, SoundSource.PLAYERS, 0.5f, 1f);

            // 设置2秒装填冷却
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(), 40);
            }
        }
    }
}
