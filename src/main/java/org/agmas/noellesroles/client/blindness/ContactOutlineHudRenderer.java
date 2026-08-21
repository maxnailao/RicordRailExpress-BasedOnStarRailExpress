package org.agmas.noellesroles.client.blindness;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.List;

/**
 * 失明症方块轮廓 HUD 渲染器（移植自"失明症"模组 EnderEyeTrackingRenderer 的投影绘制模式）
 * <p>
 * 每帧在 HUD 回调内<b>自建</b>视图/投影矩阵（camera 四元数 + 相机位置 + FOV 透视），
 * 把活跃揭示方块的 12 条棱投影到屏幕并立即描线。
 * <p>
 * 关键实现约束（均对齐原模组/原版架构）：
 * <ul>
 *   <li>视图旋转必须用 {@code camera.rotation()} 的<b>共轭四元数</b>——原版
 *       {@code GameRenderer.renderLevel} 字节码实证：camera.rotation() 是"相机局部→世界"变换，
 *       直接用（不共轭）等于逆旋转，转头时轮廓会朝相反方向移动，表现为不跟随视角；</li>
 *   <li>投影矩阵直接调 {@code gameRenderer.getProjectionMatrix(fov)}（原版同款公开 API，
 *       含变焦处理与窗口宽高比），不自建 perspective 避免参数不一致；</li>
 *   <li>注册在 Fabric 标准 {@link HudRenderCallback}（原模组所有 HUD 渲染器同款），
 *       回调时 GUI pose 为干净单位阵、每帧恰好触发一次，真实 GuiGraphics 即时绘制——
 *       项目自有的 CommonHudRenderCallback 走延迟批量框架，pose 可能被污染且帧时序
 *       不稳定，会造成轮廓错位与剧烈闪烁。</li>
 *   <li>描线用 {@code RenderType.gui()} 缓冲直接提交四边形（每条棱 4 顶点）——
 *       早期版本沿棱每 2 像素一次 fill，探查时单帧数千次填充调用导致严重掉帧。</li>
 * </ul>
 * <p>
 * 颜色编码与原模组一致：中心块纯白、邻接块冰蓝；声纹揭示按威胁程度分红/白/灰三档。
 */
public final class ContactOutlineHudRenderer {

    /** 线段粗细（GUI 像素，垂直于棱方向的单边半径） */
    private static final float LINE_HALF_THICKNESS = 1.0F;
    /** 近平面裁切阈值（裁剪空间 w），避免背后顶点除零产生横扫全屏的长线条 */
    private static final double NEAR_EPSILON = 1.0E-3;
    /** 屏幕外允许延伸的最大像素，超出部分丢弃（防止不可见超长线段空耗 fill） */
    private static final double OFFSCREEN_MARGIN = 512.0;
    /** 立方体 12 条棱的顶点索引对，index = xBit*4 + yBit*2 + zBit */
    private static final int[][] EDGES = {
            { 0, 1 }, { 2, 3 }, { 4, 5 }, { 6, 7 }, // z 轴
            { 0, 2 }, { 1, 3 }, { 4, 6 }, { 5, 7 }, // y 轴
            { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 }, // x 轴
    };

    private ContactOutlineHudRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(ContactOutlineHudRenderer::render);
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        if (!client.player.hasEffect(ModEffects.BLINDNESS_SICKNESS) || client.player.isSpectator()) {
            return;
        }
        List<RevealedBlock> reveals = ContactRevealManager.snapshot();
        if (reveals.isEmpty()) {
            return;
        }
        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();
        if (guiWidth <= 0 || guiHeight <= 0) {
            return;
        }

