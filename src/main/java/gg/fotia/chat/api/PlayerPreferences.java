package gg.fotia.chat.api;

import gg.fotia.basictool.bridge.PaperPreferencesBridge;
import gg.fotia.basictool.bridge.PaperNickBridge;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** 扩展共用的可选个人设置入口，未安装 BasicTool 时沿用原来的接收行为。 */
public final class PlayerPreferences {
    private record Pending(String value, UUID token) {}
    private static final java.util.concurrent.ConcurrentHashMap<String, Pending> pending = new java.util.concurrent.ConcurrentHashMap<>();
    private PlayerPreferences() {}
    public static boolean allows(UUID viewer, String source, String category, UUID sender) {
        return PaperPreferencesBridge.allows(viewer, source, category, sender);
    }
    public static boolean allows(Player viewer, String source, String category, UUID sender) { return allows(viewer.getUniqueId(), source, category, sender); }
    public static boolean sound(Player viewer) { return PaperPreferencesBridge.flag(viewer.getUniqueId(), "fotiachat", "notification.sound", true); }
    public static String value(Player viewer, String key, String fallback) {
        Pending choice = pending.get(viewer.getUniqueId() + ":" + key);
        return choice == null ? PaperPreferencesBridge.value(viewer.getUniqueId(), "fotiachat", key, fallback) : choice.value();
    }
    public static boolean ready(Player viewer) { return PaperPreferencesBridge.ready(viewer.getUniqueId()); }
    public static CompletableFuture<Boolean> save(Player viewer, String key, String value) {
        if (!PaperPreferencesBridge.available()) return CompletableFuture.completedFuture(false);
        String id = viewer.getUniqueId() + ":" + key; Pending choice = new Pending(value, UUID.randomUUID()); pending.put(id, choice);
        var future = PaperPreferencesBridge.set(viewer.getUniqueId(), "global", key, value);
        future.whenComplete((saved, error) -> {
            pending.remove(id, choice);
            if (error != null) {
                var plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("FotiaChat");
                if (plugin instanceof gg.fotia.chat.FotiaChat chat && chat.isEnabled())
                    org.bukkit.Bukkit.getScheduler().runTask(chat, () -> { if (viewer.isOnline()) chat.getMessageManager().send(viewer, "preferences.save-failed"); });
            }
        });
        return future;
    }
    public static String display(Player viewer, String text) { return PaperNickBridge.forViewer("FotiaChat", viewer, text); }
    public static String anonymous(String text) { return PaperNickBridge.redact("FotiaChat", text); }
    public static net.kyori.adventure.text.Component display(Player viewer, net.kyori.adventure.text.Component text) { return PaperNickBridge.forViewer("FotiaChat", viewer, text); }
}
