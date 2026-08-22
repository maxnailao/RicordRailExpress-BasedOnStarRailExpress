package org.agmas.noellesroles.content.effects;

import net.minecraft.world.effect.MobEffectCategory;
import org.agmas.noellesroles.content.effects.SimpleMobEffect;

/**
 * 失明症效果
 * <p>
 * 仿"失明症"模组玩法：拥有该效果的玩家画面近乎全黑（客户端雾效压黑，见
 * {@code WorldRendererMixin.tmm$applyBlindnessSicknessFog} 与
 * {@code BackgroundRendererMixin.tmm$blackFog}），必须依靠
 * {@link org.agmas.noellesroles.content.item.GuidanceCaneItem}（导盲杖）
 * 探测前方方块，以发光轮廓的形式短暂"看见"环境；附近生物的声音也会以
 * 声纹标记 + 弱轮廓的形式提示方位（见
 * {@link org.agmas.noellesroles.game.blindness.SoundEchoService}）。
 * <p>
 * 效果本体无服务端周期逻辑，所有视觉与交互由客户端按"是否持有该效果"驱动。
 */
public class BlindnessSicknessEffect extends SimpleMobEffect {

    public BlindnessSicknessEffect() {
        super(MobEffectCategory.HARMFUL, 0x1B1B24);
    }
}
