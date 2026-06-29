package io.wifi.starrailexpress.rules;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 回放（录像）发送相关规则。
 * 从 {@link io.wifi.starrailexpress.SRE} 的静态列表中按类别剥离归一化而来。
 */
public class ReplayRules {
    /** 满足任一条件的玩家被禁止接收回放。 */
    public static List<Predicate<ServerPlayer>> cantSendReplay = new ArrayList<>();
    /** 满足任一条件的玩家允许接收回放（优先于普通存活判定）。 */
    public static List<Predicate<ServerPlayer>> canSendReplay = new ArrayList<>();
}
