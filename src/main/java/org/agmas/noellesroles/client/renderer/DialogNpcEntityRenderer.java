package org.agmas.noellesroles.client.renderer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.DialogNpcEntity;

import java.util.UUID;

/**
 * 对话 NPC 实体渲染器。
 * <p>
 * 使用原版玩家模型渲染，皮肤贴图取自
 * {@code noellesroles:textures/entity/dialog_npc/<skin>.png}
 * （skin 由对话 JSON 的 "skin" 字段指定）；未配置或为空时回退到 Steve 默认皮肤。
 * <p>
 * 对话 JSON 配置 {@code "slim": true} 时使用 Alex（细臂）模型，否则为 Steve 宽体模型，
 * 请提供对应的 64x64 皮肤贴图。
 */
public class DialogNpcEntityRenderer extends LivingEntityRenderer<DialogNpcEntity, PlayerModel<DialogNpcEntity>> {

    /** 回退用默认皮肤对应的 UUID（Steve） */
    private static final UUID DEFAULT_SKIN_UUID = UUID.fromString("7833c811-436e-40c4-868a-ffb1073f48a2");

    /** Alex（细臂）模型 */
    private final PlayerModel<DialogNpcEntity> modelSlim;
    /** Steve（宽体）模型 */
    private final PlayerModel<DialogNpcEntity> modelWide;

    public DialogNpcEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5F);
        this.modelSlim = this.model;
        this.modelWide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
    }

    @Override
    public ResourceLocation getTextureLocation(DialogNpcEntity entity) {
        // 根据实体的 slim 同步字段切换模型
        this.model = entity.isSlim() ? this.modelSlim : this.modelWide;
        String skin = entity.getSkinId();
        if (skin != null && !skin.isBlank()) {
            return Noellesroles.id("textures/entity/dialog_npc/" + skin + ".png");
        }
        return DefaultPlayerSkin.get(DEFAULT_SKIN_UUID).texture();
    }
}
