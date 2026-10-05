package io.wifi.starrailexpress.api.replay.board;

/**
 * 把榜单的一行文字裁剪到指定像素宽度内。
 *
 * <h2>为什么不用字符数估算</h2>
 * 之前用"宽度 / 6 估算字符数"来截断，对中文和宽窄不一的字符都不准，
 * 结果 3 格宽的屏幕上名字还没用满就被加上了省略号。这里改成按**像素**裁剪。
 *
 * <h2>为什么不用 {@code net.minecraft.client.gui.Font}</h2>
 * {@code Font} 需要 {@code ResourceManager} 才能度量字形，是纯客户端对象；
 * 排行榜的文字是服务端 {@code TextDisplay} 实体渲染的。这里直接用
 * Minecraft 默认字体的已知度量（与 {@code Font.width} 对 ASCII/CJK 的结果一致），
 * 既能在服务端安全使用，也能脱离客户端单元测试。
 *
 * <h2>单位</h2>
 * 传进来的预算是"文字实体的行宽"（模型空间像素）。屏幕世界宽度是
 * {@code 宽格数 × 16}，而文字整体被缩放了 {@code scale}，所以
 * 模型空间预算 = {@code 宽格数 × 16 / scale}。
 */
final class TextFitter {

    /** Minecraft 默认字体里半角字符的宽度（5px 字形 + 1px 间隔） */
    private static final int WIDTH_ASCII = 6;
    /** 空格的宽度 */
    private static final int WIDTH_SPACE = 4;
    /** 全角（中日韩）字符回退到 unicode 字体时的宽度 */
    private static final int WIDTH_WIDE = 9;
    /** 省略号的宽度 */
    private static final int WIDTH_ELLIPSIS = 6;

    private TextFitter() {
    }

    /** 单字符像素宽度 */
    static int charWidth(char c) {
        if (c == ' ') {
            return WIDTH_SPACE;
        }
        return isWide(c) ? WIDTH_WIDE : WIDTH_ASCII;
    }

    /** 字符串像素宽度 */
    static int pixelWidth(String s) {
        if (s == null) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += charWidth(s.charAt(i));
        }
        return w;
    }

    /**
     * 裁剪到不超过 {@code budget} 像素；超长时以 … 结尾（省略号也算进预算）。
     *
     * @return 裁剪后的字符串；budget 太小时返回空串而不是一个超宽的省略号
     */
    static String fit(String s, int budget) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (pixelWidth(s) <= budget) {
            return s;
        }
        if (budget < WIDTH_ELLIPSIS) {
            return "";
        }
        int room = budget - WIDTH_ELLIPSIS;
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < s.length(); i++) {
            int cw = charWidth(s.charAt(i));
            if (used + cw > room) {
                break;
            }
            sb.append(s.charAt(i));
            used += cw;
        }
        return sb + "…";
    }

    static boolean isWide(char c) {
        return c >= 0x1100 && (c <= 0x115F || c == 0x2329 || c == 0x232A
                || (c >= 0x2E80 && c <= 0xA4CF)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6));
    }
}
