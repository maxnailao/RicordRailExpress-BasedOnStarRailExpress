package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.GunDropPayload;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.DurabilityBoatEntity;
import org.agmas.noellesroles.content.item.PirateFlintlockItem;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public record PirateFlintlockShootPayload(int target) implements CustomPacketPayload {
    public static final Type<PirateFlintlockShootPayload> ID = new Type<>(Noellesroles.id("pirate_flintlock_shoot"));
    public static final StreamCodec<FriendlyByteBuf, PirateFlintlockShootPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PirateFlintlockShootPayload::target,
            PirateFlintlockShootPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<PirateFlintlockShootPayload> {
        @Override
        public void receive(@NotNull PirateFlintlockShootPayload payload,
                ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            ItemStack mainHandStack = player.getMainHandItem();

            // 检查冷却
            if (player.getCooldowns().isOnCooldown(mainHandStack.getItem())) return;

            // 检查是否持有燧发枪
            if (!mainHandStack.is(ModItems.PIRATE_FLINTLOCK)) return;

            // 播放扣扳机音效
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.5F,
                    1.0F + player.getRandom().nextFloat() * 0.1F - 0.05F);

            Entity targetEntity = player.serverLevel().getEntity(payload.target());

            // 计算有效射程
            float range = PirateFlintlockItem.getEffectiveRange(player);
            float rangeSq = range * range;

            // 处理命中玩家
            if (targetEntity instanceof ServerPlayer target
                    && target.distanceToSqr(player) < rangeSq) {

                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());

                // 角色命中回调
                final var role = game.getRole(player);
                if (role != null) {
                    if (!role.onGunHit(player, target)) {
                        return;
                    }
                }

                // 击杀目标
                GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.PIRATE_FLINTLOCK);

                // 80%概率掉落左轮手枪（参考强盗手枪的掉落逻辑）
                if (!player.isCreative()) {
                    boolean shouldDrop = player.getRandom().nextFloat() <= 0.8F;
                    if (shouldDrop) {
                        if (player.getMainHandItem().is(TMMItemTags.GUNS)) {
                            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            ItemEntity item = player.drop(TMMItems.REVOLVER.getDefaultInstance(), false, false);
                            if (item != null) {
                                item.setPickUpDelay(10);
                                item.setThrower(player);
                            }
                            ServerPlayNetworking.send(player, new GunDropPayload());
                        }
                    }
                }
            }

            // 播放枪响音效
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5.0F,
                    1.0F + player.getRandom().nextFloat() * 0.1F - 0.05F);

            // 发送枪口闪光
            for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
                ServerPlayNetworking.send(tracking, new ShootMuzzleS2CPayload(player.getId()));
            }
            ServerPlayNetworking.send(player, new ShootMuzzleS2CPayload(player.getId()));

            // 添加30秒冷却
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(ModItems.PIRATE_FLINTLOCK, 30 * 20);
            }
        }
    }
}
