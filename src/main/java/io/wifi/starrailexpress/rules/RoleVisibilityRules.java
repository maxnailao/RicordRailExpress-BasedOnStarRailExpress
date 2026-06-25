package io.wifi.starrailexpress.rules;

import io.wifi.starrailexpress.api.SRERole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 角色可见性 / 互动相关规则。
 * 从 {@link io.wifi.starrailexpress.SRE} 的静态列表中按类别剥离归一化而来。
 */
public class RoleVisibilityRules {
    /** 满足任一条件的角色可以看到 / 使用其他玩家（无视常规限制）。 */
    public static List<Predicate<SRERole>> canUseOtherPerson = new ArrayList<>();
}
