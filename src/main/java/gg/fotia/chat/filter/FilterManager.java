package gg.fotia.chat.filter;

import gg.fotia.chat.FotiaChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 屏蔽词过滤管理器
 */
public class FilterManager {

    private final FotiaChat plugin;
    private FileConfiguration filterConfig;
    private final Map<String, FilterRule> rules = new LinkedHashMap<>();

    private boolean enabled;
    private boolean logBlocked;

    public FilterManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rules.clear();
        saveDefaultConfig();

        File filterFile = new File(plugin.getDataFolder(), "filters.yml");
        filterConfig = YamlConfiguration.loadConfiguration(filterFile);

        // 合并默认配置
        InputStream defaultStream = plugin.getResource("filters.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            filterConfig.setDefaults(defaultConfig);
        }

        // 读取全局设置
        this.enabled = filterConfig.getBoolean("enabled", true);
        this.logBlocked = filterConfig.getBoolean("log-blocked", true);

        // 加载过滤规则
        ConfigurationSection rulesSection = filterConfig.getConfigurationSection("rules");
        if (rulesSection != null) {
            for (String id : rulesSection.getKeys(false)) {
                ConfigurationSection ruleSection = rulesSection.getConfigurationSection(id);
                if (ruleSection != null) {
                    loadRule(id, ruleSection);
                }
            }
        }

        plugin.getLogger().info("已加载 " + rules.size() + " 条屏蔽词规则");
    }

    private void loadRule(String id, ConfigurationSection section) {
        String pattern = section.getString("pattern", "");
        if (pattern.isEmpty()) {
            plugin.getLogger().warning("屏蔽词规则 " + id + " 缺少pattern配置");
            return;
        }

        FilterType type;
        try {
            type = FilterType.valueOf(section.getString("type", "CONTAINS").toUpperCase());
        } catch (IllegalArgumentException e) {
            type = FilterType.CONTAINS;
        }

        FilterRule.ReplaceMode replaceMode;
        try {
            replaceMode = FilterRule.ReplaceMode.valueOf(
                    section.getString("replace-mode", "REPLACE_WORD").toUpperCase());
        } catch (IllegalArgumentException e) {
            replaceMode = FilterRule.ReplaceMode.REPLACE_WORD;
        }

        String replacement = section.getString("replacement", "***");
        boolean caseSensitive = section.getBoolean("case-sensitive", false);

        FilterRule rule = new FilterRule(id, pattern, type, replaceMode, replacement, caseSensitive);
        rules.put(id, rule);
    }

    private void saveDefaultConfig() {
        File filterFile = new File(plugin.getDataFolder(), "filters.yml");
        if (!filterFile.exists()) {
            plugin.saveResource("filters.yml", false);
        }
    }

    /**
     * 过滤消息
     * @return 过滤后的消息，如果返回null表示消息被阻止
     */
    public String filter(String message) {
        if (!enabled || message == null || message.isEmpty()) {
            return message;
        }

        String result = message;
        for (FilterRule rule : rules.values()) {
            result = rule.process(result);
            if (result == null) {
                if (logBlocked) {
                    plugin.getLogger().info("消息被屏蔽词规则 " + rule.getId() + " 阻止: " + message);
                }
                return null;
            }
        }

        return result;
    }

    /**
     * 检查消息是否包含敏感词
     */
    public boolean containsSensitiveWord(String message) {
        if (!enabled || message == null || message.isEmpty()) {
            return false;
        }

        for (FilterRule rule : rules.values()) {
            if (rule.matches(message)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取匹配的规则列表
     */
    public List<FilterRule> getMatchingRules(String message) {
        List<FilterRule> matching = new ArrayList<>();
        if (!enabled || message == null || message.isEmpty()) {
            return matching;
        }

        for (FilterRule rule : rules.values()) {
            if (rule.matches(message)) {
                matching.add(rule);
            }
        }
        return matching;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, FilterRule> getRules() {
        return rules;
    }

    public FilterRule getRule(String id) {
        return rules.get(id);
    }
}
