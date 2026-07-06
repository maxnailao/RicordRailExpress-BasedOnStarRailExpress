package org.agmas.noellesroles.modifier.touhou;

import net.minecraft.resources.ResourceLocation;

public class THMiscModifiers {
    public static final String NAMESPACE = "th_misc";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static void init() {
    }
}
