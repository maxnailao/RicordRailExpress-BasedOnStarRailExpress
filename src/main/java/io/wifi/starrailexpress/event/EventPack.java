package io.wifi.starrailexpress.event;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务包：一组条件（全部达成才发奖）+ 一组奖励。
 * 编码：condition/rewards 字段均按 '|' 分包、包内按 ';' 分条，两字段下标一一对应；
 * 无 '|' 的旧数据自然退化为单包。
 */
public record EventPack(List<String> conditions, List<String> rewards) {

    public static List<EventPack> parse(String condRaw, String rewardRaw) {
        String[] cs = splitPacks(condRaw);
        String[] rs = splitPacks(rewardRaw);
        int n = Math.max(cs.length, rs.length);
        List<EventPack> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new EventPack(tokens(i < cs.length ? cs[i] : ""),
                    tokens(i < rs.length ? rs[i] : "")));
        }
        return out;
    }

    private static String[] splitPacks(String raw) {
        if (raw == null) return new String[0];
        String t = raw.trim();
        if (t.isEmpty()) return new String[0];
        return t.split("\\|");
    }

    private static List<String> tokens(String pack) {
        List<String> out = new ArrayList<>();
        for (String raw : pack.split(";")) {
            String t = raw.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** 包内所有 kill:N 条件的最大目标；无击杀条件返回 0 */
    public int killTarget() {
        int max = 0;
        for (String c : conditions) {
            if (c.startsWith("kill:")) {
                try {
                    max = Math.max(max, Integer.parseInt(c.substring(5).trim()));
                } catch (Exception ignored) {
                }
            }
        }
        return max;
    }

    /** 逐条解析为通用条件模型（供服务端多条件计数、客户端逐条展示） */
    public List<EventCondition> parsedConditions() {
        List<EventCondition> out = new ArrayList<>();
        for (String c : conditions) out.add(EventCondition.parse(c));
        return out;
    }
}