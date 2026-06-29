package io.wifi.starrailexpress.rules;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * 物品丢弃相关规则。
 * 从 {@link io.wifi.starrailexpress.SRE} 的静态列表中按类别剥离归一化而来。
 */
public class DropRules {
    /** 允许被丢弃的物品 ID 列表（字符串形式）。 */
    public static ArrayList<String> canDropItem = new ArrayList<>();
    /** 满足任一条件的玩家允许丢弃物品。 */
    public static ArrayList<Predicate<Player>> canDrop = new ArrayList<>();
}
