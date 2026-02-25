package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Premium功能占位命令
 * 当Premium模块未安装时，提示用户需要安装Premium模块
 */
public class PremiumPlaceholderCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final String featureName;

    public PremiumPlaceholderCommand(FotiaChat plugin, String featureName) {
        this.plugin = plugin;
        this.featureName = featureName;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        plugin.getMessageManager().send(sender, "premium.not-installed",
                java.util.Map.of("feature", featureName));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
