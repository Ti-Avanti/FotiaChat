package gg.fotia.chat.menu.futureui;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.color.ChatColor;
import gg.fotia.chat.color.ColorType;
import gg.fotia.chat.menu.ColorMenuOptions;
import gg.fotia.chat.util.MessageUtil;
import gg.fotia.futureui.config.Node;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只在主线程读取颜色与权限；返回独立快照，不访问数据库。 */
final class ColorMenuData {
    private final FotiaChat plugin;
    private final ColorMenuOptions options;
    private final ColorSwatches swatches = new ColorSwatches();

    ColorMenuData(FotiaChat plugin, ColorMenuOptions options) {
        this.plugin = plugin; this.options = options;
    }

    List<ChatColor> colors(Player player, ColorMenuSession session) {
        return plugin.getColorManager().getColors().values().stream()
                .filter(color -> options.showLocked() || color.hasPermission(player))
                .filter(color -> switch (session.filter) {
                    case "single" -> color.getType() == ColorType.SINGLE;
                    case "effects" -> color.getType() != ColorType.SINGLE;
                    case "available" -> color.hasPermission(player);
                    default -> true;
                }).toList();
    }

    List<ChatColor> entries(Player player, ColorMenuSession session) {
        List<ChatColor> colors = colors(player, session);
        int size = options.profile(session.compact).pageSize();
        session.page = Math.max(0, Math.min(session.page, Math.max(0, (colors.size() - 1) / size)));
        int first = session.page * size;
        return colors.subList(Math.min(first, colors.size()), Math.min(first + size, colors.size()));
    }

    List<Node> rows(Player player, ColorMenuSession session, String view) {
        ChatColor current = plugin.getColorManager().getPlayerColor(player);
        String selected = current == null ? "" : current.getId();
        if (view.equals("entries")) {
            List<Node> rows = new ArrayList<>();
            for (ChatColor color : entries(player, session)) {
                boolean allowed = color.hasPermission(player);
                rows.add(Node.of(Map.of("id", color.getId(), "name", name(player, color, session.compact ? 62 : 100),
                        "selected", selected.equals(color.getId()), "available", allowed,
                        "swatch", swatches.image(color, allowed))));
            }
            return List.copyOf(rows);
        }
        if (!view.equals("page")) return List.of();
        entries(player, session);
        int count = colors(player, session).size(), size = options.profile(session.compact).pageSize();
        int pages = Math.max(1, (count + size - 1) / size);
        String sample = plugin.getMessageManager().resolveConfigured(player, options.previewText());
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("current-name", current == null ? Component.empty() : name(player, current, session.compact ? 72 : 144));
        page.put("has-current", current != null);
        page.put("preview", MessageUtil.parse(current == null ? sample : current.apply(sample), player));
        page.put("page", session.page + 1); page.put("pages", pages); page.put("count", count);
        page.put("previous", session.page > 0); page.put("next", session.page + 1 < pages);
        page.put("empty", count == 0); page.put("filter", session.filter); page.put("feedback", session.feedback);
        return List.of(Node.of(page));
    }

    private Component name(Player player, ChatColor color, int maxWidth) {
        String plain = MessageUtil.toPlainText(MessageUtil.parse(plugin.getColorManager().getDisplayName(player, color), player));
        int used = 0;
        StringBuilder clipped = new StringBuilder();
        int[] points = plain.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            int width = points[i] > 255 ? 9 : 6;
            if (used + width + (i + 1 < points.length ? 9 : 0) > maxWidth) {
                clipped.append('…'); break;
            }
            clipped.appendCodePoint(points[i]); used += width;
        }
        return Component.text(clipped.toString());
    }
}
