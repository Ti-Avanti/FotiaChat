package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.announcement.Announcement;
import gg.fotia.chat.announcement.AnnouncementManager;
import gg.fotia.chat.manager.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 公告命令
 * /announcement send <id> - 发送指定公告
 * /announcement broadcast <消息> - 广播自定义消息
 * /announcement list - 列出所有公告
 * /announcement toggle - 开关自动公告
 * /announcement reload - 重载公告配置
 */
public class AnnouncementCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;

    public AnnouncementCommand(FotiaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("fotiachat.command.announcement")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return true;
        }

        AnnouncementManager announcementManager = plugin.getAnnouncementManager();
        MessageManager messageManager = plugin.getMessageManager();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "send" -> {
                if (args.length < 2) {
                    messageManager.send(sender, "announcement.usage-send");
                    return true;
                }
                String id = args[1];
                if (announcementManager.sendAnnouncement(id)) {
                    messageManager.send(sender, "announcement.sent", Map.of("id", id));
                } else {
                    messageManager.send(sender, "announcement.not-found", Map.of("id", id));
                }
            }

            case "broadcast", "bc" -> {
                if (args.length < 2) {
                    messageManager.send(sender, "announcement.usage-broadcast");
                    return true;
                }
                StringBuilder message = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    if (i > 1) message.append(" ");
                    message.append(args[i]);
                }
                announcementManager.broadcastMessage(message.toString());
                messageManager.send(sender, "announcement.broadcast-success");
            }

            case "list" -> {
                messageManager.send(sender, "announcement.list-header");
                for (Announcement announcement : announcementManager.getAnnouncements().values()) {
                    String status = announcement.isEnabled() ? "<green>启用</green>" : "<red>禁用</red>";
                    messageManager.send(sender, "announcement.list-item", Map.of(
                            "id", announcement.getId(),
                            "status", status,
                            "interval", String.valueOf(announcement.getInterval())
                    ));
                }
            }

            case "toggle" -> {
                boolean newState = !announcementManager.isEnabled();
                announcementManager.setEnabled(newState);
                String state = newState ? "enabled" : "disabled";
                messageManager.send(sender, "announcement.toggle-" + state);
            }

            case "reload" -> {
                announcementManager.load();
                messageManager.send(sender, "announcement.reloaded");
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().send(sender, "announcement.help-header");
        plugin.getMessageManager().send(sender, "announcement.help-send");
        plugin.getMessageManager().send(sender, "announcement.help-broadcast");
        plugin.getMessageManager().send(sender, "announcement.help-list");
        plugin.getMessageManager().send(sender, "announcement.help-toggle");
        plugin.getMessageManager().send(sender, "announcement.help-reload");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("fotiachat.command.announcement")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> completions = List.of("send", "broadcast", "list", "toggle", "reload");
            String input = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            String input = args[1].toLowerCase();
            return plugin.getAnnouncementManager().getAnnouncementIds().stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
