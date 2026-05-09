package gg.fotia.chat.itemdisplay;

import gg.fotia.chat.FotiaChat;
import net.kyori.adventure.text.Component;
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
 * 鐗╁搧灞曠ず绠＄悊鍣?
 */
public class ItemDisplayManager {

    private final FotiaChat plugin;
    private final MiniMessage miniMessage;
    private final Map<UUID, ItemSnapshot> snapshots = new ConcurrentHashMap<>();
    private ItemDisplayGuiManager guiManager;
    private FileConfiguration itemDisplayConfig;

    // 閰嶇疆
    private boolean handItemEnabled;
    private String handItemPlaceholder;
    private String handItemEmptyHand;
    private String handItemPermission;
    private HandItemDisplayMode handItemDisplayMode = HandItemDisplayMode.NATIVE;
    private String handItemGuiDisplay = "<!i><aqua>[{item_name}]</aqua>";
    private List<String> handItemGuiHover = List.of();

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
     * 鍔犺浇閰嶇疆
     */
    public void load() {
        saveDefaultConfig();

        File configFile = new File(plugin.getDataFolder(), "menus/item-display.yml");
        itemDisplayConfig = YamlConfiguration.loadConfiguration(configFile);

        // 鍚堝苟榛樿閰嶇疆
        InputStream defaultStream = plugin.getResource("menus/item-display.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            itemDisplayConfig.setDefaults(defaultConfig);
        }

        // 蹇収杩囨湡鏃堕棿锛堢锛?
        snapshotExpireTime = itemDisplayConfig.getInt("snapshot-expire-time", 300);

        // 鎵嬫寔鐗╁搧閰嶇疆
        ConfigurationSection handConfig = itemDisplayConfig.getConfigurationSection("hand-item");
        if (handConfig != null) {
            handItemEnabled = handConfig.getBoolean("enabled", true);
            handItemPlaceholder = handConfig.getString("placeholder", "[i]");
            handItemEmptyHand = handConfig.getString("empty-hand", "<!i><gray>[绌篯</gray>");
            handItemPermission = handConfig.getString("permission", "fotiachat.item.hand");
            handItemDisplayMode = HandItemDisplayMode.fromId(handConfig.getString("display-mode", "NATIVE"));
            handItemGuiDisplay = handConfig.getString("gui-display", "<!i><aqua>[{item_name}]</aqua>");
            handItemGuiHover = handConfig.getStringList("gui-hover");
        }

        // 鑳屽寘閰嶇疆
        ConfigurationSection invConfig = itemDisplayConfig.getConfigurationSection("inventory");
        if (invConfig != null) {
            inventoryEnabled = invConfig.getBoolean("enabled", true);
            inventoryPlaceholder = invConfig.getString("placeholder", "[inv]");
            inventoryFormat = invConfig.getString("format", "<!i><gold>[鏌ョ湅鑳屽寘]</gold>");
            inventoryHover = invConfig.getStringList("hover");
            inventoryPermission = invConfig.getString("permission", "fotiachat.item.inventory");
            inventoryViewPermission = invConfig.getString("view-permission", "fotiachat.item.inventory.view");
        }

        // 鏈奖绠遍厤缃?
        ConfigurationSection ecConfig = itemDisplayConfig.getConfigurationSection("enderchest");
        if (ecConfig != null) {
            enderchestEnabled = ecConfig.getBoolean("enabled", true);
            enderchestPlaceholder = ecConfig.getString("placeholder", "[ec]");
            enderchestFormat = ecConfig.getString("format", "<!i><dark_purple>[鏌ョ湅鏈奖绠盷</dark_purple>");
            enderchestHover = ecConfig.getStringList("hover");
            enderchestPermission = ecConfig.getString("permission", "fotiachat.item.enderchest");
            enderchestViewPermission = ecConfig.getString("view-permission", "fotiachat.item.enderchest.view");
        }

        // 鍚姩娓呯悊浠诲姟
        startCleanupTask();

        // 鍔犺浇GUI閰嶇疆
        guiManager.load(itemDisplayConfig);
    }

