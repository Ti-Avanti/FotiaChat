package gg.fotia.chat.menu.futureui;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.menu.ColorMenuBridge;
import gg.fotia.chat.menu.ColorMenuOptions;
import gg.fotia.chat.storage.DatabaseWriteRejectedException;
import gg.fotia.futureui.api.ActionResult;
import gg.fotia.futureui.api.FutureUIService;
import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** FutureUI 只接管颜色菜单显示；所有颜色写入复用 ColorManager。 */
public final class FutureUiColorMenu implements ColorMenuBridge, Listener {
    private static final String TOKEN = "fotiachat.session";
    private static final String EXTENSION = "fotiachat:colors";
    private final FotiaChat plugin;
    private final ColorMenuOptions options;
    private final ColorMenuData data;
    private final NamespacedKey layoutKey;
    private final Map<UUID, ColorMenuSession> sessions = new HashMap<>();
    private final List<AutoCloseable> registrations = new ArrayList<>();
    private FutureUIService service;
    private boolean closed;

    public FutureUiColorMenu(FotiaChat plugin, ColorMenuOptions options) {
        this.plugin = plugin; this.options = options; this.data = new ColorMenuData(plugin, options);
        layoutKey = new NamespacedKey(plugin, "color-menu-layout");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override public OpenResult open(Player player) {
        String remembered = player.getPersistentDataContainer().get(layoutKey, PersistentDataType.STRING);
        boolean compact = remembered == null ? options.compactByDefault() : remembered.equals("compact");
        return open(player, new ColorMenuSession(compact));
    }

    private OpenResult open(Player player, ColorMenuSession session) {
        if (closed || !connect()) return OpenResult.UNAVAILABLE;
        String menu = options.profile(session.compact).menu();
        if (!service.canRender(player, menu)) return OpenResult.UNAVAILABLE;
        if (!player.hasPermission("fotiachat.color")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return OpenResult.REJECTED;
        }
        ColorMenuSession previous = sessions.put(player.getUniqueId(), session);
        if (service.open(player, menu, arguments(session))) return OpenResult.OPENED;
        if (previous != null && service.isOpen(player, options.profile(previous.compact).menu(), arguments(previous)))
            sessions.put(player.getUniqueId(), previous);
        else sessions.remove(player.getUniqueId(), session);
        return OpenResult.REJECTED;
    }

    private boolean connect() {
        if (!Bukkit.getPluginManager().isPluginEnabled("FutureUI")) return false;
        FutureUIService current = Bukkit.getServicesManager().load(FutureUIService.class);
        if (current == null) return false;
        if (current == service) return true;
        release(); service = current;
        registrations.add(service.registerData(plugin, EXTENSION, this::rows));
        registrations.add(service.registerAction(plugin, EXTENSION, this::action));
        return true;
    }

    private CompletableFuture<List<Node>> rows(MenuContext context, Node node) {
        requireMainThread();
        ColorMenuSession session = current(context);
        return CompletableFuture.completedFuture(session == null || !context.player().hasPermission("fotiachat.color")
                ? List.of() : data.rows(context.player(), session, node.text("view", "page")));
    }

    private CompletableFuture<ActionResult> action(MenuContext context, Node node) {
        requireMainThread();
        Player player = context.player();
        ColorMenuSession session = current(context);
        String operation = node.text("operation", "");
        if (operation.equals("closed")) {
            if (session != null) sessions.remove(player.getUniqueId(), session);
            return ok();
        }
        if (session == null || !player.hasPermission("fotiachat.color")) return denied();
        String id = argument(context, node, "id");
        try {
            switch (operation) {
                case "opened" -> { return ok(); }
                case "select" -> {
                    if (data.entries(player, session).stream().noneMatch(color -> color.getId().equals(id))) return denied();
                    if (!plugin.getColorManager().setPlayerColor(player, id)) return denied();
                    session.feedback = "saved";
                }
                case "reset" -> {
                    plugin.getColorManager().resetPlayerColor(player);
                    session.feedback = "reset";
                }
                case "filter" -> {
                    if (!Set.of("all", "single", "effects", "available").contains(id)) return denied();
                    session.filter = id; session.page = 0;
                }
                case "previous" -> session.page = Math.max(0, session.page - 1);
                case "next" -> {
                    int count = data.colors(player, session).size(), size = options.profile(session.compact).pageSize();
                    session.page = Math.min(session.page + 1, Math.max(0, (count - 1) / size));
                }
                case "layout" -> {
                    if (!Set.of("standard", "compact").contains(id)) return denied();
                    ColorMenuSession next = new ColorMenuSession(id.equals("compact"));
                    next.filter = session.filter;
                    if (open(player, next) == OpenResult.OPENED)
                        player.getPersistentDataContainer().set(layoutKey, PersistentDataType.STRING, id);
                    else return denied();
                }
                case "close" -> service.close(player);
                default -> { return denied(); }
            }
        } catch (DatabaseWriteRejectedException busy) {
            plugin.getMessageManager().send(player, "storage.busy");
            return CompletableFuture.completedFuture(ActionResult.fail("integrations.fotiachat.save-failed"));
        }
        return ok();
    }

    private ColorMenuSession current(MenuContext context) {
        ColorMenuSession session = sessions.get(context.player().getUniqueId());
        if (session == null || !session.token.toString().equals(Objects.toString(context.variables().get(TOKEN), ""))) return null;
        return context.session().menu.id().equals(options.profile(session.compact).menu()) ? session : null;
    }

    private static Map<String, String> arguments(ColorMenuSession session) { return Map.of(TOKEN, session.token.toString()); }
    private static String argument(MenuContext context, Node node, String key) {
        String value = node.text(key, "");
        return value.startsWith("{") && value.endsWith("}")
                ? Objects.toString(context.variables().get(value.substring(1, value.length() - 1)), "") : value;
    }
    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Color menu access requires the server thread");
    }
    private static CompletableFuture<ActionResult> ok() { return CompletableFuture.completedFuture(ActionResult.ok()); }
    private static CompletableFuture<ActionResult> denied() {
        return CompletableFuture.completedFuture(ActionResult.fail("messages.requirement-failed"));
    }

    @EventHandler public void quit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void disabled(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("FutureUI")) release();
    }

    private void release() {
        if (service != null) for (Map.Entry<UUID, ColorMenuSession> entry : List.copyOf(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            ColorMenuSession session = entry.getValue();
            if (player != null && service.isOpen(player, options.profile(session.compact).menu(), arguments(session))) service.close(player);
        }
        sessions.clear();
        for (AutoCloseable registration : registrations) {
            try { registration.close(); }
            catch (Exception error) { plugin.getLogger().warning("注销 FutureUI 颜色菜单失败: " + error.getMessage()); }
        }
        registrations.clear(); service = null;
    }

    @Override public void close() {
        if (closed) return;
        release(); closed = true; HandlerList.unregisterAll(this);
    }
}
