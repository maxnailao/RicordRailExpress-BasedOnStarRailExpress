package io.wifi.starrailexpress.api.replay.board;

/**
 * 名字能放多长：旧字符估算 vs 新像素裁剪（调用项目已编译的 class）。
 */
public class FitCheck {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String extra) {
        System.out.printf("  %-56s %s %s%n", name, ok ? "OK" : "FAIL", extra);
        if (ok) pass++; else fail++;
    }

    public static void main(String[] args) {
        // 宽 3 的屏：世界宽 48px，缩放 0.375 -> 模型空间预算
        double scale = Math.max(0.35, Math.min(1.25, 3 / 8.0));
        int worldPx = 3 * 16;
        int budget = Math.max(24, (int) Math.floor(worldPx / scale) - 2);
        System.out.printf("=== 宽 3 的屏：世界宽 %dpx，缩放 %.3f，模型空间预算 %dpx ===%n%n",
                worldPx, scale, budget);

        System.out.println("=== 旧实现（按 宽度/6 估字符数）能放的名字长度 ===");
        // 旧: charBudget = width*16/6 = 8 个半角；再减 "1."(12px?) 等
        int oldBudgetHalf = Math.max(4, (int) ((3 * 16) / 6.0D));
        System.out.printf("  旧预算 %d 个半角单位；一行 \"1. 名字  x3\" 里名字只剩 %d 个半角%n",
                oldBudgetHalf, oldBudgetHalf - 2 - 1 - 4);
        System.out.println();

        System.out.println("=== 新实现（像素裁剪）典型结果 ===");
        String[] names = { "Steve", "Alex", "Herobrine", "SteveIsVeryLong", "xX_Player_Xx",
                "一个超级长名字的玩家", "小明", "Notch", "Dinnerbone" };
        for (String n : names) {
            String rankStr = "1 ";
            String countStr = "×3";
            int nameRoom = budget - TextFitter.pixelWidth(rankStr) - TextFitter.pixelWidth(countStr);
            String fitted = TextFitter.fit(n, nameRoom);
            String line = rankStr + fitted + countStr;
            boolean truncated = !fitted.equals(n);
            System.out.printf("  %-22s -> %-22s 行宽%3d/%d %s%n",
                    n, fitted, TextFitter.pixelWidth(line), budget,
                    truncated ? "(截断)" : "完整");
        }
        System.out.println();

        System.out.println("=== 能放下的最长 ASCII 名字 ===");
        int nameRoom = budget - TextFitter.pixelWidth("1 ") - TextFitter.pixelWidth("×3");
        int maxChars = nameRoom / TextFitter.charWidth('W');
        System.out.printf("  名字区可用 %dpx -> 约 %d 个半角字符%n", nameRoom, maxChars);
        check("宽3 能放下 >= 10 个字符的名字", maxChars >= 10, maxChars + " 字符");
        check("旧实现只能放 ~1 个字符", oldBudgetHalf - 7 <= 2, "旧剩 " + (oldBudgetHalf - 7));

        System.out.println("\n=== 常见名字不该被截断 ===");
        for (String n : new String[] { "Steve", "Alex", "Notch", "Dinnerbone", "Herobrine", "xX_Player_Xx" }) {
            String fitted = TextFitter.fit(n, nameRoom);
            check("完整显示: " + n, fitted.equals(n), fitted);
        }

        System.out.println("\n=== 真正超长的才截断，且带省略号 ===");
        String longName = "ThisNameIsWayTooLongForTheBoard";
        String fitted = TextFitter.fit(longName, nameRoom);
        System.out.printf("  %s -> %s%n", longName, fitted);
        check("超长名字确实被截断", !fitted.equals(longName));
        check("截断后带省略号", fitted.endsWith("…"));
        check("截断后不超预算", TextFitter.pixelWidth(fitted) <= nameRoom,
                TextFitter.pixelWidth(fitted) + " <= " + nameRoom);

        System.out.println("\n=== 整行恒不超预算（各种名次/次数/名字） ===");
        String[] ranks = { "1 ", "2 ", "10 " };
        String[] counts = { "×1", "×12", "×123", "×99999" };
        String[] namePool = { "Steve", "一个超级长名字的玩家", "ABCDEFGHIJKLMNOPQRSTUVWXYZ" };
        boolean allFit = true;
        for (String r : ranks) {
            for (String c : counts) {
                for (String n : namePool) {
                    String cs = c;
                    String nm;
                    if (TextFitter.pixelWidth(r) + TextFitter.pixelWidth(cs) >= budget) {
                        cs = "×99+";
                        nm = TextFitter.fit(n, Math.max(0, budget
                                - TextFitter.pixelWidth(r) - TextFitter.pixelWidth(cs)));
                    } else {
                        nm = TextFitter.fit(n, budget - TextFitter.pixelWidth(r)
                                - TextFitter.pixelWidth(cs));
                    }
                    String line = r + nm + cs;
                    int w = TextFitter.pixelWidth(line);
                    if (w > budget) {
                        allFit = false;
                        System.out.printf("      !! 超预算: %-26s %d > %d%n", line, w, budget);
                    }
                }
            }
        }
        check("所有组合行宽都不超预算", allFit);

        System.out.printf("%n结果: %d 通过, %d 失败%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
