package org.agmas.noellesroles.client.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.IllusionDecoyEntity;

import java.util.UUID;

/**
 * 幻术师假人渲染器：使用 RemotePlayer 假玩家渲染（参照 MorphlingKnifeDummyRenderer）。
 * 支持手持物品显示、举刀/举枪姿态、疾跑动画。
 */
public class IllusionDecoyRenderer extends EntityRenderer<IllusionDecoyEntity> {

    public IllusionDecoyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(IllusionDecoyEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        final var instance = Minecraft.getInstance();
        UUID skinUuid = entity.getSkinUuid();
        if (skinUuid != null && instance.level != null) {
            PlayerInfo entry = ClientSkinCache.getCachedPlayerInfo(skinUuid);
            String name = entry != null ? entry.getProfile().getName() : "Decoy";
            // 重写 isModelPartShown 强制所有皮肤外层可见（帽子/外套/袖子/裤子）
            RemotePlayer fakePlayer = new RemotePlayer(instance.level, new GameProfile(skinUuid, name)) {
                @Override
                public boolean isModelPartShown(PlayerModelPart part) {
                    return true;
                }
            };

            // 同步朝向
            fakePlayer.setYRot(entity.getYRot());
            fakePlayer.yRotO = entity.yRotO;
            fakePlayer.setYBodyRot(entity.yBodyRot);
            fakePlayer.yBodyRotO = entity.yBodyRotO;
            fakePlayer.setYHeadRot(entity.getYHeadRot());
            fakePlayer.yHeadRotO = entity.yHeadRotO;
            fakePlayer.setXRot(entity.getXRot());
            fakePlayer.xRotO = entity.xRotO;

            // 同步行走动画
            fakePlayer.walkAnimation.speed = entity.walkAnimation.speed;
            fakePlayer.walkAnimation.speedOld = entity.walkAnimation.speedOld;
            fakePlayer.walkAnimation.position = entity.walkAnimation.position;

            // 设置手持物品（从服务端同步）
            ItemStack heldItem = entity.getHeldItem();
            if (!heldItem.isEmpty()) {
                fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, heldItem);
            }

            // 姿态处理
            int poseFlags = entity.getPoseFlags();
            // 举刀姿态：主手持刀并摆出使用姿态
            if ((poseFlags & 1) != 0 && !heldItem.isEmpty()) {
                fakePlayer.startUsingItem(InteractionHand.MAIN_HAND);
            }
            // 疾跑姿态
            if ((poseFlags & 2) != 0) {
                fakePlayer.setSprinting(true);
            }

            instance.getEntityRenderDispatcher().render(fakePlayer, 0.0D, 0.0D, 0.0D, 0, tickDelta, matrices,
                    vertexConsumers, light);
            return;
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(IllusionDecoyEntity entity) {
        UUID skinUuid = entity.getSkinUuid();
        if (skinUuid != null) {
            PlayerInfo entry = ClientSkinCache.getCachedPlayerInfo(skinUuid);
            if (entry != null) {
                return entry.getSkin().texture();
            }
            return DefaultPlayerSkin.get(skinUuid).texture();
        }
        return DefaultPlayerSkin.get(UUID.fromString("7833c811-436e-40c4-868a-ffb1073f48a2")).texture();
    }
}
