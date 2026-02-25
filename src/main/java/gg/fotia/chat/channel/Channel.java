package gg.fotia.chat.channel;

import net.kyori.adventure.text.event.ClickEvent;

import java.util.List;

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

    // Hover配置
    private final boolean hoverEnabled;
    private final List<String> hoverText;

    // Click配置
    private final boolean clickEnabled;
    private final ClickEvent.Action clickAction;
    private final String clickValue;

    public Channel(String id, String name, ChannelType type, String format,
                   String permission, String shortcut, int radius, boolean isDefault,
                   boolean hoverEnabled, List<String> hoverText,
                   boolean clickEnabled, ClickEvent.Action clickAction, String clickValue) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.format = format;
        this.permission = permission;
        this.shortcut = shortcut;
        this.radius = radius;
        this.isDefault = isDefault;
        this.hoverEnabled = hoverEnabled;
        this.hoverText = hoverText;
        this.clickEnabled = clickEnabled;
        this.clickAction = clickAction;
        this.clickValue = clickValue;
    }

    // 兼容旧构造函数
    public Channel(String id, String name, ChannelType type, String format,
                   String permission, String shortcut, int radius, boolean isDefault) {
        this(id, name, type, format, permission, shortcut, radius, isDefault,
                false, List.of(), false, ClickEvent.Action.SUGGEST_COMMAND, "");
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

    /**
     * 检查是否为范围频道
     */
    public boolean isLocalChannel() {
        return type == ChannelType.LOCAL && radius > 0;
    }
}
