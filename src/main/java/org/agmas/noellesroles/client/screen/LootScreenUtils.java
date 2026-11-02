package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class LootScreenUtils {
    public static ResourceLocation getItemResourceLocation(String itemName) {
        ResourceLocation ans = null;
        if (itemName.equals("coin")) {
            ans = StarRailExpressID.watheId("textures/font/coin.png");
        }
        // 处理 CS2 皮肤格式 "type/skinName" → "textures/item/skins/{type}/{skinName}.png"
        else if (itemName.contains("/") && itemName.indexOf('/') == itemName.lastIndexOf('/')) {
            String[] parts = itemName.split("/");
            ans = ResourceLocation.fromNamespaceAndPath("starrailexpress",
                    "textures/item/skins/" + parts[0] + "/" + parts[1] + ".png");
        }
        else {
            ans = ResourceLocation.fromNamespaceAndPath("starrailexpress",
                    "textures/item/" +
                            itemName
                            + ".png");
        }
        boolean exists = Minecraft.getInstance().getResourceManager()
                .getResource(ans).isPresent();
        if (!exists) {
            int splitCounter = 0;
            for (char c : itemName.toCharArray())
                if (c == '/')
                    ++splitCounter;
            if (splitCounter > 2) {
                String totalPath = itemName.substring(itemName.indexOf('/') + 1);
                totalPath = totalPath.substring(totalPath.indexOf('/') + 1);
                int nameSpaceSplitIdx = totalPath.indexOf('/');
                String nameSpace = totalPath.substring(0, nameSpaceSplitIdx);
                String assetPath = totalPath.substring(nameSpaceSplitIdx + 1);
                ans = ResourceLocation.fromNamespaceAndPath(nameSpace, assetPath);
            }
        }
        return ans;
    }

    /**
     * 预览贴图是否存在。
     * <p>
     * 皮肤贴图已随素材拆到独立的 ricord_skins 模组；未安装该素材包（或某个皮肤本就没有独立预览图）时
     * 返回 false，调用方应改用品质底图，避免界面出现缺失贴图的紫黑块。
     * <p>
     * 这里必须逐个判定而不是问"皮肤包装了没有"：金币、道具等非皮肤奖池物品的贴图仍在主模组内。
     */
    public static boolean hasPreviewTexture(String itemName) {
        return Minecraft.getInstance().getResourceManager()
                .getResource(getItemResourceLocation(itemName)).isPresent();
    }

    public static ResourceLocation getCoinResourceLocation() {
        return StarRailExpressID.watheId("textures/font/coin.png");
    }
    public static void openLootInfoScreen(Minecraft environment) {
        if (environment != null) {
            if (environment.screen instanceof LootInfoScreen) {
                Screen screen = environment.screen;
                environment.setScreen(screen);
            }
            else
                environment.setScreen(new LootInfoScreen());
        }
    }
    public static void renderPixelScaleSkinItem(int x, int y, int pixelSize, GuiGraphics guiGraphics,
                                                @NotNull ItemStack itemType, String skinName) {
        ItemStack skinItem = itemType.copy();
        skinItem.set(SREDataComponentTypes.SKIN, skinName);
        // 使用缩放渲染
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(pixelSize, pixelSize, 1.0f);
        guiGraphics.renderFakeItem(skinItem, 0, 0);
        poseStack.popPose();
    }
}
