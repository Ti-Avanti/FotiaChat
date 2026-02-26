package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.ignore.IgnoreManager;
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
 * 屏蔽玩家命令
 * /chatignore <玩家名> - 屏蔽/取消屏蔽玩家
 * /chatignore list - 查看屏蔽列表
 */
public class ChatIgnoreCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final IgnoreManager ignoreManager;

    public ChatIgnoreCommand(FotiaChat plugin) {
        this.plugin = plugin;
        this.ignoreManager = plugin.getIgnoreManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        if (!player.hasPermission("fotiachat.command.chatignore")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }

        if (args.length < 1) {
            plugin.getMessageManager().send(player, "general.invalid-args",
                    Map.of("usage", "/chatignore <玩家名|list>"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("list")) {
            // 显示屏蔽列表
            List<String> ignoreList = ignoreManager.getIgnoreList(player.getUniqueId());
            if (ignoreList.isEmpty()) {
                plugin.getMessageManager().send(player, "ignore.list-empty");
            } else {
                plugin.getMessageManager().send(player, "ignore.list-header");
                for (String name : ignoreList) {
                    plugin.getMessageManager().send(player, "ignore.list-item",
                            Map.of("player", name));
                }
            }
            return true;
        }

        // 屏蔽/取消屏蔽玩家
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            plugin.getMessageManager().send(player, "general.player-not-found",
                    Map.of("player", targetName));
            return true;
        }

        if (target.equals(player)) {
            plugin.getMessageManager().send(player, "ignore.self");
            return true;
        }

        boolean added = ignoreManager.toggleIgnore(player.getUniqueId(), target.getUniqueId(), target.getName());

        if (added) {
            plugin.getMessageManager().send(player, "ignore.added",
                    Map.of("player", target.getName()));
        } else {
            plugin.getMessageManager().send(player, "ignore.removed",
                    Map.of("player", target.getName()));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            completions.add("list");

            // 添加在线玩家
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