    /**
     * 淇濆瓨榛樿閰嶇疆鏂囦欢
     */
    private void saveDefaultConfig() {
        File configFile = new File(plugin.getDataFolder(), "menus/item-display.yml");
        if (!configFile.exists()) {
            plugin.saveResource("menus/item-display.yml", false);
        }
    }

    /**
     * 澶勭悊娑堟伅涓殑鐗╁搧灞曠ず鍗犱綅绗?
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

            // 鎵惧埌鏈€杩戠殑鍗犱綅绗?
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
                // 娌℃湁鏇村鍗犱綅绗?
                parts.add(miniMessage.deserialize(remaining));
                break;
            }

            // 娣诲姞鍗犱綅绗︿箣鍓嶇殑鏂囨湰
            if (minIndex > 0) {
                parts.add(miniMessage.deserialize(remaining.substring(0, minIndex)));
            }

            // 澶勭悊鍗犱綅绗?
            Component itemComponent = processPlaceholder(player, type);
            parts.add(itemComponent);

            // 缁х画澶勭悊鍓╀綑鏂囨湰
            remaining = remaining.substring(minIndex + placeholder.length());
        }

        // 鍚堝苟鎵€鏈夐儴鍒?
        Component result = Component.empty();
        for (Component part : parts) {
            result = result.append(part);
        }
        return result;
    }

    /**
     * 澶勭悊鍗曚釜鍗犱綅绗?
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
     * 澶勭悊鎵嬫寔鐗╁搧灞曠ず
     */
    private Component processHandItem(Player player) {
        if (!player.hasPermission(handItemPermission)) {
            return miniMessage.deserialize(handItemEmptyHand);
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return miniMessage.deserialize(handItemEmptyHand);
        }

        if (handItemDisplayMode == HandItemDisplayMode.GUI) {
            return processHandItemGui(player, item);
        }

        return processHandItemNative(item);
    }

    /**
     * 处理原生手持物品展示
     */
    private Component processHandItemNative(ItemStack item) {
        return item.displayName().hoverEvent(item.asHoverEvent());
    }

    /**
     * 处理 GUI 手持物品展示
     */
    private Component processHandItemGui(Player player, ItemStack item) {
        UUID snapshotId = createHandItemSnapshot(player, item);
        Component component = buildHandItemDisplayComponent(item);

        if (handItemGuiHover != null && !handItemGuiHover.isEmpty()) {
            component = component.hoverEvent(HoverEvent.showText(buildItemHover(item, handItemGuiHover)));
        }

        return component.clickEvent(ClickEvent.runCommand("/fotiachat viewsnapshot " + snapshotId));
    }

    private Component buildHandItemDisplayComponent(ItemStack item) {
        String safeFormat = handItemGuiDisplay == null || handItemGuiDisplay.isEmpty()
                ? "<!i><aqua>[{item_name}]</aqua>"
                : handItemGuiDisplay;

        String processed = safeFormat
                .replace("{amount}", String.valueOf(item.getAmount()))
                .replace("{item_name}", "<item_name>");

        return miniMessage.deserialize(
                processed,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(
                        "item_name",
                        getItemDisplayNameComponent(item)
                )
        );
    }

    private String buildHandItemDisplayText(ItemStack item) {
        String safeFormat = handItemGuiDisplay == null || handItemGuiDisplay.isEmpty()
                ? "<!i><aqua>[{item_name}]</aqua>"
                : handItemGuiDisplay;

        return safeFormat.replace("{item_name}", getItemDisplayName(item))
                .replace("{amount}", String.valueOf(item.getAmount()));
    }
    private Component processInventory(Player player) {
        if (!player.hasPermission(inventoryPermission)) {
            return Component.empty();
        }

        // 鍒涘缓蹇収
        UUID snapshotId = createInventorySnapshot(player);
        int itemCount = countItems(player.getInventory().getContents());

        String format = inventoryFormat;
        Component component = miniMessage.deserialize(format);

        // 娣诲姞鎮诞鏂囨湰
        if (inventoryHover != null && !inventoryHover.isEmpty()) {
            Component hoverText = buildSnapshotHover(player.getName(), itemCount, inventoryHover);
            component = component.hoverEvent(HoverEvent.showText(hoverText));
        }

        // 娣诲姞鐐瑰嚮浜嬩欢锛堣繍琛屽懡浠ゆ煡鐪嬪揩鐓э級
        component = component.clickEvent(ClickEvent.runCommand("/fotiachat viewsnapshot " + snapshotId));

        return component;
    }

