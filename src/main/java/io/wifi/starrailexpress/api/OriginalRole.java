package io.wifi.starrailexpress.api;

import net.minecraft.resources.ResourceLocation;

// 原版列车的职业
public class OriginalRole extends NormalRole {
    public OriginalRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.addFlag("inner.original");
    }
    
}
