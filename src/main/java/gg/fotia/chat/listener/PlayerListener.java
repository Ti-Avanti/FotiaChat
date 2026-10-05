package gg.fotia.chat.listener;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.storage.DatabaseManager;
import gg.fotia.chat.storage.PlayerDataLoadResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家监听器
 */
public class PlayerListener implements Listener {

    private final FotiaChat plugin;

    public PlayerListener(FotiaChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.getUpdateChecker() != null) {
                plugin.getUpdateChecker().notifyPlayerIfUpdateAvailable(player);
            }
        }, 40L);

        // 从数据库加载玩家数据
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            // 在事件线程先取好默认频道，避免异步窗口撞上 reload 导致 NPE
            gg.fotia.chat.channel.Channel defaultChannel = plugin.getChannelManager().getDefaultChannel();
            String defaultChannelId = defaultChannel != null ? defaultChannel.getId() : "global";
            java.util.UUID uuid = player.getUniqueId();
            String username = player.getName();
            // 登录读取与设置写入使用同一队列，避免越过尚未完成的写操作。
            boolean accepted = dbManager.submitTask(() -> {
                PlayerDataLoadResult result = dbManager.initializePlayerData(uuid, username, defaultChannelId);
                if (!plugin.isEnabled()) return;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (!result.successful()) {
                        plugin.getMessageManager().send(player, "storage.load-failed");
                        return;
                    }
                    DatabaseManager.PlayerData data = result.data();
                    plugin.getChannelManager().loadPlayerChannel(player, data.channelId());
                    plugin.getColorManager().loadPlayerColor(player, data.colorId());
                    gg.fotia.basictool.bridge.PaperPreferencesBridge.importValues(uuid, "fotiachat", java.util.Map.of(
                            "chat.channel", data.channelId() == null ? "" : data.channelId(),
                            "chat.color", data.colorId() == null ? "" : data.colorId()));
                });
            });
            if (!accepted) plugin.getMessageManager().send(player, "storage.busy");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        // 清理玩家数据
        plugin.getChannelManager().removePlayer(uuid);
        plugin.getColorManager().removePlayer(uuid);
        // 清理私聊数据
        plugin.getPrivateMessageManager().clearPlayer(uuid);
        plugin.getPrivateMessageManager().getSocialSpyManager().clearPlayer(uuid);
        plugin.getAnnouncementManager().getConditionEngine().clearCache(uuid);
    }

    @EventHandler
    public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
        refreshIntegrationCache(event.getPlugin().getName());
    }

    @EventHandler
    public void onPluginDisable(org.bukkit.event.server.PluginDisableEvent event) {
        refreshIntegrationCache(event.getPlugin().getName());
    }

    private void refreshIntegrationCache(String pluginName) {
        if ("PlaceholderAPI".equals(pluginName) || "CraftEngine".equals(pluginName)) {
            gg.fotia.chat.util.MessageUtil.refreshIntegrationCache();
        }
    }
}
