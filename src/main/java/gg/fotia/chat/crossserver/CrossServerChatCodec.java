package gg.fotia.chat.crossserver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import gg.fotia.chat.util.ClickEvents;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** 跨服聊天只传输已完成权限检查的组件，不再次解析玩家的旧颜色码。 */
public final class CrossServerChatCodec {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private CrossServerChatCodec() {
    }

    public static String encode(Component component) {
        return MINI_MESSAGE.serialize(removeLocalSnapshotLinks(component == null ? Component.empty() : component));
    }

    public static Component decode(String message) {
        return MINI_MESSAGE.deserialize(message == null ? "" : message);
    }

    private static Component removeLocalSnapshotLinks(Component component) {
        ClickEvent click = component.clickEvent();
        if (ClickEvents.isCommandStartingWith(click, "/fotiachat viewsnapshot ")) {
            component = component.clickEvent(null).hoverEvent(null);
        }
        // 保持跨服物品文字预览，不携带大块物品 NBT，避免超出代理消息大小限制。
        if (component.hoverEvent() != null && component.hoverEvent().action() == HoverEvent.Action.SHOW_ITEM) {
            component = component.hoverEvent(null);
        }
        return component.children(component.children().stream()
                .map(CrossServerChatCodec::removeLocalSnapshotLinks).toList());
    }
}
