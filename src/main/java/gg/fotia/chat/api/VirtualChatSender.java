package gg.fotia.chat.api;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 表示一个不一定对应 Bukkit 在线玩家的聊天发言者。
 */
public final class VirtualChatSender {

    private final UUID uniqueId;
    private final String name;
    private final String displayName;
    private final Location location;
    private final Map<String, String> placeholders;
    private final boolean bypassFilter;

    public VirtualChatSender(UUID uniqueId, String name) {
        this(uniqueId, name, name, null, Map.of(), false);
    }

    public VirtualChatSender(UUID uniqueId,
                             String name,
                             String displayName,
                             Location location,
                             Map<String, String> placeholders,
                             boolean bypassFilter) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = normalizeName(name);
        this.displayName = normalizeDisplayName(displayName, this.name);
        this.location = location == null ? null : location.clone();
        this.placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
        this.bypassFilter = bypassFilter;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    public boolean isBypassFilter() {
        return bypassFilter;
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        return normalized;
    }

    private String normalizeDisplayName(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
