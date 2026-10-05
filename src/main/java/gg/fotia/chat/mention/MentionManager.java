package gg.fotia.chat.mention;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.manager.ConfigManager;
import gg.fotia.chat.util.LegacyColorConverter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MentionManager {

    private static final String USE_PERMISSION = "fotiachat.mention.use";

    private final FotiaChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();
    private String warnedInvalidSound;

    public MentionManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public Result apply(Player sender, Component message, Collection<? extends Player> onlinePlayers) {
        return apply(sender, message, onlinePlayers, sender);
    }

    public Result apply(Player sender, Component message, Collection<? extends Player> onlinePlayers, Player viewer) {
        return render(sender, prepare(sender, message, onlinePlayers), viewer);
    }

    public MentionPlan prepare(Player sender, Component message, Collection<? extends Player> onlinePlayers) {
        ConfigManager config = plugin.getConfigManager();
        Component safeMessage = message == null ? Component.empty() : message;
        boolean hasPermission = sender != null && sender.hasPermission(USE_PERMISSION);
        int onlinePlayerCount = onlinePlayers == null ? 0 : onlinePlayers.size();
        if (config.isDebugMode()) {
            plugin.getLogger().info("艾特解析前置: enabled=" + config.isMentionEnabled()
                    + ", sender=" + (sender == null ? "null" : sender.getName())
                    + ", permission=" + hasPermission
                    + ", onlinePlayers=" + onlinePlayerCount
                    + ", message=" + plainText.serialize(safeMessage));
        }
        if (!config.isMentionEnabled() || sender == null || !hasPermission || onlinePlayerCount == 0) {
            return new MentionPlan(safeMessage, List.of(), Set.of());
        }

        Map<String, Player> playersByName = new LinkedHashMap<>();
        for (Player player : onlinePlayers) {
            playersByName.putIfAbsent(gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", player).toLowerCase(Locale.ROOT), player);
        }

        List<MentionMatcher.Match> matches = MentionMatcher.findMatches(
                plainText.serialize(safeMessage),
                playersByName.values().stream().map(p -> gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", p)).toList(),
                config.isMentionAutoDetectPlayerName()
        );
        if (config.isDebugMode()) {
            plugin.getLogger().info("艾特匹配结果: candidates=" + playersByName.values().stream()
                    .map(Player::getName)
                    .toList() + ", matches=" + matches);
        }
        Set<UUID> mentionedPlayers = matches.stream().map(MentionMatcher.Match::playerName)
                .map(name -> playersByName.get(name.toLowerCase(Locale.ROOT)))
                .filter(player -> player != null)
                .map(Player::getUniqueId)
                .collect(Collectors.toUnmodifiableSet());
        return new MentionPlan(safeMessage, matches, mentionedPlayers);
    }

    public Result render(Player sender, MentionPlan plan, Player viewer) {
        MentionComponentDecorator.Result decorated = MentionComponentDecorator.decorate(
                plan.message(), plan.matches(), name -> createMentionComponent(sender, name, viewer));
        return new Result(decorated.component(), plan.mentionedPlayers());
    }

    public void notify(Player player) {
        ConfigManager config = plugin.getConfigManager();
        if (player == null || !config.isMentionSoundEnabled() || !gg.fotia.chat.api.PlayerPreferences.sound(player)) {
            return;
        }

        String soundName = config.getMentionSoundName();
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(
                    player.getLocation(),
                    sound,
                    config.getMentionSoundVolume(),
                    config.getMentionSoundPitch()
            );
            warnedInvalidSound = null;
        } catch (IllegalArgumentException exception) {
            if (!soundName.equals(warnedInvalidSound)) {
                warnedInvalidSound = soundName;
                plugin.getLogger().warning("无效的艾特提示音: " + soundName);
            }
        }
    }

    private Component createMentionComponent(Player sender, String playerName, Player viewer) {
        ConfigManager config = plugin.getConfigManager();
        Component mention = parseConfiguredText(
                plugin.getMessageManager().resolveConfigured(viewer, config.getMentionDisplay()),
                sender,
                playerName
        );
        List<String> hoverLines = config.getMentionHover().stream()
                .map(line -> plugin.getMessageManager().resolveConfigured(viewer, line))
                .toList();
        if (hoverLines.isEmpty()) {
            return mention;
        }

        Component hover = Component.empty();
        for (int index = 0; index < hoverLines.size(); index++) {
            if (index > 0) {
                hover = hover.append(Component.newline());
            }
            hover = hover.append(parseConfiguredText(hoverLines.get(index), sender, playerName));
        }
        return mention.hoverEvent(HoverEvent.showText(hover));
    }

    private Component parseConfiguredText(String text, Player sender, String playerName) {
        String resolved = (text == null ? "" : text)
                .replace("{player}", playerName)
                .replace("{sender}", gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", sender));
        try {
            return miniMessage.deserialize(LegacyColorConverter.convertToMiniMessage(resolved));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("艾特文本格式无效: " + exception.getMessage());
            return Component.text("@" + playerName);
        }
    }

    public record Result(Component component, Set<UUID> mentionedPlayers) {
    }
}
