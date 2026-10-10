package io.wifi.starrailexpress.content.title;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
/**
 * 一个称号的定义。
 *
 * <p>称号由**管理员命令发放**，所以这里存的是"模板"：文本 + 颜色 + 显示位置。
 * 玩家拥有的称号记录在 {@link TitlePlayerComponent} 里。
 *
 * @param id       唯一 id（自动生成，来自文本+颜色+位置的哈希，便于去重）
 * @param text     显示文本，例如 {@code [VIP]}
 * @param colorHex 颜色，{@code RRGGBB} 形式（不含 #）
 * @param suffix   true = 显示在名字后面，false = 显示在名字前面
 */
public record Title(String id, String text, String colorHex, boolean suffix) {

    /** 文本长度上限，防止有人用超长称号刷屏 */
    public static final int MAX_TEXT_LENGTH = 32;

    /** 默认颜色（白色） */
    public static final String DEFAULT_COLOR = "FFFFFF";

    /**
     * 规范化颜色字符串。
     *
     * @param raw 用户输入，允许带 {@code #} 前缀、大小写混写
     * @return {@code RRGGBB} 大写形式；无法解析时返回 {@code null}
     */
    @Nullable
    public static String normalizeColor(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6 || !s.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return s.toUpperCase();
    }

    /**
     * 把 MC 的 16 个颜色名（如 {@code gold}）解析成 {@code RRGGBB}。
     *
     * @return 解析结果；不是已知颜色名时返回 {@code null}
     */
    @Nullable
    public static String colorNameToHex(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        ChatFormatting fmt = ChatFormatting.getByName(name.trim().toLowerCase());
        if (fmt == null || fmt.getColor() == null) {
            return null;
        }
        return String.format("%06X", fmt.getColor() & 0xFFFFFF);
    }

    /** 解析颜色：先试 16 色名，再试 {@code #RRGGBB} / {@code RRGGBB} */
    @Nullable
    public static String parseColor(@Nullable String raw) {
        String byName = colorNameToHex(raw);
        return byName != null ? byName : normalizeColor(raw);
    }

    /** 生成一个稳定的 id：同样的文本+颜色+位置得到同一个 id，便于去重 */
    public static String makeId(String text, String colorHex, boolean suffix) {
        return Integer.toHexString((text + "|" + colorHex + "|" + suffix).hashCode());
    }

    /** 构造并自动生成 id */
    public static Title of(String text, String colorHex, boolean suffix) {
        return new Title(makeId(text, colorHex, suffix), text, colorHex, suffix);
    }

    /**
     * 显示文本：自动套上中括号，例如 {@code [奶酪]}。
     *
     * <p>管理员发放时只需要写内容（{@code 奶酪}），括号由这里统一加 ——
     * 这样格式在全服保持一致，也避免有人写成 {@code (奶酪)} / {@code 【奶酪】} 各不一样。
     *
     * <p>如果文本本身已经是「整体被一对括号包住」的形式（例如老数据里存的
     * {@code [VIP]}），就不再重复加，避免变成 {@code [[VIP]]}。
     */
    public String displayText() {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return "[]";
        }
        if (isWrapped(t)) {
            return t;
        }
        return "[" + t + "]";
    }

    /** 是否已被一对括号整体包住 */
    private static boolean isWrapped(String t) {
        if (t.length() < 2) {
            return false;
        }
        char first = t.charAt(0);
        char last = t.charAt(t.length() - 1);
        return switch (first) {
            case '[' -> last == ']';
            case '【' -> last == '】';
            case '(' -> last == ')';
            case '（' -> last == '）';
            default -> false;
        };
    }

    /** 带颜色的显示文本（含中括号，不含前后空格） */
    public MutableComponent component() {
        return Component.literal(displayText()).withStyle(Style.EMPTY.withColor(rgb()));
    }

    /** 颜色对应的 RGB 整数 */
    public int rgb() {
        try {
            return Integer.parseInt(colorHex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    /** 带颜色的文本组件，按位置决定前后补一个空格（拼在名字旁用）。
     *  颜色必须挂在根组件上：记分板队伍前后缀按根样式取色，
     *  若只把颜色放在嵌套子组件，准星名字/聊天前缀会退化成白色。 */
    public MutableComponent spacedComponent() {
        String spaced = (suffix ? " " : "") + displayText() + (suffix ? "" : " ");
        return Component.literal(spaced).withStyle(Style.EMPTY.withColor(rgb()));
    }

    /** 纯文本形式（日志 / 提示用） */
    public String plain() {
        return text;
    }
}