    /**
     * 澶勭悊鏈奖绠卞睍绀?
     */
    private Component processEnderchest(Player player) {
        if (!player.hasPermission(enderchestPermission)) {
            return Component.empty();
        }

        // 鍒涘缓蹇収
        UUID snapshotId = createEnderchestSnapshot(player);
        int itemCount = countItems(player.getEnderChest().getContents());

        String format = enderchestFormat;
        Component component = miniMessage.deserialize(format);

        // 娣诲姞鎮诞鏂囨湰
        if (enderchestHover != null && !enderchestHover.isEmpty()) {
            Component hoverText = buildSnapshotHover(player.getName(), itemCount, enderchestHover);
            component = component.hoverEvent(HoverEvent.showText(hoverText));
        }

        // 娣诲姞鐐瑰嚮浜嬩欢锛堣繍琛屽懡浠ゆ煡鐪嬪揩鐓э級
        component = component.clickEvent(ClickEvent.runCommand("/fotiachat viewsnapshot " + snapshotId));

        return component;
    }

    /**
     * 鏋勫缓鐗╁搧鎮诞鏂囨湰
     */
    private Component buildItemHover(ItemStack item, List<String> hoverLines) {
        List<Component> lines = new ArrayList<>();
        Component itemNameComponent = getItemDisplayNameComponent(item);
        String loreText = getItemLore(item);
        Component enchantComponent = getItemEnchantmentsComponent(item);
        boolean hasEnchants = !item.getEnchantments().isEmpty();

        for (String line : hoverLines) {
            // 璺宠繃绌虹殑lore鍜宔nchantments琛?
            if (line.contains("{lore}") && loreText.isEmpty()) {
                continue;
            }
            if (line.contains("{enchantments}") && !hasEnchants) {
                continue;
            }

            // 澶勭悊 {enchantments} 鍗犱綅绗︼紙浣跨敤缁勪欢浠ユ敮鎸佹湰鍦板寲锛?
            if (line.contains("{enchantments}") && hasEnchants) {
                // 濡傛灉琛屽彧鏈?{enchantments}锛岀洿鎺ユ坊鍔犻檮榄旂粍浠?
                if (line.trim().equals("{enchantments}")) {
                    lines.add(enchantComponent);
                    continue;
                }
            }

            // 澶勭悊 {lore} 鍗犱綅绗?
            if (line.contains("{lore}") && !loreText.isEmpty()) {
                if (line.trim().equals("{lore}")) {
                    lines.add(miniMessage.deserialize(loreText));
                    continue;
                }
            }

            // 浣跨敤 MiniMessage 鍗犱綅绗﹀鐞?<item_name> 鍜屽叾浠栧崰浣嶇
            String processed = line
                    .replace("{amount}", String.valueOf(item.getAmount()))
                    .replace("{lore}", loreText)
                    .replace("{enchantments}", "")
                    .replace("{item_name}", "<item_name>"); // 杞崲涓?MiniMessage 鏍煎紡

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
     * 鏋勫缓蹇収鎮诞鏂囨湰
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
     * 鑾峰彇鐗╁搧鏄剧ず鍚嶇О锛堝瓧绗︿覆褰㈠紡锛?
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        // 浣跨敤鐗╁搧绫诲瀷鍚嶇О
        return formatMaterialName(item.getType());
    }

    /**
     * 鑾峰彇鐗╁搧鏄剧ず鍚嶇О缁勪欢锛堟敮鎸佸鎴风鏈湴鍖栧拰CraftEngine鑷畾涔夊悕绉帮級
     */
    private Component getItemDisplayNameComponent(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 浼樺厛妫€鏌?displayName锛堣嚜瀹氫箟鍚嶇О锛?
            if (meta.hasDisplayName()) {
                return meta.displayName();
            }
            // 灏濊瘯浣跨敤 itemName锛?.20.5+ 鐨勭墿鍝佸悕绉扮粍浠讹級
            try {
                java.lang.reflect.Method hasItemNameMethod = meta.getClass().getMethod("hasItemName");
                boolean hasItemName = (boolean) hasItemNameMethod.invoke(meta);
                if (hasItemName) {
                    java.lang.reflect.Method itemNameMethod = meta.getClass().getMethod("itemName");
                    return (Component) itemNameMethod.invoke(meta);
                }
            } catch (Exception ignored) {
                // 鏃х増鏈笉鏀寔 itemName锛屽拷鐣?
            }
        }
        // 浣跨敤鐗╁搧绫诲瀷鐨勭炕璇戦敭
        return Component.translatable(item.getType().translationKey());
    }

