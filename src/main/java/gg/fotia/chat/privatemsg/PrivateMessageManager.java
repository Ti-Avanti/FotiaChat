package gg.fotia.chat.privatemsg;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私聊管理器
 */
public class PrivateMessageManager {

    private final FotiaChat plugin;
    private final Map<UUID, UUID> lastMessageFrom = new ConcurrentHashMap<>();
    private final SocialSpyManager socialSpyManager;

    public PrivateMessageManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.socialSpyManager = new SocialSpyManager(plugin);
    }

    /**
     * 发送私聊消息
     */
    public boolean sendMessage(Player sender, Player target, String message) {
        if (sender.equals(target)) {
            plugin.getMessageManager().send(sender, "privatemsg.self");
            return false;
        }

        // 检查发送者是否被禁言
        if (plugin.getMuteManager().isMuted(sender)) {
            plugin.getMessageManager().send(sender, "chat.muted");
            return false;
        }

        // 发送给发送者
        String senderFormat = plugin.getMessageManager().getRaw("privatemsg.format-sender");
        senderFormat = senderFormat.replace("{target}", target.getName());
        senderFormat = senderFormat.replace("{message}", message);
        sender.sendMessage(MessageUtil.parse(senderFormat, sender));

        // 发送给接收者
        String targetFormat = plugin.getMessageManager().getRaw("privatemsg.format-receiver");
        targetFormat = targetFormat.replace("{sender}", sender.getName());
        targetFormat = targetFormat.replace("{message}", message);
        target.sendMessage(MessageUtil.parse(targetFormat, target));

        // 记录最后私聊来源
        lastMessageFrom.put(target.getUniqueId(), sender.getUniqueId());
        lastMessageFrom.put(sender.getUniqueId(), target.getUniqueId());

        // 通知SocialSpy
        socialSpyManager.notifySpy(sender, target, message);

        return true;
    }

    /**
     * 回复上一个私聊
     */
    public boolean reply(Player sender, String message) {
        UUID lastUuid = lastMessageFrom.get(sender.getUniqueId());
        if (lastUuid == null) {
            plugin.getMessageManager().send(sender, "privatemsg.no-reply-target");
            return false;
        }

        Player target = Bukkit.getPlayer(lastUuid);
        if (target == null || !target.isOnline()) {
            plugin.getMessageManager().send(sender, "privatemsg.target-offline");
            return false;
        }

        return sendMessage(sender, target, message);
    }

    /**
     * 获取最后私聊的玩家
     */
    public Player getLastMessageTarget(Player player) {
        UUID lastUuid = lastMessageFrom.get(player.getUniqueId());
        if (lastUuid == null) {
            return null;
        }
        return Bukkit.getPlayer(lastUuid);
    }

    /**
     * 清除玩家的私聊记录
     */
    public void clearPlayer(UUID uuid) {
        lastMessageFrom.remove(uuid);
    }

    public SocialSpyManager getSocialSpyManager() {
        return socialSpyManager;
    }
}
