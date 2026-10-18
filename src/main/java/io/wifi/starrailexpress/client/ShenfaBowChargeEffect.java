package io.wifi.starrailexpress.client;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.client.render.entity.ShenfaArrowRenderer;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShenfaBowSkinHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.Color;

/**
 * 神罚弓蓄力特效：拉弓时用第一人称手部粒子系统（与枪械枪口火光同源）在弓模型内部
 * 嵌一根粒子箭（天蓝箭杆 + 金黄箭头/尾羽），搭箭点随拉弦进度向后移动；松手瞬间
 * 整支粒子箭沿箭尖方向(-z)向前飞出。
 * 粒子绘制在手持物品的渲染矩阵内，因此贴合弓身、跟随摆臂，纯本地，其他玩家看不到
 * （其他玩家看到的是神罚箭实体的白色拖尾）。
 *
 * 坐标换算（与星光刀一致）：手部空间 q = display平移/16 + display缩放 × ((模型坐标-8)/16)。
 * 神罚 firstperson display：平移 [0,5,1.75]、缩放 0.7、无旋转。
 * 搭箭点模型坐标约 (x8, y5, z11松弛~z16拉开)，箭尖约 model z-2。
 */
public final class ShenfaBowChargeEffect {

    private static final ResourceLocation TEXTURE =
            StarRailExpressID.watheId("textures/particle/gunshot.png");

    private static final Color SHAFT = new Color(77, 209, 255, 235); // 天蓝：箭杆
    private static final Color TIP = new Color(255, 218, 51, 235);   // 金黄：箭头/尾羽

    // 手部空间几何：x/y 固定，z 从箭尖(前,负)到搭箭点(后,正)
    private static final float ARROW_X = 0.0F;
    private static final float ARROW_Y = 0.18F;
    private static final float TIP_Z = -0.33F;       // 箭尖（模型 z≈-2）
    private static final float NOCK_Z_IDLE = 0.24F;  // 松弛搭箭点（模型 z≈11）
    private static final float NOCK_Z_DRAWN = 0.46F; // 满弓搭箭点（模型 z≈16）
    private static final int SHAFT_COUNT = 9;

    private ShenfaBowChargeEffect() {}

    public static void register() {
        // 神罚箭复用具体箭渲染器：在别人眼里就是一根普通箭
        EntityRendererRegistry.register(TMMEntities.SHENFA_ARROW, ShenfaArrowRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(ShenfaBowChargeEffect::tick);
        // 击杀圣剑天降特效（仅击杀者可见，走 CustomParticleHandlers 通道）
        ShenfaHolySwordKillFx.register();
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.isPaused()) {
            return;
        }

        ItemStack use = player.getUseItem();
        boolean isBow = use.is(TMMItems.BOW) || use.is(Items.BOW);
        boolean charging = player.isUsingItem()
                && isBow
                && ShenfaBowSkinHandler.hasShenfaSkinEquipped(player, use);

        if (!charging) {
            return;
        }

        int ticks = player.getTicksUsingItem();
        float pull = Mth.clamp((ticks - 2) / 18.0F, 0.0F, 1.0F);
        float nockZ = NOCK_Z_IDLE + (NOCK_Z_DRAWN - NOCK_Z_IDLE) * pull;
        spawnNockedArrow(nockZ);
    }

    /** 每 tick 生成（短存活刷新）→ 一根稳定搭在弓上的粒子箭 */
    private static void spawnNockedArrow(float nockZ) {
        for (int i = 0; i < SHAFT_COUNT; i++) {
            float t = (float) i / (SHAFT_COUNT - 1);
            float z = nockZ + (TIP_Z - nockZ) * t; // 从搭箭点到箭尖
            boolean head = i >= SHAFT_COUNT - 2;
            spawn(head ? TIP : SHAFT, ARROW_X, ARROW_Y, z, 0.085F, 3);
        }
        // 尾羽：搭箭点后侧两粒金黄
        spawn(TIP, ARROW_X + 0.02F, ARROW_Y, nockZ + 0.02F, 0.07F, 3);
        spawn(TIP, ARROW_X - 0.02F, ARROW_Y, nockZ + 0.02F, 0.07F, 3);
    }

    private static void spawn(Color color, float x, float y, float z, float size, int maxAge) {
        HandParticle p = new HandParticle()
                .setTexture(TEXTURE)
                .setPos(x, y, z)
                .setMaxAge(maxAge)
                .setSize(size)
                .setVelocity(0F, 0F, 0F)
                .setLight(15, 15)
                .setRenderLayer(TMMRenderLayers::additive)
                .setColor(color);
        SREClient.handParticleManager.spawn(p);
    }
}