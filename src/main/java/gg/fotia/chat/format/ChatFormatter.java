package gg.fotia.chat.format;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.api.VirtualChatSender;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.channel.ChannelSegmentConfig;
import gg.fotia.chat.color.ChatColor;
import gg.fotia.chat.itemdisplay.ItemDisplayManager;
import gg.fotia.chat.util.LegacyColorConverter;
import gg.fotia.chat.util.ComponentTextTransformer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import gg.fotia.chat.util.ClickEvents;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class ChatFormatter {

    private static final String CHANNEL_MARKER = "__fotia_segment_channel__";
    private static final String PLAYER_MARKER = "__fotia_segment_player__";
    private static final String MESSAGE_MARKER = "__fotia_segment_message__";
    private static final Pattern CHANNEL_MARKER_PATTERN = Pattern.compile(Pattern.quote(CHANNEL_MARKER));
    private static final Pattern PLAYER_MARKER_PATTERN = Pattern.compile(Pattern.quote(PLAYER_MARKER));
    private static final Pattern MESSAGE_MARKER_PATTERN = Pattern.compile(Pattern.quote(MESSAGE_MARKER));

    private final FotiaChat plugin;
    private final MiniMessage miniMessage;
    private final PlaceholderHandler placeholderHandler;
    private final CraftEngineHandler craftEngineHandler;
    private final PlainTextComponentSerializer plainTextSerializer;

    public ChatFormatter(FotiaChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.placeholderHandler = new PlaceholderHandler(plugin);
        this.craftEngineHandler = new CraftEngineHandler(plugin);
        this.plainTextSerializer = PlainTextComponentSerializer.plainText();
    }

    public Component format(Player player, Channel channel, String message) {
        return format(playerContext(player), localizedChannel(channel, player), message);
    }

    public Component format(Player player, Channel channel, String message, ChatColor chatColor) {
        return format(playerContext(player), localizedChannel(channel, player), message, chatColor);
    }

    public Component format(VirtualChatSender sender, Channel channel, String message) {
        return format(SenderContext.fromVirtualSender(sender), channel, message);
    }

    public Component format(Player player, Channel channel, Component messageComponent) {
        return format(playerContext(player), localizedChannel(channel, player), messageComponent);
    }

    public Component format(Player player, Channel channel, Component messageComponent, ChatColor chatColor) {
        return formatPreparedMessage(player, channel, prepareMessage(player, messageComponent, chatColor), player);
    }

    public Component prepareMessage(Player player, Component messageComponent, ChatColor chatColor) {
        Component safeMessage = messageComponent == null ? Component.empty() : messageComponent;
        ItemDisplayManager itemDisplayManager = plugin.getItemDisplayManager();
        if (itemDisplayManager != null) {
            safeMessage = itemDisplayManager.processComponent(player, safeMessage);
        }
        if (chatColor != null) {
            safeMessage = chatColor.apply(safeMessage, miniMessage);
        }
        return safeMessage;
    }

    public Component prepareMessage(Player player, String message, ChatColor chatColor) {
        return prepareMessageComponent(SenderContext.fromPlayer(player), message, chatColor);
    }

    public Component formatPreparedMessage(Player player, Channel channel, Component messageComponent) {
        return formatPreparedMessage(player, channel, messageComponent, player);
    }

    public Component formatPreparedMessage(Player player, Channel channel, Component messageComponent, Player viewer) {
        return format(playerContext(player), localizedChannel(channel, viewer), messageComponent);
    }

    public Component format(VirtualChatSender sender, Channel channel, Component messageComponent) {
        return format(SenderContext.fromVirtualSender(sender), channel, messageComponent);
    }

    public Component format(Player player, Channel channel, String message, Map<String, String> placeholders) {
        Map<String, String> merged = new LinkedHashMap<>(plugin.resolveChatPlaceholders(player));
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        return format(SenderContext.fromPlayer(player).withAdditionalPlaceholders(merged),
                localizedChannel(channel, player), message);
    }

    private Channel localizedChannel(Channel channel, Player viewer) {
        return channel.localized(text -> plugin.getMessageManager().resolveConfigured(viewer, text));
    }

    private SenderContext playerContext(Player player) {
        return SenderContext.fromPlayer(player)
                .withAdditionalPlaceholders(plugin.resolveChatPlaceholders(player));
    }

    private Component format(SenderContext sender, Channel channel, String message) {
        return format(sender, channel, message, null);
    }

    private Component format(SenderContext sender, Channel channel, String message, ChatColor chatColor) {
        return formatInternal(
                sender,
                channel,
                channel.getFormat(),
                prepareMessageComponent(sender, message, chatColor)
        );
    }

    private Component prepareMessageComponent(SenderContext sender, String message, ChatColor chatColor) {
        String safeMessage = craftEngineHandler.processImageTags(message);
        ItemDisplayManager itemDisplayManager = plugin.getItemDisplayManager();
        boolean containsRichPlaceholder = sender.player() != null
                && itemDisplayManager != null
                && itemDisplayManager.containsPlaceholder(safeMessage);
        Component messageComponent;
        if (chatColor != null && !containsRichPlaceholder) {
            messageComponent = miniMessage.deserialize(chatColor.apply(safeMessage));
        } else {
            messageComponent = buildMessageComponentFromString(sender, safeMessage);
        }
        if (chatColor != null && containsRichPlaceholder) {
            messageComponent = chatColor.apply(messageComponent, miniMessage);
        }
        return messageComponent;
    }

    private Component format(SenderContext sender, Channel channel, Component messageComponent) {
        Component safeMessageComponent = messageComponent == null ? Component.empty() : messageComponent;
        return formatInternal(sender, channel, channel.getFormat(), safeMessageComponent);
    }

    private Component formatInternal(SenderContext sender, Channel channel, String formatTemplate, Component messageComponent) {
        if (gg.fotia.basictool.bridge.PaperNickBridge.active("FotiaChat", sender.uniqueId())) {
            String anonymous = gg.fotia.basictool.bridge.PaperNickBridge.chatFormat("FotiaChat", sender.uniqueId(), formatTemplate)
                    .replace("{channel}", channel.getName()).replace("{message}", "<nick_message>");
            return miniMessage.deserialize("<!i>" + LegacyColorConverter.convertToMiniMessage(anonymous),
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("nick_message",
                            messageComponent == null ? Component.empty() : messageComponent));
        }
        Component processedMessage = messageComponent == null ? Component.empty() : messageComponent;

        if (channel.hasSegmentConfigs()) {
            return buildSegmentedComponent(sender, channel, formatTemplate, processedMessage);
        }

        String format = parsePlaceholdersRecursively(sender, formatTemplate);
        FormatParts parts = splitFormat(format);
        return buildLegacyComponent(sender, channel, parts, processedMessage);
    }

    private Component buildMessageComponentFromString(SenderContext sender, String message) {
        String safe = message == null ? "" : message;
        ItemDisplayManager itemDisplayManager = plugin.getItemDisplayManager();
        if (sender.player() != null && itemDisplayManager != null && itemDisplayManager.containsPlaceholder(safe)) {
            return itemDisplayManager.processMessage(sender.player(), safe);
        }
        return miniMessage.deserialize(safe);
    }

    private Component buildLegacyComponent(SenderContext sender, Channel channel, FormatParts parts, Component messageComponent) {
        Component prefixComponent = createPrefixComponent(sender, channel, parts.prefixPart);

        Component result = prefixComponent.append(messageComponent);
        if (!parts.suffixPart.isEmpty()) {
            result = result.append(miniMessage.deserialize(replacePlaceholders(parts.suffixPart, sender, channel)));
        }
        return result;
    }

    private Component buildSegmentedComponent(SenderContext sender, Channel channel, String formatTemplate, Component messageComponent) {
        String messagePlainText = plainText(messageComponent);
        String markedFormat = markSegmentTokens(formatTemplate);
        String parsedFormat = parsePlaceholdersRecursively(sender, markedFormat);
        Component result = miniMessage.deserialize(replacePlaceholders(parsedFormat, sender, channel, messagePlainText));

        result = replaceSegmentMarker(result, CHANNEL_MARKER_PATTERN,
                buildSegmentComponent(sender, channel, channel.getSegmentConfig("channel"), "{channel}", messageComponent, messagePlainText));
        result = replaceSegmentMarker(result, PLAYER_MARKER_PATTERN,
                buildSegmentComponent(sender, channel, channel.getSegmentConfig("player"), "{player}", messageComponent, messagePlainText));
        result = replaceSegmentMarker(result, MESSAGE_MARKER_PATTERN,
                buildSegmentComponent(sender, channel, channel.getSegmentConfig("message"), "{message}", messageComponent, messagePlainText));
        return result;
    }

    private Component replaceSegmentMarker(Component source, Pattern markerPattern, Component replacement) {
        return ComponentTextTransformer.replacePattern(source, markerPattern, replacement);
    }

    private String markSegmentTokens(String formatTemplate) {
        String safeFormat = formatTemplate == null ? "" : formatTemplate;
        return safeFormat.replace("{channel}", CHANNEL_MARKER)
                .replace("{player}", PLAYER_MARKER)
                .replace("{message}", MESSAGE_MARKER)
                .replace("<message>", MESSAGE_MARKER);
    }

    private Component buildSegmentComponent(SenderContext sender,
                                           Channel channel,
                                           ChannelSegmentConfig config,
                                           String defaultDisplay,
                                           Component messageComponent,
                                           String messagePlainText) {
        String display = config == null || config.getDisplay().isEmpty() ? defaultDisplay : config.getDisplay();
        Component segmentComponent;

        if ("{message}".equals(defaultDisplay)) {
            segmentComponent = buildMessageSegmentComponent(sender, channel, display, messageComponent, messagePlainText);
        } else {
            segmentComponent = deserializeConfiguredText(display, sender, channel, messagePlainText);
        }

        if (config != null && config.hasHover()) {
            segmentComponent = segmentComponent.hoverEvent(HoverEvent.showText(
                    buildHoverComponent(sender, channel, config.getHoverText(), messagePlainText)
            ));
        }

        if (config != null && config.hasClick()) {
            String clickValue = resolvePlainText(config.getClickValue(), sender, channel, messagePlainText);
            segmentComponent = segmentComponent.clickEvent(ClickEvents.create(config.getClickAction(), clickValue));
        }

        return segmentComponent;
    }

    private Component buildMessageSegmentComponent(SenderContext sender,
                                                  Channel channel,
                                                  String displayTemplate,
                                                  Component messageComponent,
                                                  String messagePlainText) {
        String safeTemplate = displayTemplate == null || displayTemplate.isEmpty() ? "{message}" : displayTemplate;

        int tokenIndex = safeTemplate.indexOf("{message}");
        int tokenLength = "{message}".length();
        if (tokenIndex < 0) {
            tokenIndex = safeTemplate.indexOf("<message>");
            tokenLength = "<message>".length();
        }

        if (tokenIndex < 0) {
            return messageComponent;
        }

        String prefix = safeTemplate.substring(0, tokenIndex);
        String suffix = safeTemplate.substring(tokenIndex + tokenLength);

        Component result = Component.empty();
        if (!prefix.isEmpty()) {
            result = result.append(deserializeConfiguredText(prefix, sender, channel, messagePlainText));
        }
        result = result.append(messageComponent);
        if (!suffix.isEmpty()) {
            result = result.append(deserializeConfiguredText(suffix, sender, channel, messagePlainText));
        }
        return result;
    }

    private Component buildHoverComponent(SenderContext sender, Channel channel, List<String> hoverText, String messagePlainText) {
        List<Component> hoverLines = new ArrayList<>();
        for (String line : hoverText) {
            hoverLines.add(deserializeConfiguredText(line, sender, channel, messagePlainText));
        }

        Component hoverComponent = Component.empty();
        for (int i = 0; i < hoverLines.size(); i++) {
            hoverComponent = hoverComponent.append(hoverLines.get(i));
            if (i < hoverLines.size() - 1) {
                hoverComponent = hoverComponent.append(Component.newline());
            }
        }
        return hoverComponent;
    }

    private FormatParts splitFormat(String format) {
        String safeFormat = format == null ? "" : format;

        int messageIndex = safeFormat.indexOf("{message}");
        if (messageIndex < 0) {
            messageIndex = safeFormat.indexOf("<message>");
        }

        if (messageIndex < 0) {
            return new FormatParts(safeFormat, "");
        }

        String prefix = safeFormat.substring(0, messageIndex);
        String suffix = safeFormat.substring(messageIndex)
                .replace("{message}", "")
                .replace("<message>", "");
        return new FormatParts(prefix, suffix);
    }

    private Component createPrefixComponent(SenderContext sender, Channel channel, String prefixText) {
        Component prefixComponent = miniMessage.deserialize(replacePlaceholders(prefixText, sender, channel));

        if (channel.isHoverEnabled() && !channel.getHoverText().isEmpty()) {
            prefixComponent = prefixComponent.hoverEvent(HoverEvent.showText(
                    buildHoverComponent(sender, channel, channel.getHoverText(), "")
            ));
        }

        if (channel.isClickEnabled() && !channel.getClickValue().isEmpty()) {
            String clickValue = resolvePlainText(channel.getClickValue(), sender, channel, "");
            ClickEvent clickEvent = ClickEvents.create(channel.getClickAction(), clickValue);
            prefixComponent = prefixComponent.clickEvent(clickEvent);
        }

        return prefixComponent;
    }

    private String parsePlaceholdersRecursively(SenderContext sender, String text) {
        String safeText = text == null ? "" : text;
        String result = applySenderPlaceholderTokens(safeText, sender);
        if (!gg.fotia.chat.util.MessageUtil.isPlaceholderAPIEnabled() || sender.player() == null || !result.contains("%")) {
            return LegacyColorConverter.convertToMiniMessage(result);
        }

        int maxIterations = 10;
        for (int i = 0; i < maxIterations; i++) {
            String parsed = placeholderHandler.setPlaceholders(sender.player(), result);
            parsed = applySenderPlaceholderTokens(parsed, sender);
            if (parsed.equals(result)) {
                break;
            }
            result = parsed;
            if (!result.contains("%")) {
                break;
            }
        }

        return LegacyColorConverter.convertToMiniMessage(result);
    }

    private String replacePlaceholders(String text, SenderContext sender, Channel channel) {
        return replacePlaceholders(text, sender, channel, "");
    }

    private String replacePlaceholders(String text, SenderContext sender, Channel channel, String messageText) {
        String safeText = applySenderPlaceholderTokens(text == null ? "" : text, sender);
        return safeText.replace("{player}", escapeMiniMessageValue(sender.displayName()))
                .replace("{channel}", escapeMiniMessageValue(channel.getName()))
                .replace("{message}", escapeMiniMessageValue(messageText))
                .replace("<message>", escapeMiniMessageValue(messageText));
    }

    private String escapeMiniMessageValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    private Component deserializeConfiguredText(String text, SenderContext sender, Channel channel, String messageText) {
        String processed = parsePlaceholdersRecursively(sender, text);
        return miniMessage.deserialize(replacePlaceholders(processed, sender, channel, messageText));
    }

    private String resolvePlainText(String text, SenderContext sender, Channel channel, String messageText) {
        String safeText = applySenderPlaceholderTokens(text == null ? "" : text, sender);
        if (!gg.fotia.chat.util.MessageUtil.isPlaceholderAPIEnabled() || sender.player() == null || !safeText.contains("%")) {
            return replacePlainPlaceholders(safeText, sender, channel, messageText);
        }

        String result = safeText;
        for (int i = 0; i < 10; i++) {
            String parsed = placeholderHandler.setPlaceholders(sender.player(), result);
            parsed = applySenderPlaceholderTokens(parsed, sender);
            if (parsed.equals(result)) {
                break;
            }
            result = parsed;
            if (!result.contains("%")) {
                break;
            }
        }
        return replacePlainPlaceholders(result, sender, channel, messageText);
    }

    private String replacePlainPlaceholders(String text, SenderContext sender, Channel channel, String messageText) {
        String safeText = applySenderPlaceholderTokens(text == null ? "" : text, sender);
        return safeText.replace("{player}", sender.name())
                .replace("{player_display}", sender.displayName())
                .replace("{player_name}", sender.name())
                .replace("{channel}", channel.getName())
                .replace("{message}", messageText == null ? "" : messageText)
                .replace("<message>", messageText == null ? "" : messageText);
    }

    private String applySenderPlaceholderTokens(String text, SenderContext sender) {
        String result = text == null ? "" : text;
        result = result.replace("%player_name%", sender.name())
                .replace("%player_displayname%", sender.displayName())
                .replace("%player_uuid%", sender.uniqueId().toString());

        String worldName = sender.location() == null || sender.location().getWorld() == null
                ? ""
                : sender.location().getWorld().getName();
        result = result.replace("%player_world%", worldName);

        for (Map.Entry<String, String> entry : sender.placeholders().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String rawKey = entry.getKey();
            String normalizedKey = rawKey;
            if (normalizedKey.startsWith("%") && normalizedKey.endsWith("%") && normalizedKey.length() > 2) {
                normalizedKey = normalizedKey.substring(1, normalizedKey.length() - 1);
            } else if (normalizedKey.startsWith("{") && normalizedKey.endsWith("}") && normalizedKey.length() > 2) {
                normalizedKey = normalizedKey.substring(1, normalizedKey.length() - 1);
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("%" + normalizedKey + "%", value)
                    .replace("{" + normalizedKey + "}", value);
        }
        return result;
    }

    private String plainText(Component component) {
        return plainTextSerializer.serialize(component == null ? Component.empty() : component);
    }

    public Component formatSimple(String message) {
        return miniMessage.deserialize(message == null ? "" : message);
    }

    public Component formatWithPlayer(Player player, String message) {
        String parsed = placeholderHandler.setPlaceholders(player, message == null ? "" : message);
        return miniMessage.deserialize(parsed);
    }

    public PlaceholderHandler getPlaceholderHandler() {
        return placeholderHandler;
    }

    public CraftEngineHandler getCraftEngineHandler() {
        return craftEngineHandler;
    }

    private record FormatParts(String prefixPart, String suffixPart) {
        private FormatParts {
            prefixPart = prefixPart == null ? "" : prefixPart;
            suffixPart = suffixPart == null ? "" : suffixPart;
        }
    }

    private static final class SenderContext {

        private final Player player;
        private final UUID uniqueId;
        private final String name;
        private final String displayName;
        private final Location location;
        private final Map<String, String> placeholders;

        private SenderContext(Player player,
                              UUID uniqueId,
                              String name,
                              String displayName,
                              Location location,
                              Map<String, String> placeholders) {
            this.player = player;
            this.uniqueId = uniqueId;
            this.name = name;
            this.displayName = displayName;
            this.location = location == null ? null : location.clone();
            this.placeholders = Map.copyOf(placeholders);
        }

        private static SenderContext fromPlayer(Player player) {
            Objects.requireNonNull(player, "player");
            return new SenderContext(
                    player,
                    player.getUniqueId(),
                    gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", player),
                    // 使用真实显示名（昵称插件设置的 displayName），而不是原始用户名
                    PlainTextComponentSerializer.plainText().serialize(player.displayName()),
                    player.getLocation(),
                    Map.of()
            );
        }

        private static SenderContext fromVirtualSender(VirtualChatSender sender) {
            Objects.requireNonNull(sender, "sender");
            return new SenderContext(
                    null,
                    sender.getUniqueId(),
                    sender.getName(),
                    sender.getDisplayName(),
                    sender.getLocation(),
                    sender.getPlaceholders()
            );
        }

        private SenderContext withAdditionalPlaceholders(Map<String, String> additionalPlaceholders) {
            if (additionalPlaceholders == null || additionalPlaceholders.isEmpty()) {
                return this;
            }
            Map<String, String> merged = new LinkedHashMap<>(placeholders);
            merged.putAll(additionalPlaceholders);
            return new SenderContext(player, uniqueId, name, displayName, location, merged);
        }

        private Player player() {
            return player;
        }

        private UUID uniqueId() {
            return uniqueId;
        }

        private String name() {
            return name;
        }

        private String displayName() {
            return displayName;
        }

        private Location location() {
            return location == null ? null : location.clone();
        }

        private Map<String, String> placeholders() {
            return placeholders;
        }
    }
}
