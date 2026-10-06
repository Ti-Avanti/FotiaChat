package gg.fotia.chat.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTextTransformerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void sliceRemovesShortcutAndWhitespaceWithoutDroppingEmojiFont() {
        Component source = Component.text("#  hello ")
                .append(Component.text("ꀣ").font(Key.key("emoji", "blush")))
                .append(Component.text("  "));

        Component sliced = ComponentTextTransformer.slice(source, 3, PLAIN.serialize(source).length() - 2);

        assertEquals("hello ꀣ", PLAIN.serialize(sliced));
        assertTrue(ComponentTextTransformer.containsFont(sliced));
    }

    @Test
    void literalReplacementKeepsUnrelatedEmojiComponent() {
        Component source = Component.text("hello ")
                .append(Component.text("ꀣ").font(Key.key("emoji", "blush")))
                .append(Component.text(" [i]"));

        Component replaced = ComponentTextTransformer.replaceLiteral(source, "[i]", Component.text("ITEM"));

        assertEquals("hello ꀣ ITEM", PLAIN.serialize(replaced));
        assertTrue(ComponentTextTransformer.containsFont(replaced));
    }
    @Test
    void segmentedChatMarkersPreserveRichPlayerContentAndFormatStyle() {
        Component player = Component.text("玩家")
                .font(Key.key("emoji", "player"))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/msg 玩家 "))
                .hoverEvent(Component.text("点击私聊"));
        Component source = Component.text("前缀 __player__ 后缀", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);

        Component result = ComponentTextTransformer.replacePattern(source,
                java.util.regex.Pattern.compile(java.util.regex.Pattern.quote("__player__")), player);

        assertEquals("前缀 玩家 后缀", PLAIN.serialize(result));
        assertTrue(ComponentTextTransformer.containsFont(result));
        assertEquals(source.style(), result.style());
        java.util.ArrayDeque<Component> nodes = new java.util.ArrayDeque<>();
        nodes.add(result);
        boolean preserved = false;
        while (!nodes.isEmpty()) {
            Component node = nodes.removeFirst();
            preserved |= node.equals(player);
            nodes.addAll(node.children());
        }
        assertTrue(preserved, "玩家组件的字体、点击及悬浮事件必须完整保留");
    }

    @Test
    void segmentedChatMarkersReplaceAllOccurrencesIncludingChildren() {
        Component source = Component.text("__message__/")
                .append(Component.text("__message__"));
        Component result = ComponentTextTransformer.replacePattern(source,
                java.util.regex.Pattern.compile("__message__"), Component.text("中文消息"));
        assertEquals("中文消息/中文消息", PLAIN.serialize(result));
    }

}
