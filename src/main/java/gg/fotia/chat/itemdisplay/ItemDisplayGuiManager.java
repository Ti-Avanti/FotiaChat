package gg.fotia.chat.itemdisplay;

import gg.fotia.chat.FotiaChat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 物品展示GUI管理器
 */
public class ItemDisplayGuiManager {

    private final FotiaChat plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration config;

    // 背包GUI配置
    private String invTitle;
    private List<String> invLayout;
    private ConfigurationSection invIcons;

    // 末影箱GUI配置
    private String ecTitle;
    private List<String> ecLayout;
    private ConfigurationSection ecIcons;

    public ItemDisplayGuiManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * 加载配置
     */
    public void load(FileConfiguration config) {
        this.config = config;

        // 加载背包GUI配置
        ConfigurationSection invSection = config.getConfigurationSection("inventory.gui");
        if (invSection != null) {
            invTitle = invSection.getString("Title", "<!i><gold>{player} 的背包</gold>");
            invLayout = invSection.getStringList("Layout");
            invIcons = invSection.getConfigurationSection("Icons");
        }

        // 加载末影箱GUI配置
        ConfigurationSection ecSection = config.getConfigurationSection("enderchest.gui");
        if (ecSection != null) {
            ecTitle = ecSection.getString("Title", "<!i><dark_purple>{player} 的末影箱</dark_purple>");
            ecLayout = ecSection.getStringList("Layout");
            ecIcons = ecSection.getConfigurationSection("Icons");
        }
    }

    /**
     * 打开背包展示GUI
     */
    public void openInventoryGui(Player viewer, ItemSnapshot snapshot) {
        String title = invTitle.replace("{player}", snapshot.playerName());
        int size = invLayout.size() * 9;
        Inventory gui = Bukkit.createInventory(new ItemDisplayHolder(snapshot.id()), size,
                miniMessage.deserialize(title));

        // 解析布局并填充
        fillInventoryFromLayout(gui, invLayout, invIcons, snapshot.contents(), "inventory");

        viewer.openInventory(gui);
    }

    /**
     * 打开末影箱展示GUI
     */
    public void openEnderchestGui(Player viewer, ItemSnapshot snapshot) {
        String title = ecTitle.replace("{player}", snapshot.playerName());
        int size = ecLayout.size() * 9;
        Inventory gui = Bukkit.createInventory(new ItemDisplayHolder(snapshot.id()), size,
                miniMessage.deserialize(title));

        // 解析布局并填充
        fillInventoryFromLayout(gui, ecLayout, ecIcons, snapshot.contents(), "enderchest");

        viewer.openInventory(gui);
    }

    /**
     * 根据布局填充GUI
     */
    private void fillInventoryFromLayout(Inventory gui, List<String> layout, ConfigurationSection icons,
                                         ItemStack[] contents, String type) {
        // 动态槽位计数器
        int inventoryIndex = 9;  // 主背包从槽位9开始
        int hotbarIndex = 0;     // 快捷栏从槽位0开始
        int enderchestIndex = 0; // 末影箱从槽位0开始

        for (int row = 0; row < layout.size(); row++) {
            String rowLayout = layout.get(row);
            for (int col = 0; col < rowLayout.length() && col < 9; col++) {
                int slot = row * 9 + col;
                char iconChar = rowLayout.charAt(col);
                String iconKey = String.valueOf(iconChar);

                if (icons == null) continue;
                ConfigurationSection iconSection = icons.getConfigurationSection(iconKey);
                if (iconSection == null) continue;

                String iconType = iconSection.getString("type", "");

                // 处理动态槽位类型
                ItemStack item = switch (iconType) {
                    case "armor_helmet" -> getArmorItem(contents, 39, iconSection);
                    case "armor_chestplate" -> getArmorItem(contents, 38, iconSection);
                    case "armor_leggings" -> getArmorItem(contents, 37, iconSection);
                    case "armor_boots" -> getArmorItem(contents, 36, iconSection);
                    case "offhand" -> getArmorItem(contents, 40, iconSection);
                    case "inventory" -> {
                        if (inventoryIndex < 36 && inventoryIndex < contents.length) {
                            ItemStack invItem = contents[inventoryIndex++];
                            yield invItem != null ? invItem.clone() : null;
                        }
                        yield null;
                    }
                    case "hotbar" -> {
                        if (hotbarIndex < 9 && hotbarIndex < contents.length) {
                            ItemStack hotbarItem = contents[hotbarIndex++];
                            yield hotbarItem != null ? hotbarItem.clone() : null;
                        }
                        yield null;
                    }
                    case "enderchest" -> {
                        if (enderchestIndex < contents.length) {
                            ItemStack ecItem = contents[enderchestIndex++];
                            yield ecItem != null ? ecItem.clone() : null;
                        }
                        yield null;
                    }
                    default -> createStaticItem(iconSection);
                };

                if (item != null) {
                    gui.setItem(slot, item);
                }
            }
        }
    }

    /**
     * 获取盔甲/副手物品
     */
    private ItemStack getArmorItem(ItemStack[] contents, int armorSlot, ConfigurationSection iconSection) {
        if (armorSlot < contents.length && contents[armorSlot] != null) {
            return contents[armorSlot].clone();
        }
        // 返回空槽位显示
        ConfigurationSection emptySection = iconSection.getConfigurationSection("empty");
        if (emptySection != null) {
            return createItemFromSection(emptySection);
        }
        return null;
    }

    /**
     * 创建静态物品
     */
    private ItemStack createStaticItem(ConfigurationSection iconSection) {
        ConfigurationSection displaySection = iconSection.getConfigurationSection("display");
        if (displaySection != null) {
            return createItemFromSection(displaySection);
        }
        return null;
    }

    /**
     * 从配置节创建物品
     */
    private ItemStack createItemFromSection(ConfigurationSection section) {
        String materialStr = section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialStr);
        if (material == null) {
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name", "");
            if (!name.isEmpty()) {
                meta.displayName(miniMessage.deserialize(name));
            }

            List<String> loreStrings = section.getStringList("lore");
            if (!loreStrings.isEmpty()) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String line : loreStrings) {
                    lore.add(miniMessage.deserialize(line));
                }
                meta.lore(lore);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 物品展示GUI持有者
     */
    public static class ItemDisplayHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID snapshotId;

        public ItemDisplayHolder(UUID snapshotId) {
            this.snapshotId = snapshotId;
        }

        public UUID getSnapshotId() {
            return snapshotId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
