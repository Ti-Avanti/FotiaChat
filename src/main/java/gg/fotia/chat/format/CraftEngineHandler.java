package gg.fotia.chat.format;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import org.bukkit.entity.Player;

/**
 * CraftEngine图片处理器
 * 支持在聊天中显示CraftEngine图片
 */
public class CraftEngineHandler {

    private final FotiaChat plugin;

    public CraftEngineHandler(FotiaChat plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理消息中的图片标签
     * CraftEngine使用 <image:namespace:id> 格式
     */
    public String processImageTags(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        if (!MessageUtil.isCraftEngineEnabled()) {
            // 如果CraftEngine未启用，移除图片标签
            return removeImageTags(message);
        }

        // CraftEngine图片标签会被MiniMessage自动处理
        return message;
    }

    /**
     * 移除消息中的图片标签
     */
    private String removeImageTags(String message) {
        // 移除 <image:xxx> 格式的标签
        return message.replaceAll("<image:[^>]+>", "");
    }

    /**
     * 检查消息是否包含图片标签
     */
    public boolean containsImageTag(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        return message.contains("<image:");
    }

    /**
     * 检查玩家是否有权限使用图片
     */
    public boolean hasImagePermission(Player player) {
        return player.hasPermission("fotiachat.image");
    }
}
