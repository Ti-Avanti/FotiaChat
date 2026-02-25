package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.mute.MuteData;
import gg.fotia.chat.mute.MuteManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 解除禁言命令
 * /unmute <玩家>
 */
public class UnmuteCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final MuteManager muteManager;

    public UnmuteCommand(FotiaChat plugin) {
        this.plugin = plugin;
        this.muteManager = plugin.getMuteManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("fotiachat.command.unmute")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return true;
        }

        if (args.length < 1) {
            plugin.getMessageManager().send(sender, "general.invalid-args",
                    Map.of("usage", "/unmute <玩家>"));
            return true;
        }

        String targetName = args[0];

        // 先尝试在线玩家
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUuid = null;

        if (target != null) {
            targetUuid = target.getUniqueId();
        } else {
            // 尝试从禁言列表中查找
            for (Map.Entry<UUID, MuteData> entry : muteManager.getAllMutes().entrySet()) {
                if (entry.getValue().getPlayerName().equalsIgnoreCase(targetName)) {
                    targetUuid = entry.getKey();
                    break;
                }
            }
        }

        if (targetUuid == null) {
            plugin.getMessageManager().send(sender, "general.player-not-found",
                    Map.of("player", targetName));
            return true;
        }

        // 检查是否被禁言
        if (!muteManager.isMuted(targetUuid)) {
            plugin.getMessageManager().send(sender, "admin.not-muted",
                    Map.of("player", targetName));
            return true;
        }

        // 解除禁言
        muteManager.unmute(targetUuid);

        // 通知执行者
        plugin.getMessageManager().send(sender, "admin.unmute-success",
                Map.of("player", targetName));

        // 如果玩家在线，通知他
        if (target != null && target.isOnline()) {
            plugin.getMessageManager().send(target, "chat.mute-expired");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            // 返回被禁言的玩家列表
            return muteManager.getAllMutes().values().stream()
                    .map(MuteData::getPlayerName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
