package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：玩家在有击杀者时死亡时触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when a player dies with a killer present.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnPlayerDeathWithKiller {

    /**
     * 玩家在有击杀者时死亡时触发的事件。
     * 游戏当前定义的死亡类型名称有：
     * 'fell_out_of_train'、'poison'、'grenade'、'bat_hit'、'gun_shot'、'knife_stab'。
     * 其他未显式定义的死亡类型默认为 'generic'。
     *
     * <p>Event callback invoked when a player dies with a killer.
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot',
     * 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     *
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<OnPlayerDeathWithKiller> EVENT = createArrayBacked(OnPlayerDeathWithKiller.class,
            listeners -> (player, killer, deathReason) -> {
                for (OnPlayerDeathWithKiller listener : listeners) {
                    listener.onPlayerDeath(player, killer, deathReason);
                }
                return;
            });

    /**
     * 完全走完 {@code GameUtils} 的死亡流程后触发（在所有与击杀相关的事件之后）。
     *
     * <p>移植自 StarRailExpress：需要在「玩家确实已经死透」之后再改职业 / 做转换的逻辑
     * （例如蕾米莉亚把被击杀者转化为仆从）必须挂在这里；若挂在 {@link #EVENT} 上，
     * 会在死亡处理途中改变职业，从而把这次击杀取消掉。</p>
     */
    Event<OnPlayerDeathWithKiller> FINAL_EVENT = createArrayBacked(OnPlayerDeathWithKiller.class,
            listeners -> (player, killer, deathReason) -> {
                for (OnPlayerDeathWithKiller listener : listeners) {
                    listener.onPlayerDeath(player, killer, deathReason);
                }
                return;
            });

    /**
     * 玩家死亡时（有击杀者）的回调方法。
     *
     * <p>Callback invoked when a player dies with an optional killer.
     *
     * @param player     死亡的玩家 / the player who died
     * @param killer     击杀者，可能为 null / the killer, may be null
     * @param deathReason 死亡原因的资源定位符 / resource location identifying the death reason
     */
    void onPlayerDeath(Player player, @Nullable Player killer, ResourceLocation deathReason);
}