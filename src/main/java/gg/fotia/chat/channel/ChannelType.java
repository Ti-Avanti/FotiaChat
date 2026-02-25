package gg.fotia.chat.channel;

/**
 * 频道类型枚举
 */
public enum ChannelType {
    /**
     * 公开频道 - 所有人可见
     */
    PUBLIC("public", null),

    /**
     * 管理员频道 - 需要权限
     */
    ADMIN("admin", "fotiachat.channel.admin"),

    /**
     * 求助频道 - 需要权限
     */
    HELP("help", "fotiachat.channel.help"),

    /**
     * 范围频道 - 只在配置半径内可见
     */
    LOCAL("local", null);

    private final String id;
    private final String permission;

    ChannelType(String id, String permission) {
        this.id = id;
        this.permission = permission;
    }

    public String getId() {
        return id;
    }

    public String getPermission() {
        return permission;
    }

    public static ChannelType fromId(String id) {
        for (ChannelType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return PUBLIC;
    }
}
