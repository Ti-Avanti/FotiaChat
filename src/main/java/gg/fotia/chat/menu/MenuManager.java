package gg.fotia.chat.menu;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单管理器
 */
public class MenuManager {

    private final FotiaChat plugin;
    private final Map<String, Menu> menus = new HashMap<>();

    public MenuManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        menus.clear();
        saveDefaultMenus();

        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) {
            menusDir.mkdirs();
        }

        File[] menuFiles = menusDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (menuFiles != null) {
            for (File file : menuFiles) {
                String menuId = file.getName().replace(".yml", "");
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                Menu menu = new Menu(menuId, config);
                menus.put(menuId, menu);
            }
        }

        plugin.getLogger().info("已加载 " + menus.size() + " 个菜单");
    }

    private void saveDefaultMenus() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) {
            menusDir.mkdirs();
        }

        File colorMenuFile = new File(menusDir, "color-menu.yml");
        if (!colorMenuFile.exists()) {
            plugin.saveResource("menus/color-menu.yml", false);
        }
    }

    /**
     * 获取菜单
     */
    public Menu getMenu(String id) {
        return menus.get(id);
    }

    /**
     * 打开菜单
     */
    public boolean openMenu(Player player, String menuId) {
        Menu menu = menus.get(menuId);
        if (menu == null) {
            return false;
        }
        menu.open(player);
        return true;
    }

    /**
     * 执行菜单动作
     */
    public void executeActions(Player player, List<String> actions) {
        for (String action : actions) {
            executeAction(player, action);
        }
    }

    /**
     * 执行单个动作
     */
    public void executeAction(Player player, String action) {
        if (action == null || action.isEmpty()) {
            return;
        }

        String[] parts = action.split(":", 2);
        String type = parts[0].trim().toLowerCase();
        String value = parts.length > 1 ? parts[1].trim() : "";

        switch (type) {
            case "close" -> {
                // 延迟1tick关闭，避免在事件处理中直接关闭导致问题
                plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            }

            case "sound" -> {
                // 格式: sound: SOUND_NAME-volume-pitch
                String[] soundParts = value.split("-");
                try {
                    Sound sound = Sound.valueOf(soundParts[0].toUpperCase());
                    float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                    float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception ignored) {
                }
            }

            case "command", "cmd" -> {
                // 玩家执行命令
                String cmd = parsePlaceholders(value, player);
                player.performCommand(cmd);
            }

            case "console", "console_command" -> {
                // 控制台执行命令
                String cmd = parsePlaceholders(value, player);
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
            }

            case "message", "msg" -> {
                // 发送消息
                String msg = parsePlaceholders(value, player);
                player.sendMessage(MessageUtil.parse(msg));
            }

            case "open_menu", "menu" -> {
                // 打开另一个菜单
                openMenu(player, value);
            }

            case "set_color", "color" -> {
                // 设置聊天颜色
                plugin.getColorManager().setPlayerColor(player, value);
                Map<String, String> placeholders = Map.of("color",
                        plugin.getColorManager().getColor(value) != null
                                ? plugin.getColorManager().getColor(value).getName() : value);
                plugin.getMessageManager().send(player, "color.set-success", placeholders);
            }

            case "switch_channel", "channel" -> {
                // 切换频道
                plugin.getChannelManager().setPlayerChannel(player, value);
            }

            case "refresh" -> {
                // 刷新当前菜单
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu) {
                    menu.open(player);
                }
            }
        }
    }

    private String parsePlaceholders(String text, Player player) {
        if (player == null) return text;

        text = text.replace("{player}", player.getName());

        if (MessageUtil.isPlaceholderAPIEnabled()) {
            text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }

        return text;
    }

    public Map<String, Menu> getMenus() {
        return menus;
    }
}
