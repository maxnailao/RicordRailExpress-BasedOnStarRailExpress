package org.agmas.noellesroles.game.roles.neutral.cipher;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnGetInstinctHighlight;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 密客：无人机驾驶时，把本体（密客自己）以“穿墙透视”的方式渲染出来。
 * 原理：在 AFTER_TRANSLUCENT 阶段关闭深度测试，直接调用 EntityRenderDispatcher
 * 渲染本地玩家模型，使其始终绘制在最上层，即使本体在墙后/相机背后也可见。
 * 纯客户端渲染，只驾驶员自己看得到，不发任何网络包。
 */
public class CipherSelfXrayRenderer {

    private static final int FULL_BRIGHT = 0xF000F0;

    // 阵营透视描边色（不能返回纯白，会被解析为 -1）
    private static final int XRAY_PINK = new Color(255, 105, 180).getRGB();  // 其余所有玩家：粉
    private static final int XRAY_RED = new Color(255, 0, 0).getRGB();       // 杀手阵营/同伙：红
    private static final int XRAY_ORANGE = new Color(255, 165, 0).getRGB();  // 悍匪：橙

    /** 注册：密客无人机驾驶时对存活玩家按阵营染色透视（复用原版发光描边，穿墙可见） */
    public static void registerFactionHighlight() {
        OnGetInstinctHighlight.EVENT.register((target, instinctEnabled) -> factionXrayColor(target));
    }

    private static int factionXrayColor(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return -1;
        // 常态下密客也开启阵营透视（不再要求正在驾驶无人机）
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) return -1;
        // 角色门：仅本机为密客时启用（OnGetInstinctHighlight 是全局事件，必须自筛）
        if (!SREClient.gameComponent.isRole(mc.player, org.agmas.noellesroles.role.ModRoles.CIPHER)) return -1;
        // 只透视其他存活玩家
        if (!(target instanceof Player tp) || tp == mc.player) return -1;
        if (!GameUtils.isPlayerAliveAndSurvival(tp)) return -1;

        SRERole role = SREClient.gameComponent.getRole(tp);
        // 悍匪优先判定（其 canUseKiller=true，否则会被 isKillerTeamRole 归为杀手阵营→红）
        if (role == ModRoles.GANGSTERS) return XRAY_ORANGE;
        if (SREClient.gameComponent.isKillerTeamRole(role)) return XRAY_RED;
        // 其余所有人（含未分配/平民/其他中立）：粉
        return XRAY_PINK;
    }

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        CipherPlayerComponent comp = CipherPlayerComponent.KEY.get(mc.player);
        if (!comp.droneActive) return;

        Entity self = mc.player;
        // 相机仍是本体自己（尚未绑到无人机）时不画，避免贴脸巨大模型
        if (mc.getCameraEntity() == self) return;

        Vec3 cam = context.camera().getPosition();
        double dx = self.getX() - cam.x;
        double dy = self.getY() - cam.y;
        double dz = self.getZ() - cam.z;

        PoseStack ps = context.matrixStack();
        float partialTicks = context.tickCounter().getGameTimeDeltaPartialTick(false);

        ps.pushPose();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            mc.getEntityRenderDispatcher().render(
                    self, dx, dy, dz, self.getYRot(), partialTicks, ps, context.consumers(), FULL_BRIGHT);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            ps.popPose();
        }
    }
}