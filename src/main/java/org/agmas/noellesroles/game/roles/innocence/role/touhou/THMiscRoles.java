package org.agmas.noellesroles.role.touhou;

import org.agmas.noellesroles.role.touhou.roles.THTenshiRole;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;

public class THMiscRoles {
  public static final String NAMESPACE = "th_misc";

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
  }

  // 天子Hinanawi Tenshi
  public static final ResourceLocation TENSHI_ID = id("hinanawi_tenshi");
  public static SRERole TENSHI = TMMRoles
      .registerRole(new THTenshiRole(TENSHI_ID, new java.awt.Color(89, 177, 250).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime() * 2, false));

  public static void init() {
  }
}
