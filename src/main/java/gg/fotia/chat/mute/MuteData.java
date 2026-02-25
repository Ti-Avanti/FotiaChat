package gg.fotia.chat.mute;

import java.util.UUID;

/**
 * 禁言数据类
 */
public class MuteData {

    private final UUID playerUuid;
    private final String playerName;
    private final long muteTime;
    private final long expireTime;
    private final String reason;
    private final String mutedBy;

    public MuteData(UUID playerUuid, String playerName, long muteTime, long expireTime, String reason, String mutedBy) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.muteTime = muteTime;
        this.expireTime = expireTime;
        this.reason = reason;
        this.mutedBy = mutedBy;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getMuteTime() {
        return muteTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public String getReason() {
        return reason;
    }

    public String getMutedBy() {
        return mutedBy;
    }

    /**
     * 检查禁言是否已过期
     */
    public boolean isExpired() {
        // expireTime为0表示永久禁言
        if (expireTime == 0) {
            return false;
        }
        return System.currentTimeMillis() > expireTime;
    }

    /**
     * 检查是否为永久禁言
     */
    public boolean isPermanent() {
        return expireTime == 0;
    }

    /**
     * 获取剩余时间(毫秒)
     */
    public long getRemainingTime() {
        if (isPermanent()) {
            return -1;
        }
        long remaining = expireTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
