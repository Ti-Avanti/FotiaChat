package gg.fotia.chat.itemdisplay;

import gg.fotia.chat.FotiaChat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemDisplayGuiManager {

    private final FotiaChat plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration config;

    private String handTitle;
    private List<String> handLayout;
    private ConfigurationSection handIcons;

    private String invTitle;
    private List<String> invLayout;
    private ConfigurationSection invIcons;

    private String ecTitle;
    private List<String> ecLayout;
    private ConfigurationSection ecIcons;

    public ItemDisplayGuiManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void load(FileConfiguration config) {
        this.config = config;

        handTitle = config.getString("hand-item.gui.Title", "<!i><aqua>{player}'s Item</aqua>");
        handLayout = config.getStringList("hand-item.gui.Layout");
        if (handLayout == null || handLayout.isEmpty()) {
            handLayout = List.of("#########", "####i####", "#########");
        }
        handIcons = config.getConfigurationSection("hand-item.gui.Icons");

        ConfigurationSection invSection = config.getConfigurationSection("inventory.gui");
        if (invSection != null) {
            invTitle = invSection.getString("Title", "<!i><gold>{player} Inventory</gold>");
            invLayout = invSection.getStringList("Layout");
            invIcons = invSection.getConfigurationSection("Icons");
        }

        ConfigurationSection ecSection = config.getConfigurationSection("enderchest.gui");
        if (ecSection != null) {
            ecTitle = ecSection.getString("Title", "<!i><dark_purple>{player} Ender Chest</dark_purple>");
            ecLayout = ecSection.getStringList("Layout");
            ecIcons = ecSection.getConfigurationSection("Icons");
        }
    }

    public void openHandItemGui(Player viewer, ItemSnapshot snapshot) {
        String title = resolveTitle(handTitle, snapshot);
        int size = Math.max(9, handLayout.size() * 9);
        Inventory gui = Bukkit.createInventory(new ItemDisplayHolder(snapshot.id()), size,
                miniMessage.deserialize(title));

        fillInventoryFromLayout(gui, handLayout, handIcons, snapshot.contents());
        viewer.openInventory(gui);
    }

    public void openInventoryGui(Player viewer, ItemSnapshot snapshot) {
        String title = resolveTitle(invTitle, snapshot);
        int size = invLayout.size() * 9;
        Inventory gui = Bukkit.createInventory(new ItemDisplayHolder(snapshot.id()), size,
                miniMessage.deserialize(title));

        fillInventoryFromLayout(gui, invLayout, invIcons, snapshot.contents());
        viewer.openInventory(gui);
    }

    public void openEnderchestGui(Player viewer, ItemSnapshot snapshot) {
        String title = resolveTitle(ecTitle, snapshot);
        int size = ecLayout.size() * 9;
        Inventory gui = Bukkit.createInventory(new ItemDisplayHolder(snapshot.id()), size,
                miniMessage.deserialize(title));

        fillInventoryFromLayout(gui, ecLayout, ecIcons, snapshot.contents());
        viewer.openInventory(gui);
    }

    private void fillInventoryFromLayout(Inventory gui, List<String> layout, ConfigurationSection icons,
                                         ItemStack[] contents) {
        if (layout == null || layout.isEmpty() || icons == null) {
            return;
        }

        int inventoryIndex = 9;
        int hotbarIndex = 0;
        int enderchestIndex = 0;

        for (int row = 0; row < layout.size(); row++) {
            String rowLayout = layout.get(row);
            for (int col = 0; col < rowLayout.length() && col < 9; col++) {
                int slot = row * 9 + col;
                char iconChar = rowLayout.charAt(col);
                String iconKey = String.valueOf(iconChar);

                ConfigurationSection iconSection = icons.getConfigurationSection(iconKey);
                if (iconSection == null) {
                    continue;
                }

                String iconType = iconSection.getString("type", "");
                ItemStack item = switch (iconType) {
                    case "armor_helmet" -> getArmorItem(contents, 39, iconSection);
                    case "armor_chestplate" -> getArmorItem(contents, 38, iconSection);
                    case "armor_leggings" -> getArmorItem(contents, 37, iconSection);
                    case "armor_boots" -> getArmorItem(contents, 36, iconSection);
                    case "offhand" -> getArmorItem(contents, 40, iconSection);
                    case "hand_item" -> getSingleItem(contents, iconSection);
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

    private ItemStack getArmorItem(ItemStack[] contents, int armorSlot, ConfigurationSection iconSection) {
        if (armorSlot < contents.length && contents[armorSlot] != null) {
            return contents[armorSlot].clone();
        }
        ConfigurationSection emptySection = iconSection.getConfigurationSection("empty");
        if (emptySection != null) {
            return createItemFromSection(emptySection);
        }
        return null;
    }

    private ItemStack getSingleItem(ItemStack[] contents, ConfigurationSection iconSection) {
        if (contents.length > 0 && contents[0] != null) {
            return contents[0].clone();
        }
        ConfigurationSection emptySection = iconSection.getConfigurationSection("empty");
        if (emptySection != null) {
            return createItemFromSection(emptySection);
        }
        return null;
    }

    private ItemStack createStaticItem(ConfigurationSection iconSection) {
        ConfigurationSection displaySection = iconSection.getConfigurationSection("display");
        if (displaySection != null) {
            return createItemFromSection(displaySection);
        }
        return null;
    }

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

    private String resolveTitle(String titleTemplate, ItemSnapshot snapshot) {
        String safeTitle = titleTemplate == null ? "<!i><gray>Item Preview</gray>" : titleTemplate;
        ItemStack displayItem = snapshot.contents().length > 0 ? snapshot.contents()[0] : null;
        return safeTitle.replace("{player}", snapshot.playerName())
                .replace("{item_name}", getItemName(displayItem))
                .replace("{amount}", displayItem == null ? "0" : String.valueOf(displayItem.getAmount()));
    }

    private String getItemName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "Empty Item";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }

        String name = item.getType().name().toLowerCase().replace("_", " ");
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

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