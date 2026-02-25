package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.color.ChatColor;
import gg.fotia.chat.color.ColorManager;
import gg.fotia.chat.manager.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
 * 聊天颜色命令
 * /chatcolor [颜色ID] - 设置聊天颜色
 * /chatcolor list - 查看可用颜色
 * /chatcolor reset - 重置为默认颜色
 */
public class ChatColorCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;

    public ChatColorCommand(FotiaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        if (!player.hasPermission("fotiachat.color")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }

        ColorManager colorManager = plugin.getColorManager();
        MessageManager messageManager = plugin.getMessageManager();

        if (args.length == 0) {
            // 无参数时打开GUI菜单
            plugin.getMenuManager().openMenu(player, "color-menu");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> showColorList(player);
            case "gui", "menu" -> plugin.getMenuManager().openMenu(player, "color-menu");
            case "reset" -> {
                colorManager.resetPlayerColor(player);
                messageManager.send(player, "color.reset");
            }
            default -> {
                // 尝试设置颜色
                if (colorManager.setPlayerColor(player, subCommand)) {
                    ChatColor color = colorManager.getColor(subCommand);
                    Map<String, String> placeholders = Map.of("color", color.getName());
                    messageManager.send(player, "color.set-success", placeholders);
                } else {
                    ChatColor color = colorManager.getColor(subCommand);
                    if (color == null) {
                        Map<String, String> placeholders = Map.of("color", subCommand);
                        messageManager.send(player, "color.not-found", placeholders);
                    } else {
                        Map<String, String> placeholders = Map.of("color", color.getName());
                        messageManager.send(player, "color.no-permission", placeholders);
                    }
                }
            }
        }

        return true;
    }

    private void showColorList(Player player) {
        MessageManager messageManager = plugin.getMessageManager();
        ColorManager colorManager = plugin.getColorManager();

        messageManager.send(player, "color.list-header");

        List<ChatColor> availableColors = colorManager.getAvailableColors(player);
        if (availableColors.isEmpty()) {
            messageManager.send(player, "color.no-colors-available");
            return;
        }

        for (ChatColor color : availableColors) {
            // 创建可点击的颜色项
            String preview = color.apply("示例文字");
            Component colorComponent = plugin.getMessageManager().get("color.list-item",
                    Map.of("id", color.getId(), "name", color.getName(), "preview", preview));

            // 添加点击事件
            colorComponent = colorComponent
                    .clickEvent(ClickEvent.runCommand("/chatcolor " + color.getId()))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("点击选择此颜色")));

            player.sendMessage(colorComponent);
        }

        messageManager.send(player, "color.list-footer");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("list");
            completions.add("gui");
            completions.add("reset");

            // 添加玩家有权限的颜色ID
            ColorManager colorManager = plugin.getColorManager();
            for (ChatColor color : colorManager.getAvailableColors(player)) {
                completions.add(color.getId());
            }

            String input = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
