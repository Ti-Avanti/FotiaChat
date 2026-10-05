package gg.fotia.chat.itemdisplay;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.ComponentTextTransformer;
import gg.fotia.chat.util.MessageUtil;
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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 鐗╁搧灞曠ず绠＄悊鍣?
 */
public class ItemDisplayManager {

    // 1.20.5+ 的 ItemMeta#hasItemName/itemName 反射句柄，启动时解析一次并缓存
    private static final java.lang.reflect.Method HAS_ITEM_NAME_METHOD;
    private static final java.lang.reflect.Method ITEM_NAME_METHOD;

    static {
        java.lang.reflect.Method hasItemName = null;
        java.lang.reflect.Method itemName = null;
        try {
            hasItemName = ItemMeta.class.getMethod("hasItemName");
            itemName = ItemMeta.class.getMethod("itemName");
        } catch (NoSuchMethodException ignored) {
            // 旧版本服务端不支持 itemName
        }
        HAS_ITEM_NAME_METHOD = hasItemName;
        ITEM_NAME_METHOD = itemName;
    }

    private final FotiaChat plugin;
    private final MiniMessage miniMessage;
    private final SnapshotStore snapshots = new SnapshotStore(1000, 20);
    private ItemDisplayGuiManager guiManager;
    private FileConfiguration itemDisplayConfig;
    private BukkitTask cleanupTask;

    // 閰嶇疆
    private boolean handItemEnabled;
    private String handItemPlaceholder;
    private String handItemEmptyHand;
    private String handItemEmptyHandKey;
    private String handItemPermission;
    private HandItemDisplayMode handItemDisplayMode = HandItemDisplayMode.NATIVE;
    private String handItemGuiDisplay = "<!i><aqua>[{item_name}]</aqua>";
    private String handItemGuiDisplayKey;
    private List<String> handItemGuiHover = List.of();
    private List<String> handItemGuiHoverKeys = List.of();

    private boolean inventoryEnabled;
    private String inventoryPlaceholder;
    private String inventoryFormat;
    private String inventoryFormatKey;
    private List<String> inventoryHover;
    private List<String> inventoryHoverKeys = List.of();
    private String inventoryPermission;
    private String inventoryViewPermission;

    private boolean enderchestEnabled;
    private String enderchestPlaceholder;
    private String enderchestFormat;
    private String enderchestFormatKey;
    private List<String> enderchestHover;
    private List<String> enderchestHoverKeys = List.of();
    private String enderchestPermission;
    private String enderchestViewPermission;

    private int snapshotExpireTime;
    private int snapshotCleanupInterval;

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
        snapshotCleanupInterval = Math.max(1, itemDisplayConfig.getInt("snapshot-cleanup-interval-seconds", 60));
        snapshots.configure(
                itemDisplayConfig.getInt("snapshot-max-total", 1000),
                itemDisplayConfig.getInt("snapshot-max-per-player", 20),
                System.currentTimeMillis()
        );

        // 鎵嬫寔鐗╁搧閰嶇疆
        ConfigurationSection handConfig = itemDisplayConfig.getConfigurationSection("hand-item");
        if (handConfig != null) {
            handItemEnabled = handConfig.getBoolean("enabled", true);
            handItemPlaceholder = handConfig.getString("placeholder", "[i]");
            handItemEmptyHand = handConfig.getString("empty-hand", "<!i><gray>[空手]</gray>");
            handItemEmptyHandKey = handConfig.getString("empty-hand-key", "");
            handItemPermission = handConfig.getString("permission", "fotiachat.item.hand");
            handItemDisplayMode = HandItemDisplayMode.fromId(handConfig.getString("display-mode", "NATIVE"));
            handItemGuiDisplay = handConfig.getString("gui-display", "<!i><aqua>[{item_name}]</aqua>");
            handItemGuiDisplayKey = handConfig.getString("gui-display-key", "");
            handItemGuiHover = handConfig.getStringList("gui-hover");
            handItemGuiHoverKeys = handConfig.getStringList("gui-hover-keys");
        }

