package org.agmas.noellesroles.game.fake_steve;

import net.minecraft.core.BlockPos;

/** 怯懦逃离控制所需的最小 AI 状态。 */
public final class FakeSteveAgentState {
    public BlockPos pathGoal;
    public long nextDecisionTick;
    public long startedTick;

    public void clear() {
        pathGoal = null;
        nextDecisionTick = 0L;
        startedTick = 0L;
    }
}