    /**
     * 鏍煎紡鍖栨潗璐ㄥ悕绉?
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
     * 鑾峰彇鐗╁搧Lore
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
     * 鑾峰彇鐗╁搧闄勯瓟锛堣繑鍥炵粍浠朵互鏀寔鏈湴鍖栵級
     */
    private Component getItemEnchantmentsComponent(ItemStack item) {
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty()) {
            return Component.empty();
        }

        Component result = Component.empty();
        int i = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            // 浣跨敤闄勯瓟鐨勭炕璇戦敭
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
     * 鑾峰彇鐗╁搧闄勯瓟锛堝瓧绗︿覆褰㈠紡锛岀敤浜庝笉鏀寔缁勪欢鐨勫湴鏂癸級
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
     * 鏍煎紡鍖栭檮榄斿悕绉?
     */
    private String formatEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        // 灏嗕笅鍒掔嚎鍒嗛殧鐨勫悕绉拌浆鎹负棣栧瓧姣嶅ぇ鍐欑殑鏍煎紡
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
     * 鍒涘缓鑳屽寘蹇収
     */
    /**
     * 创建手持物品快照
     */
    private UUID createHandItemSnapshot(Player player, ItemStack item) {
        UUID id = UUID.randomUUID();
        ItemStack[] contents = new ItemStack[]{item.clone()};

        ItemSnapshot snapshot = new ItemSnapshot(
                id,
                player.getUniqueId(),
                player.getName(),
                ItemSnapshot.Type.HAND_ITEM,
                contents,
                System.currentTimeMillis() + snapshotExpireTime * 1000L
        );
        snapshots.put(id, snapshot);
        return id;
    }
    private UUID createInventorySnapshot(Player player) {
        UUID id = UUID.randomUUID();
        ItemStack[] contents = player.getInventory().getContents().clone();
        // 娣辨嫹璐?
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
     * 鍒涘缓鏈奖绠卞揩鐓?
     */
    private UUID createEnderchestSnapshot(Player player) {
        UUID id = UUID.randomUUID();
        ItemStack[] contents = player.getEnderChest().getContents().clone();
        // 娣辨嫹璐?
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
     * 鑾峰彇蹇収
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
     * 鎵撳紑蹇収GUI
     */
    public void openSnapshotGui(Player viewer, UUID snapshotId) {
        ItemSnapshot snapshot = getSnapshot(snapshotId);
        if (snapshot == null) {
            plugin.getMessageManager().send(viewer, "item-display.snapshot-expired");
            return;
        }

        switch (snapshot.type()) {
            case HAND_ITEM -> guiManager.openHandItemGui(viewer, snapshot);
            case INVENTORY -> {
                if (!viewer.hasPermission(inventoryViewPermission)) {
                    plugin.getMessageManager().send(viewer, "item-display.view-no-permission");
                    return;
                }
                guiManager.openInventoryGui(viewer, snapshot);
            }
            case ENDERCHEST -> {
                if (!viewer.hasPermission(enderchestViewPermission)) {
                    plugin.getMessageManager().send(viewer, "item-display.view-no-permission");
                    return;
                }
                guiManager.openEnderchestGui(viewer, snapshot);
            }
        }
    }
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
     * 鍚姩娓呯悊浠诲姟
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            snapshots.entrySet().removeIf(entry -> entry.getValue().expireTime() < now);
        }, 6000L, 6000L); // 姣?鍒嗛挓娓呯悊涓€娆?
    }

    /**
     * 妫€鏌ユ秷鎭槸鍚﹀寘鍚墿鍝佸睍绀哄崰浣嶇
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
     * 澶勭悊娑堟伅涓殑鐗╁搧灞曠ず鍗犱綅绗︼紙璺ㄦ湇鐗堟湰锛岃浆涓虹函鏂囨湰锛?
     * 鐢ㄤ簬璺ㄦ湇娑堟伅浼犺緭锛屽皢鐗╁搧灞曠ず杞负鐗╁搧鍚嶇О鏂囨湰
     */
    public String processMessageForCrossServer(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = message;

        // 澶勭悊鎵嬫寔鐗╁搧鍗犱綅绗?
        if (handItemEnabled && handItemPlaceholder != null && result.contains(handItemPlaceholder)) {
            String replacement = getHandItemTextForCrossServer(player);
            result = result.replace(handItemPlaceholder, replacement);
        }

        // 澶勭悊鑳屽寘鍗犱綅绗?
        if (inventoryEnabled && inventoryPlaceholder != null && result.contains(inventoryPlaceholder)) {
            String replacement = getInventoryTextForCrossServer(player);
            result = result.replace(inventoryPlaceholder, replacement);
        }

        // 澶勭悊鏈奖绠卞崰浣嶇
        if (enderchestEnabled && enderchestPlaceholder != null && result.contains(enderchestPlaceholder)) {
            String replacement = getEnderchestTextForCrossServer(player);
            result = result.replace(enderchestPlaceholder, replacement);
        }

        return result;
    }

    /**
     * 鑾峰彇鎵嬫寔鐗╁搧鐨勮法鏈嶆枃鏈〃绀?
     */
    private String getHandItemTextForCrossServer(Player player) {
        if (!player.hasPermission(handItemPermission)) {
            return PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(handItemEmptyHand));
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(handItemEmptyHand));
        }

        if (handItemDisplayMode == HandItemDisplayMode.GUI) {
            return buildHandItemDisplayText(item);
        }

        String itemName = getItemDisplayName(item);
        int amount = item.getAmount();

        if (amount > 1) {
            return "<!i><aqua>[" + itemName + " x" + amount + "]</aqua>";
        } else {
            return "<!i><aqua>[" + itemName + "]</aqua>";
        }
    }
    private String getInventoryTextForCrossServer(Player player) {
        if (!player.hasPermission(inventoryPermission)) {
            return "";
        }
        // 杩斿洖鏍煎紡鍖栨枃鏈紙涓嶅甫鐐瑰嚮浜嬩欢锛?
        return inventoryFormat;
    }

    /**
     * 鑾峰彇鏈奖绠辩殑璺ㄦ湇鏂囨湰琛ㄧず
     */
    private String getEnderchestTextForCrossServer(Player player) {
        if (!player.hasPermission(enderchestPermission)) {
            return "";
        }
        // 杩斿洖鏍煎紡鍖栨枃鏈紙涓嶅甫鐐瑰嚮浜嬩欢锛?
        return enderchestFormat;
    }

    private enum HandItemDisplayMode {
        NATIVE,
        GUI;

        private static HandItemDisplayMode fromId(String id) {
            if (id == null) {
                return NATIVE;
            }
            for (HandItemDisplayMode mode : values()) {
                if (mode.name().equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return NATIVE;
        }
    }
}
