package gg.fotia.chat.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 表示一个可接收私聊、但不一定是 Bukkit 在线玩家的目标。
 */
public interface VirtualPrivateMessageTarget {

    UUID getUniqueId();

    String getName();

    default String getDisplayName() {
        return getName();
    }

    boolean receivePrivateMessage(Player sender, String message);
}
