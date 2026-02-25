package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FotiaChat主命令
 * /fotiachat reload - 重载配置
 * /fotiachat help - 帮助信息
 * /fotiachat version - 版本信息
 */
public class FotiaChatCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final List<String> subCommands = Arrays.asList("reload", "help", "version");

    public FotiaChatCommand(FotiaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "help" -> showHelp(sender);
            case "version" -> showVersion(sender);
            case "viewsnapshot" -> handleViewSnapshot(sender, args);
            default -> {
                plugin.getMessageManager().send(sender, "general.invalid-args",
                        Map.of("usage", "/fotiachat <reload|help|version>"));
            }
        }

        return true;
    }

    /**
     * 处理重载命令
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("fotiachat.admin.reload")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        try {
            plugin.reload();
            plugin.getMessageManager().send(sender, "general.reload-success");
        } catch (Exception e) {
            sender.sendMessage(MessageUtil.parse("<!i><red>重载配置时发生错误: " + e.getMessage()));
            plugin.getLogger().severe("重载配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(CommandSender sender) {
        List<String> helpLines = Arrays.asList(
                "<!i><gradient:#FF6B6B:#4ECDC4>========== FotiaChat 帮助 ==========</gradient>",
                "<!i><gray>/fotiachat reload</gray> <dark_gray>-</dark_gray> <white>重载配置文件</white>",
                "<!i><gray>/fotiachat help</gray> <dark_gray>-</dark_gray> <white>显示帮助信息</white>",
                "<!i><gray>/fotiachat version</gray> <dark_gray>-</dark_gray> <white>显示版本信息</white>",
                "<!i><gray>/channel [频道]</gray> <dark_gray>-</dark_gray> <white>切换聊天频道</white>",
                "<!i><gray>/chatcolor [颜色]</gray> <dark_gray>-</dark_gray> <white>设置聊天颜色</white>",
                "<!i><gray>/msg <玩家> <消息></gray> <dark_gray>-</dark_gray> <white>发送私聊</white>",
                "<!i><gray>/reply <消息></gray> <dark_gray>-</dark_gray> <white>回复私聊</white>",
                "<!i><gray>/mute <玩家> [时长] [原因]</gray> <dark_gray>-</dark_gray> <white>禁言玩家</white>",
                "<!i><gray>/unmute <玩家></gray> <dark_gray>-</dark_gray> <white>解除禁言</white>",
                "<!i><gradient:#FF6B6B:#4ECDC4>====================================</gradient>"
        );

        for (String line : helpLines) {
            sender.sendMessage(MessageUtil.parse(line));
        }
    }

    /**
     * 显示版本信息
     */
    private void showVersion(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        String author = String.join(", ", plugin.getDescription().getAuthors());

        List<String> versionLines = Arrays.asList(
                "<!i><gradient:#FF6B6B:#4ECDC4>========== FotiaChat ==========</gradient>",
                "<!i><gray>版本:</gray> <white>" + version + "</white>",
                "<!i><gray>作者:</gray> <white>" + author + "</white>",
                "<!i><gray>API版本:</gray> <white>" + plugin.getDescription().getAPIVersion() + "</white>",
                "<!i><gray>跨服通信:</gray> <white>" + (plugin.getCrossServerManager().isEnabled() ?
                        "已启用 (" + plugin.getCrossServerManager().getType() + ")" : "已禁用") + "</white>",
                "<!i><gradient:#FF6B6B:#4ECDC4>===============================</gradient>"
        );

        for (String line : versionLines) {
            sender.sendMessage(MessageUtil.parse(line));
        }
    }

    /**
     * 处理查看快照命令
     */
    private void handleViewSnapshot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            return;
        }

        try {
            UUID snapshotId = UUID.fromString(args[1]);
            plugin.getItemDisplayManager().openSnapshotGui(player, snapshotId);
        } catch (IllegalArgumentException e) {
            // 无效的UUID，忽略
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return subCommands.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
