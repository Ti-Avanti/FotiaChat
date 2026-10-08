package gg.fotia.chat.menu.futureui;

import gg.fotia.chat.color.ChatColor;
import gg.fotia.chat.util.MessageUtil;
import gg.fotia.futureui.api.RasterImage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** 用真实颜色格式绘制低分辨率色条，不读取文件或重建资源包。 */
final class ColorSwatches {
    private final Map<String, RasterImage> cache = new HashMap<>();

    RasterImage image(ChatColor color, boolean available) {
        String key = color.getId() + ":" + available;
        return cache.computeIfAbsent(key, ignored -> create(color, available));
    }

    private RasterImage create(ChatColor color, boolean available) {
        List<Integer> colors = new ArrayList<>();
        collect(MessageUtil.parse(color.apply("|".repeat(24))), 0xffffff, colors);
        if (colors.isEmpty()) colors.add(0xffffff);
        int width = 72, height = 9;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rgb = colors.get(Math.min(colors.size() - 1, x * colors.size() / width));
            if (!available) {
                int gray = (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
                int shade = Math.min(128, 48 + gray / 3);
                rgb = shade << 16 | shade << 8 | shade;
            }
            pixels[y * width + x] = 0xff000000 | rgb;
        }
        return new RasterImage(width, height, pixels);
    }

    private void collect(Component component, int inherited, List<Integer> colors) {
        TextColor color = component.color();
        int rgb = color == null ? inherited : color.value();
        if (component instanceof TextComponent text)
            text.content().codePoints().forEach(ignored -> colors.add(rgb));
        for (Component child : component.children()) collect(child, rgb, colors);
    }
}
