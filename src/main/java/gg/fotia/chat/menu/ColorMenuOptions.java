package gg.fotia.chat.menu;

import org.bukkit.configuration.ConfigurationSection;

/** 管理员选择显示引擎和分页；实际布局由 FutureUI 的菜单模板定义。 */
public record ColorMenuOptions(boolean futureUi, Profile standard, Profile compact,
                               boolean compactByDefault, boolean showLocked, String previewText) {
    public record Profile(String menu, int pageSize) {}

    public static ColorMenuOptions read(ConfigurationSection config) {
        String engine = config.getString("ui-engine", "inventory");
        if (!engine.equals("inventory") && !engine.equals("futureui"))
            throw new IllegalArgumentException("color-menu.yml ui-engine: inventory / futureui");
        String layout = config.getString("futureui.default-layout", "standard");
        if (!layout.equals("standard") && !layout.equals("compact"))
            throw new IllegalArgumentException("futureui.default-layout: standard / compact");
        return new ColorMenuOptions(engine.equals("futureui"),
                profile(config, "futureui", "fotiachat/colors", 9),
                profile(config, "futureui.compact", "fotiachat/colors_compact", 4),
                layout.equals("compact"), config.getBoolean("futureui.show-locked", true),
                config.getString("futureui.preview-text", "lang:color.preview-text"));
    }

    private static Profile profile(ConfigurationSection config, String path, String fallback, int size) {
        String menu = config.getString(path + ".menu", fallback);
        int pageSize = config.getInt(path + ".page-size", size);
        if (menu.isBlank() || pageSize < 1 || pageSize > 36)
            throw new IllegalArgumentException(path + ": menu must not be blank; page-size must be 1..36");
        return new Profile(menu, pageSize);
    }

    public Profile profile(boolean small) { return small ? compact : standard; }
}
