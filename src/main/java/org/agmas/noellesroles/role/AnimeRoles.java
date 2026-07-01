package org.agmas.noellesroles.role;

import net.minecraft.resources.ResourceLocation;

public class AnimeRoles {
    public static final String NAMESPACE = "anime";
    
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static void init() {
    }
}
