package gg.fotia.chat.format;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.LegacyColorConverter;
import gg.fotia.chat.util.MessageUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;


/**
 * PlaceholderAPI占位符处理器
 */
public class PlaceholderHandler {

    private final FotiaChat plugin;

    public PlaceholderHandler(FotiaChat plugin) {
        this.plugin = plugin;
    }

    /**
     * 解析PlaceholderAPI占位符
     */
    public String setPlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (!MessageUtil.isPlaceholderAPIEnabled()) {
            return text;
        }

        String result = PlaceholderAPI.setPlaceholders(player, text);
        // 将PAPI返回的旧版颜色代码转换为MiniMessage格式
        return LegacyColorConverter.convertToMiniMessage(result);
    }

    /**
     * 检查文本是否包含占位符
     */
    public boolean containsPlaceholders(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("%") && text.indexOf('%') != text.lastIndexOf('%');
    }
}
