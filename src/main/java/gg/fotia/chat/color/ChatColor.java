package gg.fotia.chat.color;

/**
 * 聊天颜色数据类
 */
public class ChatColor {

    private final String id;
    private final String name;
    private final ColorType type;
    private final String format;
    private final String permission;

    public ChatColor(String id, String name, ColorType type, String format, String permission) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.format = format;
        this.permission = permission;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ColorType getType() {
        return type;
    }

    public String getFormat() {
        return format;
    }

    public String getPermission() {
        return permission;
    }

    /**
     * 应用颜色到消息
     */
    public String apply(String message) {
        return switch (type) {
            case SINGLE -> format + message;
            case GRADIENT -> "<gradient:" + format + ">" + message + "</gradient>";
            case RAINBOW -> "<rainbow>" + message + "</rainbow>";
        };
    }

    /**
     * 检查玩家是否有权限使用此颜色
     */
    public boolean hasPermission(org.bukkit.entity.Player player) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return player.hasPermission(permission);
    }
}
