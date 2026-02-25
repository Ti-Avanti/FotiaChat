package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.privatemsg.PrivateMessageManager;
import org.bukkit.Bukkit;
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
import java.util.stream.Collectors;

/**
 * 私聊命令
 * /msg <玩家> <消息>
 */
public class MsgCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final PrivateMessageManager privateMessageManager;

    public MsgCommand(FotiaChat plugin) {
        this.plugin = plugin;
        this.privateMessageManager = plugin.getPrivateMessageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        if (!player.hasPermission("fotiachat.command.msg")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(player, "general.invalid-args",
                    Map.of("usage", "/msg <玩家> <消息>"));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            plugin.getMessageManager().send(player, "general.player-offline",
                    Map.of("player", targetName));
            return true;
        }

        // 拼接消息
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }
        String message = messageBuilder.toString();

        privateMessageManager.sendMessage(player, target, message);
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
        }
        return new ArrayList<>();
    }
}
