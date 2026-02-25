package gg.fotia.chat.filter;

import java.util.regex.Pattern;

/**
 * 过滤规则数据类
 */
public class FilterRule {

    /**
     * 替换方式
     */
    public enum ReplaceMode {
        /**
         * 只替换敏感词
         */
        REPLACE_WORD,

        /**
         * 替换整条消息
         */
        REPLACE_MESSAGE,

        /**
         * 阻止发送
         */
        BLOCK
    }

    private final String id;
    private final String pattern;
    private final FilterType type;
    private final ReplaceMode replaceMode;
    private final String replacement;
    private final boolean caseSensitive;
    private Pattern compiledPattern;

    public FilterRule(String id, String pattern, FilterType type, ReplaceMode replaceMode,
                      String replacement, boolean caseSensitive) {
        this.id = id;
        this.pattern = pattern;
        this.type = type;
        this.replaceMode = replaceMode;
        this.replacement = replacement;
        this.caseSensitive = caseSensitive;
        compilePattern();
    }

    private void compilePattern() {
        if (type == FilterType.REGEX) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            this.compiledPattern = Pattern.compile(pattern, flags);
        }
    }

    public String getId() {
        return id;
    }

    public String getPattern() {
        return pattern;
    }

    public FilterType getType() {
        return type;
    }

    public ReplaceMode getReplaceMode() {
        return replaceMode;
    }

    public String getReplacement() {
        return replacement;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * 检查消息是否匹配此规则
     */
    public boolean matches(String message) {
        String checkMessage = caseSensitive ? message : message.toLowerCase();
        String checkPattern = caseSensitive ? pattern : pattern.toLowerCase();

        return switch (type) {
            case EXACT -> checkMessage.equals(checkPattern);
            case CONTAINS -> checkMessage.contains(checkPattern);
            case REGEX -> compiledPattern.matcher(message).find();
        };
    }

    /**
     * 处理消息
     * @return 处理后的消息，如果返回null表示消息被阻止
     */
    public String process(String message) {
        if (!matches(message)) {
            return message;
        }

        return switch (replaceMode) {
            case BLOCK -> null;
            case REPLACE_MESSAGE -> replacement;
            case REPLACE_WORD -> replaceWord(message);
        };
    }

    private String replaceWord(String message) {
        if (type == FilterType.REGEX) {
            return compiledPattern.matcher(message).replaceAll(replacement);
        }

        if (caseSensitive) {
            return message.replace(pattern, replacement);
        }

        // 不区分大小写的替换
        StringBuilder result = new StringBuilder();
        String lowerMessage = message.toLowerCase();
        String lowerPattern = pattern.toLowerCase();
        int lastEnd = 0;
        int index;

        while ((index = lowerMessage.indexOf(lowerPattern, lastEnd)) != -1) {
            result.append(message, lastEnd, index);
            result.append(replacement);
            lastEnd = index + pattern.length();
        }
        result.append(message.substring(lastEnd));

        return result.toString();
    }
}
