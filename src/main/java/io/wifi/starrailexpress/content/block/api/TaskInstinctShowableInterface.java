package io.wifi.starrailexpress.content.block.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface TaskInstinctShowableInterface {
    @Environment(EnvType.CLIENT)

    /**
     * 仅客户端：是否渲染
     * 
     * @return
     */
    boolean shouldRenderTaskInstinct(Level level, BlockState state, BlockPos pos, Player player);

    @Environment(EnvType.CLIENT)

    /**
     * 仅客户端：渲染颜色
     * 
     * @return
     */
    java.awt.Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player);

    /**
     * 需要12+。可不改
     * 
     * @return
     */
    public default int taskInstinctId() {
        return 1145;
    }
}