        // 鑳屽寘閰嶇疆
        ConfigurationSection invConfig = itemDisplayConfig.getConfigurationSection("inventory");
        if (invConfig != null) {
            inventoryEnabled = invConfig.getBoolean("enabled", true);
            inventoryPlaceholder = invConfig.getString("placeholder", "[inv]");
            inventoryFormat = invConfig.getString("format", "<!i><gold>[查看背包]</gold>");
            inventoryFormatKey = invConfig.getString("format-key", "");
            inventoryHover = invConfig.getStringList("hover");
            inventoryHoverKeys = invConfig.getStringList("hover-keys");
            inventoryPermission = invConfig.getString("permission", "fotiachat.item.inventory");
            inventoryViewPermission = invConfig.getString("view-permission", "fotiachat.item.inventory.view");
        }

        // 鏈奖绠遍厤缃?
        ConfigurationSection ecConfig = itemDisplayConfig.getConfigurationSection("enderchest");
        if (ecConfig != null) {
            enderchestEnabled = ecConfig.getBoolean("enabled", true);
            enderchestPlaceholder = ecConfig.getString("placeholder", "[ec]");
            enderchestFormat = ecConfig.getString("format", "<!i><dark_purple>[查看末影箱]</dark_purple>");
            enderchestFormatKey = ecConfig.getString("format-key", "");
            enderchestHover = ecConfig.getStringList("hover");
            enderchestHoverKeys = ecConfig.getStringList("hover-keys");
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
            return Component.empty();
        }

        List<Component> parts = new ArrayList<>();
        Map<String, Component> resolvedPlaceholders = new HashMap<>();
        String remaining = message;

