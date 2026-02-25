package gg.fotia.chat.api;

import gg.fotia.chat.FotiaChat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * FotiaChat Addon基类
 * 所有Addon必须继承此类
 */
public abstract class FotiaChatAddon {

    private FotiaChat plugin;
    private AddonInfo addonInfo;
    private File dataFolder;
    private FileConfiguration config;
    private File configFile;
    private boolean enabled = false;
    private ClassLoader classLoader;

    /**
     * 初始化Addon（由AddonManager调用）
     */
    public final void init(FotiaChat plugin, AddonInfo addonInfo, File dataFolder, ClassLoader classLoader) {
        this.plugin = plugin;
        this.addonInfo = addonInfo;
        this.dataFolder = dataFolder;
        this.classLoader = classLoader;
        this.configFile = new File(dataFolder, "config.yml");
    }

    /**
     * Addon启用时调用
     */
    public abstract void onEnable();

    /**
     * Addon禁用时调用
     */
    public abstract void onDisable();

    /**
     * 重载Addon配置
     */
    public void onReload() {
        reloadConfig();
    }

    /**
     * 获取主插件实例
     */
    public FotiaChat getPlugin() {
        return plugin;
    }

    /**
     * 获取Addon信息
     */
    public AddonInfo getAddonInfo() {
        return addonInfo;
    }

    /**
     * 获取Addon名称
     */
    public String getName() {
        return addonInfo.getName();
    }

    /**
     * 获取Addon版本
     */
    public String getVersion() {
        return addonInfo.getVersion();
    }

    /**
     * 获取Addon数据文件夹
     */
    public File getDataFolder() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        return dataFolder;
    }

    /**
     * 获取日志记录器
     */
    public Logger getLogger() {
        return plugin.getLogger();
    }

    /**
     * 记录信息日志
     */
    public void info(String message) {
        plugin.getLogger().info("[" + getName() + "] " + message);
    }

    /**
     * 记录警告日志
     */
    public void warning(String message) {
        plugin.getLogger().warning("[" + getName() + "] " + message);
    }

    /**
     * 记录错误日志
     */
    public void severe(String message) {
        plugin.getLogger().severe("[" + getName() + "] " + message);
    }

    /**
     * 获取配置文件
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    /**
     * 重载配置文件
     */
    public void reloadConfig() {
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
        } else {
            config = new YamlConfiguration();
        }

        // 加载默认配置
        InputStream defaultStream = getResource("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);

            // 将缺失的配置项补充到配置文件中
            boolean needSave = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!config.isSet(key)) {
                    config.set(key, defaultConfig.get(key));
                    needSave = true;
                }
            }
            if (needSave && configFile.exists()) {
                saveConfig();
            }
        }
    }

    /**
     * 保存配置文件
     */
    public void saveConfig() {
        if (config != null && configFile != null) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                severe("无法保存配置文件: " + e.getMessage());
            }
        }
    }

    /**
     * 保存默认配置文件
     */
    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
    }

    /**
     * 保存资源文件
     */
    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return;
        }

        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) {
            warning("无法找到资源文件: " + resourcePath);
            return;
        }

        File outFile = new File(getDataFolder(), resourcePath);
        File outDir = outFile.getParentFile();
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        if (!outFile.exists() || replace) {
            try {
                if (replace) {
                    java.nio.file.Files.copy(in, outFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    java.nio.file.Files.copy(in, outFile.toPath());
                }
                info("已保存资源文件: " + resourcePath);
            } catch (IOException e) {
                severe("无法保存资源文件 " + resourcePath + ": " + e.getMessage());
            }
        }
    }

    /**
     * 获取资源文件流
     * 优先从Addon自己的jar中查找资源
     */
    public InputStream getResource(String filename) {
        // 优先使用AddonClassLoader的getAddonResource来从当前jar中查找
        if (classLoader instanceof AddonClassLoader addonClassLoader) {
            InputStream in = addonClassLoader.getAddonResource(filename);
            if (in != null) {
                return in;
            }
        }
        return classLoader.getResourceAsStream(filename);
    }

    /**
     * 是否已启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置启用状态（由AddonManager调用）
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
