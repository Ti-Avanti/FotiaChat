package gg.fotia.chat.crossserver;

import java.util.UUID;

/**
 * 跨服消息数据类
 */
public class CrossServerMessage {

    private final String type;
    private final String serverName;
    private final UUID senderUuid;
    private final String senderName;
    private final String channelId;
    private final String message;
    private final long timestamp;

    public CrossServerMessage(String type, String serverName, UUID senderUuid,
                              String senderName, String channelId, String message) {
        this.type = type;
        this.serverName = serverName;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.channelId = channelId;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() {
        return type;
    }

    public String getServerName() {
        return serverName;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 序列化为字符串
     */
    public String serialize() {
        return String.join("||",
                type,
                serverName,
                senderUuid != null ? senderUuid.toString() : "",
                senderName,
                channelId != null ? channelId : "",
                message,
                String.valueOf(timestamp)
        );
    }

    /**
     * 从字符串反序列化
     */
    public static CrossServerMessage deserialize(String data) {
        String[] parts = data.split("\\|\\|", 7);
        if (parts.length < 7) {
            return null;
        }

        String type = parts[0];
        String serverName = parts[1];
        UUID senderUuid = parts[2].isEmpty() ? null : UUID.fromString(parts[2]);
        String senderName = parts[3];
        String channelId = parts[4].isEmpty() ? null : parts[4];
        String message = parts[5];

        return new CrossServerMessage(type, serverName, senderUuid, senderName, channelId, message);
    }

    /**
     * 消息类型常量
     */
    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_PRIVATE = "PRIVATE";
    public static final String TYPE_BROADCAST = "BROADCAST";
}
