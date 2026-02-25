package gg.fotia.chat.menu;

import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.*;

/**
 * 菜单数据类
 */
public class Menu implements InventoryHolder {

    private final String id;
    private final String title;
    private final List<String> layout;
    private final Map<Character, MenuIcon> icons;
    private final int size;

    public Menu(String id, FileConfiguration config) {
        this.id = id;
        this.title = config.getString("Title", "菜单");
        this.layout = config.getStringList("Layout");
        this.icons = new HashMap<>();

        // 计算菜单大小
        this.size = Math.min(layout.size() * 9, 54);

        // 加载图标
        ConfigurationSection iconsSection = config.getConfigurationSection("Icons");
        if (iconsSection != null) {
            for (String key : iconsSection.getKeys(false)) {
                if (key.length() == 1) {
                    ConfigurationSection iconSection = iconsSection.getConfigurationSection(key);
                    if (iconSection != null) {
                        icons.put(key.charAt(0), new MenuIcon(key, iconSection));
                    }
                }
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getLayout() {
        return layout;
    }

    public Map<Character, MenuIcon> getIcons() {
        return icons;
    }

    public int getSize() {
        return size;
    }

    /**
     * 获取指定槽位的图标
     */
    public MenuIcon getIconAt(int slot) {
        int row = slot / 9;
        int col = slot % 9;

        if (row >= layout.size()) {
            return null;
        }

        String rowLayout = layout.get(row);
        if (col >= rowLayout.length()) {
            return null;
        }

        char iconChar = rowLayout.charAt(col);
        return icons.get(iconChar);
    }

    /**
     * 为玩家打开菜单
     */
    public Inventory open(Player player) {
        Component titleComponent = MessageUtil.parse(parsePlaceholders(title, player));
        Inventory inventory = Bukkit.createInventory(this, size, titleComponent);

        // 填充物品
        for (int slot = 0; slot < size; slot++) {
            MenuIcon icon = getIconAt(slot);
            if (icon != null) {
                inventory.setItem(slot, icon.buildItem(player));
            }
        }

        player.openInventory(inventory);
        return inventory;
    }

    private String parsePlaceholders(String text, Player player) {
        if (player == null) return text;

        text = text.replace("{player}", player.getName());

        if (MessageUtil.isPlaceholderAPIEnabled()) {
            text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }

        return text;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
