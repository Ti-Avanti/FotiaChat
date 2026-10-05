package gg.fotia.chat.localization;

import gg.fotia.chat.FotiaChat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalizationService {

    private final FotiaChat plugin;
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();
    private volatile LocaleResolver localeResolver = new LocaleResolver(LocaleResolver.FALLBACK_LOCALE, true);
    private volatile TranslationCatalog catalog = new TranslationCatalog(Map.of(), LocaleResolver.FALLBACK_LOCALE);
    private volatile boolean warnMissingTranslations = true;

    public LocalizationService(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void load(String defaultLocale, boolean useClientLocale, boolean warnMissingTranslations) {
        saveBundledCatalogs();
        LocaleResolver nextResolver = new LocaleResolver(defaultLocale, useClientLocale);
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        for (String locale : LocaleResolver.supportedLocales()) {
            loaded.put(locale, loadCatalog(locale));
        }
        this.localeResolver = nextResolver;
        this.catalog = new TranslationCatalog(loaded, nextResolver.defaultLocale());
        this.warnMissingTranslations = warnMissingTranslations;
        warnedMissingKeys.clear();
        plugin.getLogger().info("已加载 " + loaded.size() + " 种玩家语言，默认语言: "
                + nextResolver.defaultLocale());
    }

    public String locale(Player player) {
        return gg.fotia.basictool.bridge.PaperPreferencesBridge.locale(player, gg.fotia.translator.bridge.PaperTranslatorBridge.locale(player, defaultLocale()));
    }

    public String defaultLocale() {
        return localeResolver.defaultLocale();
    }

    public String translate(Player player, String key) {
        return translate(locale(player), key);
    }

    public String translateDefault(String key) {
        return translate(defaultLocale(), key);
    }

    public String translate(String locale, String key) {
        var translated = gg.fotia.translator.bridge.PaperTranslatorBridge.find("fotiachat", locale, key);
        if (translated.isPresent() && !(translated.get() instanceof java.util.List<?>)) return String.valueOf(translated.get());

        String result = catalog.translate(locale, key);
        if (warnMissingTranslations && result.equals(key) && warnedMissingKeys.add(key)) {
            plugin.getLogger().warning("缺少翻译键: " + key);
        }
        return result;
    }

    public boolean hasTranslation(String locale, String key) {
        return catalog.contains(locale, key);
    }

    private void saveBundledCatalogs() {
        File localesDir = new File(plugin.getDataFolder(), "locales");
        if (!localesDir.exists() && !localesDir.mkdirs()) {
            plugin.getLogger().warning("无法创建语言目录: " + localesDir);
        }
        for (String locale : LocaleResolver.supportedLocales()) {
            String resourcePath = "locales/" + locale + ".yml";
            File target = new File(plugin.getDataFolder(), resourcePath);
            if (target.exists()) {
                continue;
            }
            File legacy = new File(plugin.getDataFolder(), "messages/" + locale + ".yml");
            if (legacy.exists()) {
                try {
                    Files.copy(legacy.toPath(), target.toPath());
                    continue;
                } catch (Exception exception) {
                    plugin.getLogger().warning("迁移旧语言文件失败 " + locale + ": " + exception.getMessage());
                }
            }
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            }
        }
    }

    private Map<String, String> loadCatalog(String locale) {
        String resourcePath = "locales/" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), resourcePath);
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream != null) {
                bundled = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("读取内置语言文件失败 " + resourcePath + ": " + exception.getMessage());
        }

        return mergeCatalog(configuration, bundled);
    }

    static Map<String, String> mergeCatalog(YamlConfiguration configured, YamlConfiguration bundled) {
        Set<String> keys = new LinkedHashSet<>(bundled.getKeys(true));
        keys.addAll(configured.getKeys(true));

        Map<String, String> entries = new LinkedHashMap<>();
        for (String key : keys) {
            if (configured.isString(key)) {
                entries.put(key, configured.getString(key, key));
            } else if (bundled.isString(key)) {
                entries.put(key, bundled.getString(key, key));
            }
        }
        return entries;
    }
}
