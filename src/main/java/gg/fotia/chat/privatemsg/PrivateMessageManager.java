package gg.fotia.chat.privatemsg;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.api.VirtualPrivateMessageProvider;
import gg.fotia.chat.api.VirtualPrivateMessageTarget;
import gg.fotia.chat.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 私聊管理器。
 */
public class PrivateMessageManager {

    private final FotiaChat plugin;
    private final Map<UUID, UUID> lastMessageFrom = new ConcurrentHashMap<>();
    private final SocialSpyManager socialSpyManager;
    private final List<VirtualPrivateMessageProvider> virtualProviders = new CopyOnWriteArrayList<>();

    public PrivateMessageManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.socialSpyManager = new SocialSpyManager(plugin);
    }

    /**
     * 发送私聊到指定名字，自动解析真人或虚拟目标。
     */
    public boolean sendMessage(Player sender, String targetName, String message) {
        if (targetName == null || targetName.isBlank()) {
            plugin.getMessageManager().send(sender, "general.player-offline", Map.of("player", ""));
            return false;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.isOnline()) {
            return sendMessage(sender, target, message);
        }

        VirtualPrivateMessageTarget virtualTarget = resolveVirtualTarget(targetName);
        if (virtualTarget != null) {
            return sendMessage(sender, virtualTarget, message);
        }

        plugin.getMessageManager().send(sender, "general.player-offline", Map.of("player", targetName));
        return false;
    }

    /**
     * 从 /msg 参数中自动识别带空格的显示名目标。
     */
    public boolean sendMessage(Player sender, String[] args) {
        if (args == null || args.length < 2) {
            return false;
        }

        for (int targetArgLength = args.length - 1; targetArgLength >= 1; targetArgLength--) {
            String targetCandidate = joinArgs(args, 0, targetArgLength);
            ResolvedPrivateTarget resolvedTarget = resolveTarget(targetCandidate);
            if (resolvedTarget == null) {
                continue;
            }

            String message = joinArgs(args, targetArgLength, args.length);
            if (message.isBlank()) {
                break;
            }
            return resolvedTarget.send(sender, message);
        }

        plugin.getMessageManager().send(sender, "general.player-offline", Map.of("player", args[0]));
        return false;
    }

    /**
     * 发送私聊消息给真人玩家。
     */
    public boolean sendMessage(Player sender, Player target, String message) {
        if (sender.equals(target)) {
            plugin.getMessageManager().send(sender, "privatemsg.self");
            return false;
        }

        if (plugin.getMuteManager().isMuted(sender)) {
            plugin.getMessageManager().send(sender, "chat.muted");
            return false;
        }

        sendSenderEcho(sender, target.getName(), message);
        sendReceiverEcho(target, sender.getName(), message);

        lastMessageFrom.put(target.getUniqueId(), sender.getUniqueId());
        lastMessageFrom.put(sender.getUniqueId(), target.getUniqueId());
        socialSpyManager.notifySpy(sender, target, message);
        return true;
    }

    /**
     * 发送私聊消息给虚拟目标。
     */
    public boolean sendMessage(Player sender, VirtualPrivateMessageTarget target, String message) {
        if (target == null) {
            return false;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            plugin.getMessageManager().send(sender, "privatemsg.self");
            return false;
        }

        if (plugin.getMuteManager().isMuted(sender)) {
            plugin.getMessageManager().send(sender, "chat.muted");
            return false;
        }

        boolean accepted = target.receivePrivateMessage(sender, message);
        if (!accepted) {
            plugin.getMessageManager().send(sender, "privatemsg.target-offline");
            return false;
        }

        sendSenderEcho(sender, target.getName(), message);
        lastMessageFrom.put(sender.getUniqueId(), target.getUniqueId());
        socialSpyManager.notifyVirtualSpy(sender.getName(), target.getName(), message);
        return true;
    }

    /**
     * 由虚拟目标向真人玩家发送私聊回包。
     */
    public boolean sendVirtualMessageToPlayer(String senderName, String senderDisplayName, UUID senderUuid, Player target, String message) {
        if (target == null || !target.isOnline()) {
            return false;
        }

        sendReceiverEcho(target, senderName, message);
        if (senderUuid != null) {
            lastMessageFrom.put(target.getUniqueId(), senderUuid);
        }
        socialSpyManager.notifyVirtualSpy(senderName, target.getName(), message);
        return true;
    }

    /**
     * 回复上一条私聊。
     */
    public boolean reply(Player sender, String message) {
        UUID lastUuid = lastMessageFrom.get(sender.getUniqueId());
        if (lastUuid == null) {
            plugin.getMessageManager().send(sender, "privatemsg.no-reply-target");
            return false;
        }

        Player target = Bukkit.getPlayer(lastUuid);
        if (target != null && target.isOnline()) {
            return sendMessage(sender, target, message);
        }

        VirtualPrivateMessageTarget virtualTarget = resolveVirtualTarget(lastUuid);
        if (virtualTarget != null) {
            return sendMessage(sender, virtualTarget, message);
        }

        plugin.getMessageManager().send(sender, "privatemsg.target-offline");
        return false;
    }

    /**
     * 获取最后一次私聊的真人目标。
     */
    public Player getLastMessageTarget(Player player) {
        UUID lastUuid = lastMessageFrom.get(player.getUniqueId());
        if (lastUuid == null) {
            return null;
        }
        return Bukkit.getPlayer(lastUuid);
    }

    public void registerVirtualProvider(VirtualPrivateMessageProvider provider) {
        if (provider != null && !virtualProviders.contains(provider)) {
            virtualProviders.add(provider);
        }
    }

    public void unregisterVirtualProvider(VirtualPrivateMessageProvider provider) {
        if (provider != null) {
            virtualProviders.remove(provider);
        }
    }

    public List<String> suggestVirtualTargetNames(String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase();
        List<String> results = new ArrayList<>();
        for (VirtualPrivateMessageProvider provider : virtualProviders) {
            List<String> suggestions = provider.suggestNames(prefix);
            if (suggestions == null) {
                continue;
            }
            for (String suggestion : suggestions) {
                if (suggestion == null || suggestion.isBlank()) {
                    continue;
                }
                if (!normalized.isEmpty() && !suggestion.toLowerCase().startsWith(normalized)) {
                    continue;
                }
                if (!results.contains(suggestion)) {
                    results.add(suggestion);
                }
            }
        }
        return results;
    }

    /**
     * 清除玩家的私聊记录。
     */
    public void clearPlayer(UUID uuid) {
        lastMessageFrom.remove(uuid);
    }

    public SocialSpyManager getSocialSpyManager() {
        return socialSpyManager;
    }

    private VirtualPrivateMessageTarget resolveVirtualTarget(String name) {
        for (VirtualPrivateMessageProvider provider : virtualProviders) {
            VirtualPrivateMessageTarget target = provider.resolveByName(name);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private VirtualPrivateMessageTarget resolveVirtualTarget(UUID uniqueId) {
        if (uniqueId == null) {
            return null;
        }
        for (VirtualPrivateMessageProvider provider : virtualProviders) {
            VirtualPrivateMessageTarget target = provider.resolveByUniqueId(uniqueId);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private ResolvedPrivateTarget resolveTarget(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Player playerTarget = Bukkit.getPlayer(name);
        if (playerTarget != null && playerTarget.isOnline()) {
            return new ResolvedPrivateTarget(playerTarget);
        }

        VirtualPrivateMessageTarget virtualTarget = resolveVirtualTarget(name);
        if (virtualTarget != null) {
            return new ResolvedPrivateTarget(virtualTarget);
        }
        return null;
    }

    private String joinArgs(String[] args, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int index = startInclusive; index < endExclusive; index++) {
            if (index > startInclusive) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString().trim();
    }

    private void sendSenderEcho(Player sender, String targetName, String message) {
        String senderFormat = plugin.getMessageManager().getRaw("privatemsg.format-sender");
        senderFormat = senderFormat.replace("{target}", targetName);
        senderFormat = senderFormat.replace("{message}", message);
        sender.sendMessage(MessageUtil.parse(senderFormat, sender));
    }

    private void sendReceiverEcho(Player target, String senderName, String message) {
        String targetFormat = plugin.getMessageManager().getRaw("privatemsg.format-receiver");
        targetFormat = targetFormat.replace("{sender}", senderName);
        targetFormat = targetFormat.replace("{message}", message);
        target.sendMessage(MessageUtil.parse(targetFormat, target));
    }

    private final class ResolvedPrivateTarget {

        private final Player playerTarget;
        private final VirtualPrivateMessageTarget virtualTarget;

        private ResolvedPrivateTarget(Player playerTarget) {
            this.playerTarget = playerTarget;
            this.virtualTarget = null;
        }

        private ResolvedPrivateTarget(VirtualPrivateMessageTarget virtualTarget) {
            this.playerTarget = null;
            this.virtualTarget = virtualTarget;
        }

        private boolean send(Player sender, String message) {
            if (playerTarget != null) {
                return sendMessage(sender, playerTarget, message);
            }
            return sendMessage(sender, virtualTarget, message);
        }
    }
}
