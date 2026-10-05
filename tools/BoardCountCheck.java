package io.wifi.starrailexpress.api.replay.board;

import io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单独改某人次数 的逻辑验证（直接调用项目已编译的 class）。
 *
 * 用法（仓库根目录）：
 *   MC=<minecraft-merged jar>
 *   javac -encoding UTF-8 -cp "build/classes/java/main;$MC" -d build/harness-out tools/BoardCountCheck.java
 *   java -cp "build/harness-out;build/classes/java/main;$MC" \
 *        io.wifi.starrailexpress.api.replay.board.BoardCountCheck
 */
public class BoardCountCheck {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String extra) {
        System.out.printf("  %-52s %s %s%n", name, ok ? "OK" : "FAIL", extra);
        if (ok) pass++; else fail++;
    }

    static UUID id(int n) {
        return new UUID(0L, n);
    }

    public static void main(String[] args) {
        Map<UUID, Integer> xn = new LinkedHashMap<>();
        Map<UUID, Integer> bx = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        UUID bob = id(2), alice = id(1);

        System.out.println("### set：直接设定");
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", 5, names);
        check("设定 Bob 小脑=5", XiaoNaoBoardStats.getCount(xn, bob) == 5);
        check("名字被记下", "Bob".equals(names.get(bob)));
        check("不影响被小脑榜", bx.isEmpty());

        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", 8, names);
        check("再次 set 直接覆盖为 8", XiaoNaoBoardStats.getCount(xn, bob) == 8);

        System.out.println("\n### add / sub");
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", XiaoNaoBoardStats.getCount(xn, bob) + 3, names);
        check("add 3 -> 11", XiaoNaoBoardStats.getCount(xn, bob) == 11);
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", XiaoNaoBoardStats.getCount(xn, bob) - 4, names);
        check("sub 4 -> 7", XiaoNaoBoardStats.getCount(xn, bob) == 7);

        System.out.println("\n### 归零 / 负数 = 从榜上移除");
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", 0, names);
        check("set 0 移除记录", XiaoNaoBoardStats.getCount(xn, bob) == 0 && !xn.containsKey(bob));
        check("两个榜都没有他 -> 名字缓存也清掉", !names.containsKey(bob));
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", 5, names);
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", -3, names);
        check("负数同样移除", XiaoNaoBoardStats.getCount(xn, bob) == 0);

        System.out.println("\n### 只清一个榜时，名字保留（另一个榜还有他）");
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", 4, names);          // 小脑 4
        XiaoNaoBoardStats.setCount(bx, xn, bob, "Bob", 6, names);          // 被小脑 6
        check("两榜都有记录", XiaoNaoBoardStats.getCount(xn, bob) == 4
                && XiaoNaoBoardStats.getCount(bx, bob) == 6);
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", 0, names);          // 只清小脑
        check("小脑清零", XiaoNaoBoardStats.getCount(xn, bob) == 0);
        check("被小脑仍在", XiaoNaoBoardStats.getCount(bx, bob) == 6);
        check("名字仍保留（被小脑榜还要显示他）", names.containsKey(bob));

        System.out.println("\n### 改动后排名正确");
        XiaoNaoBoardStats.setCount(xn, bx, alice, "Alice", 9, names);
        XiaoNaoBoardStats.setCount(xn, bx, bob, "Bob", 3, names);
        XiaoNaoBoardStats.setCount(xn, bx, id(3), "Carol", 6, names);
        List<Entry> top = XiaoNaoBoardStats.rank(xn, names, 10);
        for (Entry e : top) System.out.println("      " + e.name() + " x" + e.count());
        check("第1名 Alice(9)", top.get(0).name().equals("Alice") && top.get(0).count() == 9);
        check("第2名 Carol(6)", top.get(1).name().equals("Carol") && top.get(1).count() == 6);
        check("第3名 Bob(3)", top.get(2).name().equals("Bob") && top.get(2).count() == 3);

        System.out.println("\n### 边界");
        check("查不存在的人返回 0", XiaoNaoBoardStats.getCount(xn, id(999)) == 0);
        XiaoNaoBoardStats.setCount(xn, bx, id(4), null, 2, names);
        check("名字传 null 不崩（不进名字表）", XiaoNaoBoardStats.getCount(xn, id(4)) == 2
                && !names.containsKey(id(4)));
        XiaoNaoBoardStats.setCount(xn, bx, id(5), "  ", 2, names);
        check("名字传空白不崩", XiaoNaoBoardStats.getCount(xn, id(5)) == 2 && !names.containsKey(id(5)));

        System.out.printf("%n结果: %d 通过, %d 失败%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
