package io.wifi.starrailexpress.rules;

import io.wifi.starrailexpress.DeathInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 护甲（盔甲）相关规则。
 * 从 {@link io.wifi.starrailexpress.SRE} 的静态列表中按类别剥离归一化而来。
 */
public class ArmorRules {
    /** 满足任一条件的死亡情形下护甲会保留（无法被击碎）。 */
    public static List<Predicate<DeathInfo>> canStickArmor = new ArrayList<>();
}
