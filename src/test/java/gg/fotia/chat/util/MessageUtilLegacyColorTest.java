package gg.fotia.chat.util;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageUtilLegacyColorTest {

    @Test
    void parseConvertsLegacyAndHexColorsBeforeMiniMessage() {
        // 验证实际显示样式，避免依赖不同 Adventure 版本的序列化标签写法。
        assertEquals(NamedTextColor.RED, MessageUtil.parse("&cRed").color());
        assertEquals(TextColor.color(0x12AB34), MessageUtil.parse("&#12AB34Hex").color());
        assertEquals(TextDecoration.State.TRUE, MessageUtil.parse("§lBold").decoration(TextDecoration.BOLD));
        assertEquals("Red Hex Bold", PlainTextComponentSerializer.plainText().serialize(
                MessageUtil.parse("&cRed &#12AB34Hex §lBold")));
    }
}
