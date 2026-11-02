package io.wifi.starrailexpress.client.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.model.GeneralModelLoadingPlugin.Variant;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashSet;
import java.util.Set;

/**
 * 皮肤素材包（ricord_skins）可用性索引。
 * <p>
 * 皮肤贴图、皮肤模型与皮肤显示名已从本模组拆出，改由独立的纯资源模组
 * {@code ricord_skins} 提供，其资源仍写在 {@code starrailexpress} 命名空间下
 * （{@code assets/starrailexpress/models/item/skins/...}），因此各处
 * {@code SRE.id("item/skins/...")} 的引用方式不变。
 * <p>
 * 皮肤包未安装时主模组照常运行：这里为界面与渲染提供"素材是否存在"的判定，
 * 使皮肤模型注册不再刷一屏缺失模型告警、皮肤列表与头顶帽子不会显示缺失贴图。
 * <p>
 * 索引在每次模型烘焙（游戏启动、F3+T、切换语言）时由
 * {@link GeneralModelLoadingPlugin#onInitializeModelLoader} 调用 {@link #rebuild()} 重建，
 * 因此无需额外的缓存失效逻辑。
 */
public final class SkinAssetIndex {

    /** 实际存在模型 JSON 的路径，形如 {@code item/skins/knife/knife_bunana_in_hand} */
    private static volatile Set<String> modelPaths = Set.of();
    /** 索引是否已成功建过一次；未建过之前一律按"素材存在"放行 */
    private static volatile boolean built;

    private SkinAssetIndex() {
    }

    /**
     * 遍历皮肤注册表，逐个探测模型 JSON 是否存在。
     * 注册表留在主模组（皮肤的整数 ID 依赖注册顺序，不能挪到素材包里）。
     */
    public static void rebuild() {
        var registry = ItemSkinManager.getSkins();
        if (registry.isEmpty()) {
            // 注册表还没填充（皮肤在 SRE.onInitialize 里注册）：此时探测必然一无所获，
            // 不能把这个"假阴性"写进缓存，否则皮肤会被永久判为素材缺失。保留上一次结果。
            SRE.LOGGER.warn("[SkinAssets] 模型烘焙时皮肤注册表为空，本次不重建可用性索引");
            return;
        }

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Set<String> found = new HashSet<>();
        for (var typeEntry : registry.entrySet()) {
            String skinType = typeEntry.getKey();
            for (String skinName : typeEntry.getValue().keySet()) {
                for (Variant variant : Variant.values()) {
                    String suffix = variant == Variant.DEFAULT ? "" : "_" + variant.getSerializedName();
                    String path = "item/skins/" + skinType + "/" + skinName + suffix;
                    if (resourceManager.getResource(modelJsonLocation(path)).isPresent()) {
                        found.add(path);
                    }
                }
            }
        }

        boolean wasInstalled = isInstalled();
        modelPaths = Set.copyOf(found);
        built = true;

        int skinCount = found.size();
        if (skinCount == 0) {
            SRE.LOGGER.warn("[SkinAssets] 未找到任何皮肤模型资源：ricord_skins 皮肤素材包未安装，"
                    + "武器与帽子皮肤将不可用（注册表皮肤类型数={}）", registry.size());
        } else if (!wasInstalled) {
            SRE.LOGGER.info("[SkinAssets] 皮肤素材包可用，命中 {} 个皮肤模型（皮肤类型数={}）",
                    skinCount, registry.size());
        }
    }

    /**
     * 皮肤素材包是否已安装。索引还没建过时按"已安装"处理，
     * 以免把"还没查过"误判成"没有素材"而永久屏蔽皮肤。
     */
    public static boolean isInstalled() {
        return !built || !modelPaths.isEmpty();
    }

    /** 指定皮肤的基础模型 JSON 是否存在 */
    public static boolean hasSkin(String skinType, String skinName) {
        return !built || modelPaths.contains("item/skins/" + skinType + "/" + skinName);
    }

    /**
     * 该模型是否可用。
     * <p>
     * 本索引只裁决皮肤规范路径（{@code item/skins/...}）下的资源；其它模型路径一律放行，
     * 避免调用方把"物品默认模型"也当成皮肤资源判断。索引尚未建立时同样放行——
     * 宁可多注册一个模型（最坏是一条缺失模型告警），也不能把皮肤永久判为不可用。
     */
    public static boolean hasModel(ResourceLocation modelId) {
        if (modelId == null) {
            return false;
        }
        if (!built || !SRE.MOD_ID.equals(modelId.getNamespace()) || !modelId.getPath().startsWith("item/skins/")) {
            return true;
        }
        return modelPaths.contains(modelId.getPath());
    }

    /** 模型 RL → {@code assets/<命名空间>/models/<路径>.json} */
    private static ResourceLocation modelJsonLocation(String modelPath) {
        return SRE.id("models/" + modelPath + ".json");
    }
}
