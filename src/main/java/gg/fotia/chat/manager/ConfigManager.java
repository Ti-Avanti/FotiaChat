package gg.fotia.chat.manager;

import gg.fotia.chat.FotiaChat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final FotiaChat plugin;
    private FileConfiguration config;

    // 配置项
    private String language;
    private boolean debugMode;
    private boolean allowColorCodes;
    private String mutePermission;

    public ConfigManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // 保存默认配置文件
        saveDefaultConfig();

        // 加载配置
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);

        // 合并默认配置
        InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        // 读取配置项
        this.language = config.getString("language", "zh_CN");
        this.debugMode = config.getBoolean("debug", false);
        this.allowColorCodes = config.getBoolean("chat.allow-color-codes", true);
        this.mutePermission = config.getString("mute.permission", "");
    }

    private void saveDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
    }

    public void saveConfig() {
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存配置文件: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isAllowColorCodes() {
        return allowColorCodes;
    }

    public String getMutePermission() {
        return mutePermission;
    }
}
