package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.index.SRECosmetics;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.util.ShengxuanSkinHandler;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class GeneralModel implements UnbakedModel, BakedModel {

    /**
     * indexed by skin, then variant!
     */
    private final Map<String, Map<GeneralModelLoadingPlugin.Variant, BakedModel>> bakeModels = new HashMap<>();
    private final UnbakedModel defaultUnbakedModel;
    private final ModelResourceLocation defaultModelLocation;
    private BakedModel defaultBakedModel = null;
    private String itemType;

    /**
     * 渲染管线当前正在渲染的 stack（由 ItemRendererRenderStackMixin 在 ItemRenderer.render 前后写入/清除）。
     * getTransforms() 没有 stack 入参，靠它实现"谁的武器用谁自己皮肤的变换"，
     * 消除"本地玩家皮肤的 third_person 被套到所有人身上"的跨玩家污染。
     */
    private static final ThreadLocal<ItemStack> CURRENT_RENDER_STACK = new ThreadLocal<>();

    public static void setCurrentRenderStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            CURRENT_RENDER_STACK.remove();
        } else {
            CURRENT_RENDER_STACK.set(stack);
        }
    }

    public GeneralModel(String itemType, ModelResourceLocation defaulLocation, UnbakedModel defaultUnbakedModel) {
        this.defaultUnbakedModel = defaultUnbakedModel;
        this.defaultModelLocation = defaulLocation;
        this.itemType = itemType;
    }

    /** 供 ItemOverrides Mixin 判定该包装模型是否为弓/弩类型 */
    public String getItemType() {
        return itemType;
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return defaultUnbakedModel.getDependencies();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelLoader) {
        defaultUnbakedModel.resolveParents(modelLoader);
    }

    @Override
    public @Nullable BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> textureGetter,
            ModelState settings) {
        var itml = defaultModelLocation.id();
        defaultBakedModel = baker.bake(itml.withPath("item/" + itml.getPath()),
                settings);
        for (ItemSkinManager.Skin skin : ItemSkinManager.getSkins(itemType).values()) {
            Map<GeneralModelLoadingPlugin.Variant, BakedModel> variantMap = new HashMap<>();
            for (GeneralModelLoadingPlugin.Variant variant : GeneralModelLoadingPlugin.Variant.values()) {
                // pulling 系列仅弓 / 弩需要，其它类型无 <皮肤>_pulling_x.json，跳过
                if (variant.getSerializedName().startsWith("pulling")
                        && !ItemSkinManager.SkinTypes.BOW.equals(itemType)
                        && !ItemSkinManager.SkinTypes.CROSSBOW.equals(itemType)) {
                    continue;
                }
                var bakedModel = baker.bake(
                        GeneralModelLoadingPlugin.getModelLocation(itemType, skin.getName(), variant),
                        settings);
                if (bakedModel != null) {
                    variantMap.put(variant, bakedModel);
                }
            }
            if (!variantMap.isEmpty() && !"default".equals(skin.getName())) {
                bakeModels.put(skin.getName(), variantMap);
            }
        }

        return this;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    private static final Set<ItemDisplayContext> IN_HAND = EnumSet.of(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.HEAD, ItemDisplayContext.FIXED);

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        var mode = context.itemTransformationMode();
        var variant = mode.firstPerson() || IN_HAND.contains(mode) ? GeneralModelLoadingPlugin.Variant.IN_HAND
                : GeneralModelLoadingPlugin.Variant.DEFAULT;

        // 从玩家的CCA组件获取皮肤，而不是仅依赖TMMCosmetics
        String skinName = stack.get(SREDataComponentTypes.SKIN);
        if (skinName == null) {
            // equipped 兜底仅限本地玩家自己手持的 stack：
            // 渲染他人/地面/容器的无组件 stack 时若回退本地玩家 equipped 皮肤，
            // 会导致"我装备爪子 → 全世界所有普通刀都变 3D 爪"的错误扩散。
            Player viewer = Minecraft.getInstance().player;
            if (viewer != null && (stack == viewer.getMainHandItem() || stack == viewer.getOffhandItem())) {
                skinName = getSkinFromPlayerComponent(stack);
            }
        }
        var skin = ItemSkinManager.Skin.fromString(itemType, skinName);

        // 圣宣皮肤特殊处理：根据当前形态选择模型
        if (skin != null && ShengxuanSkinHandler.SKIN_ID.equals(skin.getName())) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                String formSuffix = ShengxuanSkinHandler.getCurrentFormSuffix(player);
                String formSkinName = skin.getName() + formSuffix;
                if (bakeModels.containsKey(formSkinName)) {
                    var variantMap = bakeModels.get(formSkinName);
                    if (variantMap != null && variantMap.containsKey(variant)) {
                        variantMap.get(variant).emitItemQuads(stack, randomSupplier, context);
                        return;
                    }
                }
            }
        }

        // 暗星皮肤特殊处理：根据当前形态选择模型（天使⇄恶魔）
        if (skin != null && io.wifi.starrailexpress.util.AnxingSkinHandler.SKIN_ID.equals(skin.getName())) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                String formSuffix = io.wifi.starrailexpress.util.AnxingSkinHandler.getCurrentFormSuffix(player);
                String formSkinName = skin.getName() + formSuffix;
                if (bakeModels.containsKey(formSkinName)) {
                    var variantMap = bakeModels.get(formSkinName);
                    if (variantMap != null && variantMap.containsKey(variant)) {
                        variantMap.get(variant).emitItemQuads(stack, randomSupplier, context);
                        return;
                    }
                }
            }
        }

        // 坚守者之怒：根据当前形态选择模型（形态1 ⇄ 形态2）
        if (skin != null && io.wifi.starrailexpress.util.JianshouzheSkinHandler.SKIN_ID.equals(skin.getName())) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                String formSuffix = io.wifi.starrailexpress.util.JianshouzheSkinHandler.getCurrentFormSuffix(player);
                String formSkinName = skin.getName() + formSuffix;
                if (bakeModels.containsKey(formSkinName)) {
                    var variantMap = bakeModels.get(formSkinName);
                    if (variantMap != null && variantMap.containsKey(variant)) {
                        variantMap.get(variant).emitItemQuads(stack, randomSupplier, context);
                        return;
                    }
                }
            }
        }

        // 恶魔之刃：举刀（蓄力/使用刀）时切换为形态2，平时形态1（纯客户端瞬时状态，无需同步）
        if (skin != null && "knife_emozhidao_1".equals(skin.getName())) {
            Player player = Minecraft.getInstance().player;
            if (player != null && player.isUsingItem() && player.getUseItem().is(TMMItems.KNIFE)) {
                var variantMap = bakeModels.get("knife_emozhidao_2");
                if (variantMap != null && variantMap.containsKey(variant)) {
                    variantMap.get(variant).emitItemQuads(stack, randomSupplier, context);
                    return;
                }
            }
        }

        // 弓 / 弩：手持且正在拉弓时，按蓄力进度切换 pulling_0/1/2 三档皮肤变体
        // （仅手持/第一人称上下文生效，不影响背包与 GUI 图标；基于本地玩家，与他人无关）
        if (skin != null
                && variant == GeneralModelLoadingPlugin.Variant.IN_HAND
                && (ItemSkinManager.SkinTypes.BOW.equals(itemType)
                        || ItemSkinManager.SkinTypes.CROSSBOW.equals(itemType))) {
            Player player = Minecraft.getInstance().player;
            if (player != null && player.isUsingItem()) {
                ItemStack use = player.getUseItem();
                if (use.is(TMMItems.BOW) || use.is(TMMItems.CROSSBOW)
                        || use.is(net.minecraft.world.item.Items.BOW)
                        || use.is(net.minecraft.world.item.Items.CROSSBOW)) {
                    var pullMap = bakeModels.get(skin.getName());
                    if (pullMap != null) {
                        int ticks = player.getTicksUsingItem();
                        GeneralModelLoadingPlugin.Variant stage = ticks >= 20
                                ? GeneralModelLoadingPlugin.Variant.PULLING_2
                                : ticks >= 7
                                        ? GeneralModelLoadingPlugin.Variant.PULLING_1
                                        : GeneralModelLoadingPlugin.Variant.PULLING_0;
                        // 若对应档位缺文件，向低档回退，保证至少显示一个拉弓形态
                        GeneralModelLoadingPlugin.Variant[] order = { stage,
                                GeneralModelLoadingPlugin.Variant.PULLING_1,
                                GeneralModelLoadingPlugin.Variant.PULLING_0 };
                        for (GeneralModelLoadingPlugin.Variant v : order) {
                            if (pullMap.containsKey(v)) {
                                pullMap.get(v).emitItemQuads(stack, randomSupplier, context);
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (skin != null && bakeModels.containsKey(skin.getName())) {
            var variantMap = bakeModels.get(skin.getName());
            if (variantMap != null && variantMap.containsKey(variant)) {
                variantMap.get(variant).emitItemQuads(stack, randomSupplier, context);
                return;
            }
        }
        getDefaultModel().emitItemQuads(stack, randomSupplier, context);
    }

    /**
     * 从玩家的CCA组件获取皮肤名称
     */
    private String getSkinFromPlayerComponent(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            return ItemSkinManager.getEquippedSkin(player, stack);
        }
        // 如果无法获取玩家或组件，则回退到原始方法
        return SRECosmetics.getSkin(stack);
    }

    /**
     * 双形态皮肤基名 → 当前形态变体名。
     * 圣宣/暗星只注册了基名 + "_1"/"_2" 形态，模型文件仅存在于形态变体上；
     * 任何按皮肤名查 bakeModels 的路径都必须先做这层重映射，
     * 否则会查到基名的"缺失模型"（抽奖/仓库装备的就是基名 ID）。
     * 与 emitItemQuads 中的形态选择逻辑保持一致。
     */
    private String resolveDualFormSkinName(String skinName) {
        Player player = Minecraft.getInstance().player;
        if (player == null || skinName == null) {
            return skinName;
        }
        if (ShengxuanSkinHandler.SKIN_ID.equals(skinName)) {
            return skinName + ShengxuanSkinHandler.getCurrentFormSuffix(player);
        }
        if (io.wifi.starrailexpress.util.AnxingSkinHandler.SKIN_ID.equals(skinName)) {
            return skinName + io.wifi.starrailexpress.util.AnxingSkinHandler.getCurrentFormSuffix(player);
        }
        if (io.wifi.starrailexpress.util.JianshouzheSkinHandler.SKIN_ID.equals(skinName)) {
            return skinName + io.wifi.starrailexpress.util.JianshouzheSkinHandler.getCurrentFormSuffix(player);
        }
        return skinName;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        return getDefaultModel().getQuads(state, face, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return getDefaultModel().useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return getDefaultModel().isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return getDefaultModel().usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return getDefaultModel().isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return getDefaultModel().getParticleIcon();
    }

    @Override
    public ItemOverrides getOverrides() {
        return getDefaultModel().getOverrides();
    }

    @Override
    public ItemTransforms getTransforms() {
        ItemTransforms skinTransforms = getSkinHandTransforms();
        return skinTransforms != null ? skinTransforms : getDefaultModel().getTransforms();
    }

    /**
     * 1.21.1 渲染管线只读取根模型的 getTransforms()，皮肤模型 JSON 里的 display 段会被忽略，
     * 导致 3D 皮肤（如星光刀）手持姿势永远是默认模型的。这里把 IN_HAND 变体的手持变换合并进来。
     *
     * 关键：变换必须来自"当前正在被渲染的那个 stack 自身的皮肤"（由 ItemRendererRenderStackMixin
     * 写入 CURRENT_RENDER_STACK），而不是本地玩家——否则本地玩家皮肤的 third_person 会套到所有人身上，
     * 使他人手上的 3D 武器被转到侧对镜头、压成“2D 贴图”状（跨玩家污染）。
     * 只有真正的 3D 皮肤模型(isGui3d=true)才套用皮肤变换；2D 平铺皮肤返回 null 用物品默认变换。
     */
    private @Nullable ItemTransforms getSkinHandTransforms() {
        ItemStack stack = CURRENT_RENDER_STACK.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!(stack.getItem() instanceof SkinableItem skinable) || !itemType.equals(skinable.getItemSkinType())) {
            return null;
        }
        // 皮肤名严格来自该 stack 自身组件；equipped 兜底仅限本地玩家自己手持（与 emitItemQuads 几何规则一致）
        String skinName = stack.get(SREDataComponentTypes.SKIN);
        if (skinName == null) {
            Player viewer = Minecraft.getInstance().player;
            if (viewer != null && (stack == viewer.getMainHandItem() || stack == viewer.getOffhandItem())) {
                skinName = getSkinFromPlayerComponent(stack);
            }
        }
        if (skinName == null || "default".equals(skinName)) {
            return null;
        }
        // 双形态基名先重映射到当前形态模型，避免命中缺失模型导致手持变换错乱（圣宣显示 bug 根因）
        skinName = resolveDualFormSkinName(skinName);
        var skin = ItemSkinManager.Skin.fromString(itemType, skinName);
        if (skin == null) {
            return null;
        }
        var variantMap = bakeModels.get(skin.getName());
        if (variantMap == null) {
            return null;
        }
        BakedModel handModel = variantMap.get(GeneralModelLoadingPlugin.Variant.IN_HAND);
        if (handModel == null) {
            return null;
        }
        // 仅 3D 皮肤(如星光刀)套用此变换；2D 平铺皮肤(item/generated)返回 null，用默认模型变换。
        if (!handModel.isGui3d()) {
            return null;
        }
        ItemTransforms ht = handModel.getTransforms();
        if (ht == ItemTransforms.NO_TRANSFORMS) {
            return null;
        }
        BakedModel defaultVariant = variantMap.get(GeneralModelLoadingPlugin.Variant.DEFAULT);
        ItemTransforms guiSrc = defaultVariant != null ? defaultVariant.getTransforms()
                : ItemTransforms.NO_TRANSFORMS;
        ItemTransforms dm = getDefaultModel().getTransforms();
        // skinName 现已锁定为“当前 stack 自己的皮肤”，几何与变换同源：
        // 第三人称可安全采用该皮肤自身的 third_person——他人用它自己皮肤的变换，本地用自己皮肤的变换，互不污染。
        return new ItemTransforms(
                ht.getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND),
                ht.getTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND),
                ht.getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND),
                ht.getTransform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND),
                dm.getTransform(ItemDisplayContext.HEAD),
                guiSrc.getTransform(ItemDisplayContext.GUI),
                guiSrc.getTransform(ItemDisplayContext.GROUND),
                dm.getTransform(ItemDisplayContext.FIXED));
    }

    private BakedModel getDefaultModel() {
        if (!bakeModels.containsKey("default")) {
            return defaultBakedModel;
        }
        return bakeModels.get("default")
                .getOrDefault(GeneralModelLoadingPlugin.Variant.DEFAULT, defaultBakedModel);
    }
}