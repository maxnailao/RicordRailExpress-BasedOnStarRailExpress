package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：判断一名玩家是否允许被另一名玩家（操纵师）操控/附身。
 * 这是一个拦截型事件：只要任意监听器返回 {@code false}，操控即被阻止。
 *
 * <p>Event interface to determine whether a target player is allowed to be
 * controlled / possessed by a controller (e.g. the Manipulator).
 * This is a veto event: if ANY listener returns {@code false}, the control
 * attempt is cancelled. By default (no listeners) control is allowed.
 *
 * <p>Other roles / effects can register a listener to make themselves immune
 * to being possessed, or to forbid possessing protected targets.
 */
public interface AllowPlayerControlled {

    /**
     * 拦截事件：任意监听器返回 {@code false} 即阻止操控。
     *
     * <p>Veto event: any listener returning {@code false} blocks the control.
     */
    Event<AllowPlayerControlled> EVENT = createArrayBacked(AllowPlayerControlled.class, listeners -> (controller, target) -> {
        for (AllowPlayerControlled listener : listeners) {
            if (!listener.allowControlled(controller, target)) {
                return false;
            }
        }
        return true;
    });

    /**
     * 判断 {@code target} 是否允许被 {@code controller} 操控。
     *
     * <p>Determines whether {@code target} may be controlled by {@code controller}.
     *
     * @param controller 发起操控的玩家 / the player attempting to take control
     * @param target     被操控的目标玩家 / the player who would be controlled
     * @return {@code true} 若允许被操控 / {@code true} if control is allowed
     */
    boolean allowControlled(Player controller, Player target);
}
