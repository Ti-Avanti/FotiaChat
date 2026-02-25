package gg.fotia.chat.announcement;

import net.kyori.adventure.text.event.ClickEvent;

import java.util.List;

/**
 * 公告数据类
 */
public class Announcement {

    private final String id;
    private final String permission;
    private final int interval;
    private final List<String> messages;
    private final boolean enabled;
    private final String sound;
    private final float soundVolume;
    private final float soundPitch;

    // Hover配置
    private final boolean hoverEnabled;
    private final List<String> hoverText;

    // Click配置
    private final boolean clickEnabled;
    private final ClickEvent.Action clickAction;
    private final String clickValue;

    public Announcement(String id, String permission, int interval, List<String> messages,
                        boolean enabled, String sound, float soundVolume, float soundPitch,
                        boolean hoverEnabled, List<String> hoverText,
                        boolean clickEnabled, ClickEvent.Action clickAction, String clickValue) {
        this.id = id;
        this.permission = permission;
        this.interval = interval;
        this.messages = messages;
        this.enabled = enabled;
        this.sound = sound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
        this.hoverEnabled = hoverEnabled;
        this.hoverText = hoverText;
        this.clickEnabled = clickEnabled;
        this.clickAction = clickAction;
        this.clickValue = clickValue;
    }

    // 兼容旧构造函数
    public Announcement(String id, String permission, int interval, List<String> messages,
                        boolean enabled, String sound, float soundVolume, float soundPitch) {
        this(id, permission, interval, messages, enabled, sound, soundVolume, soundPitch,
                false, List.of(), false, ClickEvent.Action.SUGGEST_COMMAND, "");
    }

    public String getId() {
        return id;
    }

    public String getPermission() {
        return permission;
    }

    public int getInterval() {
        return interval;
    }

    public List<String> getMessages() {
        return messages;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSound() {
        return sound;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    public boolean hasSound() {
        return sound != null && !sound.isEmpty();
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
}
