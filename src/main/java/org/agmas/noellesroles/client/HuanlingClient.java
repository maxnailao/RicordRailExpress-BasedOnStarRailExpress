package org.agmas.noellesroles.client;

import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.roles.innocence.huanling.HuanlingPlayerComponent;

/**
 * 幻灵客户端状态工具。
 *
 * <p>
 * 附身期间（clientPossessTarget 非空）幻灵处于旁观模式、视角锁定宿主，
 * 为避免幻灵借旁观者特权窥探其他玩家的职业与聊天信息，
 * 客户端一切「旁观信息特权」入口都须先经过 {@link #isPossessing()} 检查。
 */
public final class HuanlingClient {
    private HuanlingClient() {
    }

    /** 本地玩家是否为正处于附身宿主状态的幻灵。 */
    public static boolean isPossessing() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null)
            return false;
        var comp = HuanlingPlayerComponent.KEY.maybeGet(mc.player).orElse(null);
        return comp != null && comp.clientPossessTarget != null;
    }
}
