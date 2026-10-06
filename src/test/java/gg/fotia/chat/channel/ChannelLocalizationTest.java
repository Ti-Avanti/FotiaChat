package gg.fotia.chat.channel;

import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelLocalizationTest {

    @Test
    void createsLocalizedCopyWithoutChangingCommands() {
        ChannelSegmentConfig segment = new ChannelSegmentConfig(
                "channel", "lang:channels.global.display", true,
                List.of("lang:channels.global.hover"), true,
                ClickEvent.suggestCommand("").action(), "/channel global"
        );
        Channel channel = new Channel(
                "global", "lang:channels.global.name", ChannelType.PUBLIC,
                "lang:channels.global.format", "", "!", 0, true, true,
                true, List.of("lang:channels.global.hover"), true,
                ClickEvent.suggestCommand("").action(), "/channel global",
                Map.of("channel", segment)
        );

        Channel localized = channel.localized(value -> value.replace("lang:", "translated:"));

        assertEquals("translated:channels.global.name", localized.getName());
        assertEquals("translated:channels.global.format", localized.getFormat());
        assertEquals("translated:channels.global.display",
                localized.getSegmentConfig("channel").getDisplay());
        assertEquals("/channel global", localized.getSegmentConfig("channel").getClickValue());
        assertEquals("lang:channels.global.name", channel.getName());
    }
}
