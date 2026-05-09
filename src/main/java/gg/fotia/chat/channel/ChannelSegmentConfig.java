package gg.fotia.chat.channel;

import net.kyori.adventure.text.event.ClickEvent;

import java.util.List;

/**
 * 频道格式分段配置
 */
public class ChannelSegmentConfig {

    private final String id;
    private final String display;
    private final boolean hoverEnabled;
    private final List<String> hoverText;
    private final boolean clickEnabled;
    private final ClickEvent.Action clickAction;
    private final String clickValue;

    public ChannelSegmentConfig(String id, String display,
                                boolean hoverEnabled, List<String> hoverText,
                                boolean clickEnabled, ClickEvent.Action clickAction, String clickValue) {
        this.id = id;
        this.display = display == null ? "" : display;
        this.hoverEnabled = hoverEnabled;
        this.hoverText = List.copyOf(hoverText == null ? List.of() : hoverText);
        this.clickEnabled = clickEnabled;
        this.clickAction = clickAction == null ? ClickEvent.Action.SUGGEST_COMMAND : clickAction;
        this.clickValue = clickValue == null ? "" : clickValue;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
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

    public boolean hasHover() {
        return hoverEnabled && !hoverText.isEmpty();
    }

    public boolean hasClick() {
        return clickEnabled && !clickValue.isEmpty();
    }
}
