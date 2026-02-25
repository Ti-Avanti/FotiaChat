package gg.fotia.chat.privatemsg;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * SocialSpy监听管理器
 */
public class SocialSpyManager {

    private final FotiaChat plugin;
    private final Set<UUID> spyingPlayers = new HashSet<>();

    public SocialSpyManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    /**
     * 切换玩家的监听状态
     */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (spyingPlayers.contains(uuid)) {
            spyingPlayers.remove(uuid);
            return false;
        } else {
            spyingPlayers.add(uuid);
            return true;
        }
    }

    /**
     * 检查玩家是否在监听
     */
    public boolean isSpying(Player player) {
        return spyingPlayers.contains(player.getUniqueId());
    }

    /**
     * 启用监听
     */
    public void enable(Player player) {
        spyingPlayers.add(player.getUniqueId());
    }

    /**
     * 禁用监听
     */
    public void disable(Player player) {
        spyingPlayers.remove(player.getUniqueId());
    }

    /**
     * 通知所有监听者
     */
    public void notifySpy(Player sender, Player target, String message) {
        String spyFormat = plugin.getMessageManager().getRaw("privatemsg.socialspy-format");
        spyFormat = spyFormat.replace("{sender}", sender.getName());
        spyFormat = spyFormat.replace("{target}", target.getName());
        spyFormat = spyFormat.replace("{message}", message);

        for (UUID uuid : spyingPlayers) {
            Player spy = Bukkit.getPlayer(uuid);
            if (spy != null && spy.isOnline()) {
                // 不通知发送者和接收者
                if (!spy.equals(sender) && !spy.equals(target)) {
                    spy.sendMessage(MessageUtil.parse(spyFormat, spy));
                }
            }
        }
    }

    /**
     * 清除玩家的监听状态
     */
    public void clearPlayer(UUID uuid) {
        spyingPlayers.remove(uuid);
    }
}
