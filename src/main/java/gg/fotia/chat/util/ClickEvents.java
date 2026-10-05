package gg.fotia.chat.util;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import java.util.Locale;

/** 通过稳定的名称与工厂方法兼容 Adventure 4/5，避免绑定旧枚举字段和旧载荷构造器。 */
public final class ClickEvents {
    private ClickEvents() {}

    public static ClickEvent.Action action(String name) {
        ClickEvent.Action action = ClickEvent.Action.NAMES.value(name.toLowerCase(Locale.ROOT));
        return action == null ? ClickEvent.suggestCommand("").action() : action;
    }

    /** 兼容新旧 JSON 字段，从稳定的序列化接口读取命令载荷。 */
    public static boolean isCommandStartingWith(ClickEvent event, String prefix) {
        if (event == null || !event.action().toString().equals("run_command")) return false;
        var json = GsonComponentSerializer.gson().serializeToTree(Component.text(" ").clickEvent(event)).getAsJsonObject();
        var click = json.has("click_event") ? json.getAsJsonObject("click_event") : json.getAsJsonObject("clickEvent");
        if (click == null) return false;
        var command = click.has("command") ? click.get("command") : click.get("value");
        return command != null && command.isJsonPrimitive() && command.getAsString().startsWith(prefix);
    }

    public static ClickEvent create(ClickEvent.Action action, String value) {
        return switch (action.toString()) {
            case "run_command" -> ClickEvent.runCommand(value);
            case "open_url" -> ClickEvent.openUrl(value);
            case "open_file" -> ClickEvent.openFile(value);
            case "copy_to_clipboard" -> ClickEvent.copyToClipboard(value);
            case "change_page" -> ClickEvent.changePage(Integer.parseInt(value));
            default -> ClickEvent.suggestCommand(value);
        };
    }
}
