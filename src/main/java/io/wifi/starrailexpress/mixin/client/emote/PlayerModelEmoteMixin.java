package io.wifi.starrailexpress.mixin.client.emote;

import io.wifi.starrailexpress.client.emote.EmoteClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 表情动画：在 {@link PlayerModel#setupAnim} 末尾（TAIL）追加式覆盖手臂/头部姿态，
 * 实现程序化表情动画。不取消任何原版逻辑，仅叠加最终姿态；
 * 因原版在 setupAnim 内先执行外层模型复制（袖子等），修改手臂后需重新复制一次。
 * <p>
 * 注意：此处刻意不使用 {@code @Shadow} 字段（对继承自父类 {@link HumanoidModel} 的
 * 字段在运行时映射解析会失败导致崩溃），改为让 Mixin 类继承 HumanoidModel 直接访问
 * 其 public 字段（head/leftArm/rightArm），袖套字段经 PlayerModel 强转访问。
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerModel.class)
public abstract class PlayerModelEmoteMixin extends HumanoidModel<LivingEntity> {

    // Mixin 构造器仅满足继承需要，永远不会被调用
    private PlayerModelEmoteMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void sre$applyEmoteAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity.level() instanceof ClientLevel clientLevel)) {
            return;
        }
        EmoteClientState.Entry entry = EmoteClientState.get(entity.getId());
        if (entry == null) {
            return;
        }
        // ageInTicks - tickCount ≈ partialTick，保证动画平滑
        float t = (clientLevel.getGameTime() - entry.startGameTick()) + (ageInTicks - entity.tickCount);
        if (t < 0.0F || t > entry.emote().durationTicks() + 5.0F) {
            return;
        }

        switch (entry.emote()) {
            case WAVE -> {
                // 挥手：右臂高举过头，左右摆动
                this.rightArm.xRot = -(float) Math.PI * 0.92F;
                this.rightArm.yRot = 0.0F;
                this.rightArm.zRot = -0.15F + Mth.sin(t * 0.35F) * 0.3F;
                this.leftArm.xRot = 0.05F;
                this.leftArm.yRot = 0.0F;
                this.leftArm.zRot = 0.05F;
                this.head.zRot = Mth.sin(t * 0.175F) * 0.06F;
            }
            case CLAP -> {
                // 鼓掌：双臂前伸，双手交替合拢
                float clap = Math.abs(Mth.sin(t * 0.45F));
                this.rightArm.xRot = -1.35F;
                this.rightArm.yRot = -0.5F + clap * 0.45F;
                this.rightArm.zRot = 0.0F;
                this.leftArm.xRot = -1.35F;
                this.leftArm.yRot = 0.5F - clap * 0.45F;
                this.leftArm.zRot = 0.0F;
                this.head.xRot = 0.12F;
            }
            case SCRATCH_HEAD -> {
                // 挠头：右臂高举，手绕到脑后，小幅摩擦摆动；头微微偏向对侧
                this.rightArm.xRot = -2.6F;
                this.rightArm.yRot = 0.3F + Mth.sin(t * 0.4F) * 0.25F;
                this.rightArm.zRot = -0.8F;
                this.leftArm.xRot = 0.05F;
                this.leftArm.yRot = 0.0F;
                this.leftArm.zRot = 0.05F;
                this.head.xRot = 0.06F;
                this.head.yRot = 0.12F;
                this.head.zRot = 0.05F;
            }
            case THINK -> {
                // 思考：左臂横于胸前作支撑，右手竖起托下巴，低头微偏并极缓点头
                this.leftArm.xRot = -1.45F;
                this.leftArm.yRot = 0.5F;
                this.leftArm.zRot = 0.15F;
                this.rightArm.xRot = -2.5F;
                this.rightArm.yRot = -0.35F;
                this.rightArm.zRot = -0.3F;
                this.head.xRot = 0.25F + Mth.sin(t * 0.06F) * 0.03F;
                this.head.yRot = 0.12F;
                this.head.zRot = 0.06F;
            }
            case POINT -> {
                // 指认：右臂沿视线方向笔直指出
                this.rightArm.xRot = -(float) Math.PI / 2.0F + this.head.xRot;
                this.rightArm.yRot = this.head.yRot - 0.05F;
                this.rightArm.zRot = 0.0F;
                this.leftArm.xRot = 0.05F;
                this.leftArm.yRot = 0.0F;
                this.leftArm.zRot = 0.05F;
            }
            case CONFUSED -> {
                // 疑惑：耸肩摊手（双臂微抬外张），头部侧倾摇晃
                float sway = Mth.sin(t * 0.12F);
                this.rightArm.xRot = -0.25F;
                this.rightArm.yRot = 0.0F;
                this.rightArm.zRot = -0.55F + sway * 0.05F;
                this.leftArm.xRot = -0.25F;
                this.leftArm.yRot = 0.0F;
                this.leftArm.zRot = 0.55F - sway * 0.05F;
                this.head.xRot = 0.12F;
                this.head.zRot = sway * 0.18F;
            }
        }

        // 原版在 setupAnim 内先复制基础部位到外层（帽子/袖子），修改后必须全部重新同步，
        // 否则头部额外层会与头部本体分离（hat 复制发生在 HumanoidModel.setupAnim 内）
        this.hat.copyFrom(this.head);
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;
        self.leftSleeve.copyFrom(this.leftArm);
        self.rightSleeve.copyFrom(this.rightArm);
    }
}
