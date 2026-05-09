package gg.fotia.chat.itemdisplay;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 物品快照记录
 */
public record ItemSnapshot(
        UUID id,
        UUID playerId,
        String playerName,
        Type type,
        ItemStack[] contents,
        long expireTime
) {
    public enum Type {
        HAND_ITEM,
        INVENTORY,
        ENDERCHEST
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }
}
