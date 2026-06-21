package org.agmas.noellesroles.client.scene;

import org.agmas.noellesroles.content.block.scene.FogZoneBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 客户端：判断本地玩家是否处于迷雾区域内（用于关闭本能）。
 */
public final class SceneFogClient {
    private SceneFogClient() {
    }

    public static boolean isLocalPlayerInFog() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        BlockPos feet = mc.player.blockPosition();
        if (mc.level.getBlockState(feet).getBlock() instanceof FogZoneBlock) {
            return true;
        }
        BlockPos eye = BlockPos.containing(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        return mc.level.getBlockState(eye).getBlock() instanceof FogZoneBlock;
    }
}
