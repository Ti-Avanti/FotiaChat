package gg.fotia.chat.channel;

import net.kyori.adventure.text.event.ClickEvent;
import gg.fotia.chat.util.ClickEvents;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 频道数据类
 */
public class Channel {

    private final String id;
    private final String name;
    private final ChannelType type;
    private final String format;
    private final String permission;
    private final String shortcut;
    private final int radius;
    private final boolean isDefault;
    private final boolean crossServer;

    private final boolean hoverEnabled;
    private final List<String> hoverText;

    private final boolean clickEnabled;
    private final ClickEvent.Action clickAction;
    private final String clickValue;
    private final Map<String, ChannelSegmentConfig> segmentConfigs;

    public Channel(String id, String name, ChannelType type, String format,
                   String permission, String shortcut, int radius, boolean isDefault,
                   boolean crossServer,
                   boolean hoverEnabled, List<String> hoverText,
                   boolean clickEnabled, ClickEvent.Action clickAction, String clickValue,
                   Map<String, ChannelSegmentConfig> segmentConfigs) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.format = format;
        this.permission = permission;
        this.shortcut = shortcut;
        this.radius = radius;
        this.isDefault = isDefault;
        this.crossServer = crossServer;
        this.hoverEnabled = hoverEnabled;
        this.hoverText = hoverText == null ? List.of() : List.copyOf(hoverText);
        this.clickEnabled = clickEnabled;
        this.clickAction = clickAction == null ? ClickEvents.action("SUGGEST_COMMAND") : clickAction;
        this.clickValue = clickValue == null ? "" : clickValue;
        this.segmentConfigs = normalizeSegmentConfigs(segmentConfigs);
    }

    public Channel(String id, String name, ChannelType type, String format,
                   String permission, String shortcut, int radius, boolean isDefault) {
        this(id, name, type, format, permission, shortcut, radius, isDefault,
                true, false, List.of(), false, ClickEvents.action("SUGGEST_COMMAND"), "", Map.of());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ChannelType getType() {
        return type;
    }

    public String getFormat() {
        return format;
    }

    public String getPermission() {
        return permission;
    }

    public String getShortcut() {
        return shortcut;
    }

    public int getRadius() {
        return radius;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isHoverEnabled() {
        return hoverEnabled;
    }

    public List<String> getHoverText() {
        return hoverText;
    }

    public boolean isClickEnabled() {
        return clickEnabled;
    }

    public ClickEvent.Action getClickAction() {
        return clickAction;
    }

    public String getClickValue() {
        return clickValue;
    }

    public Map<String, ChannelSegmentConfig> getSegmentConfigs() {
        return segmentConfigs;
    }

    public ChannelSegmentConfig getSegmentConfig(String id) {
        if (id == null) {
            return null;
        }
        return segmentConfigs.get(id.toLowerCase());
    }

    public boolean hasSegmentConfigs() {
        return !segmentConfigs.isEmpty();
    }

    public boolean isLocalChannel() {
        return type == ChannelType.LOCAL && radius > 0;
    }

    /**
     * 该频道是否参与跨服转发：本地/范围频道永远不跨服（距离语义无法跨服成立）。
     */
    public boolean isCrossServerEnabled() {
        return crossServer && !isLocalChannel();
    }

    public Channel localized(UnaryOperator<String> resolver) {
        Map<String, ChannelSegmentConfig> localizedSegments = new LinkedHashMap<>();
        segmentConfigs.forEach((key, value) -> localizedSegments.put(key, value.localized(resolver)));
        return new Channel(
                id,
                resolver.apply(name),
                type,
                resolver.apply(format),
                permission,
                shortcut,
                radius,
                isDefault,
                crossServer,
                hoverEnabled,
                hoverText.stream().map(resolver).toList(),
                clickEnabled,
                clickAction,
                clickValue,
                localizedSegments
        );
    }

    private Map<String, ChannelSegmentConfig> normalizeSegmentConfigs(Map<String, ChannelSegmentConfig> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, ChannelSegmentConfig> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ChannelSegmentConfig> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        return Collections.unmodifiableMap(normalized);
    }
}
