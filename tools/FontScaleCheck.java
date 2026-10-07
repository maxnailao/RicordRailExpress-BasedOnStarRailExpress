package io.wifi.starrailexpress.api.replay.board;

import io.wifi.starrailexpress.api.replay.board.ReplayBoardSavedData.ReplayScreenEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 字号放大后仍要放得下：验证 textScale 与行宽预算、以及几个真实名字能不能完整显示。
 */
public class FontScaleCheck {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String extra) {
        System.out.printf("  %-58s %s %s%n", name, ok ? "OK" : "FAIL", extra);
        if (ok) pass++; else fail++;
    }

    static ReplayScreenEntry entry(int w, int h) {
        return new ReplayScreenEntry("xiaonao_top", Level.OVERWORLD, new BlockPos(0, 64, 0),
                w, h, Direction.NORTH, null);
    }

    public static void main(String[] args) {
        // ReplayBoardService 的静态初始化会碰 Blocks，需要先做注册表 bootstrap；
        // 而 bootstrap 里的 datafixer 又要求先设定游戏版本。
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        System.out.println("=== 字号变化（旧 -> 新） ===");
        for (int w = 2; w <= 12; w++) {
            float oldScale = Math.max(0.35F, Math.min(1.25F, w / 8.0F));
            float newScale = ReplayBoardService.textScale(entry(w, 4));
            System.out.printf("  宽 %-3d 旧 %.3f -> 新 %.3f  (x%.2f)%n",
                    w, (double) oldScale, (double) newScale, (double) (newScale / oldScale));
        }

        System.out.println("\n=== 3 格宽的屏（你的尺寸） ===");
        ReplayScreenEntry e3 = entry(3, 4);
        float sc = ReplayBoardService.textScale(e3);
        int budget = XiaoNaoBoardService.lineBudgetPx(e3);
        System.out.printf("  缩放 %.3f，一行预算 %dpx%n", sc, budget);
        check("字号确实变大了 (>=0.5)", sc >= 0.5F, String.format("%.3f", sc));
        check("没有超出上限 1.25", sc <= 1.25F, String.format("%.3f", sc));

        System.out.println("\n=== 放大后每个名字是否还放得下（3 格宽） ===");
        String[] names = { "Steve", "Alex", "Notch", "Herobrine", "Dinnerbone",
                "xX_Player_Xx", "SteveIsVeryLong", "一个超长名字的玩家" };
        for (String n : names) {
            String rankStr = "1 ";
            String countStr = "×3";
            int room = budget - TextFitter.pixelWidth(rankStr) - TextFitter.pixelWidth(countStr);
            String fitted = TextFitter.fit(n, room);
            boolean full = fitted.equals(n);
            System.out.printf("  %-22s -> %-22s 行宽%3d/%d %s%n",
                    n, fitted, TextFitter.pixelWidth(rankStr + fitted + countStr), budget,
                    full ? "(完整)" : "(截断)");
        }
        // 常见名字仍应完整
        for (String n : new String[] { "Steve", "Alex", "Notch", "Herobrine", "Dinnerbone" }) {
            int room = budget - TextFitter.pixelWidth("1 ") - TextFitter.pixelWidth("×3");
            check("常见名完整显示: " + n, TextFitter.fit(n, room).equals(n),
                    TextFitter.fit(n, room));
        }

        System.out.println("\n=== 整行恒不超预算（放大后） ===");
        boolean allFit = true;
        for (int w : new int[] { 3, 4, 5, 6, 8, 12 }) {
            ReplayScreenEntry e = entry(w, 4);
            int bud = XiaoNaoBoardService.lineBudgetPx(e);
            for (String r : new String[] { "1 ", "10 " }) {
                for (String c : new String[] { "×1", "×123", "×99999" }) {
                    for (String n : names) {
                        String cs = c;
                        String nm;
                        if (TextFitter.pixelWidth(r) + TextFitter.pixelWidth(cs) >= bud) {
                            cs = "×99+";
                            nm = TextFitter.fit(n, Math.max(0,
                                    bud - TextFitter.pixelWidth(r) - TextFitter.pixelWidth(cs)));
                        } else {
                            nm = TextFitter.fit(n,
                                    bud - TextFitter.pixelWidth(r) - TextFitter.pixelWidth(cs));
                        }
                        int lineW = TextFitter.pixelWidth(r + nm + cs);
                        if (lineW > bud) {
                            allFit = false;
                            System.out.printf("      !! 超预算 宽%d: %s (%d > %d)%n", w, r + nm + cs, lineW, bud);
                        }
                    }
                }
            }
        }
        check("全部宽带组合都不超预算", allFit);

        System.out.println("\n=== 行数：放大后仍能放满 TOP10 ===");
        for (int h : new int[] { 2, 3, 4, 6, 10 }) {
            ReplayScreenEntry e = entry(3, h);
            int rows = ReplayBoardService.maxRowsFor(e);
            System.out.printf("  高 %-3d -> 可放 %2d 行%n", h, rows);
        }
        check("高4 仍能放 >= 10 行", ReplayBoardService.maxRowsFor(entry(3, 4)) >= 10,
                Integer.toString(ReplayBoardService.maxRowsFor(entry(3, 4))));

        System.out.printf("%n结果: %d 通过, %d 失败%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
