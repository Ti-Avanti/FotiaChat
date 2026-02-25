package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.mute.MuteManager;
import gg.fotia.chat.util.TimeUtil;
import org.bukkit.Bukkit;
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
import java.util.stream.Collectors;

/**
 * 禁言命令
 * /mute <玩家> [时长] [原因]
 */
public class MuteCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final MuteManager muteManager;

    public MuteCommand(FotiaChat plugin) {
        this.plugin = plugin;
        this.muteManager = plugin.getMuteManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("fotiachat.command.mute")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return true;
        }

        if (args.length < 1) {
            plugin.getMessageManager().send(sender, "general.invalid-args",
                    Map.of("usage", "/mute <玩家> [时长] [原因]"));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            plugin.getMessageManager().send(sender, "general.player-not-found",
                    Map.of("player", targetName));
            return true;
        }

        // 检查是否有豁免权限
        if (target.hasPermission("fotiachat.mute.exempt")) {
            plugin.getMessageManager().send(sender, "admin.mute-exempt",
                    Map.of("player", target.getName()));
            return true;
        }

        // 解析时长
        long duration = 0;
        String reason = "无";
        String durationStr = "永久";

        if (args.length >= 2) {
            duration = TimeUtil.parseTime(args[1]);
            if (duration > 0) {
                durationStr = TimeUtil.formatDuration(duration);
            }
        }

        if (args.length >= 3) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) reasonBuilder.append(" ");
                reasonBuilder.append(args[i]);
            }
            reason = reasonBuilder.toString();
        }

        String mutedBy = sender instanceof Player ? sender.getName() : "Console";

        // 执行禁言
        muteManager.mute(target, duration, reason, mutedBy);

        // 通知执行者
        plugin.getMessageManager().send(sender, "admin.mute-success",
                Map.of("player", target.getName(), "duration", durationStr));

        // 通知被禁言者
        plugin.getMessageManager().send(target, "chat.muted");

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            return Arrays.asList("1m", "5m", "10m", "30m", "1h", "6h", "12h", "1d", "7d", "30d");
        }
        return new ArrayList<>();
    }
}
