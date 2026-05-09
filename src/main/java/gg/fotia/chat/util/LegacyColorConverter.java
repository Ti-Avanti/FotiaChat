package gg.fotia.chat.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旧版颜色代码转换器
 * 将 & 和 § 颜色代码转换为 MiniMessage 格式
 */
public class LegacyColorConverter {

    private static final Map<Character, String> COLOR_MAP = new HashMap<>();
    private static final Map<Character, String> FORMAT_MAP = new HashMap<>();

    // 匹配 &#RRGGBB 格式的十六进制颜色
    private static final Pattern HEX_PATTERN = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    // 匹配 §x§R§R§G§G§B§B 格式的十六进制颜色
    private static final Pattern BUKKIT_HEX_PATTERN = Pattern.compile("[§&]x([§&][0-9a-fA-F]){6}");
    // 匹配普通颜色代码
    private static final Pattern COLOR_PATTERN = Pattern.compile("[&§]([0-9a-fA-FklmnorKLMNOR])");

    static {
        // 基础颜色
        COLOR_MAP.put('0', "<reset><black>");
        COLOR_MAP.put('1', "<reset><dark_blue>");
        COLOR_MAP.put('2', "<reset><dark_green>");
        COLOR_MAP.put('3', "<reset><dark_aqua>");
        COLOR_MAP.put('4', "<reset><dark_red>");
        COLOR_MAP.put('5', "<reset><dark_purple>");
        COLOR_MAP.put('6', "<reset><gold>");
        COLOR_MAP.put('7', "<reset><gray>");
        COLOR_MAP.put('8', "<reset><dark_gray>");
        COLOR_MAP.put('9', "<reset><blue>");
        COLOR_MAP.put('a', "<reset><green>");
        COLOR_MAP.put('b', "<reset><aqua>");
        COLOR_MAP.put('c', "<reset><red>");
        COLOR_MAP.put('d', "<reset><light_purple>");
        COLOR_MAP.put('e', "<reset><yellow>");
        COLOR_MAP.put('f', "<reset><white>");

        // 格式代码
        FORMAT_MAP.put('k', "<obfuscated>");
        FORMAT_MAP.put('l', "<bold>");
        FORMAT_MAP.put('m', "<strikethrough>");
        FORMAT_MAP.put('n', "<underlined>");
        FORMAT_MAP.put('o', "<italic>");
        FORMAT_MAP.put('r', "<reset>");
    }

    /**
     * 将旧版颜色代码转换为 MiniMessage 格式
     *
     * @param text 包含 & 或 § 颜色代码的文本
     * @return 转换后的 MiniMessage 格式文本
     */
    public static String convertToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 先处理 &#RRGGBB 格式的十六进制颜色
        text = convertHexColors(text);

        // 处理 §x§R§R§G§G§B§B 格式的十六进制颜色
        text = convertBukkitHexColors(text);

        // 处理普通颜色代码
        text = convertBasicColors(text);

        return text;
    }

    /**
     * 转换 &#RRGGBB 格式的十六进制颜色
     */
    private static String convertHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1).toLowerCase();
            matcher.appendReplacement(result, "<reset><#" + hex + ">");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 转换 §x§R§R§G§G§B§B 格式的十六进制颜色
     */
    private static String convertBukkitHexColors(String text) {
        Matcher matcher = BUKKIT_HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String match = matcher.group();
            // 提取十六进制字符
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < match.length(); i++) {
                char c = match.charAt(i);
                if (Character.isLetterOrDigit(c) && c != 'x' && c != 'X') {
                    hex.append(c);
                }
            }
            matcher.appendReplacement(result, "<reset><#" + hex.toString().toLowerCase() + ">");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 转换普通颜色代码
     */
    private static String convertBasicColors(String text) {
        Matcher matcher = COLOR_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String replacement;

            if (COLOR_MAP.containsKey(code)) {
                replacement = COLOR_MAP.get(code);
            } else if (FORMAT_MAP.containsKey(code)) {
                replacement = FORMAT_MAP.get(code);
            } else {
                replacement = matcher.group();
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
