package org.agmas.noellesroles.content.block.scene;

import net.minecraft.world.level.block.Block;

/**
 * 雕像/玩偶（场景任务「祷告任务」）：玩家持续看向它 5 秒完成。祷告检测在 SceneTaskManager 中通过视线射线判定。
 * 原版雕纹石英块贴图。
 */
public class StatueBlock extends Block {

    public StatueBlock(Properties settings) {
        super(settings);
    }
}
