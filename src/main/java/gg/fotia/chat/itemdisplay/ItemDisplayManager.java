package gg.fotia.chat.itemdisplay;

import gg.fotia.chat.FotiaChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品展示管理器
 */
public class ItemDisplayManager {

    private final FotiaChat plugin;
    private final MiniMessage miniMessage;
    private final Map<UUID, ItemSnapshot> snapshots = new ConcurrentHashMap<>();
    private ItemDisplayGuiManager guiManager;
    private FileConfiguration itemDisplayConfig;

    // 配置
    private boolean handItemEnabled;
    private String handItemPlaceholder;
    private String handItemEmptyHand;
    private String handItemPermission;

    private boolean inventoryEnabled;
    private String inventoryPlaceholder;
    private String inventoryFormat;
    private List<String> inventoryHover;
    private String inventoryPermission;
    private String inventoryViewPermission;

    private boolean enderchestEnabled;
    private String enderchestPlaceholder;
    private String enderchestFormat;
    private List<String> enderchestHover;
    private String enderchestPermission;
    private String enderchestViewPermission;

    private int snapshotExpireTime;

    public ItemDisplayManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.guiManager = new ItemDisplayGuiManager(plugin);
    }

    /**
     * 加载配置
     */
    public void load() {
        saveDefaultConfig();

        File configFile = new File(plugin.getDataFolder(), "menus/item-display.yml");
        itemDisplayConfig = YamlConfiguration.loadConfiguration(configFile);

        // 合并默认配置
        InputStream defaultStream = plugin.getResource("menus/item-display.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            itemDisplayConfig.setDefaults(defaultConfig);
        }

        // 快照过期时间（秒）
        snapshotExpireTime = itemDisplayConfig.getInt("snapshot-expire-time", 300);

        // 手持物品配置
        ConfigurationSection handConfig = itemDisplayConfig.getConfigurationSection("hand-item");
        if (handConfig != null) {
            handItemEnabled = handConfig.getBoolean("enabled", true);
            handItemPlaceholder = handConfig.getString("placeholder", "[i]");
            handItemEmptyHand = handConfig.getString("empty-hand", "<!i><gray>[空]</gray>");
            handItemPermission = handConfig.getString("permission", "fotiachat.item.hand");
        }

        // 背包配置
        ConfigurationSection invConfig = itemDisplayConfig.getConfigurationSection("inventory");
        if (invConfig != null) {
            inventoryEnabled = invConfig.getBoolean("enabled", true);
            inventoryPlaceholder = invConfig.getString("placeholder", "[inv]");
            inventoryFormat = invConfig.getString("format", "<!i><gold>[查看背包]</gold>");
            inventoryHover = invConfig.getStringList("hover");
            inventoryPermission = invConfig.getString("permission", "fotiachat.item.inventory");
            inventoryViewPermission = invConfig.getString("view-permission", "fotiachat.item.inventory.view");
        }

        // 末影箱配置
        ConfigurationSection ecConfig = itemDisplayConfig.getConfigurationSection("enderchest");
        if (ecConfig != null) {
            enderchestEnabled = ecConfig.getBoolean("enabled", true);
            enderchestPlaceholder = ecConfig.getString("placeholder", "[ec]");
            enderchestFormat = ecConfig.getString("format", "<!i><dark_purple>[查看末影箱]</dark_purple>");
            enderchestHover = ecConfig.getStringList("hover");
            enderchestPermission = ecConfig.getString("permission", "fotiachat.item.enderchest");
            enderchestViewPermission = ecConfig.getString("view-permission", "fotiachat.item.enderchest.view");
        }

        // 启动清理任务
        startCleanupTask();

        // 加载GUI配置
        guiManager.load(itemDisplayConfig);
    }

    /**
     * 保存默认配置文件
     */
    private void saveDefaultConfig() {
        File configFile = new File(plugin.getDataFolder(), "menus/item-display.yml");
        if (!configFile.exists()) {
            plugin.saveResource("menus/item-display.yml", false);
        }
    }

    /**
     * 处理消息中的物品展示占位符
     */
    public Component processMessage(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return miniMessage.deserialize(message);
        }

        List<Component> parts = new ArrayList<>();
        String remaining = message;

        while (!remaining.isEmpty()) {
            int handIndex = handItemEnabled && handItemPlaceholder != null ?
                    remaining.indexOf(handItemPlaceholder) : -1;
            int invIndex = inventoryEnabled && inventoryPlaceholder != null ?
                    remaining.indexOf(inventoryPlaceholder) : -1;
            int ecIndex = enderchestEnabled && enderchestPlaceholder != null ?
                    remaining.indexOf(enderchestPlaceholder) : -1;

            // 找到最近的占位符
            int minIndex = -1;
            String placeholder = null;
            String type = null;

            if (handIndex >= 0 && (minIndex < 0 || handIndex < minIndex)) {
                minIndex = handIndex;
                placeholder = handItemPlaceholder;
                type = "hand";
            }
            if (invIndex >= 0 && (minIndex < 0 || invIndex < minIndex)) {
                minIndex = invIndex;
                placeholder = inventoryPlaceholder;
                type = "inventory";
            }
            if (ecIndex >= 0 && (minIndex < 0 || ecIndex < minIndex)) {
                minIndex = ecIndex;
                placeholder = enderchestPlaceholder;
                type = "enderchest";
            }

            if (minIndex < 0) {
                // 没有更多占位符
                parts.add(miniMessage.deserialize(remaining));
                break;
            }

            // 添加占位符之前的文本
            if (minIndex > 0) {
                parts.add(miniMessage.deserialize(remaining.substring(0, minIndex)));
            }

            // 处理占位符
            Component itemComponent = processPlaceholder(player, type);
            parts.add(itemComponent);

            // 继续处理剩余文本
            remaining = remaining.substring(minIndex + placeholder.length());
        }

        // 合并所有部分
        Component result = Component.empty();
        for (Component part : parts) {
            result = result.append(part);
        }
        return result;
    }

    /**
     * 处理单个占位符
     */
    private Component processPlaceholder(Player player, String type) {
        return switch (type) {
            case "hand" -> processHandItem(player);
            case "inventory" -> processInventory(player);
            case "enderchest" -> processEnderchest(player);
            default -> Component.empty();
        };
    }

    /**
     * 处理手持物品展示
     */
    private Component processHandItem(Player player) {
        if (!player.hasPermission(handItemPermission)) {
            return miniMessage.deserialize(handItemEmptyHand);
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return miniMessage.deserialize(handItemEmptyHand);
        }

        // 直接使用物品的显示名称（带方括号），并添加 showItem 悬浮事件
        // 这样会显示物品的完整信息，就像原版聊天展示物品一样
        Component itemComponent = item.displayName()
                .hoverEvent(item.asHoverEvent());

        return itemComponent;
    }

    /**
     * 处理背包展示
     */
    private Component processInventory(Player player) {
        if (!player.hasPermission(inventoryPermission)) {
            return Component.empty();
        }

        // 创建快照
        UUID snapshotId = createInventorySnapshot(player);
        int itemCount = countItems(player.getInventory().getContents());

        String format = inventoryFormat;
        Component component = miniMessage.deserialize(format);

        // 添加悬浮文本
        if (inventoryHover != null && !inventoryHover.isEmpty()) {
            Component hoverText = buildSnapshotHover(player.getName(), itemCount, inventoryHover);
            component = component.hoverEvent(HoverEvent.showText(hoverText));
        }

        // 添加点击事件（运行命令查看快照）
        component = component.clickEvent(ClickEvent.runCommand("/fotiachat viewsnapshot " + snapshotId));

        return component;
    }

    /**
     * 处理末影箱展示
     */
    private Component processEnderchest(Player player) {
        if (!player.hasPermission(enderchestPermission)) {
            return Component.empty();
        }

        // 创建快照
        UUID snapshotId = createEnderchestSnapshot(player);
        int itemCount = countItems(player.getEnderChest().getContents());

        String format = enderchestFormat;
        Component component = miniMessage.deserialize(format);

        // 添加悬浮文本
        if (enderchestHover != null && !enderchestHover.isEmpty()) {
            Component hoverText = buildSnapshotHover(player.getName(), itemCount, enderchestHover);
            component = component.hoverEvent(HoverEvent.showText(hoverText));
        }

        // 添加点击事件（运行命令查看快照）
        component = component.clickEvent(ClickEvent.runCommand("/fotiachat viewsnapshot " + snapshotId));

        return component;
    }

    /**
     * 构建物品悬浮文本
     */
    private Component buildItemHover(ItemStack item, List<String> hoverLines) {
        List<Component> lines = new ArrayList<>();
        Component itemNameComponent = getItemDisplayNameComponent(item);
        String loreText = getItemLore(item);
        Component enchantComponent = getItemEnchantmentsComponent(item);
        boolean hasEnchants = !item.getEnchantments().isEmpty();

        for (String line : hoverLines) {
            // 跳过空的lore和enchantments行
            if (line.contains("{lore}") && loreText.isEmpty()) {
                continue;
            }
            if (line.contains("{enchantments}") && !hasEnchants) {
                continue;
            }

            // 处理 {enchantments} 占位符（使用组件以支持本地化）
            if (line.contains("{enchantments}") && hasEnchants) {
                // 如果行只有 {enchantments}，直接添加附魔组件
                if (line.trim().equals("{enchantments}")) {
                    lines.add(enchantComponent);
                    continue;
                }
            }

            // 处理 {lore} 占位符
            if (line.contains("{lore}") && !loreText.isEmpty()) {
                if (line.trim().equals("{lore}")) {
                    lines.add(miniMessage.deserialize(loreText));
                    continue;
                }
            }

            // 使用 MiniMessage 占位符处理 <item_name> 和其他占位符
            String processed = line
                    .replace("{amount}", String.valueOf(item.getAmount()))
                    .replace("{lore}", loreText)
                    .replace("{enchantments}", "")
                    .replace("{item_name}", "<item_name>"); // 转换为 MiniMessage 格式

            if (!processed.trim().isEmpty()) {
                Component lineComponent = miniMessage.deserialize(processed,
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("item_name", itemNameComponent));
                lines.add(lineComponent);
            }
        }

        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            result = result.append(lines.get(i));
            if (i < lines.size() - 1) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }

    /**
     * 构建快照悬浮文本
     */
    private Component buildSnapshotHover(String playerName, int itemCount, List<String> hoverLines) {
        List<Component> lines = new ArrayList<>();

        for (String line : hoverLines) {
            String processed = line
                    .replace("{player}", playerName)
                    .replace("{item_count}", String.valueOf(itemCount));
            lines.add(miniMessage.deserialize(processed));
        }

        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            result = result.append(lines.get(i));
            if (i < lines.size() - 1) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }

    /**
     * 获取物品显示名称（字符串形式）
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        // 使用物品类型名称
        return formatMaterialName(item.getType());
    }

    /**
     * 获取物品显示名称组件（支持客户端本地化和CraftEngine自定义名称）
     */
    private Component getItemDisplayNameComponent(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 优先检查 displayName（自定义名称）
            if (meta.hasDisplayName()) {
                return meta.displayName();
            }
            // 尝试使用 itemName（1.20.5+ 的物品名称组件）
            try {
                java.lang.reflect.Method hasItemNameMethod = meta.getClass().getMethod("hasItemName");
                boolean hasItemName = (boolean) hasItemNameMethod.invoke(meta);
                if (hasItemName) {
                    java.lang.reflect.Method itemNameMethod = meta.getClass().getMethod("itemName");
                    return (Component) itemNameMethod.invoke(meta);
                }
            } catch (Exception ignored) {
                // 旧版本不支持 itemName，忽略
            }
        }
        // 使用物品类型的翻译键
        return Component.translatable(item.getType().translationKey());
    }

    /**
     * 格式化材质名称
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
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

    /**
     * 获取物品Lore
     */
    private String getItemLore(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return "";
        }

        List<Component> lore = item.getItemMeta().lore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lore.size(); i++) {
            String line = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(lore.get(i));
            sb.append("<!i><gray>").append(line).append("</gray>");
            if (i < lore.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取物品附魔（返回组件以支持本地化）
     */
    private Component getItemEnchantmentsComponent(ItemStack item) {
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty()) {
            return Component.empty();
        }

        Component result = Component.empty();
        int i = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            // 使用附魔的翻译键
            Component enchantName = Component.translatable(entry.getKey().translationKey());
            Component line = Component.text("", net.kyori.adventure.text.format.NamedTextColor.AQUA)
                    .append(enchantName)
                    .append(Component.text(" " + entry.getValue()));

            result = result.append(line);
            if (i < enchants.size() - 1) {
                result = result.append(Component.newline());
            }
            i++;
        }
        return result;
    }

    /**
     * 获取物品附魔（字符串形式，用于不支持组件的地方）
     */
    private String getItemEnchantments(ItemStack item) {
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            String enchantName = formatEnchantmentName(entry.getKey());
            sb.append("<!i><aqua>").append(enchantName).append(" ").append(entry.getValue()).append("</aqua>");
            if (i < enchants.size() - 1) {
                sb.append("\n");
            }
            i++;
        }
        return sb.toString();
    }

    /**
     * 格式化附魔名称
     */
    private String formatEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        // 将下划线分隔的名称转换为首字母大写的格式
        String name = key.toLowerCase().replace("_", " ");
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

    /**
     * 创建背包快照
     */
    private UUID createInventorySnapshot(Player player) {
        UUID id = UUID.randomUUID();
        ItemStack[] contents = player.getInventory().getContents().clone();
        // 深拷贝
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                contents[i] = contents[i].clone();
            }
        }

        ItemSnapshot snapshot = new ItemSnapshot(
                id,
                player.getUniqueId(),
                player.getName(),
                ItemSnapshot.Type.INVENTORY,
                contents,
                System.currentTimeMillis() + snapshotExpireTime * 1000L
        );
        snapshots.put(id, snapshot);
        return id;
    }

    /**
     * 创建末影箱快照
     */
    private UUID createEnderchestSnapshot(Player player) {
        UUID id = UUID.randomUUID();
        ItemStack[] contents = player.getEnderChest().getContents().clone();
        // 深拷贝
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                contents[i] = contents[i].clone();
            }
        }

        ItemSnapshot snapshot = new ItemSnapshot(
                id,
                player.getUniqueId(),
                player.getName(),
                ItemSnapshot.Type.ENDERCHEST,
                contents,
                System.currentTimeMillis() + snapshotExpireTime * 1000L
        );
        snapshots.put(id, snapshot);
        return id;
    }

    /**
     * 获取快照
     */
    public ItemSnapshot getSnapshot(UUID id) {
        ItemSnapshot snapshot = snapshots.get(id);
        if (snapshot != null && snapshot.isExpired()) {
            snapshots.remove(id);
            return null;
        }
        return snapshot;
    }

    /**
     * 打开快照GUI
     */
    public void openSnapshotGui(Player viewer, UUID snapshotId) {
        ItemSnapshot snapshot = getSnapshot(snapshotId);
        if (snapshot == null) {
            plugin.getMessageManager().send(viewer, "item-display.snapshot-expired");
            return;
        }

        // 检查权限
        String viewPermission = snapshot.type() == ItemSnapshot.Type.INVENTORY ?
                inventoryViewPermission : enderchestViewPermission;
        if (!viewer.hasPermission(viewPermission)) {
            plugin.getMessageManager().send(viewer, "item-display.view-no-permission");
            return;
        }

        // 使用GUI管理器打开
        if (snapshot.type() == ItemSnapshot.Type.INVENTORY) {
            guiManager.openInventoryGui(viewer, snapshot);
        } else {
            guiManager.openEnderchestGui(viewer, snapshot);
        }
    }

    /**
     * 统计物品数量
     */
    private int countItems(ItemStack[] contents) {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                count++;
            }
        }
        return count;
    }

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            snapshots.entrySet().removeIf(entry -> entry.getValue().expireTime() < now);
        }, 6000L, 6000L); // 每5分钟清理一次
    }

    /**
     * 检查消息是否包含物品展示占位符
     */
    public boolean containsPlaceholder(String message) {
        if (message == null) return false;
        if (handItemEnabled && handItemPlaceholder != null && message.contains(handItemPlaceholder)) return true;
        if (inventoryEnabled && inventoryPlaceholder != null && message.contains(inventoryPlaceholder)) return true;
        if (enderchestEnabled && enderchestPlaceholder != null && message.contains(enderchestPlaceholder)) return true;
        return false;
    }

    // Getters
    public boolean isHandItemEnabled() { return handItemEnabled; }
    public boolean isInventoryEnabled() { return inventoryEnabled; }
    public boolean isEnderchestEnabled() { return enderchestEnabled; }
    public String getHandItemPlaceholder() { return handItemPlaceholder; }
    public String getInventoryPlaceholder() { return inventoryPlaceholder; }
    public String getEnderchestPlaceholder() { return enderchestPlaceholder; }

    /**
     * 处理消息中的物品展示占位符（跨服版本，转为纯文本）
     * 用于跨服消息传输，将物品展示转为物品名称文本
     */
    public String processMessageForCrossServer(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = message;

        // 处理手持物品占位符
        if (handItemEnabled && handItemPlaceholder != null && result.contains(handItemPlaceholder)) {
            String replacement = getHandItemTextForCrossServer(player);
            result = result.replace(handItemPlaceholder, replacement);
        }

        // 处理背包占位符
        if (inventoryEnabled && inventoryPlaceholder != null && result.contains(inventoryPlaceholder)) {
            String replacement = getInventoryTextForCrossServer(player);
            result = result.replace(inventoryPlaceholder, replacement);
        }

        // 处理末影箱占位符
        if (enderchestEnabled && enderchestPlaceholder != null && result.contains(enderchestPlaceholder)) {
            String replacement = getEnderchestTextForCrossServer(player);
            result = result.replace(enderchestPlaceholder, replacement);
        }

        return result;
    }

    /**
     * 获取手持物品的跨服文本表示
     */
    private String getHandItemTextForCrossServer(Player player) {
        if (!player.hasPermission(handItemPermission)) {
            return PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(handItemEmptyHand));
        }

        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(handItemEmptyHand));
        }

        // 获取物品显示名称
        String itemName = getItemDisplayName(item);
        int amount = item.getAmount();

        // 格式: [物品名称 x数量] 或 [物品名称]
        if (amount > 1) {
            return "<!i><aqua>[" + itemName + " x" + amount + "]</aqua>";
        } else {
            return "<!i><aqua>[" + itemName + "]</aqua>";
        }
    }

    /**
     * 获取背包的跨服文本表示
     */
    private String getInventoryTextForCrossServer(Player player) {
        if (!player.hasPermission(inventoryPermission)) {
            return "";
        }
        // 返回格式化文本（不带点击事件）
        return inventoryFormat;
    }

    /**
     * 获取末影箱的跨服文本表示
     */
    private String getEnderchestTextForCrossServer(Player player) {
        if (!player.hasPermission(enderchestPermission)) {
            return "";
        }
        // 返回格式化文本（不带点击事件）
        return enderchestFormat;
    }
}