        while (!remaining.isEmpty()) {
            int handIndex = handItemEnabled && handItemPlaceholder != null && !handItemPlaceholder.isEmpty() ?
                    remaining.indexOf(handItemPlaceholder) : -1;
            int invIndex = inventoryEnabled && inventoryPlaceholder != null && !inventoryPlaceholder.isEmpty() ?
                    remaining.indexOf(inventoryPlaceholder) : -1;
            int ecIndex = enderchestEnabled && enderchestPlaceholder != null && !enderchestPlaceholder.isEmpty() ?
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
            Component itemComponent = resolvedPlaceholders.computeIfAbsent(type,
                    key -> processPlaceholder(player, key));
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
     * 在不扁平化原组件的情况下替换物品占位符。
     */
    public Component processComponent(Player player, Component message) {
        if (message == null) {
            return Component.empty();
        }
        String plainText = PlainTextComponentSerializer.plainText().serialize(message);
        Component result = message;
        if (handItemEnabled && handItemPlaceholder != null && !handItemPlaceholder.isEmpty()
                && plainText.contains(handItemPlaceholder)) {
            result = ComponentTextTransformer.replaceLiteral(
                    result,
                    handItemPlaceholder,
                    processPlaceholder(player, "hand")
            );
        }
        if (inventoryEnabled && inventoryPlaceholder != null && !inventoryPlaceholder.isEmpty()
                && plainText.contains(inventoryPlaceholder)) {
            result = ComponentTextTransformer.replaceLiteral(
                    result,
                    inventoryPlaceholder,
                    processPlaceholder(player, "inventory")
            );
        }
        if (enderchestEnabled && enderchestPlaceholder != null && !enderchestPlaceholder.isEmpty()
                && plainText.contains(enderchestPlaceholder)) {
            result = ComponentTextTransformer.replaceLiteral(
                    result,
                    enderchestPlaceholder,
                    processPlaceholder(player, "enderchest")
            );
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
            return MessageUtil.parseConfigured(localized(player, handItemEmptyHandKey, handItemEmptyHand));
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return MessageUtil.parseConfigured(localized(player, handItemEmptyHandKey, handItemEmptyHand));
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
        ItemSnapshot snapshot = createHandItemSnapshot(player, item);
        Component component = buildHandItemDisplayComponent(player, item);

        List<String> hoverLines = localizedList(player, handItemGuiHoverKeys, handItemGuiHover);
        if (!hoverLines.isEmpty()) {
            component = component.hoverEvent(HoverEvent.showText(buildItemHover(item, hoverLines)));
        }

        return component.clickEvent(ClickEvent.runCommand("/fotiachat viewsnapshot " + snapshot.id()));
    }

    private Component buildHandItemDisplayComponent(Player player, ItemStack item) {
        String localizedFormat = localized(player, handItemGuiDisplayKey, handItemGuiDisplay);
        String safeFormat = localizedFormat == null || localizedFormat.isEmpty()
                ? "<!i><aqua>[{item_name}]</aqua>"
                : localizedFormat;

        String processed = safeFormat
                .replace("{amount}", String.valueOf(item.getAmount()))
                .replace("{item_name}", "<item_name>");

        return MessageUtil.parseConfigured(
                processed,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(
                        "item_name",
                        getItemDisplayNameComponent(item)
                )
        );
    }

    private String buildHandItemDisplayText(Player player, ItemStack item) {
        String localizedFormat = localized(player, handItemGuiDisplayKey, handItemGuiDisplay);
        String safeFormat = localizedFormat == null || localizedFormat.isEmpty()
                ? "<!i><aqua>[{item_name}]</aqua>"
                : localizedFormat;

        return safeFormat.replace("{item_name}", getItemDisplayName(item))
                .replace("{amount}", String.valueOf(item.getAmount()));
    }
    private Component processInventory(Player player) {
        if (!player.hasPermission(inventoryPermission)) {
            return Component.empty();
        }

        // 鍒涘缓蹇収
        ItemSnapshot snapshot = createInventorySnapshot(player);
        int itemCount = countItems(snapshot.contents());

        String format = localized(player, inventoryFormatKey, inventoryFormat);
        Component component = MessageUtil.parseConfigured(format);

        // 娣诲姞鎮诞鏂囨湰
        List<String> hoverLines = localizedList(player, inventoryHoverKeys, inventoryHover);
        if (!hoverLines.isEmpty()) {
            Component hoverText = buildSnapshotHover(gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", player), itemCount, hoverLines);
            component = component.hoverEvent(HoverEvent.showText(hoverText));
        }

        // 娣诲姞鐐瑰嚮浜嬩欢锛堣繍琛屽懡浠ゆ煡鐪嬪揩鐓э級
        component = component.clickEvent(ClickEvent.runCommand("/fotiachat viewsnapshot " + snapshot.id()));

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
        ItemSnapshot snapshot = createEnderchestSnapshot(player);
        int itemCount = countItems(snapshot.contents());

        String format = localized(player, enderchestFormatKey, enderchestFormat);
        Component component = MessageUtil.parseConfigured(format);

        // 娣诲姞鎮诞鏂囨湰
        List<String> hoverLines = localizedList(player, enderchestHoverKeys, enderchestHover);
        if (!hoverLines.isEmpty()) {
            Component hoverText = buildSnapshotHover(gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", player), itemCount, hoverLines);
            component = component.hoverEvent(HoverEvent.showText(hoverText));
        }

        // 娣诲姞鐐瑰嚮浜嬩欢锛堣繍琛屽懡浠ゆ煡鐪嬪揩鐓э級
        component = component.clickEvent(ClickEvent.runCommand("/fotiachat viewsnapshot " + snapshot.id()));

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
                Component lineComponent = MessageUtil.parseConfigured(processed,
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
            lines.add(MessageUtil.parseConfigured(processed));
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
                if (HAS_ITEM_NAME_METHOD != null && ITEM_NAME_METHOD != null
                        && (boolean) HAS_ITEM_NAME_METHOD.invoke(meta)) {
                    return (Component) ITEM_NAME_METHOD.invoke(meta);
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
    private ItemSnapshot createHandItemSnapshot(Player player, ItemStack item) {
        UUID id = UUID.randomUUID();
        ItemStack[] contents = new ItemStack[]{item.clone()};

        ItemSnapshot snapshot = new ItemSnapshot(
                id,
                player.getUniqueId(),
                gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", player),
                ItemSnapshot.Type.HAND_ITEM,
                contents,
                System.currentTimeMillis() + snapshotExpireTime * 1000L
        );
        snapshots.put(snapshot, System.currentTimeMillis());
        return snapshot;
    }

    private ItemSnapshot createInventorySnapshot(Player player) {
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
                gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", player),
                ItemSnapshot.Type.INVENTORY,
                contents,
                System.currentTimeMillis() + snapshotExpireTime * 1000L
        );
        snapshots.put(snapshot, System.currentTimeMillis());
        return snapshot;
    }

    /**
     * 鍒涘缓鏈奖绠卞揩鐓?
     */
    private ItemSnapshot createEnderchestSnapshot(Player player) {
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
                gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", player),
                ItemSnapshot.Type.ENDERCHEST,
                contents,
                System.currentTimeMillis() + snapshotExpireTime * 1000L
        );
        snapshots.put(snapshot, System.currentTimeMillis());
        return snapshot;
    }

    /**
     * 鑾峰彇蹇収
     */
    public ItemSnapshot getSnapshot(UUID id) {
        return snapshots.get(id, System.currentTimeMillis());
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

    private String localized(Player player, String key, String fallback) {
        return key == null || key.isBlank() ? fallback : plugin.getMessageManager().getRaw(player, key);
    }

    private List<String> localizedList(Player player, List<String> keys, List<String> fallback) {
        if (keys == null || keys.isEmpty()) {
            return fallback == null ? List.of() : fallback;
        }
        return keys.stream()
                .filter(key -> key != null && !key.isBlank())
                .map(key -> plugin.getMessageManager().getRaw(player, key))
                .toList();
    }

    /**
     * 鍚姩娓呯悊浠诲姟
     */
    private void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        long intervalTicks = snapshotCleanupInterval * 20L;
        cleanupTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> snapshots.cleanup(System.currentTimeMillis()),
                intervalTicks,
                intervalTicks
        );
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        snapshots.clear();
    }

    /**
     * 妫€鏌ユ秷鎭槸鍚﹀寘鍚墿鍝佸睍绀哄崰浣嶇
     */
    public boolean containsPlaceholder(String message) {
        if (message == null) return false;
        if (handItemEnabled && handItemPlaceholder != null && !handItemPlaceholder.isEmpty()
                && message.contains(handItemPlaceholder)) return true;
        if (inventoryEnabled && inventoryPlaceholder != null && !inventoryPlaceholder.isEmpty()
                && message.contains(inventoryPlaceholder)) return true;
        if (enderchestEnabled && enderchestPlaceholder != null && !enderchestPlaceholder.isEmpty()
                && message.contains(enderchestPlaceholder)) return true;
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
            return PlainTextComponentSerializer.plainText().serialize(MessageUtil.parseConfigured(
                    localized(player, handItemEmptyHandKey, handItemEmptyHand)));
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return PlainTextComponentSerializer.plainText().serialize(MessageUtil.parseConfigured(
                    localized(player, handItemEmptyHandKey, handItemEmptyHand)));
        }

        if (handItemDisplayMode == HandItemDisplayMode.GUI) {
            return buildHandItemDisplayText(player, item);
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
        return localized(player, inventoryFormatKey, inventoryFormat);
    }

    /**
     * 鑾峰彇鏈奖绠辩殑璺ㄦ湇鏂囨湰琛ㄧず
     */
    private String getEnderchestTextForCrossServer(Player player) {
        if (!player.hasPermission(enderchestPermission)) {
            return "";
        }
        // 杩斿洖鏍煎紡鍖栨枃鏈紙涓嶅甫鐐瑰嚮浜嬩欢锛?
        return localized(player, enderchestFormatKey, enderchestFormat);
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
