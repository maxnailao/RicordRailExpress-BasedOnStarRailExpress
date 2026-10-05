package io.wifi.starrailexpress.api.replay.board;

import io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 小脑排行榜逻辑验证（直接调用项目已编译的 class）。
 *
 * 用法（在仓库根目录）：
 *   MC=<minecraft-merged jar>
 *   javac -encoding UTF-8 -cp "build/classes/java/main;$MC" -d build/harness-out tools/BoardLogicCheck.java
 *   java -cp "build/harness-out;build/classes/java/main;$MC" \
 *        io.wifi.starrailexpress.api.replay.board.BoardLogicCheck
 */
public class BoardLogicCheck {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String extra) {
        System.out.printf("  %-50s %s %s%n", name, ok ? "OK" : "FAIL", extra);
        if (ok) pass++; else fail++;
    }

    static UUID id(int n) {
        return new UUID(0L, n);
    }

    public static void main(String[] args) {
        Map<UUID, Integer> xn = new LinkedHashMap<>();
        Map<UUID, Integer> bx = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();

        System.out.println("### 记录方向（误杀者进小脑 / 被误杀者进被小脑）");
        XiaoNaoBoardStats.record(id(1), "Alice", id(2), "Bob", xn, bx, names);
        check("Bob 进 小脑", xn.getOrDefault(id(2), 0) == 1 && !xn.containsKey(id(1)));
        check("Alice 进 被小脑", bx.getOrDefault(id(1), 0) == 1 && !bx.containsKey(id(2)));

        System.out.println("\n### 跨局累计（核心：开新局不清零）");
        XiaoNaoBoardStats.record(id(1), "Alice", id(2), "Bob", xn, bx, names);
        check("Bob=2", xn.get(id(2)) == 2);
        // 模拟“第 2 局”：不做任何 reset，直接继续记录
        // 第2局：Bob 又误杀 Carol 一次（Bob=3），Carol 误杀 Alice 两次（Carol=2）
        XiaoNaoBoardStats.record(id(3), "Carol", id(2), "Bob", xn, bx, names);
        check("第2局后 Bob 累计=3", xn.get(id(2)) == 3, "=" + xn.get(id(2)));
        check("Carol 此局是被误杀者，不进小脑榜", !xn.containsKey(id(3)));
        check("被小脑 Carol=1（她被 Bob 误杀）", bx.get(id(3)) == 1, "=" + bx.get(id(3)));
        XiaoNaoBoardStats.record(id(1), "Alice", id(3), "Carol", xn, bx, names);
        XiaoNaoBoardStats.record(id(1), "Alice", id(3), "Carol", xn, bx, names);
        check("Carol 误杀 2 次后进小脑榜=2", xn.get(id(3)) == 2, "=" + xn.get(id(3)));
        check("被小脑 Alice=4（累计：Bob 1 次 + Carol 2 次 + 首条）", bx.get(id(1)) == 4,
                "=" + bx.get(id(1)));

        System.out.println("\n### 排序：多的在上，少的在下");
        List<Entry> top = XiaoNaoBoardStats.rank(xn, names, 10);
        for (Entry e : top) System.out.println("      " + e.name() + " x" + e.count());
        check("第1名 Bob(3)", top.get(0).name().equals("Bob") && top.get(0).count() == 3);
        check("第2名 Carol(2)", top.get(1).name().equals("Carol") && top.get(1).count() == 2);
        check("严格递减", top.get(0).count() >= top.get(1).count()
                && top.get(1).count() >= top.get(top.size() - 1).count());

        System.out.println("\n### 管理员重置");
        XiaoNaoBoardStats.reset(xn, bx, names);
        check("小脑榜清空", xn.isEmpty());
        check("被小脑榜清空", bx.isEmpty());
        check("名字缓存清空", names.isEmpty());
        check("重置后榜单为空", XiaoNaoBoardStats.rank(xn, names, 10).isEmpty());
        XiaoNaoBoardStats.record(id(1), "Alice", id(2), "Bob", xn, bx, names);
        check("重置后可重新累计", xn.get(id(2)) == 1, "=" + xn.get(id(2)));

        System.out.println("\n### 同分稳定 + 边界");
        Map<UUID, Integer> tie = new LinkedHashMap<>();
        Map<UUID, String> tieNames = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            tie.put(id(100 + i), 1);
            tieNames.put(id(100 + i), String.valueOf((char) ('Z' - i)));
        }
        StringBuilder s1 = new StringBuilder();
        for (Entry e : XiaoNaoBoardStats.rank(tie, tieNames, 10)) s1.append(e.name());
        check("同分按名字升序", s1.toString().equals("UVWXYZ"), s1.toString());
        check("limit=3 截断", XiaoNaoBoardStats.rank(tie, tieNames, 3).size() == 3);
        check("limit=0 不限制", XiaoNaoBoardStats.rank(tie, tieNames, 0).size() == 6);

        Map<UUID, Integer> zero = new LinkedHashMap<>();
        zero.put(id(9), 0);
        check("次数 0 不上榜", XiaoNaoBoardStats.rank(zero, tieNames, 10).isEmpty());
        Map<UUID, Integer> nul = new LinkedHashMap<>();
        nul.put(id(9), null);
        check("次数 null 不崩不上榜", XiaoNaoBoardStats.rank(nul, tieNames, 10).isEmpty());
        check("空数据返回空榜", XiaoNaoBoardStats.rank(new LinkedHashMap<>(), tieNames, 10).isEmpty());

        Map<UUID, Integer> noName = new LinkedHashMap<>();
        noName.put(id(77), 2);
        check("名字缺失用 ? 兜底",
                "?".equals(XiaoNaoBoardStats.rank(noName, new LinkedHashMap<>(), 10).get(0).name()));

        System.out.printf("%n结果: %d 通过, %d 失败%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
