package gg.fotia.chat.format;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.itemdisplay.ItemDisplayManager;
import gg.fotia.chat.util.LegacyColorConverter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聊天格式化器
 */
public class ChatFormatter {

    private final FotiaChat plugin;
    private final MiniMessage miniMessage;
    private final PlaceholderHandler placeholderHandler;
    private final CraftEngineHandler craftEngineHandler;

    public ChatFormatter(FotiaChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.placeholderHandler = new PlaceholderHandler(plugin);
        this.craftEngineHandler = new CraftEngineHandler(plugin);
    }

    /**
     * 格式化聊天消息
     */
    public Component format(Player player, Channel channel, String message) {
        String format = channel.getFormat();

        // 处理CraftEngine图片标签
        message = craftEngineHandler.processImageTags(message);

        // 处理PAPI占位符（二次解析）
        format = parsePlaceholdersRecursively(player, format);

        // 分离前缀和消息部分
        String prefixPart;
        String messagePart;
        int messageIndex = format.indexOf("{message}");
        if (messageIndex >= 0) {
            prefixPart = format.substring(0, messageIndex);
            messagePart = format.substring(messageIndex);
        } else {
            messageIndex = format.indexOf("<message>");
            if (messageIndex >= 0) {
                prefixPart = format.substring(0, messageIndex);
                messagePart = format.substring(messageIndex);
            } else {
                prefixPart = format;
                messagePart = "";
            }
        }

        // 替换{player}为玩家名
        prefixPart = prefixPart.replace("{player}", player.getName());

        // 构建前缀组件（带hover和click）
        Component prefixComponent = createPrefixComponent(player, channel, prefixPart);

        // 处理消息部分
        messagePart = messagePart.replace("{message}", "").replace("<message>", "");

        // 处理物品展示占位符
        ItemDisplayManager itemDisplayManager = plugin.getItemDisplayManager();
        Component messageComponent;
        if (itemDisplayManager != null && itemDisplayManager.containsPlaceholder(message)) {
            messageComponent = itemDisplayManager.processMessage(player, message);
        } else {
            messageComponent = miniMessage.deserialize(message);
        }

        // 组合前缀和消息
        Component result = prefixComponent.append(messageComponent);

        // 如果消息后还有内容，追加
        if (!messagePart.isEmpty()) {
            result = result.append(miniMessage.deserialize(messagePart));
        }

        return result;
    }

    /**
     * 格式化聊天消息（带自定义占位符）
     */
    public Component format(Player player, Channel channel, String message, Map<String, String> placeholders) {
        String format = channel.getFormat();

        // 处理CraftEngine图片标签
        message = craftEngineHandler.processImageTags(message);

        // 处理PAPI占位符（二次解析）
        format = parsePlaceholdersRecursively(player, format);

        // 替换自定义占位符
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                format = format.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        // 分离前缀和消息部分
        String prefixPart;
        String messagePart;
        int messageIndex = format.indexOf("{message}");
        if (messageIndex >= 0) {
            prefixPart = format.substring(0, messageIndex);
            messagePart = format.substring(messageIndex);
        } else {
            messageIndex = format.indexOf("<message>");
            if (messageIndex >= 0) {
                prefixPart = format.substring(0, messageIndex);
                messagePart = format.substring(messageIndex);
            } else {
                prefixPart = format;
                messagePart = "";
            }
        }

        // 替换{player}为玩家名
        prefixPart = prefixPart.replace("{player}", player.getName());

        // 构建前缀组件（带hover和click）
        Component prefixComponent = createPrefixComponent(player, channel, prefixPart);

        // 处理消息部分
        messagePart = messagePart.replace("{message}", "").replace("<message>", "");

        // 处理物品展示占位符
        ItemDisplayManager itemDisplayManager = plugin.getItemDisplayManager();
        Component messageComponent;
        if (itemDisplayManager != null && itemDisplayManager.containsPlaceholder(message)) {
            messageComponent = itemDisplayManager.processMessage(player, message);
        } else {
            messageComponent = miniMessage.deserialize(message);
        }

        // 组合前缀和消息
        Component result = prefixComponent.append(messageComponent);

        // 如果消息后还有内容，追加
        if (!messagePart.isEmpty()) {
            result = result.append(miniMessage.deserialize(messagePart));
        }

        return result;
    }

    /**
     * 创建带hover和click事件的前缀组件（对整个前缀生效）
     */
    private Component createPrefixComponent(Player player, Channel channel, String prefixText) {
        // 先解析MiniMessage格式
        Component prefixComponent = miniMessage.deserialize(prefixText);

        // 添加Hover事件
        if (channel.isHoverEnabled() && !channel.getHoverText().isEmpty()) {
            List<Component> hoverLines = new ArrayList<>();
            for (String line : channel.getHoverText()) {
                String processedLine = line.replace("{player}", player.getName());
                processedLine = parsePlaceholdersRecursively(player, processedLine);
                hoverLines.add(miniMessage.deserialize(processedLine));
            }

            Component hoverComponent = Component.empty();
            for (int i = 0; i < hoverLines.size(); i++) {
                hoverComponent = hoverComponent.append(hoverLines.get(i));
                if (i < hoverLines.size() - 1) {
                    hoverComponent = hoverComponent.append(Component.newline());
                }
            }

            prefixComponent = prefixComponent.hoverEvent(HoverEvent.showText(hoverComponent));
        }

        // 添加Click事件
        if (channel.isClickEnabled() && !channel.getClickValue().isEmpty()) {
            String clickValue = channel.getClickValue().replace("{player}", player.getName());
            clickValue = parsePlaceholdersRecursively(player, clickValue);

            ClickEvent clickEvent = ClickEvent.clickEvent(channel.getClickAction(), clickValue);
            prefixComponent = prefixComponent.clickEvent(clickEvent);
        }

        return prefixComponent;
    }

    /**
     * 循环解析PAPI变量（支持二次解析）
     */
    private String parsePlaceholdersRecursively(Player player, String text) {
        if (!gg.fotia.chat.util.MessageUtil.isPlaceholderAPIEnabled() || text == null || !text.contains("%")) {
            // 即使没有PAPI变量，也要转换可能存在的旧版颜色代码
            return LegacyColorConverter.convertToMiniMessage(text);
        }

        String result = text;
        int maxIterations = 10;

        for (int i = 0; i < maxIterations; i++) {
            String parsed = placeholderHandler.setPlaceholders(player, result);
            if (parsed.equals(result)) {
                break;
            }
            result = parsed;
            if (!result.contains("%")) {
                break;
            }
        }

        // 确保最终结果也经过颜色转换
        return LegacyColorConverter.convertToMiniMessage(result);
    }

    /**
     * 简单格式化消息
     */
    public Component formatSimple(String message) {
        return miniMessage.deserialize(message);
    }

    /**
     * 格式化消息（带玩家PAPI占位符）
     */
    public Component formatWithPlayer(Player player, String message) {
        message = placeholderHandler.setPlaceholders(player, message);
        return miniMessage.deserialize(message);
    }

    public PlaceholderHandler getPlaceholderHandler() {
        return placeholderHandler;
    }

    public CraftEngineHandler getCraftEngineHandler() {
        return craftEngineHandler;
    }
}
