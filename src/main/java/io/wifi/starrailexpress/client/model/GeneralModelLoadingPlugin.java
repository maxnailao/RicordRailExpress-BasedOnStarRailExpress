package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import java.util.HashMap;

public class GeneralModelLoadingPlugin implements ModelLoadingPlugin {

    public static final HashMap<String, ModelResourceLocation> MODEL_IDS = new HashMap<>();
    static {
        for (Item skinnableitem : TMMItems.SkinableItem) {
            if (skinnableitem instanceof SkinableItem it) {
                String skinId = it.getItemSkinType();
                var model = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(it));
                MODEL_IDS.putIfAbsent(skinId, model);
            }
        }
    }

    public static ResourceLocation getModelLocation(String itemType, String skin, Variant variant) {
        var MODEL_ID = MODEL_IDS.get(itemType);
        if (MODEL_ID == null) {
            return null;
        }
        if (skin == "default") {
            return MODEL_ID.id().withPath(path -> "item/%s".formatted(MODEL_ID.id().getPath()));
        }
        var skinPart = "%s".formatted(skin);
        var variantPart = variant == Variant.DEFAULT ? "" : "_%s".formatted(variant.getSerializedName());

        return SRE.id("item/skins/%s/%s%s".formatted(MODEL_ID.id().getPath(), skinPart, variantPart));
    }

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        // make sure all models get loaded
        for (var entry : ItemSkinManager.getSkins().entrySet()) {
            for (ItemSkinManager.Skin skin : entry.getValue().values()) {
                if (ItemSkinManager.SkinTypes.HAT.equals(entry.getKey())) {
                    // 帽子皮肤无物品载体，直接注册 models/item/skins/hat/{name}.json 进行烘焙，
                    // 运行时通过 FabricBakedModelManager.getModel(同一 ID) 获取
                    pluginContext.addModels(SRE.id("item/skins/hat/%s".formatted(skin.getName())));
                    continue;
                }
                for (Variant variant : Variant.values()) {
                    // pulling 系列变体只对弓 / 弩有意义，其它类型无对应模型文件，跳过避免缺失模型告警
                    if (variant.getSerializedName().startsWith("pulling")
                            && !ItemSkinManager.SkinTypes.BOW.equals(entry.getKey())
                            && !ItemSkinManager.SkinTypes.CROSSBOW.equals(entry.getKey())) {
                        continue;
                    }
                    // 无物品载体的皮肤类型在 MODEL_IDS 中没有映射，跳过避免空指针
                    var modelLocation = getModelLocation(entry.getKey(), skin.getName(), variant);
                    if (modelLocation == null) {
                        continue;
                    }
                    pluginContext.addModels(modelLocation);
                }
            }
        }

        pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
            if (context.topLevelId() != null) {
                var item = BuiltInRegistries.ITEM.get(context.topLevelId().id());
                if (item instanceof SkinableItem it) {
                    var itemName = it.getItemSkinType();
                    // 皮肤类型为 null 的物品不使用皮肤系统，直接使用原始模型
                    if (itemName == null) {
                        return unbakedModel;
                    }
                    return new GeneralModel(itemName, context.topLevelId(),unbakedModel);
                }
            }

            // var mid = context.topLevelId();
            // if (MODEL_IDS.values().contains(mid)) {
            // return new GeneralModel(MODEL_IDS_MAPPINGS.get(mid), unbakedModel);
            // }
            return unbakedModel;
        });
    }

    public enum Variant implements StringRepresentable {
        DEFAULT("default"),
        IN_HAND("in_hand"),
        // 弓 / 弩拉弓蓄力的三档变体，对应模型文件 <皮肤名>_pulling_0/1/2.json
        PULLING_0("pulling_0"),
        PULLING_1("pulling_1"),
        PULLING_2("pulling_2");

        private final String name;

        Variant(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
