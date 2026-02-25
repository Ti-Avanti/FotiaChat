package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.privatemsg.SocialSpyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SocialSpy命令
 * /socialspy - 切换监听模式
 */
public class SocialSpyCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final SocialSpyManager socialSpyManager;

    public SocialSpyCommand(FotiaChat plugin) {
        this.plugin = plugin;
        this.socialSpyManager = plugin.getPrivateMessageManager().getSocialSpyManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        if (!player.hasPermission("fotiachat.command.socialspy")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }

        boolean enabled = socialSpyManager.toggle(player);
        if (enabled) {
            plugin.getMessageManager().send(player, "privatemsg.socialspy-enabled");
        } else {
            plugin.getMessageManager().send(player, "privatemsg.socialspy-disabled");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return new ArrayList<>();
    }
}
