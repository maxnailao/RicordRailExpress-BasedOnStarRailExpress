package io.wifi.starrailexpress.event;

/**
 * 通用击杀条件模型（无 Minecraft 依赖，客户端/服务端共用）。
 * 编码：
 *  - kill:&lt;N&gt;                    任意击杀
 *  - kill_role:&lt;roleId&gt;:&lt;N&gt;   以指定职业击杀（roleId 形如 namespace:path，内含冒号）
 *  - kill_weapon:&lt;type&gt;:&lt;N&gt;   以指定武器类别击杀（knife|gun|bat|grenade|bow|trident）
 * 未来新增类型只需加一种 head 与 kind，并在服务端 EventManager.matches 里补匹配规则。
 */
public record EventCondition(String kind, String arg, int target) {
    public static final String ANY = "any";
    public static final String ROLE = "role";
    public static final String WEAPON = "weapon";
    public static final String UNKNOWN = "unknown";

    public boolean countable() {
        if (target <= 0) return false;
        return kind.equals(ANY) || kind.equals(ROLE) || kind.equals(WEAPON);
    }

    public static EventCondition parse(String token) {
        if (token == null) return new EventCondition(UNKNOWN, null, 0);
        String t = token.trim();
        int c1 = t.indexOf(':');
        if (c1 < 0) return new EventCondition(UNKNOWN, null, 0);
        String head = t.substring(0, c1);
        String mid = t.substring(c1 + 1);
        if (head.equals("kill")) {
            return new EventCondition(ANY, null, parseIntOr(mid, 0));
        }
        // 带参数类型：末段是数值目标，中间整段是参数（参数自身可能含冒号，如 namespace:path）
        int c2 = mid.lastIndexOf(':');
        if (c2 < 0) return new EventCondition(UNKNOWN, null, 0);
        String arg = mid.substring(0, c2);
        int n = parseIntOr(mid.substring(c2 + 1), 0);
        if (head.equals("kill_role")) return new EventCondition(ROLE, arg, n);
        if (head.equals("kill_weapon")) return new EventCondition(WEAPON, arg, n);
        return new EventCondition(UNKNOWN, null, 0);
    }

    private static int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}