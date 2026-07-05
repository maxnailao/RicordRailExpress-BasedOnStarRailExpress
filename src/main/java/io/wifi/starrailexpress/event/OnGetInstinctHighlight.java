package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.Entity;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：获取直觉高亮显示的颜色（用于特殊视觉效果）。
 * 首个返回非 -1 结果的监听器生效：
 * 返回 -1 表示不改变（默认），返回 -2 表示禁用直觉高亮。<br/>
 * 请注意，不要返回白色，因为白色会被解析为-1！
 * 建议使用
 * 
 * <pre>
 * new java.awt.Color(254, 254, 254).getRGB();
 * </pre>
 * 
 * 替代白色。
 * <p>
 * Event interface to obtain the instinct highlight color for an entity (for
 * special visual effects).
 * The first listener returning a value != -1 takes effect:
 * -1 means no change (default), -2 means disable instinct highlight.
 */
public interface OnGetInstinctHighlight {

    /**
     * 获取直觉高亮颜色的事件。
     * 首个返回非 -1 的监听器结果生效；
     * -1 表示不改变，-2 表示禁用直觉高亮。<br/>
     * 请注意，不要返回白色，因为白色会被解析为-1！
     * 建议使用
     * 
     * <pre>
     * new java.awt.Color(254, 254, 254).getRGB();
     * </pre>
     * 
     * 替代白色。
     *
     * <p>
     * Callback for obtaining the highlight color for instinct vision.
     * The first listener returning != -1 wins;
     * -1 means no change (default), -2 means disable instinct highlight.
     */
    Event<OnGetInstinctHighlight> EVENT = createArrayBacked(OnGetInstinctHighlight.class,
            listeners -> (target, isInstinctEnabled) -> {
                if (target == null)
                    return -1;
                for (OnGetInstinctHighlight listener : listeners) {
                    int color = listener.GetInstinctHighlight(target, isInstinctEnabled);
                    if (color != -1) {
                        return color;
                    }
                }
                return -1;
            });

    /**
     * 获取指定实体在直觉视野下的高亮颜色。
     *
     * <p>
     * Returns the instinct highlight color for the given target entity.
     *
     * @param target            需要高亮的目标实体 / the target entity to highlight
     * @param isInstinctEnabled 直觉功能当前是否开启 / whether the instinct ability is
     *                          currently active
     * @return 高亮颜色值；-1 表示不改变，-2 表示禁用直觉高亮 /
     *         the highlight color; -1 for no change, -2 to disable instinct
     *         highlight
     */
    int GetInstinctHighlight(Entity target, boolean isInstinctEnabled);
}
