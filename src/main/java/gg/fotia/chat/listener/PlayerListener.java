package gg.fotia.chat.listener;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.storage.DatabaseManager;
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
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                DatabaseManager.PlayerData data = dbManager.loadPlayerData(player.getUniqueId());
                if (data != null) {
                    // 在主线程应用数据
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            // 加载频道
                            if (data.channelId() != null) {
                                plugin.getChannelManager().loadPlayerChannel(player, data.channelId());
                            }
                            // 加载颜色
                            if (data.colorId() != null) {
                                plugin.getColorManager().loadPlayerColor(player, data.colorId());
                            }
                        }
                    });
                } else {
                    // 新玩家，创建数据库记录
                    dbManager.savePlayerData(
                            player.getUniqueId(),
                            player.getName(),
                            plugin.getChannelManager().getDefaultChannel().getId(),
                            null
                    );
                }
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        // 清理玩家数据
        plugin.getChannelManager().removePlayer(uuid);
        // 清理私聊数据
        plugin.getPrivateMessageManager().clearPlayer(uuid);
        plugin.getPrivateMessageManager().getSocialSpyManager().clearPlayer(uuid);
    }
}