        // ── 每帧自建视图投影矩阵（与原版 GameRenderer.renderLevel 字节码 485-554 完全一致）──
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        // 视图旋转 = camera.rotation() 的共轭：camera.rotation() 把相机局部坐标变换到世界，
        // 世界→相机视图必须用其逆（单位四元数的逆即共轭）。不共轭会导致转头时轮廓反向移动。
        Matrix4f viewMatrix = new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate());
        viewMatrix.translate(-(float) camPos.x, -(float) camPos.y, -(float) camPos.z);
        // 投影矩阵直接取原版实现（含变焦缩放与窗口宽高比）
        Matrix4f projMatrix = client.gameRenderer.getProjectionMatrix(client.options.fov().get());
        Matrix4f viewProj = new Matrix4f(projMatrix).mul(viewMatrix);

        long now = System.nanoTime();
        // 性能：整帧复用投影临时对象与碰撞上下文，避免逐揭示方块分配
        Vector4f projTemp = new Vector4f();
        double[][] clipTemp = new double[8][3];
        CollisionContext collisionContext = CollisionContext.of(client.player);
        VertexConsumer buffer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f guiPose = guiGraphics.pose().last().pose();
        for (RevealedBlock reveal : reveals) {
            float alpha = reveal.alpha(now);
            if (alpha <= 0.001F) {
                continue;
            }
            BlockState state = client.level.getBlockState(reveal.pos());
            if (state.isAir() && state.getFluidState().isEmpty()) {
                continue;
            }
            VoxelShape shape = state.getShape(client.level, reveal.pos(), collisionContext);
            if (shape.isEmpty() && !state.getFluidState().isEmpty()) {
                shape = state.getFluidState().getShape(client.level, reveal.pos());
            }
            if (shape.isEmpty()) {
                continue;
            }
            int color = colorFor(reveal.source(), alpha);
            for (AABB box : shape.toAabbs()) {
                drawBoxEdges(buffer, guiPose, reveal.pos().getX(), reveal.pos().getY(), reveal.pos().getZ(),
                        box, viewProj, guiWidth, guiHeight, color, projTemp, clipTemp);
            }
        }
    }

    /**
     * 投影并绘制单个盒子的 12 条棱。
     * <p>
     * 逐棱近平面裁切：顶点到相机后方（裁剪空间 w ≤ 阈值）时按 w 线性插值裁到近平面上，
     * 而不是整盒丢弃——整盒丢弃会让玩家走近揭示方块时轮廓整块突消失/突出现（闪炼），
     * 不裁切则背后顶点的负 w 除法会投影出横扫全屏的错乱长线条（敲击后显示错误）。
     */
    private static void drawBoxEdges(VertexConsumer buffer, Matrix4f guiPose, int bx, int by, int bz, AABB box,
            Matrix4f viewProj, int guiWidth, int guiHeight, int color, Vector4f projTemp, double[][] clip) {
        double[] xs = { bx + box.minX, bx + box.maxX };
        double[] ys = { by + box.minY, by + box.maxY };
        double[] zs = { bz + box.minZ, bz + box.maxZ };
        // 8 个顶点一次性投影到裁剪空间（x, y, w），索引：index = xBit*4 + yBit*2 + zBit
        int index = 0;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    projTemp.set((float) x, (float) y, (float) z, 1.0F).mul(viewProj);
                    clip[index][0] = projTemp.x;
                    clip[index][1] = projTemp.y;
                    clip[index][2] = projTemp.w;
                    index++;
                }
            }
        }
        for (int[] edge : EDGES) {
            drawClippedEdge(buffer, guiPose, clip[edge[0]], clip[edge[1]], guiWidth, guiHeight, color);
        }
    }

    /** 单棱近平面裁切后投影到 GUI 坐标，以四边形提交（每条棱仅 4 个顶点） */
    private static void drawClippedEdge(VertexConsumer buffer, Matrix4f guiPose, double[] a, double[] b,
            int guiWidth, int guiHeight, int color) {
        boolean aFront = a[2] > NEAR_EPSILON;
        boolean bFront = b[2] > NEAR_EPSILON;
        if (!aFront && !bFront) {
            return;
        }
        double[] start = aFront ? a : lerpToNearPlane(a, b);
        double[] end = bFront ? b : lerpToNearPlane(b, a);
        // NDC → 屏幕（Y 轴翻转：NDC y 向上，屏幕 y 向下）
        double x1 = (start[0] / start[2] * 0.5 + 0.5) * guiWidth;
        double y1 = (-start[1] / start[2] * 0.5 + 0.5) * guiHeight;
        double x2 = (end[0] / end[2] * 0.5 + 0.5) * guiWidth;
        double y2 = (-end[1] / end[2] * 0.5 + 0.5) * guiHeight;
        // 完全在屏幕外（含余量）的线段直接丢弃
        if ((x1 < -OFFSCREEN_MARGIN && x2 < -OFFSCREEN_MARGIN)
                || (x1 > guiWidth + OFFSCREEN_MARGIN && x2 > guiWidth + OFFSCREEN_MARGIN)
                || (y1 < -OFFSCREEN_MARGIN && y2 < -OFFSCREEN_MARGIN)
                || (y1 > guiHeight + OFFSCREEN_MARGIN && y2 > guiHeight + OFFSCREEN_MARGIN)) {
            return;
        }
        drawSegmentQuad(buffer, guiPose, x1, y1, x2, y2, color);
    }

    /** 把"背后顶点"沿棱插值到 w = NEAR_EPSILON 的近平面位置（裁剪空间坐标） */
    private static double[] lerpToNearPlane(double[] behind, double[] front) {
        double t = (NEAR_EPSILON - behind[2]) / (front[2] - behind[2]);
        return new double[] {
                behind[0] + (front[0] - behind[0]) * t,
                behind[1] + (front[1] - behind[1]) * t,
                NEAR_EPSILON,
        };
    }

    /**
     * 把线段作为单个四边形（TRIANGLE_STRIP 4 顶点）直接写入 GUI 缓冲。
     * <p>
     * 早期实现沿直线每 2 像素调一次 {@code GuiGraphics.fill}：一条 100px 的棱约 50 次
     * 调用，探查一次揭示 5 个方块 × 12 条棱就是单帧 3000+ 次填充，造成严重掉帧。
     * 改为每棱一个四边形后，同样的揭示只有约 240 个顶点。
     */
    private static void drawSegmentQuad(VertexConsumer buffer, Matrix4f guiPose,
            double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5) {
            return;
        }
        // 垂直于棱方向的单位法向 × 半宽，得到粗线的四个角点
        double nx = -dy / length * LINE_HALF_THICKNESS;
        double ny = dx / length * LINE_HALF_THICKNESS;
        buffer.addVertex(guiPose, (float) (x1 - nx), (float) (y1 - ny), 0).setColor(color);
        buffer.addVertex(guiPose, (float) (x1 + nx), (float) (y1 + ny), 0).setColor(color);
        buffer.addVertex(guiPose, (float) (x2 + nx), (float) (y2 + ny), 0).setColor(color);
        buffer.addVertex(guiPose, (float) (x2 - nx), (float) (y2 - ny), 0).setColor(color);
    }

    /** 按揭示来源取线框颜色：中心纯白、邻接冰蓝、声纹按威胁分档；alpha 叠入 ARGB */
    private static int colorFor(RevealSource source, float alpha) {
        float[] rgb = switch (source) {
            case CANE_CENTER -> new float[] { 1.0F, 1.0F, 1.0F };
            case CANE_ADJACENT -> new float[] { 0.72F, 0.96F, 1.0F };
            case ENTITY_DANGER -> new float[] { 1.0F, 0.30F, 0.30F };
            case ENTITY_AMBIENT -> new float[] { 0.50F, 0.62F, 0.70F };
            case ENTITY_FOOTSTEP -> new float[] { 0.55F, 0.55F, 0.55F };
        };
        int a = Math.max(0, Math.min(255, (int) (alpha * 255F)));
        int r = (int) (rgb[0] * 255F);
        int g = (int) (rgb[1] * 255F);
        int b = (int) (rgb[2] * 255F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
