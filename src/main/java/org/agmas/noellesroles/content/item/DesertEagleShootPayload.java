package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
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
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.jetbrains.annotations.NotNull;

/**
 * 沙漠之鹰网络包：处理射击(SHOOT)和装填(RELOAD)两种动作。
 * 射击时携带爆头判定标志，由客户端射线检测计算。
 */
public record DesertEagleShootPayload(Action action, int targetId, boolean isHeadshot) implements CustomPacketPayload {

    public enum Action {
        SHOOT, RELOAD
    }

    public static final Type<DesertEagleShootPayload> ID = new Type<>(
            SRE.id("desert_eagle_shoot"));
    public static final StreamCodec<FriendlyByteBuf, DesertEagleShootPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, p -> p.action.ordinal(),
            ByteBufCodecs.INT, DesertEagleShootPayload::targetId,
            ByteBufCodecs.BOOL, DesertEagleShootPayload::isHeadshot,
            (actionOrd, targetId, headshot) -> new DesertEagleShootPayload(Action.values()[actionOrd], targetId, headshot));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<DesertEagleShootPayload> {
        @Override
        public void receive(@NotNull DesertEagleShootPayload payload,
                ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            ItemStack mainHandStack = player.getMainHandItem();

            if (!mainHandStack.is(ModItems.DESERT_EAGLE))
                return;

            // 旁观者/死亡检查
            if (player.isSpectator() || !player.isAlive())
                return;

            switch (payload.action()) {
                case SHOOT -> handleShoot(player, mainHandStack, payload.targetId(), payload.isHeadshot());
                case RELOAD -> handleReload(player, mainHandStack);
            }
        }

        private void handleShoot(ServerPlayer player, ItemStack mainHandStack, int targetId, boolean isHeadshot) {
            // 冷却检查
            if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                return;
            // 弹药检查
            if (DesertEagleItem.getAmmoCount(mainHandStack) <= 0)
                return;

            // 消耗弹药
            if (!player.isCreative()) {
                DesertEagleItem.consumeAmmo(mainHandStack);
            }

            // 设置0.3秒冷却（6 ticks）
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(), DesertEagleItem.SHOOT_COOLDOWN);
            }

            // 播放沙漠之鹰射击音效
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    NRSounds.DESERT_EAGLE_SHOOT, SoundSource.PLAYERS, 2.0f,
                    1f + player.getRandom().nextFloat() * .1f - .05f);

            // 发送枪口火焰效果
            for (ServerPlayer tracking : PlayerLookup.tracking(player))
                PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));
            PacketTracker.sendToClient(player, new ShootMuzzleS2CPayload(player.getId()));

            // 处理命中逻辑
            Entity targetEntity = player.serverLevel().getEntity(targetId);
            if (targetEntity instanceof ServerPlayer target
                    && target.distanceToSqr(player) < DesertEagleItem.RANGE * DesertEagleItem.RANGE) {

                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
                final var role = game.getRole(player);
                if (role != null) {
                    if (!role.onGunHit(player, target)) {
                        return;
                    }
                }

                // 掉落逻辑 - 参考狙击枪：好人打好人时掉落沙鹰、给予左轮
                // 巫毒师在 voodooShotLikeEvil 配置下视为邪恶目标，不触发掉落
                if (game.isInnocent(target) && !player.isCreative()
                        && !(NoellesRolesConfig.HANDLER.instance().voodooShotLikeEvil
                                && game.isRole(target, ModRoles.VOODOO))) {
                    if (game.isInnocent(player) && player.getRandom().nextFloat() <= game.getBackfireChance()) {
                        // 反向击发（好人打好人）
                        GameUtils.killPlayer(player, true, player, GameConstants.DeathReasons.BACKFIRE);
                        return;
                    } else {
                        // 移除沙鹰，掉落左轮手枪
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        ItemEntity item = player.drop(TMMItems.REVOLVER.getDefaultInstance(), false, false);
                        if (item != null) {
                            item.setPickUpDelay(10);
                            item.setThrower(player);
                        }
                    }
                }

                // 调用击杀系统处理命中（爆头/致残/累计）
                DesertEagleKillSystem.processHit(player, target, isHeadshot);

                OnRevolverUsed.EVENT.invoker().onPlayerShoot(player, target);
            } else {
                OnRevolverUsed.EVENT.invoker().onPlayerShoot(player, null);
            }
        }

        private void handleReload(ServerPlayer player, ItemStack mainHandStack) {
            // 冷却检查
            if (player.getCooldowns().isOnCooldown(mainHandStack.getItem()))
                return;

            int currentAmmo = DesertEagleItem.getAmmoCount(mainHandStack);
            if (currentAmmo >= DesertEagleItem.MAX_AMMO)
                return;

            // 查找并消耗一个沙鹰弹匣
            boolean hasMagazine = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack invStack = player.getInventory().getItem(i);
                if (invStack.is(ModItems.DESERT_EAGLE_MAGAZINE)) {
                    if (!player.isCreative()) {
                        invStack.shrink(1);
                    }
                    hasMagazine = true;
                    break;
                }
            }
            if (!hasMagazine)
                return;

            // 回满弹药
            DesertEagleItem.setAmmoCount(mainHandStack, DesertEagleItem.MAX_AMMO);

            // 播放装填音效
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    NRSounds.DESERT_EAGLE_RELOAD, SoundSource.PLAYERS, 1.0f, 1f);

            // 设置2.5秒换弹冷却（50 ticks）
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(mainHandStack.getItem(), 50);
            }
        }
    }
}
