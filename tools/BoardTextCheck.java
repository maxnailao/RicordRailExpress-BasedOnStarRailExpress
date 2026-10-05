package io.wifi.starrailexpress.api.replay.board;

/**
 * 行截断 / 宽度预算 的逻辑验证（直接调用项目已编译的 class）。
 *
 * 用法（仓库根目录）：
 *   MC=&lt;minecraft-merged jar&gt;
 *   javac -encoding UTF-8 -cp "build/classes/java/main;$MC" -d build/harness-out tools/BoardTextCheck.java
 *   java -cp "build/harness-out;build/classes/java/main;$MC" \
 *        io.wifi.starrailexpress.api.replay.board.BoardTextCheck
 */
public class BoardTextCheck {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String extra) {
        System.out.printf("  %-50s %s %s%n", name, ok ? "OK" : "FAIL", extra);
        if (ok) pass++; else fail++;
    }

    public static void main(String[] args) {
        System.out.println("### 宽度预算（半角单位）");
        for (int w : new int[] { 3, 4, 6, 8, 12 }) {
            System.out.printf("      宽 %-3d -> 预算 %d 个半角%n", w, XiaoNaoBoardService.charBudget(w));
        }
        check("宽3 预算为 8", XiaoNaoBoardService.charBudget(3) == 8,
                "" + XiaoNaoBoardService.charBudget(3));

        System.out.println("\n### 宽度计算（中文算 2）");
        check("纯 ASCII 长度即宽度", XiaoNaoBoardService.width("Steve") == 5);
        check("中文一个字算 2", XiaoNaoBoardService.width("小脑榜") == 6);
        check("中英混合", XiaoNaoBoardService.width("小脑Steve") == 9);

        System.out.println("\n### 截断：保证一条数据一行");
        int budget = XiaoNaoBoardService.charBudget(3);   // 8
        System.out.println("      宽3 预算 = " + budget);
        String[] names = { "Steve", "SteveIsVeryLong", "小脑榜", "一个超级长名字的玩家", "Alex" };
        for (String n : names) {
            String t = XiaoNaoBoardService.truncate(n, budget);
            int w = XiaoNaoBoardService.width(t);
            System.out.printf("      %-24s -> %-14s (宽 %d)%n", n, t, w);
            check("  截断后不超预算: " + n, w <= budget, w + " <= " + budget);
        }
        check("短名字原样保留", XiaoNaoBoardService.truncate("Steve", 8).equals("Steve"));
        check("超长名字带省略号", XiaoNaoBoardService.truncate("SteveIsVeryLong", 8).endsWith("…"));
        check("中文超长也带省略号", XiaoNaoBoardService.truncate("一个超级长名字的玩家", 8).endsWith("…"));
        check("空/ null 安全", XiaoNaoBoardService.truncate(null, 8).isEmpty()
                && XiaoNaoBoardService.truncate("", 8).isEmpty());

        System.out.println("\n### 真实一行的宽度（宽3 屏幕）");
        // 模拟 buildLines 的拼装：rank + " " + name + " x" + count
        String[] lines = {
                "1. Steve x1",
                "1. Steve x100",
                "2. Alex x3",
                "10. Bob x12",
        };
        for (String line : lines) {
            System.out.printf("      %-18s 宽 %d%n", line, XiaoNaoBoardService.width(line));
        }
        // 用与 buildLines 完全相同的拼装方式（含极端情况的退化分支）验证
        String[] ranks = { "1 ", "2 ", "10 " };
        String[] counts = { " x1", " x100", " x99999" };
        String[] longNames = { "Steve", "SteveIsVeryLong", "一个超级长名字的玩家" };
        for (String r : ranks) {
            for (String c : counts) {
                for (String n : longNames) {
                    String name;
                    String label = c;
                    if (XiaoNaoBoardService.width(r) + XiaoNaoBoardService.width(c) >= budget) {
                        label = " 99+";
                        name = XiaoNaoBoardService.truncate(n,
                                Math.max(0, budget - XiaoNaoBoardService.width(r)
                                        - XiaoNaoBoardService.width(label)));
                    } else {
                        name = XiaoNaoBoardService.truncate(n,
                                budget - XiaoNaoBoardService.width(r) - XiaoNaoBoardService.width(c));
                    }
                    String built = r + name + label;
                    int w = XiaoNaoBoardService.width(built);
                    boolean okLine = w <= budget;
                    if (!okLine) {
                        System.out.printf("      !! 超预算: %-22s 宽 %d > %d%n", built, w, budget);
                    }
                    check(String.format("行不超预算 [%s|%s|%s]", r.trim(), c.trim(), n), okLine,
                            w + " <= " + budget);
                }
            }
        }

        System.out.printf("%n结果: %d 通过, %d 失败%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
