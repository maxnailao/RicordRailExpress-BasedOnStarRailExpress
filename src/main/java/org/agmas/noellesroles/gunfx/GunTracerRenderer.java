package org.agmas.noellesroles.gunfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import io.wifi.starrailexpress.util.ShengxuanSkinHandler;
import io.wifi.starrailexpress.util.JianshouzheSkinHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * 枪械射击轨迹渲染。除默认黄色外，按射手皮肤切换特殊弹道：
 * 圣宣＝黑白交替；坚守者之怒＝监守者咆哮射线（青/暗青交替）。
 */
public final class GunTracerRenderer {
    private static final int LIFE_TICKS = 8;
    private static final List<Tracer> TRACERS = new ArrayList<>();

    private static final int STYLE_NORMAL = 0;
    private static final int STYLE_BLACK_WHITE = 1;
    private static final int STYLE_ROAR = 2;

    private static final RenderType THICK_LINES = RenderType.create("noellesroles_gun_tracer",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private record Tracer(Vec3 from, Vec3 to, long expireGameTime, int style) {
    }

    private GunTracerRenderer() {
    }

    public static void onPacket(GunTracerS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Entity shooter = client.level.getEntity(packet.shooterId());
        if (shooter == null) {
            return;
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 to = new Vec3(packet.toX(), packet.toY(), packet.toZ());
        Vec3 view = to.subtract(eye);
        if (view.lengthSqr() < 0.01) {
            return;
        }
        view = view.normalize();
        Vec3 side = view.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 from = eye.add(view.scale(0.6D)).add(side.scale(0.12D)).add(0, -0.18D, 0);

        int style = STYLE_NORMAL;
        if (shooter instanceof Player p) {
            ItemStack held = p.getMainHandItem();
            if (ShengxuanSkinHandler.hasShengxuanSkinEquipped(p, held)) {
                style = STYLE_BLACK_WHITE;
            } else if (JianshouzheSkinHandler.hasJianshouzheSkinEquipped(p, held)) {
                style = STYLE_ROAR;
            }
        }
        TRACERS.add(new Tracer(from, to, client.level.getGameTime() + LIFE_TICKS, style));
    }

    public static void render(WorldRenderContext context) {
        if (TRACERS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            TRACERS.clear();
            return;
        }
        long now = client.level.getGameTime();
        Vec3 cameraPos = context.camera().getPosition();
        VertexConsumer vertexConsumer = context.consumers().getBuffer(THICK_LINES);
        PoseStack matrices = context.matrixStack();
        for (Iterator<Tracer> it = TRACERS.iterator(); it.hasNext();) {
            Tracer tracer = it.next();
            long left = tracer.expireGameTime() - now;
            if (left <= 0) {
                it.remove();
                continue;
            }
            float alpha = 0.15F + 0.55F * left / LIFE_TICKS;
            matrices.pushPose();
            matrices.translate(tracer.from().x - cameraPos.x, tracer.from().y - cameraPos.y,
                    tracer.from().z - cameraPos.z);
            PoseStack.Pose pose = matrices.last();
            Vec3 delta = tracer.to().subtract(tracer.from());
            switch (tracer.style()) {
                case STYLE_BLACK_WHITE -> segmentedLine(pose, vertexConsumer, delta, alpha, false);
                case STYLE_ROAR -> segmentedLine(pose, vertexConsumer, delta, alpha, true);
                default -> line(pose, vertexConsumer, delta, 1.0F, 0.85F, 0.4F, alpha);
            }
            matrices.popPose();
        }
    }

    /**
     * 分段交替着色线。roar=false 为圣宣黑/白交替；roar=true 为坚守者咆哮射线（亮青/暗青交替）。
     */
    private static void segmentedLine(PoseStack.Pose pose, VertexConsumer vertexConsumer, Vec3 delta, float alpha,
            boolean roar) {
        Vec3 normal = delta.normalize();
        float nx = (float) normal.x, ny = (float) normal.y, nz = (float) normal.z;
        final int segments = 12;
        float[] even = roar ? new float[] { 0.25F, 0.95F, 1.0F } : new float[] { 1.0F, 1.0F, 1.0F };
        float[] odd = roar ? new float[] { 0.03F, 0.30F, 0.42F } : new float[] { 0.0F, 0.0F, 0.0F };
        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / segments;
            float t1 = (float) (i + 1) / segments;
            float[] c = (i % 2 == 0) ? even : odd;
            float x0 = (float) (delta.x * t0), y0 = (float) (delta.y * t0), z0 = (float) (delta.z * t0);
            float x1 = (float) (delta.x * t1), y1 = (float) (delta.y * t1), z1 = (float) (delta.z * t1);
            vertexConsumer.addVertex(pose, x0, y0, z0)
                    .setColor(c[0], c[1], c[2], alpha)
                    .setNormal(pose, nx, ny, nz);
            vertexConsumer.addVertex(pose, x1, y1, z1)
                    .setColor(c[0], c[1], c[2], alpha)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vertexConsumer, Vec3 delta,
            float r, float g, float b, float alpha) {
        Vec3 normal = delta.normalize();
        vertexConsumer.addVertex(pose, 0F, 0F, 0F)
                .setColor(r, g, b, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        vertexConsumer.addVertex(pose, (float) delta.x, (float) delta.y, (float) delta.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
