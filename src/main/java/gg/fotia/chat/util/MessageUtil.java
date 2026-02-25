package gg.fotia.chat.util;

import gg.fotia.chat.FotiaChat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * 解析MiniMessage格式的消息
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * 解析MiniMessage格式的消息，支持PlaceholderAPI
     */
    public static Component parse(String message, Player player) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        // 处理PlaceholderAPI占位符
        if (isPlaceholderAPIEnabled() && player != null) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }

        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * 解析旧版颜色代码(&)格式的消息
     */
    public static Component parseLegacy(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(message);
    }

    /**
     * 将Component转换为纯文本
     */
    public static String toPlainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * 将Component转换为MiniMessage格式字符串
     */
    public static String toMiniMessage(Component component) {
        return MINI_MESSAGE.serialize(component);
    }

    /**
     * 检查PlaceholderAPI是否可用
     */
    public static boolean isPlaceholderAPIEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /**
     * 检查CraftEngine是否可用
     */
    public static boolean isCraftEngineEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
    }

    /**
     * 获取带前缀的消息
     */
    public static Component withPrefix(Component message) {
        String prefix = FotiaChat.getInstance().getMessageManager().getRaw("prefix");
        return parse(prefix).append(message);
    }

    /**
     * 获取带前缀的消息
     */
    public static Component withPrefix(String message) {
        return withPrefix(parse(message));
    }
}
