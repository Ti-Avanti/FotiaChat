package gg.fotia.chat.manager;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final FotiaChat plugin;
    private FileConfiguration messages;
    private final Map<String, String> messageCache = new HashMap<>();

    public MessageManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void loadMessages() {
        messageCache.clear();

        String language = plugin.getConfigManager().getLanguage();
        saveDefaultMessages(language);

        File messagesFile = new File(plugin.getDataFolder(), "messages/" + language + ".yml");
        if (!messagesFile.exists()) {
            // 如果指定语言文件不存在，使用默认语言
            plugin.getLogger().warning("语言文件 " + language + ".yml 不存在，使用默认语言 zh_CN");
            messagesFile = new File(plugin.getDataFolder(), "messages/zh_CN.yml");
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // 合并默认消息
        InputStream defaultStream = plugin.getResource("messages/" + language + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaultMessages);
        }

        // 缓存所有消息
        for (String key : messages.getKeys(true)) {
            if (messages.isString(key)) {
                messageCache.put(key, messages.getString(key));
            }
        }

        plugin.getLogger().info("已加载语言文件: " + language + ".yml");
    }

    private void saveDefaultMessages(String language) {
        File messagesDir = new File(plugin.getDataFolder(), "messages");
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }

        // 保存中文语言文件
        File zhFile = new File(messagesDir, "zh_CN.yml");
        if (!zhFile.exists()) {
            plugin.saveResource("messages/zh_CN.yml", false);
        }

        // 保存英文语言文件
        File enFile = new File(messagesDir, "en_US.yml");
        if (!enFile.exists()) {
            plugin.saveResource("messages/en_US.yml", false);
        }
    }

    /**
     * 获取原始消息字符串
     */
    public String getRaw(String key) {
        return messageCache.getOrDefault(key, messages.getString(key, key));
    }

    /**
     * 获取消息字符串，支持占位符替换
     */
    public String getRaw(String key, Map<String, String> placeholders) {
        String message = getRaw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return message;
    }

    /**
     * 获取解析后的Component消息
     */
    public Component get(String key) {
        return MessageUtil.parse(getRaw(key));
    }

    /**
     * 获取解析后的Component消息，支持占位符替换
     */
    public Component get(String key, Map<String, String> placeholders) {
        return MessageUtil.parse(getRaw(key, placeholders));
    }

    /**
     * 获取解析后的Component消息，支持玩家占位符
     */
    public Component get(String key, Player player) {
        return MessageUtil.parse(getRaw(key), player);
    }

    /**
     * 获取解析后的Component消息，支持玩家和自定义占位符
     */
    public Component get(String key, Player player, Map<String, String> placeholders) {
        return MessageUtil.parse(getRaw(key, placeholders), player);
    }

    /**
     * 发送消息给命令发送者
     */
    public void send(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            player.sendMessage(get(key, player));
        } else {
            sender.sendMessage(get(key));
        }
    }

    /**
     * 发送消息给命令发送者，支持占位符
     */
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender instanceof Player player) {
            player.sendMessage(get(key, player, placeholders));
        } else {
            sender.sendMessage(get(key, placeholders));
        }
    }
}
