package org.agmas.noellesroles.role.touhou;

import net.minecraft.resources.ResourceLocation;

public class ForestRoles {
    public static final String NAMESPACE = "th_forest";
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static void init() {
    }
}
