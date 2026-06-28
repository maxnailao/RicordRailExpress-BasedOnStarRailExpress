package io.wifi.starrailexpress.api;

import java.util.HashSet;

import org.agmas.noellesroles.utils.RoleUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class SREAbstractInfoClass {
    public abstract ResourceLocation getIdentifier();

    /**
     * 显示FLAG。用于RoleIntroduceScreen的filter
     */
    public HashSet<String> flags = new HashSet<>();

    /**
     * 获取显示FLAG
     */
    public HashSet<String> getFlags() {
        return this.flags;
    }

    public Component getDisplayName() {
        return RoleUtils.getRoleOrModifierName(this);
    }

    public Component getDisplayNameWithColor() {
        return RoleUtils.getRoleOrModifierNameWithColor(this);
    }
}
