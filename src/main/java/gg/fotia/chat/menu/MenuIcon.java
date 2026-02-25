package gg.fotia.chat.menu;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CraftEngine物品支持
 */
class CraftEngineItemSupport {
    private static Boolean available = null;

    /**
     * 检查CraftEngine是否可用
     */
    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
                available = Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
            } catch (ClassNotFoundException e) {
                available = false;
            }
        }
        return available;
    }

    /**
     * 获取CraftEngine物品
     * @param itemId 物品ID，格式为 namespace:id
     * @return ItemStack 或 null
     */
    public static ItemStack getItem(String itemId) {
        if (!isAvailable() || itemId == null || itemId.isEmpty()) {
            return null;
        }

        try {
            // 使用反射调用 CraftEngineItems.byId(Key.of(itemId))
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
            java.lang.reflect.Method keyOfMethod = keyClass.getMethod("of", String.class);
            Object key = keyOfMethod.invoke(null, itemId);

            Class<?> itemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            java.lang.reflect.Method byIdMethod = itemsClass.getMethod("byId", keyClass);
            Object customItem = byIdMethod.invoke(null, key);

            if (customItem == null) {
                return null;
            }

            // 调用 customItem.buildItemStack()
            java.lang.reflect.Method buildMethod = customItem.getClass().getMethod("buildItemStack");
            return (ItemStack) buildMethod.invoke(customItem);
        } catch (Exception e) {
            return null;
        }
    }
}

/**
 * 菜单图标类
 */
public class MenuIcon {

    private final String id;
    private final Material material;
    private final String craftEngineItem;
    private final String itemModel;
    private final int modelData;
    private final String tooltip;
    private final String name;
    private final List<String> lore;
    private final Map<String, List<String>> clickActions;

    public MenuIcon(String id, ConfigurationSection section) {
        this.id = id;
        this.clickActions = new HashMap<>();

        ConfigurationSection displaySection = section.getConfigurationSection("display");
        if (displaySection != null) {
            String materialName = displaySection.getString("material", "STONE");
            this.material = Material.matchMaterial(materialName) != null
                    ? Material.matchMaterial(materialName) : Material.STONE;
            this.craftEngineItem = displaySection.getString("craftengine-item", "");
            this.itemModel = displaySection.getString("item_model", null);
            this.modelData = displaySection.getInt("model_data", 0);
            this.tooltip = displaySection.getString("tooltip", null);
            this.name = displaySection.getString("name", "");
            this.lore = displaySection.getStringList("lore");
        } else {
            this.material = Material.STONE;
            this.craftEngineItem = "";
            this.itemModel = null;
            this.modelData = 0;
            this.tooltip = null;
            this.name = "";
            this.lore = new ArrayList<>();
        }

        ConfigurationSection actionsSection = section.getConfigurationSection("actions");
        if (actionsSection != null) {
            for (String clickType : actionsSection.getKeys(false)) {
                ConfigurationSection clickSection = actionsSection.getConfigurationSection(clickType);
                if (clickSection != null) {
                    List<String> actions = clickSection.getStringList("actions");
                    clickActions.put(clickType.toLowerCase(), actions);
                }
            }
        }
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getItemModel() {
        return itemModel;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public Map<String, List<String>> getClickActions() {
        return clickActions;
    }

    /**
     * 获取指定点击类型的动作列表
     */
    public List<String> getActions(String clickType) {
        String lowerClickType = clickType.toLowerCase();

        // 直接匹配
        if (clickActions.containsKey(lowerClickType)) {
            return clickActions.get(lowerClickType);
        }

        // 检查组合点击类型 (如 "left,right")
        for (Map.Entry<String, List<String>> entry : clickActions.entrySet()) {
            String[] types = entry.getKey().split(",");
            for (String type : types) {
                if (type.trim().equalsIgnoreCase(lowerClickType)) {
                    return entry.getValue();
                }
            }
        }

        return new ArrayList<>();
    }

    /**
     * 构建物品
     */
    public ItemStack buildItem(Player player) {
        ItemStack item;

        // 优先使用CraftEngine物品
        if (!craftEngineItem.isEmpty() && CraftEngineItemSupport.isAvailable()) {
            ItemStack ceItem = CraftEngineItemSupport.getItem(craftEngineItem);
            if (ceItem != null) {
                item = ceItem;
            } else {
                item = new ItemStack(material);
            }
        } else {
            item = new ItemStack(material);
        }

        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 设置名称
            if (!name.isEmpty()) {
                String parsedName = parsePlaceholders(name, player);
                meta.displayName(MessageUtil.parse(parsedName));
            }

            // 设置描述
            if (!lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    String parsedLine = parsePlaceholders(line, player);
                    loreComponents.add(MessageUtil.parse(parsedLine));
                }
                meta.lore(loreComponents);
            }

            // 设置CustomModelData
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }

            // 设置物品模型 (1.21.4+ Custom Item Model)
            // 使用反射以兼容不同版本
            if (itemModel != null && !itemModel.isEmpty()) {
                try {
                    String[] parts = itemModel.split(":");
                    if (parts.length == 2) {
                        // 尝试使用反射调用setItemModel方法
                        java.lang.reflect.Method setItemModelMethod = meta.getClass().getMethod("setItemModel", Key.class);
                        setItemModelMethod.invoke(meta, Key.key(parts[0], parts[1]));
                    }
                } catch (Exception ignored) {
                    // 版本不支持，忽略
                }
            }

            // 设置tooltip样式 (1.21.4+)
            // 使用反射以兼容不同版本
            if (tooltip != null && !tooltip.isEmpty()) {
                try {
                    String[] parts = tooltip.split(":");
                    if (parts.length >= 2) {
                        String namespace = parts[0];
                        String path = parts.length > 2 ? parts[1] + ":" + parts[2] : parts[1];
                        // 尝试使用反射调用setTooltipStyle方法
                        java.lang.reflect.Method setTooltipStyleMethod = meta.getClass().getMethod("setTooltipStyle", Key.class);
                        setTooltipStyleMethod.invoke(meta, Key.key(namespace, path));
                    }
                } catch (Exception ignored) {
                    // 版本不支持，忽略
                }
            }

            // 隐藏属性
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            item.setItemMeta(meta);
        }

        return item;
    }

    private String parsePlaceholders(String text, Player player) {
        if (player == null) return text;

        text = text.replace("{player}", player.getName());
        text = text.replace("{player_displayname}", player.displayName().toString());

        // PlaceholderAPI支持
        if (MessageUtil.isPlaceholderAPIEnabled()) {
            text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }

        return text;
    }
}
