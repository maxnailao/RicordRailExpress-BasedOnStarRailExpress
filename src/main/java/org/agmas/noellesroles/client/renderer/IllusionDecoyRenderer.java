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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.IllusionDecoyEntity;

import java.util.UUID;

/**
 * 幻术师假人渲染器：使用 RemotePlayer 假玩家渲染（参照 MorphlingKnifeDummyRenderer）。
 * 支持手持物品显示、举刀/举枪姿态、疾跑动画。
 */
public class IllusionDecoyRenderer extends EntityRenderer<IllusionDecoyEntity> {

    /**
     * 本帧被标记为「坐着」的分身假玩家 UUID。
     * <p>原版玩家的 {@code PlayerModel} 从不设置 {@code riding}，没法通过模型字段表达坐姿，
     * 所以渲染前登记、渲染后注销，由 {@code IllusionDecoySitPoseMixin} 读取并补上坐姿。
     */
    private static final java.util.Set<UUID> SITTING_DECOYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isMarkedSitting(UUID uuid) {
        return uuid != null && SITTING_DECOYS.contains(uuid);
    }

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
            // 强制皮肤外层可见（帽子/外套/袖子/裤子）——披风也照常显示：
            // 分身有披风才算逼真，不能靠「关掉披风」来遮挡，否则一眼就能看出真假。
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
            // 视角俯仰：用实体单独同步的 VIEW_X_ROT 字段（放置瞬间捕捉到的抬头/低头），
            // 而不是 entity.getXRot() —— 后者会被跟随/追击逻辑覆盖，导致分身永远平视。
            float viewXRot = entity.getViewXRot();
            fakePlayer.setXRot(viewXRot);
            fakePlayer.xRotO = viewXRot;

            // 同步行走动画
            fakePlayer.walkAnimation.speed = entity.walkAnimation.speed;
            fakePlayer.walkAnimation.speedOld = entity.walkAnimation.speedOld;
            fakePlayer.walkAnimation.position = entity.walkAnimation.position;

            // 披风：CapeLayer 用 (xCloak - getX()) 这类位移差来算「风吹起」的幅度。
            // 假玩家是临时 new 的，位置在场点 (0,0,0) 而 xCloak 等字段一直是 0，
            // 但它的渲染坐标跟着分身在世界里，于是位移差变成很大的假值 → 披风被吹得立起来。
            // 把上一帧与当前帧的披风跟踪点都设成同一个值，位移差为 0，披风就自然垂下。
            fakePlayer.xCloakO = fakePlayer.xCloak = fakePlayer.getX();
            fakePlayer.yCloakO = fakePlayer.yCloak = fakePlayer.getY();
            fakePlayer.zCloakO = fakePlayer.zCloak = fakePlayer.getZ();

            // 设置手持物品（主手 + 副手，从服务端同步）
            ItemStack heldItem = entity.getHeldItem();
            if (!heldItem.isEmpty()) {
                fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, heldItem);
            }
            ItemStack offhandItem = entity.getOffhandItem();
            if (!offhandItem.isEmpty()) {
                fakePlayer.setItemInHand(InteractionHand.OFF_HAND, offhandItem);
            }

            // 姿态处理：位标志见 IllusionDecoyEntity.POSE_*
            int poseFlags = entity.getPoseFlags();
            // 使用物品姿态（举刀/举枪等）
            if ((poseFlags & IllusionDecoyEntity.POSE_USING_ITEM) != 0 && !heldItem.isEmpty()) {
                fakePlayer.startUsingItem(InteractionHand.MAIN_HAND);
            }
            // 疾跑姿态
            fakePlayer.setSprinting((poseFlags & IllusionDecoyEntity.POSE_SPRINTING) != 0);
            // 蹲下姿态：必须显式设置 Pose，只调 setShiftKeyDown 不够 ——
            // LivingEntity.updatePose 只在服务端 tick 里跑，而这个假玩家是渲染期临时造的、
            // 从没 tick 过，所以光设 shift 键状态它的 Pose 仍然是 STANDING，模型不会蹲。
            boolean crouching = (poseFlags & IllusionDecoyEntity.POSE_CROUCHING) != 0;
            fakePlayer.setShiftKeyDown(crouching);
            fakePlayer.setPose(crouching ? Pose.CROUCHING : Pose.STANDING);
            // 坐下姿态：原版 PlayerRenderer 不会设置模型的 riding（玩家模型没这条链路），
            // 因此把 UUID 登记到 SITTING_DECOYS，由 IllusionDecoySitPoseMixin 在
            // HumanoidModel.setupAnim 末尾补上坐姿；渲染完立即注销。
            boolean sitting = (poseFlags & IllusionDecoyEntity.POSE_SITTING) != 0;
            if (sitting) {
                SITTING_DECOYS.add(fakePlayer.getUUID());
            }
            try {
                instance.getEntityRenderDispatcher().render(fakePlayer, 0.0D, 0.0D, 0.0D, 0, tickDelta, matrices,
                        vertexConsumers, light);
            } finally {
                if (sitting) {
                    SITTING_DECOYS.remove(fakePlayer.getUUID());
                }
            }
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
