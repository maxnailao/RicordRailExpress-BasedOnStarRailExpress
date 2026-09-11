package org.agmas.noellesroles.content.block;

import net.minecraft.world.level.block.Block;

/**
 * 重刑犯生成方块
 * <p>
 * 使用原版标靶材质（方块模型 {@code parent} 指向 {@code minecraft:block/target}，无需新增贴图）。
 * 开局流程会扫描世界中的本方块，将重刑犯传送到其上方、戴上重刑犯手铐并弹出抉择 GUI。
 * </p>
 */
public class ConvictSpawnBlock extends Block {
    public ConvictSpawnBlock(Properties settings) {
        super(settings);
    }
}
