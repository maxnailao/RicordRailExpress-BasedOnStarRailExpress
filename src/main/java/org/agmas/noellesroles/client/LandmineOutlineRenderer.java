package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.agmas.noellesroles.content.block_entity.LandmineBlockEntity;
import org.agmas.noellesroles.init.ModBlocks;

import java.awt.Color;

/**
 * 反人员地雷透视渲染器
 * - 杀手阵营开启杀手透视（直觉）时，以红色轮廓透视显示场上的所有地雷
 * - 复用 TaskBlockOverlayRenderer 的无遮挡线框绘制
 */
public class LandmineOutlineRenderer {
    public static void render(WorldRenderContext context) {
        var client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null)
            return;
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
            return;
        // 仅杀手阵营且开启了杀手透视时可见
        if (!SREClient.isInstinctEnabled())
            return;
        if (!SREClient.gameComponent.isKillerTeam(client.player))
            return;

        BlockPos selfPos = client.player.blockPosition();
        for (LandmineBlockEntity be : LandmineBlockEntity.CLIENT_INSTANCES) {
            BlockPos pos = be.getBlockPos();
            // 自清理：方块已不存在（被引爆/破坏/区块卸载后残留）时移除记录
            if (!context.world().getBlockState(pos).is(ModBlocks.LANDMINE_BLOCK)) {
                LandmineBlockEntity.CLIENT_INSTANCES.remove(be);
                continue;
            }
            // 限制渲染距离，避免超远轮廓干扰
            if (pos.distSqr(selfPos) > 64 * 64)
                continue;
            TaskBlockOverlayRenderer.renderBlockOverlay(context, pos, Color.RED, 1f, true, 0f);
        }
    }
}
