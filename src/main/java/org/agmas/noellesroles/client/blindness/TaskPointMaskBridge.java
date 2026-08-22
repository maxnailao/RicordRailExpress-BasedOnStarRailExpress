package org.agmas.noellesroles.client.blindness;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.Noellesroles;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/**
 * 任务点透视穿透失明遮罩的桥接：
 * 通过 Mixin 捕获 {@link org.agmas.noellesroles.client.TaskBlockOverlayRenderer} 本帧实际绘制过的任务点
 * （复用其全部显示条件判断），再经失明症模组开放的接触遮罩扩展点重绘进失明遮罩，
 * 使任务点轮廓在失明效果下依然以穿墙线框显示。失明症模组不存在时不做任何事。
 */
public final class TaskPointMaskBridge {
    private static final Set<BlockPos> CURRENT_FRAME_POSITIONS = new HashSet<>();
    private static boolean registered;

    private TaskPointMaskBridge() {}

    /** 每帧任务点透视渲染开始前调用，清空上一帧捕获的位置。 */
    public static void beginFrame() {
        CURRENT_FRAME_POSITIONS.clear();
    }

    /** 任务点透视每次实际绘制一个方块轮廓时记录其位置。 */
    public static void recordFramePosition(BlockPos pos) {
        CURRENT_FRAME_POSITIONS.add(pos);
    }

    /** 客户端初始化时调用：反射注册到失明症模组的遮罩扩展点。 */
    public static void init() {
        if (registered || !FabricLoader.getInstance().isModLoaded("blindness")) return;
        registered = true;
        try {
            Class<?> extensionClass = Class.forName("com.ikunkk02afk.blindness.client.render.ContactMaskExtension");
            Method register = extensionClass.getMethod("register", extensionClass);
            Object extension = Proxy.newProxyInstance(extensionClass.getClassLoader(),
                    new Class<?>[]{extensionClass}, (proxy, method, args) -> {
                        if ("render".equals(method.getName()) && args != null && args.length == 4) {
                            renderTaskPointsToMask((PoseStack) args[0], (MultiBufferSource.BufferSource) args[1],
                                    (RenderType) args[2], (Vec3) args[3]);
                        }
                        return null;
                    });
            register.invoke(null, extension);
            Noellesroles.LOGGER.info("Task point mask extension registered to blindness mod.");
        } catch (Throwable t) {
            Noellesroles.LOGGER.warn("Failed to register task point mask extension: {}", t.toString());
        }
    }

    private static void renderTaskPointsToMask(PoseStack matrices, MultiBufferSource.BufferSource buffers,
                                               RenderType lineMask, Vec3 cameraPos) {
        if (CURRENT_FRAME_POSITIONS.isEmpty()) return;
        var world = Minecraft.getInstance().level;
        if (world == null) return;
        VertexConsumer consumer = buffers.getBuffer(lineMask);
        for (BlockPos pos : CURRENT_FRAME_POSITIONS) {
            BlockState state = world.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) shape = state.getShape(world, pos);
            if (shape.isEmpty()) continue;
            AABB box = shape.bounds();
            matrices.pushPose();
            matrices.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
            // 遮罩通道颜色只影响强度通道，统一用合成输出的冷色调
            LevelRenderer.renderLineBox(matrices, consumer, box, 0.72F, 0.96F, 1.0F, 1.0F);
            matrices.popPose();
        }
    }
}
